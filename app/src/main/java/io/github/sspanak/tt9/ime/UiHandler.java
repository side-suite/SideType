package io.github.sspanak.tt9.ime;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.commands.CmdAddWord;
import io.github.sspanak.tt9.commands.CmdNextLanguage;
import io.github.sspanak.tt9.commands.CmdShowSettings;
import io.github.sspanak.tt9.commands.CmdVoiceInput;
import io.github.sspanak.tt9.hacks.AppHacks;
import io.github.sspanak.tt9.ime.modes.InputMode;
import io.github.sspanak.tt9.ime.voice.VoiceInputOps;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.ui.StatusIcon;
import io.github.sspanak.tt9.ui.UI;
import io.github.sspanak.tt9.ui.main.MainView;
import io.github.sspanak.tt9.ui.tray.StatusBar;
import io.github.sspanak.tt9.util.Logger;
import io.github.sspanak.tt9.util.Text;
import io.github.sspanak.tt9.util.sys.DeviceInfo;
import io.github.sspanak.tt9.util.sys.FoldDetector;
import io.github.sspanak.tt9.util.sys.SystemSettings;

abstract class UiHandler extends AbstractHandler {
	private final static String LOG_TAG = "UiHandler";

	@NonNull protected final AppHacks appHacks = new AppHacks();
	@Nullable protected FoldDetector foldDetector = null;
	protected SettingsStore settings;

	protected int displayTextCase = InputMode.CASE_UNDEFINED;
	protected boolean isMainViewShown = false;
	protected MainView mainView = null;
	protected StatusBar statusBar = null;

	// Sidephone: the "listening" pulse on the tray mic button. Held so it can be cancelled — an
	// infinite animator left running on a detached view keeps it alive.
	@Nullable private ObjectAnimator voiceKeyPulse = null;

	// Sidephone: on-screen Emoji | Symbols drawer, opened from the status-bar emoji button.
	private final io.github.sspanak.tt9.ui.EmojiDrawer emojiDrawer = new io.github.sspanak.tt9.ui.EmojiDrawer();


	protected void cleanUp() {
		if (foldDetector != null) {
			foldDetector.destroy();
			foldDetector = null;
		}
	}


	@Override
	public boolean onEvaluateInputViewShown() {
		super.onEvaluateInputViewShown();
		if (!SystemSettings.isTT9Selected(this)) {
			isMainViewShown = false;
			return false;
		}

		setInputField(getCurrentInputEditorInfo());
		return isMainViewShown = shouldBeVisible();
	}


	@Override
	public boolean onEvaluateFullscreenMode() {
		return false;
	}


	@Override
	protected void onInit() {
		if (foldDetector == null) {
			foldDetector = new FoldDetector(this, () -> settings.setFolded(foldDetector == null || foldDetector.isFolded()));
		}

		if (mainView == null) {
			mainView = new MainView(getFinalContext());
			initTray();
		} else {
			mainView.destroy();
			mainView.getView();
		}
	}


	@Override
	protected boolean onStart(EditorInfo inputField, boolean restarting) {
		if (getFinalContext().getInputType().isOwnSwitchPreviewField()) {
			settings.setFolded(SettingsStore.isFoldedPreview);
		} else if (foldDetector != null) {
			settings.setFolded(foldDetector.isFolded());
		}
		return true;
	}


	protected void initTray() {
		mainView.getView();
		statusBar = new StatusBar(this, settings, mainView, this::resetStatus, () -> getSuggestionOps().cancelDelayedAccept());
		statusBar.setColorScheme();
		createSuggestionBar();
		getSuggestionOps().setColorScheme();
		wireQuickActionKeys();
	}


	/**
	 * Sidephone quick-action icons in the status bar: add the current word to the dictionary, and open
	 * the emoji key binds screen. See panel_small_status_bar.xml.
	 */
	private void wireQuickActionKeys() {
		View view = mainView.getView();
		if (view == null) {
			return;
		}

		View settingsKey = view.findViewById(R.id.sidephone_settings_key);
		if (settingsKey != null) {
			settingsKey.setOnClickListener(v -> new CmdShowSettings().run((TraditionalT9) this));
		}

		View dictKey = view.findViewById(R.id.sidephone_dict_key);
		if (dictKey != null) {
			dictKey.setOnClickListener(v -> new CmdAddWord().run((TraditionalT9) this));
		}

		View emojiKey = view.findViewById(R.id.sidephone_emoji_key);
		if (emojiKey != null) {
			emojiKey.setOnClickListener(v -> toggleEmojiDrawer());
		}

		View languageKey = view.findViewById(R.id.sidephone_language_key);
		if (languageKey != null) {
			languageKey.setOnClickListener(v -> {
				new CmdNextLanguage().run((TraditionalT9) this);
				refreshLanguageKey();
				refreshVoiceKey();
			});
		}
		refreshLanguageKey();

		View voiceKey = view.findViewById(R.id.sidephone_voice_key);
		if (voiceKey != null) {
			voiceKey.setOnClickListener(v -> new CmdVoiceInput().run((TraditionalT9) this));
		}
		refreshVoiceKey();
	}


