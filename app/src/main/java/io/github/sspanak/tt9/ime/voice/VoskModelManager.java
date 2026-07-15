package io.github.sspanak.tt9.ime.voice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
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
 * It deliberately departs from that mechanism in two ways. A dictionary is a single streamed zip
 * entry, whereas a Vosk model is a <i>directory tree</i>, so the whole zip is unpacked and Vosk is
 * handed the resulting path. And a dictionary is a few MB fetched silently, whereas a model is
 * ~40 MB — so callers must obtain explicit consent first (SID-55). This class will happily download
 * whatever it is asked to; it is not the place that decides whether it should.
 * <p>
 * md5 verification and byte-level progress are SID-54.
 */
class VoskModelManager {
	private static final String LOG_TAG = VoskModelManager.class.getSimpleName();
	private static final String MODELS_SUBDIR = "vosk-models";

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

	private String buildDownloadUrl(@NonNull VoskModelCatalog.Entry model) {
		return context.getString(R.string.vosk_model_url, model.zipFileName);
	}

	/**
	 * Downloads and unpacks the model on a background thread, then invokes {@code onReady} on the
	 * main thread. {@code onError} gets a human-readable reason on failure. No-op if already ready;
	 * refuses if a download is already running.
	 */
	void ensureModel(@NonNull VoskModelCatalog.Entry model, @NonNull Runnable onReady, @NonNull Consumer<String> onError) {
		if (isModelReady(model)) {
			onReady.run();
			return;
		}
		if (downloading) {
			onError.accept("A voice model download is already in progress.");
			return;
		}

		downloading = true;
		new Thread(() -> {
			try {
				downloadAndUnpack(model);
				mainHandler.post(() -> {
					downloading = false;
					onReady.run();
				});
			} catch (Exception e) {
				Logger.e(LOG_TAG, "Vosk model download/unpack failed: " + e.getMessage());
				mainHandler.post(() -> {
					downloading = false;
					onError.accept(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
				});
			}
		}, "vosk-model-download").start();
	}

	private void downloadAndUnpack(@NonNull VoskModelCatalog.Entry model) throws IOException {
		final String url = buildDownloadUrl(model);
		Logger.d(LOG_TAG, "Downloading Vosk model: " + url);

		File modelsRoot = getModelsRoot();
		//noinspection ResultOfMethodCallIgnored
		modelsRoot.mkdirs();

		// Unpack into a temp dir and rename only on success, so an interrupted download never leaves
		// a directory that isModelReady() would wrongly accept. Killing the app mid-download must
		// leave nothing that looks usable.
		File tempDir = new File(modelsRoot, model.unpackedDirName + ".tmp");
		deleteRecursive(tempDir);
		//noinspection ResultOfMethodCallIgnored
		tempDir.mkdirs();

		URLConnection connection = new URL(url).openConnection();
		connection.setConnectTimeout(SettingsStore.DICTIONARY_DOWNLOAD_CONNECTION_TIMEOUT);
		connection.setReadTimeout(SettingsStore.DICTIONARY_DOWNLOAD_READ_TIMEOUT);

		try (ZipInputStream zip = new ZipInputStream(connection.getInputStream())) {
			byte[] buffer = new byte[8192];
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
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
					while ((read = zip.read(buffer)) != -1) {
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
}
