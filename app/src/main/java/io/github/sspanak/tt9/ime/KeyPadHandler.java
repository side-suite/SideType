package io.github.sspanak.tt9.ime;

import android.view.KeyEvent;

import io.github.sspanak.tt9.ime.helpers.Key;
import io.github.sspanak.tt9.preferences.screens.debug.DropDownInputHandlingMode;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.util.Timer;


abstract class KeyPadHandler extends UiHandler {
	// debounce handling
	private final static String DEBOUNCE_TIMER = "debounce_";

	// temporal key handling
	private int ignoreNextKeyUp = 0;

	private int lastKeyCode = 0;
	private int keyRepeatCounter = 0;

	private int lastNumKeyCode = 0;
	private int numKeyRepeatCounter = 0;

	// Emoji layer (Sidephone): SYM (ALT) held acts as a modifier. Holding SYM and pressing a key
	// types the emoji bound to that key. A lone SYM tap still shows the symbols page.
	private boolean symHeld = false;
	private boolean symUsedAsModifier = false;
	private final io.github.sspanak.tt9.ui.EmojiPreview emojiPreview = new io.github.sspanak.tt9.ui.EmojiPreview();
	// The emoji preview grid only makes sense when SYM is truly HELD (to type an emoji). A quick SYM
	// tap means "cycle symbols" and must NOT flash the grid, so showing it is deferred behind a hold
	// threshold and canceled on an early key-up. See scheduleSymPreview()/cancelSymPreview().
	private static final long SYM_PREVIEW_HOLD_MS = 280;
	private final android.os.Handler symPreviewHandler = new android.os.Handler(android.os.Looper.getMainLooper());
	private final Runnable symPreviewRunnable = () -> {
		try {
			emojiPreview.show(mainView != null ? mainView.getView() : null, settings);
		} catch (Throwable t) {
			resetSymState();
		}
	};


	/**
	 * Main initialization of the input method component. Be sure to call to
	 * super class.
	 */
	@Override
	public void onCreate() {
		super.onCreate();
		settings = new SettingsStore(getApplicationContext());

		onInit();
	}


	/**
	 * Use this to monitor key events being delivered to the application. We get
	 * first crack at them, and can either resume them or let them continue to
	 * the app.
	 */
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (debounceKey(keyCode, event)) {
			return true;
		}

		if (settings.getInputHandlingMode() == DropDownInputHandlingMode.RETURN_FALSE) {
			return false;
		} else if (settings.getInputHandlingMode() == DropDownInputHandlingMode.CALL_SUPER) {
			return super.onKeyDown(keyCode, event);
		}

		if (shouldBeOff()) {
			return false;
		}

		// Emoji layer (Sidephone): SYM (ALT) is a modifier. Track its held state here; the tap action
		// (show symbols) is decided on key up, so a lone SYM tap can be told apart from "hold SYM +
		// press key". While SYM is held, a key press types that key's bound emoji instead of a letter.
		// The emoji layer must NEVER take down the whole IME. A crash here previously left the keyboard
		// "broken" for reporters, recoverable only by reinstalling. Any failure resets to a clean state
		// and falls through to normal typing instead of propagating.
		try {
			if (keyCode == KeyEvent.KEYCODE_ALT_LEFT) {
				// Only arm the preview on the FIRST down; ALT auto-repeats while held and re-arming would
				// keep pushing the show-time forward so it never appears.
				if (!symHeld) {
					symHeld = true;
					symUsedAsModifier = false;
					scheduleSymPreview();
				}
				// fall through, so the hotkey system can still track the key for the tap action
			} else if (symHeld && Key.isCompactQwertyLetter(keyCode)) {
				symUsedAsModifier = true; // SYM acted as a modifier this press → don't also show symbols on release
				cancelSymPreview(); // pressing a key resolves the hold; the grid (if pending) is now moot
				String emoji = settings.getEmojiBind(Key.codeToNumber(settings, keyCode));
				if (emoji != null && !emoji.isEmpty()) {
					ignoreNextKeyUp = keyCode; // swallow the matching key-up so the letter is not also typed
					onText(emoji, false);
					return true;
				}
				// No emoji bound: do NOT swallow the key. Fall through so it types its normal letter rather
				// than silently vanishing — the silent swallow is what read as a "broken" keyboard.
			}
		} catch (Throwable t) {
			resetSymState();
		}

//		Logger.d("onKeyDown", "Key: " + event + " repeat?: " + event.getRepeatCount() + " long-time: " + event.isLongPress());

