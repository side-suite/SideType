package io.github.sspanak.tt9.ime;

import android.Manifest;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.ime.modes.InputMode;
import io.github.sspanak.tt9.ime.modes.helpers.AutoTextCase;
import io.github.sspanak.tt9.ime.modes.helpers.Sequences;
import io.github.sspanak.tt9.ime.voice.RemoteWhisperClient;
import io.github.sspanak.tt9.ime.voice.VoiceInputError;
import io.github.sspanak.tt9.ime.voice.VoiceInputOps;
import io.github.sspanak.tt9.ui.UI;
import io.github.sspanak.tt9.ui.dialogs.RequestPermissionDialog;
import io.github.sspanak.tt9.ui.dialogs.VoiceModelDownloadDialog;
import io.github.sspanak.tt9.util.Logger;
import io.github.sspanak.tt9.util.Text;
import io.github.sspanak.tt9.util.Ternary;

/**
 * Voice input: record while the voice key is held, transcribe with Whisper when it is released.
 * <p>
 * The voice command works two ways, depending on how its hotkey is bound. Bound to <i>holding</i> a
 * key (the default, hold SYM), it is hold-to-talk: the microphone opens when the hold registers and
 * closes when the key comes back up. Bound to a plain press, it is a toggle: one press starts, the
 * next one stops. Either way the text only appears after the recording ends, because Whisper
 * transcribes a finished recording rather than a live stream.
 */
abstract class VoiceHandler extends SuggestionHandler {
	private final static String LOG_TAG = VoiceHandler.class.getSimpleName();

	@NonNull protected VoiceInputOps voiceInputOps = new VoiceInputOps(this, null, null, null, null);
	/** The key being held to talk, or 0 when voice input was started by a plain press. */
	private int holdToTalkKey = 0;


	@Override
	protected void onInit() {
		super.onInit();

		voiceInputOps = new VoiceInputOps(
			this,
			this::onVoiceInputStarted,
			this::onVoiceInputTranscribing,
			this::onVoiceInputStopped,
			this::onVoiceInputError
		);
	}

	@Override
	protected Ternary onBack() {
		stopVoiceInput();
		return Ternary.FALSE; // we don't want to abort other operations, we just silently stop voice input
	}

	@Override
	protected boolean onNumber(int key, boolean hold, int repeat) {
		// Typing while the microphone is open means "I am done talking": keep what was said.
		finishVoiceInput();
		return super.onNumber(key, hold, repeat);
	}


	@Override
	protected void onHeldKeyReleased(int keyCode) {
		super.onHeldKeyReleased(keyCode);

		if (keyCode == holdToTalkKey) {
			holdToTalkKey = 0;
			finishVoiceInput();
		}
	}


	/**
	 * Prevents Pound and Star keys from working as hotkeys when some function or panel is active.
	 * For example, it is confusing to change the language, open the settings or trigger some other function
	 * on, when the command palette is open or voice input is active. For this reason, we disable the Pound key
	 * and make the Star key stop voice input instead of navigating back.
	 */
	@Override
	public boolean onHotkey(int keyCode, boolean repeat, boolean validateOnly) {
		return switch (keyCode) {
			case KeyEvent.KEYCODE_STAR -> validateOnly || navigateBack();
			case KeyEvent.KEYCODE_POUND -> isFnPanelVisible(); // ignore the pound key when a function is active
			default -> false;
		};
	}


	protected boolean navigateBack() {
		if (!voiceInputOps.isBusy()) {
			return false;
		}

		stopVoiceInput();
		return true;
	}


	public void toggleVoiceInput() {
		// A second press while recording ends the recording and transcribes it. While a transcription
		// or a download is already running there is nothing sensible to start, so the press is dropped.
		if (voiceInputOps.isBusy()) {
			finishVoiceInput();
			return;
		}

		if (voiceInputOps.isDownloadingModel()) {
			return;
		}

		if (!VoiceInputOps.isAvailable(mLanguage)) {
			statusBar.setError(new VoiceInputError(this, VoiceInputError.ERROR_NO_MODEL_FOR_LANGUAGE).toString());
			mainView.flashStatus();
			return;
		}

		// Commit the word in progress and clear the tray BEFORE anything else, including the consent
		// prompt. The status bar only renders while the suggestion list is empty, so a download that
		// starts with suggestions still on screen writes its progress into a hidden view.
		suggestionOps.cancelDelayedAccept();
		mInputMode.onAcceptSuggestion(suggestionOps.acceptIncomplete());

		// The model is tens of megabytes and arrives on demand, so the first use asks before fetching
		// anything. Nothing is recorded on that press: by the time the download ends the key is long up.
		// With an API key set the recording is transcribed online, so no model is needed.
		final RemoteWhisperClient remote = RemoteWhisperClient.fromSettings(settings);
		if (remote == null && VoiceModelDownloadDialog.showIfModelMissing((TraditionalT9) this)) {
			return;
		}

		// When this runs from a hold hotkey the key is still down, and its release ends the recording.
		holdToTalkKey = getHeldKey();
		voiceInputOps.listen(mLanguage, remote);
	}


	/** Downloads the model the user just agreed to. Progress goes to the status bar. */
	public void downloadVoiceModel() {
		mainView.setStatusPinned(true);
		statusBar.setText(getString(R.string.voice_model_downloading, 0));

		voiceInputOps.downloadModel(
			() -> {
				mainView.setStatusPinned(false);
				resetStatus();
				UI.toast(this, R.string.voice_model_ready);
			},
			percent -> statusBar.setText(getString(R.string.voice_model_downloading, percent)),
			this::onVoiceInputError
		);
	}


