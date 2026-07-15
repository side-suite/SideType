package io.github.sspanak.tt9.ime;

import android.Manifest;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.ime.modes.helpers.AutoTextCase;
import io.github.sspanak.tt9.ime.modes.helpers.Sequences;
import io.github.sspanak.tt9.ime.voice.VoiceInputError;
import io.github.sspanak.tt9.ime.voice.VoiceInputOps;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.ui.dialogs.RequestPermissionDialog;
import io.github.sspanak.tt9.ui.dialogs.VoiceModelDownloadDialog;
import io.github.sspanak.tt9.util.Logger;
import io.github.sspanak.tt9.util.Ternary;

abstract class VoiceHandler extends SuggestionHandler {
	private final static String LOG_TAG = VoiceHandler.class.getSimpleName();

	@Nullable private AutoTextCase autoTextCase;
	@NonNull protected VoiceInputOps voiceInputOps = new VoiceInputOps(this, null, null, null, null);
	@NonNull private String beforeSpeech = "";


	@Override
	protected void onInit() {
		super.onInit();

		voiceInputOps = new VoiceInputOps(
			this,
			this::onVoiceInputStarted,
			this::onVoiceInputStopped,
			this::onVoiceInputPartial,
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
		stopVoiceInput();
		return super.onNumber(key, hold, repeat);
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
		if (!voiceInputOps.isListening()) {
			return false;
		}

		stopVoiceInput();
		return true;
	}


	public void toggleVoiceInput() {
		if (voiceInputOps.isListening() || voiceInputOps.isDownloadingModel()) {
			stopVoiceInput();
			return;
		}

		if (!VoiceInputOps.isAvailable(mLanguage)) {
			// The mic is on the strip because some other enabled language has a model, but this one has
			// none and never will (docs/adr/0001). Say so — the button is deliberately greyed rather
			// than hidden, to stop the strip reflowing on every language switch, so a silent no-op here
			// would just look broken.
			statusBar.setError(new VoiceInputError(this, VoiceInputError.ERROR_NO_MODEL_FOR_LANGUAGE).toString());
			return;
		}

		// Commit the word in progress and clear the tray BEFORE anything else, including the consent
		// prompt. The status bar only renders while the suggestion list is empty
		// (SuggestionOps.setVisibility -> statusBar.setShown), so a download that starts with
		// suggestions still on screen writes its progress into a hidden view and looks like nothing
		// is happening.
		prepareForSpeech();

		// The model arrives on demand and is tens of megabytes, so the first press for a language asks
		// before fetching anything. Consent is a dialog, not an Activity, so the input connection and
		// the composing text survive it untouched. See VoiceModelDownloadDialog.
		if (VoiceModelDownloadDialog.showIfModelMissing((TraditionalT9) this, mLanguage)) {
			return;
		}

		startListening();
	}


	/**
	 * Accepts the in-progress word and captures the surrounding text for auto-casing. Pressing the mic
	 * already means "I am done typing this word", so this is the same commit the old flow did — it
	 * just happens before the consent prompt now, rather than after.
	 */
	private void prepareForSpeech() {
		suggestionOps.cancelDelayedAccept();
		mInputMode.onAcceptSuggestion(suggestionOps.acceptIncomplete());
		autoTextCase = new AutoTextCase(settings, new Sequences(), inputType);
		beforeSpeech = textField.getStringBeforeCursor();
	}


	private void startListening() {
		statusBar.setText(R.string.loading);
		voiceInputOps.listen(mLanguage);
	}


	/**
	 * Downloads the model the user just consented to, then starts listening. Progress goes to the
	 * status bar — the tray is the only surface guaranteed to be on screen throughout.
	 */
	public void downloadVoiceModelAndListen(@NonNull Language language) {
		statusBar.setText(getString(R.string.voice_model_downloading, language.getName(), 0));

		voiceInputOps.downloadModel(
			language,
			() -> {
				// Only start listening if the user is still on the language they consented for.
				if (language.equals(mLanguage)) {
					startListening();
				} else {
					resetStatus();
				}
			},
			percent -> statusBar.setText(
				percent >= 100
					? getString(R.string.voice_model_unpacking, language.getName())
					: getString(R.string.voice_model_downloading, language.getName(), percent)
			),
			this::onVoiceInputError
		);
	}


	public void stopVoiceInput() {
		if (voiceInputOps.isListening()) {
			statusBar.setText(R.string.voice_input_stopping);
			voiceInputOps.stop();
		}
	}


	private void onVoiceInputStarted() {
		if (!mainView.isCommandPaletteShown()) {
			mainView.render(); // disable the function keys
		}
		refreshVoiceKey(); // swap the tray mic to its "stop" icon
		statusBar.setText(voiceInputOps);
	}


	private String autoCapitalize(String str) {
		if (autoTextCase == null || !settings.isAutoTextCaseOn(mInputMode)) {
			return str;
		}

		return autoTextCase.adjustParagraphTextCase(mLanguage, str, beforeSpeech, mInputMode.getTextCase(), inputType.determineTextCase());
	}


	private void onVoiceInputStopped(String text) {
		onText(autoCapitalize(text), false);
		resetStatus();
		refreshVoiceKey();
		 if (!mainView.isCommandPaletteShown()) {
			 mainView.render(); // re-enable the function keys
		 }
	}


	private void onVoiceInputPartial(String text) {
		textField.setComposingText(autoCapitalize(text), 1);
	}


	private void onVoiceInputError(VoiceInputError error) {
		// There is no fallback engine to escalate to any more: recognition is Vosk or nothing. The
		// branches that used to force an alternative recognizer or drop to Google's online mode went
		// with the platform SpeechRecognizer in SID-53 — see docs/adr/0002.
		if (error.isIrrelevantToUser()) {
			Logger.i(LOG_TAG, "Ignoring voice input. " + error.debugMessage);
			resetStatus(); // re-enable the function keys
		} else {
			Logger.e(LOG_TAG, "Failed to listen. " + error.debugMessage);
			statusBar.setError(error.toString());
			if (error.isNoPermission()) {
				RequestPermissionDialog.show(this, Manifest.permission.RECORD_AUDIO);
			}
		}

		refreshVoiceKey();
		 if (!mainView.isCommandPaletteShown()) {
			 mainView.render(); // re-enable the function keys
		 }
	}
}