		// "backspace" key must repeat its function when held down, so we handle it in a special way
		if (Key.isBackspace(settings, keyCode)) {
			if (onBackspace(event.getRepeatCount())) {
				return Key.setHandled(KeyEvent.KEYCODE_DEL, true);
			} else {
				Key.setHandled(KeyEvent.KEYCODE_DEL, false);
			}
		}

		// start tracking key hold
		if (Key.isNumber(keyCode)) {
			event.startTracking();
			return true;
		}
		else if (getFinalContext().isHoldHotkey(-keyCode)) {
			event.startTracking();
		}

		// on many devices there is a default back handler, so we must fall back to it when we don't
		// perform any operation
		if (Key.isBack(keyCode)) {
			Key.setHandled(keyCode, onBack());
			return Key.isHandledInSuper(keyCode) ? super.onKeyDown(keyCode, event) : Key.isHandled(keyCode);
		} else {
			Key.setHandled(KeyEvent.KEYCODE_BACK, false);
		}

		return
			Key.setHandled(KeyEvent.KEYCODE_ENTER, Key.isOK(keyCode) && onOK())
			|| handleHotkey(keyCode, true, false, true) // hold a hotkey, handled in onKeyLongPress())
			|| handleHotkey(keyCode, false, keyRepeatCounter + 1 > 0, true) // press a hotkey, handled in onKeyUp()
			|| Key.isPoundOrStar(keyCode) && onText(String.valueOf((char) event.getUnicodeChar()), true)
			|| super.onKeyDown(keyCode, event); // let the system handle the keys we don't care about (usually, the touch "buttons")
	}


	private void scheduleSymPreview() {
		symPreviewHandler.removeCallbacks(symPreviewRunnable);
		symPreviewHandler.postDelayed(symPreviewRunnable, SYM_PREVIEW_HOLD_MS);
	}


	private void cancelSymPreview() {
		symPreviewHandler.removeCallbacks(symPreviewRunnable);
	}


	/**
	 * Return the SYM/emoji layer to a clean resting state. Called on any emoji-layer exception and when
	 * input focus changes, so a SYM key-up that never arrives (focus stolen mid-hold, IME restart) can
	 * never leave the modifier stuck "on" and route every later key through the emoji layer.
	 */
	protected void resetSymState() {
		cancelSymPreview();
		try {
			emojiPreview.hide();
		} catch (Throwable ignored) {
			// hiding is best-effort; never let cleanup itself throw
		}
		symHeld = false;
		symUsedAsModifier = false;
	}


	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event) {
		if (settings.getInputHandlingMode() == DropDownInputHandlingMode.RETURN_FALSE) {
			return false;
		} else if (settings.getInputHandlingMode() == DropDownInputHandlingMode.CALL_SUPER) {
			return super.onKeyLongPress(keyCode, event);
		}

		if (shouldBeOff()) {
			return false;
		}

//		Logger.d("onLongPress", "LONG PRESS: " + keyCode);

		if (event.getRepeatCount() > 1) {
			return true;
		}

		ignoreNextKeyUp = keyCode;
		if (Key.isNumber(keyCode)) {
			numKeyRepeatCounter = 0;
			lastNumKeyCode = 0;
			return onNumber(Key.codeToNumber(settings, keyCode), true, 0);
		} else {
			keyRepeatCounter = 0;
			lastKeyCode = 0;
		}

		if (handleHotkey(keyCode, true, false, false)) {
			return true;
		}

		ignoreNextKeyUp = 0;
		return false;
	}


	/**
	 * Use this to monitor key events being delivered to the application. We get
	 * first crack at them, and can either resume them or let them continue to
	 * the app.
	 */
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		if (debounceKey(keyCode, event)) {
			return true;
		}

		if (settings.getInputHandlingMode() == DropDownInputHandlingMode.RETURN_FALSE) {
			return false;
		} else if (settings.getInputHandlingMode() == DropDownInputHandlingMode.CALL_SUPER) {
			return super.onKeyUp(keyCode, event);
		}

		if (shouldBeOff()) {
			return false;
		}

		// Emoji layer (Sidephone): releasing SYM (ALT). If it was used to type an emoji, swallow the
		// key-up so the "show symbols" tap action does not also fire. Otherwise it was a lone tap and
		// we let the hotkey system show the symbols page.
		if (keyCode == KeyEvent.KEYCODE_ALT_LEFT) {
			boolean wasModifier;
			try {
				cancelSymPreview(); // if released before the hold threshold, the grid must never appear
				emojiPreview.hide();
				wasModifier = symUsedAsModifier;
			} catch (Throwable t) {
				resetSymState();
				return true;
			}
			symHeld = false;
			symUsedAsModifier = false;
			if (wasModifier) {
				return true;
			}
		}

