package io.github.sspanak.tt9.ime.voice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.speech.SpeechRecognizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.util.function.Consumer;

import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.util.Logger;

/**
 * Drives offline streaming recognition with Vosk and adapts its callbacks to the shape the rest of
 * SideType already expects (onStart / onPartial / onStop / onError — see {@link VoiceInputOps} and
 * {@link io.github.sspanak.tt9.ime.VoiceHandler}). Keeping that contract is what let the whole
 * engine swap happen without touching the mic-permission flow or the text-commit path.
 * <p>
 * Lifecycle: {@link #start} loads the model off the main thread (model init is slow), then opens a
 * {@link SpeechService} that captures the mic via AudioRecord and streams hypotheses. {@link #stop}
 * ends the utterance, which makes Vosk emit a final result that we commit.
 * <p>
 * Note {@link SpeechService} owns its AudioRecord privately and Vosk's listener exposes no
 * amplitude — there is no RMS here to drive a waveform, unlike the platform recognizer's
 * onRmsChanged. See SID-56.
 */
class VoskSpeechRecognizer implements RecognitionListener {
	private static final String LOG_TAG = VoskSpeechRecognizer.class.getSimpleName();

	@NonNull private final Context context;
	@NonNull private final Handler mainHandler = new Handler(Looper.getMainLooper());

	@NonNull private final Runnable onStart;
	@NonNull private final Consumer<String> onStop;
	@NonNull private final Consumer<String> onPartial;
	@NonNull private final Consumer<VoiceInputError> onError;

	@Nullable private Model model;
	@Nullable private SpeechService speechService;
	private boolean listening = false;

	// Loading the model takes up to ~1s (docs/adr/0001), and until it finishes there is no
	// SpeechService and nothing is listening yet. That gap needs its own state: without it, "not
	// listening" is indistinguishable from "idle", so a stop() lands on nothing and a second press
	// starts a second engine. Written on the main thread, read from the worker's post — hence volatile.
	private volatile boolean starting = false;
	private volatile boolean cancelled = false;

	// Vosk emits an endpointed final segment (onResult) whenever it detects a pause, plus a trailing
	// one on stop (onFinalResult). We accumulate those so multi-sentence dictation commits in full.
	@NonNull private final StringBuilder finalizedText = new StringBuilder();

	VoskSpeechRecognizer(
		@NonNull Context context,
		@NonNull Runnable onStart,
		@NonNull Consumer<String> onStop,
		@NonNull Consumer<String> onPartial,
		@NonNull Consumer<VoiceInputError> onError
	) {
		this.context = context;
		this.onStart = onStart;
		this.onStop = onStop;
		this.onPartial = onPartial;
		this.onError = onError;
	}

	boolean isListening() {
		return listening;
	}

	/**
	 * Whether this recognizer owns the microphone <i>or is about to</i>. Callers deciding whether to
	 * start another one, or whether a stop is meaningful, must ask this rather than
	 * {@link #isListening()} — during the model load the answer to "are you listening" is honestly
	 * "no", and acting on that starts a second engine over the top of this one.
	 */
	boolean isBusy() {
		return starting || listening;
	}

	/**
	 * Loads {@code modelDirPath} and begins listening. Safe to call on the main thread — the heavy
	 * model load happens on a worker thread and recognition starts back on the main thread after.
	 */
	void start(@NonNull String modelDirPath, @Nullable Language language) {
		if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			onError.accept(new VoiceInputError(context, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS));
			return;
		}

		if (isBusy()) {
			onError.accept(new VoiceInputError(context, SpeechRecognizer.ERROR_RECOGNIZER_BUSY));
			return;
		}

		finalizedText.setLength(0);
		starting = true;
		cancelled = false;