	/** Ends the recording and transcribes it. Does nothing if the microphone is not open. */
	private void finishVoiceInput() {
		holdToTalkKey = 0;
		if (voiceInputOps.isListening()) {
			voiceInputOps.stop();
		}
	}


	/**
	 * Abandons voice input: the recording, or the transcription in progress, is thrown away. This is
	 * what everything outside this class means by "stop": the keyboard is closing, the language is
	 * changing or another screen is opening, and text arriving a few seconds later would be wrong.
	 */
	public void stopVoiceInput() {
		holdToTalkKey = 0;
		if (voiceInputOps.isBusy()) {
			statusBar.setText(R.string.voice_input_stopping);
			voiceInputOps.cancel();
		}
	}


	private void onVoiceInputStarted() {
		mainView.setStatusPinned(true);
		if (!mainView.isCommandPaletteShown()) {
			mainView.render(); // disable the function keys
		}
		statusBar.setText(voiceInputOps);
	}


	private void onVoiceInputTranscribing() {
		statusBar.setText("[ " + getString(R.string.voice_input_transcribing) + " ]");
	}


	private void onVoiceInputStopped(@Nullable String text) {
		if (text != null && !text.isEmpty()) {
			final String before = textField.getStringBeforeCursor();
			final String cased = applyTextCase(text, before);
			final boolean needsSpace = !before.isEmpty() && !Character.isWhitespace(before.charAt(before.length() - 1));
			onText(needsSpace ? " " + cased : cased, false);
		}

		mainView.setStatusPinned(false);
		resetStatus();
		if (!mainView.isCommandPaletteShown()) {
			mainView.render(); // re-enable the function keys
		}
	}


	/**
	 * Makes dictated text follow the same upper/lower case rules as typed text.
	 * <p>
	 * Whisper returns its own capitals: every utterance starts with one, whatever is in front of the
	 * cursor. So the text goes through the keyboard's rules instead. An explicit UPPERCASE or
	 * lowercase Shift state wins. Otherwise a word is capitalised where the keyboard would capitalise
	 * a typed one (start of the text or of a sentence), and Whisper's capital on the first word is
	 * dropped when the dictation continues a sentence. Capitals inside the sentence (names, "I",
	 * acronyms) are Whisper's and are kept.
	 */
	@NonNull
	private String applyTextCase(@NonNull String text, @NonNull String before) {
		final int shiftState = mInputMode.getTextCase();

		if (!settings.isAutoTextCaseOn(mInputMode)) {
			return switch (shiftState) {
				case InputMode.CASE_UPPER -> new Text(mLanguage, text).toUpperCase();
				case InputMode.CASE_LOWER -> new Text(mLanguage, text).toLowerCase();
				default -> text;
			};
		}

		final AutoTextCase autoTextCase = new AutoTextCase(settings, new Sequences(), inputType);
		final int fieldTextCase = inputType.determineTextCase();

		final int firstWordCase = autoTextCase.determineNextWordTextCase(mLanguage, shiftState, fieldTextCase, null, "", before);
		if (firstWordCase == InputMode.CASE_DICTIONARY) {
			text = lowerFirstWord(text);
		}

		final String cased = autoTextCase.adjustParagraphTextCase(mLanguage, text, before, shiftState, fieldTextCase);
		return cased == null ? text : cased.trim();
	}


	/**
	 * Undoes the capital Whisper puts on the first word of an utterance, unless the word carries its
	 * capitals for another reason: an acronym, mixed case, or the English "I".
	 */
	@NonNull
	private String lowerFirstWord(@NonNull String text) {
		final int end = text.indexOf(' ');
		final String first = end < 0 ? text : text.substring(0, end);
		if (first.isEmpty() || first.equals("I") || first.startsWith("I'") || first.startsWith("I\u2019")) {
			return text;
		}

		// Only a plain capitalised word ("Okay") is lowered. Capitals after the first letter mean an
		// acronym or a mixed-case name, which keeps them wherever it stands.
		final String lowered = new Text(mLanguage, first).toLowerCase();
		final int firstLetterLength = Character.charCount(first.codePointAt(0));
		if (lowered.length() != first.length() || !first.substring(firstLetterLength).equals(lowered.substring(firstLetterLength))) {
			return text;
		}

		return lowered + (end < 0 ? "" : text.substring(end));
	}


	private void onVoiceInputError(VoiceInputError error) {
		holdToTalkKey = 0;
		mainView.setStatusPinned(false);

		if (error.isIrrelevantToUser()) {
			Logger.i(LOG_TAG, "Ignoring voice input. " + error.debugMessage);
			resetStatus(); // re-enable the function keys
		} else {
			Logger.e(LOG_TAG, "Failed to listen. " + error.debugMessage);
			statusBar.setError(error.toString());
			mainView.flashStatus(); // keep the message readable for a moment where the strip collapses
			if (error.isNoPermission()) {
				RequestPermissionDialog.show(this, Manifest.permission.RECORD_AUDIO);
			}
		}

		if (!mainView.isCommandPaletteShown()) {
			mainView.render(); // re-enable the function keys
		}
	}
}
