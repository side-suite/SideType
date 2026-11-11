package io.github.sspanak.tt9.ime.voice;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.speech.SpeechRecognizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import io.github.sspanak.tt9.util.Logger;

/**
 * Records one utterance and transcribes it with Whisper: on the phone, or through an online
 * service when the user has entered an API key for one.
 * <p>
 * Whisper is not a streaming engine on this hardware: it takes the whole recording and returns the
 * whole text. So the lifecycle is {@link #start} (microphone on), {@link #stop} (microphone off,
 * transcription begins) and then {@code onStop} with the text. There are no partial results.
 * <p>
 * The model is loaded while the user is still speaking, so it is normally ready by the time they
 * stop, and it is kept in memory for a few minutes afterwards so the next utterance starts at once.
 */
class WhisperSpeechRecognizer {
	private static final String LOG_TAG = WhisperSpeechRecognizer.class.getSimpleName();

	/** Whisper works on windows of at most 30 s; a longer recording is cut off and transcribed. */
	private static final int MAX_SAMPLES = WhisperModelCatalog.SAMPLE_RATE * 30;
	/** Anything shorter than this is a slipped key, not speech. */
	private static final int MIN_SAMPLES = WhisperModelCatalog.SAMPLE_RATE * 3 / 10;
	/** The model stays loaded this long after the last use, then its memory is released. */
	private static final long UNLOAD_AFTER_IDLE_MS = 3 * 60 * 1000;

	// The encoder produces 50 frames per second of audio and by default always processes a full
	// 30 s (1500 frames), which is most of the cost on a slow phone. Limiting it to the recorded
	// length plus a margin makes a short utterance several times faster.
	//
	// The floor matters. The stock model was trained on full windows, and below roughly 8 s of
	// context it starts repeating itself: with a floor of 300 frames, a 2 s "Okay, what was that?"
	// came back twice, and so did a bare "Yes.". The same clips were correct at 400 and above, so
	// 500 leaves headroom. Do not lower it without re-running such short clips. Setting it to 1500
	// disables the shortcut altogether.
	private static final int AUDIO_CTX_FULL = 1500;
	private static final int AUDIO_CTX_MIN = 500;
	private static final int AUDIO_CTX_MARGIN = 50;
	private static final int SAMPLES_PER_FRAME = 320;

	private static final int STATE_IDLE = 0;
	private static final int STATE_RECORDING = 1;
	private static final int STATE_TRANSCRIBING = 2;

	// One engine thread and one loaded model for the whole process. Only the engine thread touches
	// the handle, which is what keeps the native context single-threaded.
	private static final ExecutorService engine = Executors.newSingleThreadExecutor(r -> new Thread(r, "whisper-engine"));
	private static long handle = 0;
	@Nullable private static String loadedPath = null;

	@NonNull private final Context context;
	@NonNull private final Handler mainHandler = new Handler(Looper.getMainLooper());

	@NonNull private final Runnable onStart;
	@NonNull private final Runnable onTranscribing;
	@NonNull private final Consumer<String> onStop;
	@NonNull private final Consumer<VoiceInputError> onError;

	private int state = STATE_IDLE; // main thread only
	private volatile boolean recording = false;
	private volatile boolean cancelled = false;
	private volatile int sampleCount = 0;
	@Nullable private short[] pcm;
	@Nullable private Thread recordThread;
	@Nullable private String modelPath;
	@Nullable private String languageCode;
	@Nullable private volatile RemoteWhisperClient remote;

	private final Runnable unloadWhenIdle = WhisperSpeechRecognizer::unload;

	WhisperSpeechRecognizer(
		@NonNull Context context,
		@NonNull Runnable onStart,
		@NonNull Runnable onTranscribing,
		@NonNull Consumer<String> onStop,
		@NonNull Consumer<VoiceInputError> onError
	) {
		this.context = context;
		this.onStart = onStart;
		this.onTranscribing = onTranscribing;
		this.onStop = onStop;
		this.onError = onError;
	}

	boolean isListening() {
		return state == STATE_RECORDING;
	}

	/** Recording or transcribing. Nothing new can start until this is false again. */
	boolean isBusy() {
		return state != STATE_IDLE;
	}