//		Logger.d("onKeyUp", "Key: " + keyCode + " repeat?: " + event.getRepeatCount());

		if (keyCode == ignoreNextKeyUp) {
//			Logger.d("onKeyUp", "Ignored: " + keyCode);
			ignoreNextKeyUp = 0;
			return true;
		}

		if (Key.isBackspace(settings, keyCode) && Key.isHandled(KeyEvent.KEYCODE_DEL)) {
			return true;
		}

		keyRepeatCounter = (lastKeyCode == keyCode) ? keyRepeatCounter + 1 : 0;
		lastKeyCode = keyCode;

		if (Key.isNumber(keyCode)) {
			numKeyRepeatCounter = (lastNumKeyCode == keyCode) ? numKeyRepeatCounter + 1 : 0;
			lastNumKeyCode = keyCode;
			return onNumber(Key.codeToNumber(settings, keyCode), false, numKeyRepeatCounter);
		}

		if (Key.isBack(keyCode)) {
			return Key.isHandledInSuper(keyCode) ? super.onKeyUp(keyCode, event) : Key.isHandled(keyCode);
		}

		return
			(Key.isOK(keyCode) && Key.isHandled(KeyEvent.KEYCODE_ENTER))
			|| handleHotkey(keyCode, false, keyRepeatCounter > 0, false)
			|| Key.isPoundOrStar(keyCode) && onText(String.valueOf((char) event.getUnicodeChar()), false)
			|| super.onKeyUp(keyCode, event); // let the system handle the keys we don't care about (usually, the touch "buttons")
	}


	private boolean handleHotkey(int keyCode, boolean hold, boolean repeat, boolean validateOnly) {
		return onHotkey(keyCode * (hold ? -1 : 1), repeat, validateOnly);
	}


	public void resetKeyRepeat() {
		numKeyRepeatCounter = 0;
		keyRepeatCounter = 0;
		lastNumKeyCode = 0;
		lastKeyCode = 0;
	}


	private boolean debounceKey(int keyCode, KeyEvent event) {
		if (settings.getKeyPadDebounceTime() <= 0 || event.isLongPress()) {
			return false;
		}

		String keyTimer = DEBOUNCE_TIMER + keyCode;

		if (Timer.get(keyTimer) > 0 && Timer.get(keyTimer) < settings.getKeyPadDebounceTime()) {
			return true;
		}

		if (event.getAction() == KeyEvent.ACTION_UP) {
			Timer.start(keyTimer);
		}

		return false;
	}
}
