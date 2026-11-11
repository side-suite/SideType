package io.github.sspanak.tt9.ime.voice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.function.Consumer;

import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.util.Logger;

/**
 * On-demand download of the Whisper model: one file, fetched to a ".part" name, checked against the
 * pinned size and SHA-256, and only then renamed into place. A half-finished or tampered download
 * therefore never looks like a usable model.
 * <p>
 * This class downloads whatever it is asked to. Asking the user first is the caller's job.
 */
class WhisperModelManager {
	private static final String LOG_TAG = WhisperModelManager.class.getSimpleName();
	private static final String MODELS_SUBDIR = "whisper-models";
	/** Models of the engine this one replaced. Deleted once, so they do not sit on disk forever. */
	private static final String LEGACY_MODELS_SUBDIR = "vosk-models";
	private static final int PROGRESS_STEP_PERCENT = 2;

	// Shared by every instance: the keyboard and the settings screen each create their own manager,
	// but they live in one process and must not start two downloads of the same file.
	private static volatile boolean downloading = false;
	private static volatile boolean legacyCleanupDone = false;

	@NonNull private final Context context;
	@NonNull private final Handler mainHandler = new Handler(Looper.getMainLooper());

	// Nothing here may touch the context: the keyboard service creates one of these from a field
	// initialiser, before Android has attached its base context, and any call on it would crash.
	WhisperModelManager(@NonNull Context context) {
		this.context = context;
	}

	@NonNull
	private File getModelsRoot() {
		return new File(context.getFilesDir(), MODELS_SUBDIR);
	}

	@NonNull
	File getModelFile(@NonNull WhisperModelCatalog.Entry model) {
		return new File(getModelsRoot(), model.fileName);
	}

	@NonNull
	private File getPartFile(@NonNull WhisperModelCatalog.Entry model) {
		return new File(getModelsRoot(), model.fileName + ".part");
	}

	/** The file only gets its final name after verification, so the size check is a cheap sanity test. */
	boolean isModelReady(@NonNull WhisperModelCatalog.Entry model) {
		deleteLegacyModels();
		File file = getModelFile(model);
		return file.isFile() && file.length() == model.sizeBytes;
	}

	boolean isDownloading() {
		return downloading;
	}

	boolean deleteModel(@NonNull WhisperModelCatalog.Entry model) {
		deleteRecursive(getModelFile(model));
		deleteRecursive(getPartFile(model));
		return !getModelFile(model).exists();
	}

	/**
	 * Downloads and verifies the model on a background thread, then calls {@code onReady} on the main
	 * thread. {@code onProgress} receives 0..100. {@code onError} receives a {@link VoiceInputError}
	 * code. No-op if the model is already there; refuses if a download is already running.
	 */
	void ensureModel(
		@NonNull WhisperModelCatalog.Entry model,
		@NonNull Runnable onReady,
		@Nullable Consumer<Integer> onProgress,
		@NonNull Consumer<Integer> onError
	) {
		if (isModelReady(model)) {
			onReady.run();
			return;
		}
		if (downloading) {
			onError.accept(VoiceInputError.ERROR_MODEL_DOWNLOAD_IN_PROGRESS);
			return;
		}

		downloading = true;
		new Thread(() -> {
			int error = 0;
			try {
				download(model, onProgress);
			} catch (IntegrityException e) {
				Logger.e(LOG_TAG, "Whisper model failed verification: " + e.getMessage());
				error = VoiceInputError.ERROR_MODEL_CORRUPTED;
			} catch (Exception e) {
				Logger.e(LOG_TAG, "Whisper model download failed: " + e.getMessage());
				error = VoiceInputError.ERROR_MODEL_DOWNLOAD_FAILED;
			}

			final int result = error;
			mainHandler.post(() -> {
				downloading = false;
				if (result == 0) {
					onReady.run();
				} else {
					onError.accept(result);
				}
			});
		}, "whisper-model-download").start();
	}

	private void download(@NonNull WhisperModelCatalog.Entry model, @Nullable Consumer<Integer> onProgress) throws IOException, IntegrityException {
		//noinspection ResultOfMethodCallIgnored
		getModelsRoot().mkdirs();

		final File part = getPartFile(model);
		boolean done = false;
		try {
			fetchAndVerify(model, part, onProgress);

			final File target = getModelFile(model);
			deleteRecursive(target);
			if (!part.renameTo(target)) {
				throw new IOException("Could not move the model into place: " + target.getAbsolutePath());
			}
			done = true;
			Logger.d(LOG_TAG, "Whisper model ready at: " + target.getAbsolutePath());
		} finally {
			if (!done) {
				deleteRecursive(part);
			}
		}
	}

	private void fetchAndVerify(@NonNull WhisperModelCatalog.Entry model, @NonNull File part, @Nullable Consumer<Integer> onProgress) throws IOException, IntegrityException {
		final String url = model.getUrl();
		Logger.d(LOG_TAG, "Downloading Whisper model: " + url);

		URLConnection connection = new URL(url).openConnection();
		connection.setConnectTimeout(SettingsStore.DICTIONARY_DOWNLOAD_CONNECTION_TIMEOUT);
		connection.setReadTimeout(SettingsStore.DICTIONARY_DOWNLOAD_READ_TIMEOUT);
		connection.connect();

		final long advertisedSize = connection.getContentLengthLong();
		if (advertisedSize > 0 && advertisedSize != model.sizeBytes) {
			throw new IntegrityException("expected " + model.sizeBytes + " bytes, server advertises " + advertisedSize);
		}

		final MessageDigest sha256 = newSha256();
		long total = 0;
		int lastReportedPercent = -1;

		try (
			DigestInputStream in = new DigestInputStream(connection.getInputStream(), sha256);
			OutputStream out = new FileOutputStream(part)
		) {
			byte[] buffer = new byte[16384];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
				total += read;
				if (total > model.sizeBytes) {
					throw new IntegrityException("received more than the expected " + model.sizeBytes + " bytes");
				}

				if (onProgress != null) {
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

		final String actual = toHex(sha256.digest());
		if (!actual.equalsIgnoreCase(model.sha256)) {
			throw new IntegrityException("sha256 mismatch: expected " + model.sha256 + ", got " + actual);
		}
	}

	private void deleteLegacyModels() {
		if (legacyCleanupDone) {
			return;
		}
		legacyCleanupDone = true;

		final File legacy = new File(context.getFilesDir(), LEGACY_MODELS_SUBDIR);
		if (legacy.exists()) {
			new Thread(() -> deleteRecursive(legacy), "legacy-voice-model-cleanup").start();
		}
	}

	@NonNull
	private static MessageDigest newSha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is unavailable on this device", e);
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

	/** The downloaded bytes are not what the catalogue pinned. Distinct from a network failure. */
	private static class IntegrityException extends Exception {
		IntegrityException(String message) {
			super(message);
		}
	}
}
