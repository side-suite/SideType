package io.github.sspanak.tt9.ime.voice;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Consumer;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.util.Logger;

/**
 * Voice input, backed exclusively by the offline Vosk engine.
 * <p>
 * The platform {@link android.speech.SpeechRecognizer} path this class used to wrap is gone. The
 * SP-01 ships a minimal AOSP with no Google speech service, so all of it — the online/offline
 * probes, the third-party recognizer picker, the Android 13+ support checks — was dead code on the
 * only device SideType targets. See docs/adr/0002-voice-input-lives-in-sidetype.md.
 * <p>
 * Callbacks keep the shape {@link io.github.sspanak.tt9.ime.VoiceHandler} already expected, so the
 * mic-permission flow and the composing-text/commit path are reused unchanged.
 */
public class VoiceInputOps {
	private final static String LOG_TAG = VoiceInputOps.class.getSimpleName();

	@NonNull private final Context ims;
	@Nullable private Language language;
	@NonNull private final VoskModelManager modelManager;
	@Nullable private VoskSpeechRecognizer recognizer;

	@NonNull private final Runnable onStartListening;
	@NonNull private final Consumer<String> onStopListening;
	@NonNull private final Consumer<String> onPartialResult;
	@NonNull private final Consumer<VoiceInputError> onListeningError;


	public VoiceInputOps(
		@NonNull Context ims,
		@Nullable Runnable onStart,
		@Nullable Consumer<String> onStop,
		@Nullable Consumer<String> onPartial,
		@Nullable Consumer<VoiceInputError> onError
	) {
		this.ims = ims;
		modelManager = new VoskModelManager(ims);

		onStartListening = onStart != null ? onStart : () -> {};
		onStopListening = onStop != null ? onStop : result -> {};
		onPartialResult = onPartial != null ? onPartial : result -> {};
		onListeningError = onError != null ? onError : error -> {};
	}


	/**
	 * Whether voice input could work for this language at all — i.e. whether Vosk publishes a model
	 * for it. Deliberately <b>not</b> "is a model downloaded": models arrive on demand, and gating
	 * visibility on the disk state deadlocks (no model → the control hides → the download that would
	 * unhide it can never be triggered). Static because it is a question about the catalog, not about
	 * any particular instance.
	 */
	public static boolean isAvailable(@Nullable Language language) {
		return VoskModelCatalog.isSupported(language);
	}


	public boolean isListening() {
		return recognizer != null && recognizer.isListening();
	}


	/** Whether the model for this language still needs downloading before {@link #listen} can work. */
	public boolean isModelMissing(@Nullable Language language) {
		VoskModelCatalog.Entry model = VoskModelCatalog.getModel(language);
		return model != null && !modelManager.isModelReady(model);
	}


	@Nullable
	public VoskModelCatalog.Entry getModel(@Nullable Language language) {
		return VoskModelCatalog.getModel(language);
	}


	public void listen(@Nullable Language language) {
		this.language = language;

		if (language == null) {
			onListeningError.accept(new VoiceInputError(ims, VoiceInputError.ERROR_INVALID_LANGUAGE));
			return;
		}

		if (isListening()) {
			onListeningError.accept(new VoiceInputError(ims, android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY));
			return;
		}

		final VoskModelCatalog.Entry model = VoskModelCatalog.getModel(language);
		if (model == null) {
			// Finnish, Norwegian and Danish land here, permanently — Vosk has no model for them at any
			// size. Not a failure to handle, a limitation to state. See docs/adr/0001.
			Logger.i(LOG_TAG, "No Vosk model exists for language: " + language.getName());
			onListeningError.accept(new VoiceInputError(ims, VoiceInputError.ERROR_NO_MODEL_FOR_LANGUAGE));
			return;
		}

		if (!modelManager.isModelReady(model)) {
			// Downloading ~40 MB requires the user's explicit consent first, which needs an Activity —
			// an IME cannot show a dialog. Until SID-55 wires that up, refuse rather than surprise them.
			Logger.i(LOG_TAG, "Vosk model not downloaded for: " + language.getName());
			onListeningError.accept(new VoiceInputError(ims, VoiceInputError.ERROR_MODEL_NOT_DOWNLOADED));
			return;
		}

		recognizer = new VoskSpeechRecognizer(ims, onStartListening, this::onStop, onPartialResult, this::onError);
		recognizer.start(modelManager.getModelDir(model).getAbsolutePath(), language);
	}


	public void stop() {
		if (recognizer != null && isListening()) {
			recognizer.stop();
		}
	}


	private void onStop(@Nullable String result) {
		language = null;
		recognizer = null;
		onStopListening.accept(result);
	}


	private void onError(@NonNull VoiceInputError error) {
		language = null;
		recognizer = null;
		onListeningError.accept(error);
	}


	@NonNull
	@Override
	public String toString() {
		String languageSuffix = language == null ? "" : " / " + language.getName();
		return ims.getString(R.string.voice_input_listening) + languageSuffix;
	}
}