		new Thread(() -> {
			try {
				final Model loaded = new Model(modelDirPath);
				final Recognizer recognizer = new Recognizer(loaded, VoskModelCatalog.SAMPLE_RATE);
				// NB: SpeechService opens the AudioRecord in its constructor (hence the IOException), so
				// from here on the mic is held even if startListening() is never called. Every path out
				// of this method must release it.
				final SpeechService service = new SpeechService(recognizer, VoskModelCatalog.SAMPLE_RATE);

				mainHandler.post(() -> {
					starting = false;
					model = loaded;
					speechService = service;

					if (cancelled) {
						// Stopped while the model was loading. Release the mic we just opened and report a
						// clean stop with nothing to commit — the user asked for silence, so turning the
						// microphone on now would be the opposite of what they pressed.
						Logger.d(LOG_TAG, "Vosk start cancelled during model load; releasing the engine");
						releaseEngine();
						onStop.accept(null);
						return;
					}

					boolean started = service.startListening(this);
					if (started) {
						listening = true;
						Logger.d(LOG_TAG, "Vosk recognition started"
							+ (language != null ? " for " + language.getName() : ""));
						onStart.run();
					} else {
						Logger.e(LOG_TAG, "Vosk SpeechService.startListening() returned false");
						releaseEngine();
						onError.accept(new VoiceInputError(context, VoiceInputError.ERROR_NOT_AVAILABLE));
					}
				});
			} catch (Exception | UnsatisfiedLinkError e) {
				// UnsatisfiedLinkError here almost always means R8 stripped or renamed the JNA classes
				// Vosk resolves reflectively from native code. See proguard-rules.pro and docs/adr/0001.
				Logger.e(LOG_TAG, "Failed to start Vosk recognition: " + e.getMessage());
				mainHandler.post(() -> {
					starting = false;
					onError.accept(new VoiceInputError(context, VoiceInputError.ERROR_MODEL_LOAD_FAILED));
				});
			}
		}, "vosk-recognition").start();
	}

	/**
	 * Ends the current utterance; Vosk responds with a final result via {@link #onFinalResult}.
	 * <p>
	 * If the model is still loading there is nothing to stop yet, so this arms a cancel that the load
	 * checks when it lands. Without that, the stop hits an engine that does not exist and the mic
	 * switches on a moment later — after the user asked for it off.
	 */
	void stop() {
		if (starting) {
			cancelled = true;
			return;
		}

		if (speechService != null && listening) {
			speechService.stop();
		}
	}

	private void releaseEngine() {
		listening = false;
		if (speechService != null) {
			try {
				speechService.shutdown();
			} catch (Exception e) {
				Logger.w(LOG_TAG, "Vosk shutdown failed: " + e.getMessage());
			}
			speechService = null;
		}
		if (model != null) {
			try {
				model.close();
			} catch (Exception e) {
				Logger.w(LOG_TAG, "Vosk model close failed: " + e.getMessage());
			}
			model = null;
		}
	}

	// --- org.vosk.android.RecognitionListener (callbacks arrive on the main thread) ---

	@Override
	public void onPartialResult(String hypothesis) {
		String partial = extract(hypothesis, "partial");
		if (!partial.isEmpty()) {
			onPartial.accept(joinWithFinalized(partial));
		}
	}

	@Override
	public void onResult(String hypothesis) {
		// An endpointed segment inside an ongoing utterance — fold it into the accumulated text and
		// keep showing it as composing text until the whole utterance is committed.
		String text = extract(hypothesis, "text");
		if (!text.isEmpty()) {
			appendFinalized(text);
			onPartial.accept(finalizedText.toString());
		}
	}

	@Override
	public void onFinalResult(String hypothesis) {
		String text = extract(hypothesis, "text");
		if (!text.isEmpty()) {
			appendFinalized(text);
		}
		String committed = finalizedText.toString().trim();
		releaseEngine();
		onStop.accept(committed.isEmpty() ? null : committed);
	}

	@Override
	public void onError(Exception exception) {
		Logger.e(LOG_TAG, "Vosk recognition error: " + (exception == null ? "unknown" : exception.getMessage()));
		releaseEngine();
		onError.accept(new VoiceInputError(context, SpeechRecognizer.ERROR_CLIENT));
	}

	@Override
	public void onTimeout() {
		// Treat an engine timeout like a stop: commit whatever we have so far.
		String committed = finalizedText.toString().trim();
		releaseEngine();
		onStop.accept(committed.isEmpty() ? null : committed);
	}

	private void appendFinalized(@NonNull String text) {
		if (finalizedText.length() > 0) {
			finalizedText.append(' ');
		}
		finalizedText.append(text);
	}

	@NonNull
	private String joinWithFinalized(@NonNull String partial) {
		if (finalizedText.length() == 0) {
			return partial;
		}
		return finalizedText + " " + partial;
	}

	@NonNull
	private static String extract(@Nullable String voskJson, @NonNull String key) {
		if (voskJson == null || voskJson.isEmpty()) {
			return "";
		}
		try {
			return new JSONObject(voskJson).optString(key, "").trim();
		} catch (Exception e) {
			return "";
		}
	}
}
