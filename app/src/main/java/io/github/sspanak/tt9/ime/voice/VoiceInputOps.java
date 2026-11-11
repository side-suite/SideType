package io.github.sspanak.tt9.ime.voice;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Consumer;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.util.Logger;

/**
 * Voice input, backed by Whisper: on the phone (whisper.cpp, see src/main/cpp), or through an online
 * service when the user has entered an API key ({@link RemoteWhisperClient}).
 * <p>
 * One multilingual model serves every language; the keyboard's current language tells Whisper what
 * to expect. Recognition is record-then-transcribe: {@link #listen} opens the microphone,
 * {@link #stop} closes it and starts the transcription, and the text arrives through the
 * {@code onStop} callback. {@link #cancel} throws the recording away instead.
 */
public class VoiceInputOps {
	private final static String LOG_TAG = VoiceInputOps.class.getSimpleName();

	@NonNull private final Context ims;
	@Nullable private Language language;
	@NonNull private final WhisperModelManager modelManager;
	@NonNull private final WhisperSpeechRecognizer recognizer;

	@NonNull private final Consumer<String> onStopListening;
	@NonNull private final Consumer<VoiceInputError> onListeningError;


	public VoiceInputOps(
		@NonNull Context ims,
		@Nullable Runnable onStart,
		@Nullable Runnable onTranscribing,
		@Nullable Consumer<String> onStop,
		@Nullable Consumer<VoiceInputError> onError
	) {
		this.ims = ims;
		modelManager = new WhisperModelManager(ims);

		onStopListening = onStop != null ? onStop : result -> {};
		onListeningError = onError != null ? onError : error -> {};

		recognizer = new WhisperSpeechRecognizer(
			ims,
			onStart != null ? onStart : () -> {},
			onTranscribing != null ? onTranscribing : () -> {},
			this::onStop,
			this::onError
		);
	}


	/**
	 * Whether voice input can work for this language at all, i.e. whether Whisper was trained on it.
	 * Deliberately <b>not</b> "is the model downloaded": the model arrives on demand, and gating a
	 * control on the disk state would hide the very thing that triggers the download.
	 */
	public static boolean isAvailable(@Nullable Language language) {
		return WhisperModelCatalog.isSupported(language);
	}


	/** Whether the microphone is open right now. */
	public boolean isListening() {
		return recognizer.isListening();
	}


	/** Recording <i>or transcribing</i>. Anything deciding "start or stop?" must ask this. */
	public boolean isBusy() {
		return recognizer.isBusy();
	}


	@NonNull
	public WhisperModelCatalog.Entry getModel() {
		return WhisperModelCatalog.MODEL;
	}


	public boolean isModelReady() {
		return modelManager.isModelReady(WhisperModelCatalog.MODEL);
	}


	public boolean isDownloadingModel() {
		return modelManager.isDownloading();
	}


	/**
	 * Downloads and verifies the model. <b>Callers must have obtained the user's explicit consent
	 * first</b>: it is tens of megabytes and must never be triggered by a keypress alone.
	 * {@code onProgress} receives 0..100.
	 */
	public void downloadModel(
		@NonNull Runnable onReady,
		@Nullable Consumer<Integer> onProgress,
		@NonNull Consumer<VoiceInputError> onError
	) {
		modelManager.ensureModel(WhisperModelCatalog.MODEL, onReady, onProgress, code -> onError.accept(new VoiceInputError(ims, code)));
	}


	/** Deletes the downloaded model and releases it from memory. */
	public boolean deleteModel() {
		WhisperSpeechRecognizer.unload();
		return modelManager.deleteModel(WhisperModelCatalog.MODEL);
	}


	/**
	 * Opens the microphone. With {@code remote} set, the recording is transcribed by that online
	 * service and the local model is only a fallback, so it does not have to be downloaded.
	 */
	public void listen(@Nullable Language language, @Nullable RemoteWhisperClient remote) {
		if (language == null) {
			onListeningError.accept(new VoiceInputError(ims, VoiceInputError.ERROR_INVALID_LANGUAGE));
			return;
		}

		if (isBusy()) {
			onListeningError.accept(new VoiceInputError(ims, android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY));
			return;
		}

		final String languageCode = WhisperModelCatalog.getLanguageCode(language);
		if (languageCode == null) {
			Logger.i(LOG_TAG, "Whisper does not support language: " + language.getName());
			onListeningError.accept(new VoiceInputError(ims, VoiceInputError.ERROR_NO_MODEL_FOR_LANGUAGE));
			return;
		}

		if (remote == null && !isModelReady()) {
			// A backstop, not the consent prompt: callers ask first. If we get here the model simply is
			// not on disk, so refuse rather than fetch it unasked.
			onListeningError.accept(new VoiceInputError(ims, VoiceInputError.ERROR_MODEL_NOT_DOWNLOADED));
			return;
		}

		this.language = language;
		recognizer.start(modelManager.getModelFile(WhisperModelCatalog.MODEL), languageCode, remote);
	}


	/** Closes the microphone and transcribes the recording. */
	public void stop() {
		recognizer.stop();
	}


	/** Closes the microphone and discards the recording, or abandons a running transcription. */
	public void cancel() {
		recognizer.cancel();
	}


	private void onStop(@Nullable String result) {
		language = null;
		onStopListening.accept(result);
	}


	private void onError(@NonNull VoiceInputError error) {
		language = null;
		onListeningError.accept(error);
	}


	@NonNull
	@Override
	public String toString() {
		String languageSuffix = language == null ? "" : " / " + language.getName();
		return ims.getString(R.string.voice_input_listening) + languageSuffix;
	}
}
