package io.github.sspanak.tt9.ime.voice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.util.Logger;

/**
 * On-demand download + unpack of a Vosk acoustic model, modelled on the existing on-demand
 * dictionary mechanism ({@link io.github.sspanak.tt9.util.RemoteAssetFile} /
 * {@link io.github.sspanak.tt9.db.words.DictionaryLoader}) — same URL-from-a-string-resource shape,
 * same {@link SettingsStore} timeouts, same off-the-main-thread download.
 * <p>
 * It departs from that mechanism deliberately, in three ways:
 * <ul>
 *   <li>A dictionary is a single streamed zip entry; a Vosk model is a <i>directory tree</i>, so the
 *       zip is unpacked whole and Vosk is handed the resulting path.</li>
 *   <li>A dictionary is parsed straight off the network with nothing verifying it. A model is
 *       <b>md5-checked against the pinned digest before it is unpacked</b> — see
 *       {@link #downloadZip}. This is the first integrity check in the codebase; the dictionary
 *       mechanism is not a precedent to copy here.</li>
 *   <li>A dictionary is a few MB fetched silently; a model is ~40 MB, so callers must obtain
 *       explicit consent first (SID-55). This class downloads whatever it is asked to — it is not
 *       the place that decides whether it should.</li>
 * </ul>
 * See docs/adr/0003-voice-model-delivery.md.
 */
class VoskModelManager {
	private static final String LOG_TAG = VoskModelManager.class.getSimpleName();
	private static final String MODELS_SUBDIR = "vosk-models";
	/** Report progress at most this often, so a 40 MB download doesn't post ~5000 UI updates. */
	private static final int PROGRESS_STEP_PERCENT = 2;

	@NonNull private final Context context;
	@NonNull private final Handler mainHandler = new Handler(Looper.getMainLooper());
	private volatile boolean downloading = false;

	VoskModelManager(@NonNull Context context) {
		this.context = context;
	}

	@NonNull
	private File getModelsRoot() {
		return new File(context.getFilesDir(), MODELS_SUBDIR);
	}

	/** Absolute path Vosk should load, once the model is unpacked. */
	@NonNull
	File getModelDir(@NonNull VoskModelCatalog.Entry model) {
		return new File(getModelsRoot(), model.unpackedDirName);
	}

	/**
	 * Whether the model is on disk and usable. Vosk models always contain a top-level {@code am/}
	 * (acoustic model) directory, so it serves as a cheap completeness marker that a half-finished
	 * unpack would not satisfy.
	 * <p>
	 * Not the same question as {@link VoskModelCatalog#isSupported} — see its docs. Never gate the
	 * visibility of a control on this, or the download can never be triggered.
	 */
	boolean isModelReady(@NonNull VoskModelCatalog.Entry model) {
		File dir = getModelDir(model);
		return dir.isDirectory() && new File(dir, "am").isDirectory();
	}

	boolean isDownloading() {
		return downloading;
	}

	/** Deletes a downloaded model. Returns true if nothing of it remains on disk afterwards. */
	boolean deleteModel(@NonNull VoskModelCatalog.Entry model) {
		deleteRecursive(getModelDir(model));
		deleteRecursive(getZipFile(model));
		return !getModelDir(model).exists();
	}

	@NonNull
	private File getZipFile(@NonNull VoskModelCatalog.Entry model) {
		return new File(getModelsRoot(), model.unpackedDirName + ".zip.part");
	}

	private String buildDownloadUrl(@NonNull VoskModelCatalog.Entry model) {
		return context.getString(R.string.vosk_model_url, model.zipFileName);
	}