	/** Opens the microphone and starts recording. Call on the main thread. */
	@SuppressLint("MissingPermission") // checked explicitly below
	void start(@NonNull File modelFile, @NonNull String languageCode, @Nullable RemoteWhisperClient remoteClient) {
		if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			onError.accept(new VoiceInputError(context, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS));
			return;
		}

		if (isBusy()) {
			onError.accept(new VoiceInputError(context, SpeechRecognizer.ERROR_RECOGNIZER_BUSY));
			return;
		}

		final AudioRecord recorder;
		try {
			final int minBuffer = AudioRecord.getMinBufferSize(WhisperModelCatalog.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
			recorder = new AudioRecord(
				MediaRecorder.AudioSource.VOICE_RECOGNITION,
				WhisperModelCatalog.SAMPLE_RATE,
				AudioFormat.CHANNEL_IN_MONO,
				AudioFormat.ENCODING_PCM_16BIT,
				Math.max(minBuffer, WhisperModelCatalog.SAMPLE_RATE) * 2
			);
			if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
				recorder.release();
				throw new IllegalStateException("AudioRecord did not initialise");
			}
			recorder.startRecording();
		} catch (Exception e) {
			Logger.e(LOG_TAG, "Could not open the microphone: " + e.getMessage());
			onError.accept(new VoiceInputError(context, VoiceInputError.ERROR_NOT_AVAILABLE));
			return;
		}

		final short[] buffer = new short[MAX_SAMPLES];
		pcm = buffer;
		sampleCount = 0;
		cancelled = false;
		recording = true;
		state = STATE_RECORDING;

		final String modelPath = modelFile.getAbsolutePath();
		mainHandler.removeCallbacks(unloadWhenIdle);
		remote = remoteClient;
		if (remoteClient == null) {
			engine.execute(() -> ensureLoaded(modelPath)); // warm up while the user is talking
		}

		recordThread = new Thread(() -> record(recorder, buffer), "whisper-recorder");
		recordThread.start();