	/**
	 * Shows the mic only for languages Vosk can actually transcribe, and swaps the icon while
	 * listening so the button doubles as the stop control.
	 * <p>
	 * Hiding it for the rest is not cosmetic. The tray is ~376dp and the four other quick actions
	 * already spend 176dp of it, so a fifth costs ~22% of the suggestion strip. Finnish, Norwegian and
	 * Danish have no model and never will (docs/adr/0001), so their users would be paying that for a
	 * button that could never do anything.
	 * <p>
	 * The test is "does a model exist for this language", never "is one downloaded" — the download is
	 * triggered by pressing this very button, so gating on the disk state would hide the only way to
	 * get one. See VoskModelCatalog.isSupported().
	 */
	protected void refreshVoiceKey() {
		View view = mainView.getView();
		if (view == null) {
			return;
		}

		ImageView voiceKey = view.findViewById(R.id.sidephone_voice_key);
		if (voiceKey == null) {
			return;
		}

		Language language = getFinalContext().getLanguage();
		boolean show = VoiceInputOps.isAvailable(language);
		voiceKey.setVisibility(show ? View.VISIBLE : View.GONE);

		if (!show) {
			stopVoiceKeyPulse(voiceKey);
			return;
		}

		CmdVoiceInput voiceInput = new CmdVoiceInput();
		boolean active = voiceInput.isActive((TraditionalT9) this);
		voiceKey.setImageResource(active ? voiceInput.getIconOff() : voiceInput.getIcon());

		if (active) {
			startVoiceKeyPulse(voiceKey);
		} else {
			stopVoiceKeyPulse(voiceKey);
		}
	}


	/**
	 * Pulses the mic while listening. Vosk exposes no amplitude — its RecognitionListener has no
	 * equivalent of the platform recognizer's onRmsChanged, and SpeechService keeps its AudioRecord
	 * private — so this is a fixed pulse meaning "listening", not a level meter. The real feedback is
	 * the partial text appearing live in the editor. See SID-56.
	 */
	private void startVoiceKeyPulse(@NonNull View voiceKey) {
		if (voiceKeyPulse != null && voiceKeyPulse.isRunning()) {
			return;
		}

		voiceKeyPulse = ObjectAnimator.ofFloat(voiceKey, View.ALPHA, 1f, 0.35f);
		voiceKeyPulse.setDuration(600);
		voiceKeyPulse.setRepeatMode(ValueAnimator.REVERSE);
		voiceKeyPulse.setRepeatCount(ValueAnimator.INFINITE);
		voiceKeyPulse.start();
	}


	private void stopVoiceKeyPulse(@NonNull View voiceKey) {
		if (voiceKeyPulse != null) {
			voiceKeyPulse.cancel();
			voiceKeyPulse = null;
		}
		voiceKey.setAlpha(1f);
	}


	/**
	 * Open (or close, if already open) the on-screen Emoji | Symbols drawer. Picking a glyph types it
	 * straight into the focused field via onText and keeps the drawer open; the ⚙ corner opens the
	 * key-binds editor. The drawer keeps the text field focused, so typing works while it is up.
	 */
	private void toggleEmojiDrawer() {
		if (emojiDrawer.isShowing()) {
			emojiDrawer.hide();
			return;
		}
		View anchor = mainView != null ? mainView.getView() : null;
		emojiDrawer.show(
			anchor,
			settings,
			glyph -> onText(glyph, false),
			() -> UI.showEmojiBinds(getFinalContext()),
			this::getDrawerPreviewText
		);
	}


	protected void hideEmojiDrawer() {
		emojiDrawer.hide();
	}


	/**
	 * Text around the cursor for the emoji drawer's preview bar. Overridden where the text field is
	 * available (TypingHandler); the base returns nothing so the drawer simply shows an empty bar.
	 */
	protected String getDrawerPreviewText() {
		return "";
	}