	/**
	 * Downloads, verifies and unpacks the model on a background thread, then invokes {@code onReady}
	 * on the main thread. {@code onProgress} receives 0..100 as the bytes arrive. {@code onError}
	 * gets a {@link VoiceInputError} code on failure. No-op if already ready; refuses if a download
	 * is already running.
	 */
	void ensureModel(
		@NonNull VoskModelCatalog.Entry model,
		@NonNull Runnable onReady,
		@Nullable Consumer<Integer> onProgress,
		@NonNull Consumer<Integer> onError
	) {
		if (isModelReady(model)) {
			onReady.run();
			return;
		}
		if (downloading) {
			// Already running is not a failure. Reporting one would tell the user their download broke
			// while it is in fact still going.
			onError.accept(VoiceInputError.ERROR_MODEL_DOWNLOAD_IN_PROGRESS);
			return;
		}

		downloading = true;
		new Thread(() -> {
			try {
				downloadAndUnpack(model, onProgress);
				mainHandler.post(() -> {
					downloading = false;
					onReady.run();
				});
			} catch (IntegrityException e) {
				Logger.e(LOG_TAG, "Vosk model failed verification: " + e.getMessage());
				mainHandler.post(() -> {
					downloading = false;
					onError.accept(VoiceInputError.ERROR_MODEL_CORRUPTED);
				});
			} catch (Exception e) {
				Logger.e(LOG_TAG, "Vosk model download/unpack failed: " + e.getMessage());
				mainHandler.post(() -> {
					downloading = false;
					onError.accept(VoiceInputError.ERROR_MODEL_DOWNLOAD_FAILED);
				});
			}
		}, "vosk-model-download").start();
	}

	private void downloadAndUnpack(@NonNull VoskModelCatalog.Entry model, @Nullable Consumer<Integer> onProgress) throws IOException, IntegrityException {
		File modelsRoot = getModelsRoot();
		//noinspection ResultOfMethodCallIgnored
		modelsRoot.mkdirs();

		File zip = getZipFile(model);
		try {
			// Download to a file and verify BEFORE unpacking. Streaming the response straight into a
			// ZipInputStream would be leaner, but then the md5 is only known once the bytes are already
			// unpacked on disk as a model — which defeats the point of checking them.
			downloadZip(model, zip, onProgress);
			unpack(model, zip);
		} finally {
			deleteRecursive(zip);
		}
	}

	/** Fetches the zip and verifies its size and md5 against the pinned catalog entry. */
	private void downloadZip(@NonNull VoskModelCatalog.Entry model, @NonNull File zip, @Nullable Consumer<Integer> onProgress) throws IOException, IntegrityException {
		final String url = buildDownloadUrl(model);
		Logger.d(LOG_TAG, "Downloading Vosk model: " + url);

		URLConnection connection = new URL(url).openConnection();
		connection.setConnectTimeout(SettingsStore.DICTIONARY_DOWNLOAD_CONNECTION_TIMEOUT);
		connection.setReadTimeout(SettingsStore.DICTIONARY_DOWNLOAD_READ_TIMEOUT);
		connection.connect();

		final long advertisedSize = connection.getContentLengthLong();
		if (advertisedSize > 0 && advertisedSize != model.sizeBytes) {
			// The server is offering something other than what we pinned. Most likely the model was
			// republished and our catalog is stale (Vosk keeps obsolete revisions downloadable), so
			// stop rather than show the user a size we already know is a lie.
			throw new IntegrityException(
				"expected " + model.sizeBytes + " bytes, server advertises " + advertisedSize
			);
		}

		final MessageDigest md5 = newMd5();
		long total = 0;
		int lastReportedPercent = -1;

		try (
			DigestInputStream in = new DigestInputStream(connection.getInputStream(), md5);
			OutputStream out = new FileOutputStream(zip)
		) {
			byte[] buffer = new byte[8192];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
				total += read;

				if (onProgress != null && model.sizeBytes > 0) {
					int percent = (int) Math.min(100, total * 100 / model.sizeBytes);
					if (percent >= lastReportedPercent + PROGRESS_STEP_PERCENT) {
						lastReportedPercent = percent;
						final int reported = percent;
						mainHandler.post(() -> onProgress.accept(reported));
					}
				}
			}
		}

