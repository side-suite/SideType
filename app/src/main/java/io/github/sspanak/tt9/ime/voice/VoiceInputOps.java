package io.github.sspanak.tt9.ime.voice;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Consumer;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.languages.LanguageCollection;
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


	/**
	 * Whether <i>any</i> of the given languages has a model — i.e. whether voice input is worth
	 * offering to this user at all, as opposed to right now. The tray uses it to decide if the mic
	 * belongs on the strip, and the settings panel to decide if the hotkey is worth binding; both need
	 * the same answer, so it lives here rather than being re-looped in each.
	 */
	public static boolean isAvailableForAny(@NonNull Iterable<Integer> languageIds) {
		for (int languageId : languageIds) {
			if (isAvailable(LanguageCollection.getLanguage(languageId))) {
				return true;
			}
		}
		return false;
	}


	public boolean isListening() {
		return recognizer != null && recognizer.isListening();
	}


	/**
	 * Whether voice input owns the microphone <i>or is about to</i> — true from the moment the engine
	 * starts loading, not just once it is listening. Anything deciding "start or stop?" must ask this;
	 * see {@link VoskSpeechRecognizer#isBusy()}.
	 */
	public boolean isBusy() {
		return recognizer != null && recognizer.isBusy();
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


	/** Whether the model for this language is downloaded and usable. */
	public boolean isModelReady(@Nullable Language language) {
		VoskModelCatalog.Entry model = VoskModelCatalog.getModel(language);
		return model != null && modelManager.isModelReady(model);
	}


	public boolean isDownloadingModel() {
		return modelManager.isDownloading();
	}


	/**
	 * Downloads, verifies and unpacks the model for this language. <b>Callers must have obtained the
	 * user's explicit consent first</b> — this is tens of megabytes and must never be triggered by a
	 * keypress alone. {@code onProgress} receives 0..100.
	 */
	public void downloadModel(
		@Nullable Language language,
		@NonNull Runnable onReady,
		@Nullable Consumer<Integer> onProgress,
		@NonNull Consumer<VoiceInputError> onError
	) {
		VoskModelCatalog.Entry model = VoskModelCatalog.getModel(language);
		if (model == null) {
			onError.accept(new VoiceInputError(ims, VoiceInputError.ERROR_NO_MODEL_FOR_LANGUAGE));
			return;
		}

		modelManager.ensureModel(model, onReady, onProgress, code -> onError.accept(new VoiceInputError(ims, code)));
	}


	/** Deletes the downloaded model for this language, freeing its tens of megabytes. */
	public boolean deleteModel(@Nullable Language language) {
		VoskModelCatalog.Entry model = VoskModelCatalog.getModel(language);
		return model != null && modelManager.deleteModel(model);
	}


	public void listen(@Nullable Language language) {
		this.language = language;

		if (language == null) {
			onListeningError.accept(new VoiceInputError(ims, VoiceInputError.ERROR_INVALID_LANGUAGE));
			return;
		}

		// isBusy(), not isListening(): during the model load nothing is listening yet, and starting a
		// second engine here would orphan the first one with its microphone already open.
		if (isBusy()) {
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
			// A backstop, not the consent prompt. Callers ask first — VoiceHandler.toggleVoiceInput()
			// puts up VoiceModelDownloadDialog, and the settings panel downloads on an explicit tap. If
			// we get here the model simply is not on disk, so refuse rather than fetch ~40 MB unasked.
			Logger.i(LOG_TAG, "Vosk model not downloaded for: " + language.getName());
			onListeningError.accept(new VoiceInputError(ims, VoiceInputError.ERROR_MODEL_NOT_DOWNLOADED));
			return;
		}

		recognizer = new VoskSpeechRecognizer(ims, onStartListening, this::onStop, onPartialResult, this::onError);
		recognizer.start(modelManager.getModelDir(model).getAbsolutePath(), language);
	}


	public void stop() {
		// No isListening() gate: the recognizer decides what stopping means for it, because during the
		// model load the answer is "cancel the load", not "stop the stream".
		if (recognizer != null) {
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