	/**
	 * Updates the status-bar language chip to the current language's code (EN / FI / DE …). Hidden when
	 * fewer than two languages are enabled, since there is then nothing to switch between.
	 */
	protected void refreshLanguageKey() {
		View view = mainView.getView();
		if (view == null) {
			return;
		}

		TextView languageKey = view.findViewById(R.id.sidephone_language_key);
		if (languageKey == null) {
			return;
		}

		Language language = getFinalContext().getLanguage();
		boolean show = language != null && settings.getEnabledLanguageIds().size() > 1;
		languageKey.setVisibility(show ? View.VISIBLE : View.GONE);
		if (show) {
			languageKey.setText(language.getCode().toUpperCase(language.getLocale()));
		}
	}


	protected void initUi(InputMode inputMode) {
		if (mainView.create()) {
			initTray();
			setCurrentView();
		} else {
			getSuggestionOps().setColorScheme();
		}
		setStatusIcon(inputMode, getFinalContext().getLanguage());
		statusBar.setColorScheme().setText(inputMode);
		mainView.showKeyboard();
		mainView.render();

		SystemSettings.setNavigationBarBackground(getWindow().getWindow(), settings, mainView.isBackgroundBlendingEnabled());

		if (appHacks.isBrutalForceShowNeeded()) {
			brutalForceShowWindow();
		} else if (!isInputViewShown()) {
			updateInputViewShown();
		}
	}


	public void setCurrentView() {
		setInputView(onCreateInputView());
	}


	public int getDisplayTextCase(@Nullable Language language, int modeTextCase) {
		boolean hasUpperCase = language != null && language.hasUpperCase();
		if (!hasUpperCase) {
			return displayTextCase = InputMode.CASE_UNDEFINED;
		}

		if (modeTextCase == InputMode.CASE_UPPER) {
			return displayTextCase = InputMode.CASE_UPPER;
		}

		Text currentWord = new Text(language, getSuggestionOps().getCurrent());
		if (currentWord.isEmpty() || !currentWord.isAlphabetic()) {
			return displayTextCase = modeTextCase;
		}

		final int wordTextCase = currentWord.getTextCase();
		return displayTextCase = wordTextCase == InputMode.CASE_UPPER ? InputMode.CASE_CAPITALIZE : wordTextCase;
	}


	public void setStatusIcon(@Nullable InputMode mode, @Nullable Language language) {
		if (!settings.isStatusIconEnabled()) {
			return;
		}

		final int resId = new StatusIcon(settings.isStatusIconEnabled() ? mode : null, language, displayTextCase).resourceId;
		if (resId == 0) {
			hideStatusIcon();
		} else {
			showStatusIcon(resId);
		}
	}


	protected boolean shouldBeVisible() {
		return determineInputModeId() != InputMode.MODE_PASSTHROUGH && !settings.isMainLayoutStealth();
	}


	/**
	 * forceShowWindow
	 * Some applications may hide our window, and it remains invisible until the screen is touched or OK is pressed.
	 * This is fine for touchscreen keyboards, but the hardware keyboard allows typing even when the window and the suggestions
	 * are invisible. This function forces the InputMethodManager to show our window.
	 * WARNING! Calling this may cause a restart, which will cause InputMode to be recreated. Depending
	 * on how much time the restart takes, this may erase the current user input.
	 */
	public void forceShowWindow() {
		if (isInputViewShown() || !shouldBeVisible()) {
			return;
		}

		if (DeviceInfo.AT_LEAST_ANDROID_9) {
			requestShowSelf(DeviceInfo.isSonimGen2(getApplicationContext()) ? 0 : InputMethodManager.SHOW_IMPLICIT);
		} else {
			showWindow(true);
		}
	}


	/**
	 * Shows the IME window using brutal force, ignoring IME flags and state, and any (invalid) app
	 * requests for passthrough mode. Note that this should not be randomly used, because it will
	 * cause the UI to appear in calculators, banking apps or others where it is not desired.
	 * Reported problems (in chronological order):
	 *	- <a href="https://github.com/sspanak/tt9/issues/920">Google search field in Firefox on Android 16</a>
	 *	- <a href="https://github.com/sspanak/tt9/issues/963">Gmail reply/forward on Android 16</a>
	 */
	private void brutalForceShowWindow() {
		if (!isShowInputRequested() || !isMainViewShown) {
			forceShowWindow();
		}

		if (!isShowInputRequested() || !isMainViewShown) {
			Logger.d(LOG_TAG, "InputMethodManager refused show request. Forcing visibility with showWindow().");
			showWindow(true);
		}
	}
}