		this.modelPath = modelPath;
		this.languageCode = languageCode;
		onStart.run();
	}

	private void record(@NonNull AudioRecord recorder, @NonNull short[] buffer) {
		int count = 0;
		try {
			while (recording && count < buffer.length) {
				int read = recorder.read(buffer, count, Math.min(1600, buffer.length - count));
				if (read < 0) {
					Logger.e(LOG_TAG, "AudioRecord.read() failed: " + read);
					break;
				}
				count += read;
				sampleCount = count;
			}
		} finally {
			try {
				recorder.stop();
			} catch (Exception ignored) {}
			recorder.release();
		}

		if (recording) {
			// Hit the 30 s limit (or the microphone died) while the key was still down.
			mainHandler.post(this::stop);
		}
	}

	/** Closes the microphone and transcribes what was recorded. The text arrives through onStop. */
	void stop() {
		if (state != STATE_RECORDING) {
			return;
		}

		recording = false;
		state = STATE_TRANSCRIBING;
		if (!cancelled) {
			onTranscribing.run();
		}

		final Thread recorder = recordThread;
		final short[] buffer = pcm;
		final String path = modelPath;
		final String language = languageCode;
		recordThread = null;
		pcm = null;

		engine.execute(() -> {
			if (recorder != null) {
				try {
					recorder.join(2000);
				} catch (InterruptedException ignored) {}
			}

			final int count = sampleCount;
			if (cancelled || buffer == null || path == null) {
				finish(null, 0);
				return;
			}
			if (count < MIN_SAMPLES) {
				finish(null, SpeechRecognizer.ERROR_NO_MATCH);
				return;
			}

			// With an API key set, the recording goes to the online service. If that cannot be reached
			// and the local model is on disk, the phone transcribes it instead; a rejected key is
			// reported rather than papered over, or the user would never learn it is wrong.
			final RemoteWhisperClient online = remote;
			if (online != null) {
				try {
					final long startTime = System.currentTimeMillis();
					final String text = online.transcribe(buffer, count, language != null ? language : "en");
					Logger.d(LOG_TAG, "Transcribed online in " + (System.currentTimeMillis() - startTime) + " ms");
					finish(cancelled ? null : clean(text), 0);
					return;
				} catch (RemoteWhisperClient.UnauthorizedException e) {
					finish(null, cancelled ? 0 : VoiceInputError.ERROR_REMOTE_UNAUTHORIZED);
					return;
				} catch (Exception e) {
					Logger.w(LOG_TAG, "Online transcription failed: " + e.getMessage());
					if (cancelled) {
						finish(null, 0);
						return;
					}
					if (!new File(path).isFile()) {
						finish(null, VoiceInputError.ERROR_REMOTE_FAILED);
						return;
					}
				}
			}

			try {
				if (!ensureLoaded(path)) {
					finish(null, VoiceInputError.ERROR_MODEL_LOAD_FAILED);
					return;
				}

				final float[] samples = new float[count];
				for (int i = 0; i < count; i++) {
					samples[i] = buffer[i] / 32768f;
				}

				final long startTime = System.currentTimeMillis();
				final byte[] utf8 = WhisperLib.transcribe(handle, samples, language, getThreadCount(), getAudioCtx(count));
				Logger.d(LOG_TAG, "Transcribed " + (count / (float) WhisperModelCatalog.SAMPLE_RATE) + " s of audio in " + (System.currentTimeMillis() - startTime) + " ms");

				finish(cancelled || utf8 == null ? null : clean(new String(utf8, StandardCharsets.UTF_8)), 0);
			} catch (Throwable e) {
				// UnsatisfiedLinkError lands here too, if the native library is missing from the build.
				Logger.e(LOG_TAG, "Whisper transcription failed: " + e);
				finish(null, VoiceInputError.ERROR_MODEL_LOAD_FAILED);
			}
		});
	}

	/** Throws away the current recording or transcription. onStop still fires, with no text. */
	void cancel() {
		if (state == STATE_IDLE) {
			return;
		}

		cancelled = true;
		if (state == STATE_RECORDING) {
			stop();
		} else {
			final RemoteWhisperClient online = remote;
			if (online != null) {
				online.abort();
			}
			try {
				WhisperLib.abort();
			} catch (Throwable ignored) {}
		}
	}

	private void finish(@Nullable String text, int errorCode) {
		mainHandler.post(() -> {
			state = STATE_IDLE;
			mainHandler.removeCallbacks(unloadWhenIdle);
			mainHandler.postDelayed(unloadWhenIdle, UNLOAD_AFTER_IDLE_MS);

			if (errorCode != 0) {
				onError.accept(new VoiceInputError(context, errorCode));
			} else {
				onStop.accept(text);
			}
		});
	}

	/** Engine thread only. */
	private static boolean ensureLoaded(@NonNull String path) {
		try {
			if (handle != 0 && path.equals(loadedPath)) {
				return true;
			}
			if (handle != 0) {
				WhisperLib.free(handle);
				handle = 0;
			}

			final long startTime = System.currentTimeMillis();
			handle = WhisperLib.init(path);
			loadedPath = handle != 0 ? path : null;
			Logger.d(LOG_TAG, "Whisper model " + (handle != 0 ? "loaded" : "FAILED to load") + " in " + (System.currentTimeMillis() - startTime) + " ms");
			return handle != 0;
		} catch (Throwable e) {
			Logger.e(LOG_TAG, "Could not load the Whisper model: " + e);
			handle = 0;
			loadedPath = null;
			return false;
		}
	}

	/** Releases the loaded model. Safe from any thread; runs after whatever the engine is doing. */
	static void unload() {
		engine.execute(() -> {
			if (handle != 0) {
				try {
					WhisperLib.free(handle);
				} catch (Throwable ignored) {}
				handle = 0;
				loadedPath = null;
				Logger.d(LOG_TAG, "Whisper model unloaded");
			}
		});
	}

	private static int getThreadCount() {
		return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors()));
	}

	private static int getAudioCtx(int samples) {
		final int frames = (samples + SAMPLES_PER_FRAME - 1) / SAMPLES_PER_FRAME + AUDIO_CTX_MARGIN;
		return Math.max(AUDIO_CTX_MIN, Math.min(AUDIO_CTX_FULL, frames));
	}

	/**
	 * Whisper marks non-speech with bracketed notes such as "[BLANK_AUDIO]" or "(music)". They are
	 * never something the user said, so they are dropped. Returns null when nothing is left.
	 */
	@Nullable
	private static String clean(@NonNull String raw) {
		final String text = raw
			.replaceAll("\\[[^\\]]*\\]", " ")
			.replaceAll("\\([^)]*\\)", " ")
			.replaceAll("\\s+", " ")
			.trim();
		return text.isEmpty() ? null : text;
	}
}