		if (total != model.sizeBytes) {
			throw new IntegrityException("expected " + model.sizeBytes + " bytes, received " + total);
		}

		final String actualMd5 = toHex(md5.digest());
		if (!actualMd5.equalsIgnoreCase(model.md5)) {
			throw new IntegrityException("md5 mismatch: expected " + model.md5 + ", got " + actualMd5);
		}

		Logger.d(LOG_TAG, "Vosk model verified: " + model.zipFileName + " (" + total + " bytes, md5 ok)");
	}

	private void unpack(@NonNull VoskModelCatalog.Entry model, @NonNull File zip) throws IOException {
		// Unpack into a temp dir and rename only on success, so an interrupted unpack never leaves a
		// directory that isModelReady() would wrongly accept.
		File tempDir = new File(getModelsRoot(), model.unpackedDirName + ".tmp");
		deleteRecursive(tempDir);
		//noinspection ResultOfMethodCallIgnored
		tempDir.mkdirs();

		try (ZipInputStream zipStream = new ZipInputStream(new FileInputStream(zip))) {
			byte[] buffer = new byte[8192];
			ZipEntry entry;
			while ((entry = zipStream.getNextEntry()) != null) {
				// The zip's single top-level dir is `unpackedDirName`; strip it so files land directly
				// under tempDir, which we then rename to the final unpackedDirName.
				String relative = stripTopLevelDir(entry.getName());
				if (relative.isEmpty()) {
					continue;
				}

				File outFile = new File(tempDir, relative);
				if (!isInside(tempDir, outFile)) {
					throw new IOException("Zip entry escapes target dir (zip-slip): " + entry.getName());
				}

				if (entry.isDirectory()) {
					//noinspection ResultOfMethodCallIgnored
					outFile.mkdirs();
					continue;
				}

				File parent = outFile.getParentFile();
				if (parent != null) {
					//noinspection ResultOfMethodCallIgnored
					parent.mkdirs();
				}

				try (OutputStream out = new FileOutputStream(outFile)) {
					int read;
					while ((read = zipStream.read(buffer)) != -1) {
						out.write(buffer, 0, read);
					}
				}
			}
		}

		File finalDir = getModelDir(model);
		deleteRecursive(finalDir);
		if (!tempDir.renameTo(finalDir)) {
			throw new IOException("Could not finalize model directory: " + finalDir.getAbsolutePath());
		}

		Logger.d(LOG_TAG, "Vosk model ready at: " + finalDir.getAbsolutePath());
	}

	@NonNull
	private static MessageDigest newMd5() {
		try {
			return MessageDigest.getInstance("MD5");
		} catch (NoSuchAlgorithmException e) {
			// Every Android release ships MD5; if it is gone, something is very wrong.
			throw new IllegalStateException("MD5 is unavailable on this device", e);
		}
	}

	@NonNull
	private static String toHex(@NonNull byte[] bytes) {
		StringBuilder hex = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			hex.append(String.format(Locale.ROOT, "%02x", b));
		}
		return hex.toString();
	}

	private static String stripTopLevelDir(@NonNull String entryName) {
		int slash = entryName.indexOf('/');
		return slash < 0 ? "" : entryName.substring(slash + 1);
	}

	private static boolean isInside(@NonNull File dir, @NonNull File child) {
		try {
			return child.getCanonicalPath().startsWith(dir.getCanonicalPath() + File.separator);
		} catch (IOException e) {
			return false;
		}
	}

	private static void deleteRecursive(@NonNull File file) {
		File[] children = file.listFiles();
		if (children != null) {
			for (File child : children) {
				deleteRecursive(child);
			}
		}
		//noinspection ResultOfMethodCallIgnored
		file.delete();
	}

	/** The downloaded bytes are not what the catalog pinned. Distinct from a network failure. */
	private static class IntegrityException extends Exception {
		IntegrityException(String message) {
			super(message);
		}
	}
}
