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
	private int heldKeyCode = 0;

	// paging the SYM grid: tap = next page, hold = previous pages
	private final static int GRID_PAGING_REPEAT_INTERVAL = 350; // ms between pages while held
	private int gridPagingKeyCode = 0;
	private boolean gridPagingHeld = false;
	private long gridPagingLastStep = 0;

	private int lastKeyCode = 0;
	private int keyRepeatCounter = 0;

	private int lastNumKeyCode = 0;
	private int numKeyRepeatCounter = 0;


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

		// Sidephone: while the SYM grid is open, the tile picks, pages or closes it. Nothing reaches the
		// editor until it is closed. SYM itself is not intercepted: its tap is the hotkey that cycles
		// the grid (CmdShowSymbols), and that is decided on key-up as usual.
		if (isSymbolGridShown() && isSymbolGridPagingKey(keyCode)) {
			return onSymbolGridPagingKeyDown(keyCode, event);
		}
		gridPagingKeyCode = 0; // any other key press ends a paging gesture that never saw its release
		gridPagingHeld = false;

		if (isSymbolGridShown() && onSymbolGridKey(keyCode)) {
			ignoreNextKeyUp = keyCode;
			return true;
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

		// Sidephone: holding the ▲▼ key over the SYM grid pages backwards instead of forwards
		if (keyCode == gridPagingKeyCode && isSymbolGridShown()) {
			gridPagingHeld = true;
			gridPagingLastStep = event.getEventTime();
			pageSymbolGrid(-1);
			return true;
		}

		ignoreNextKeyUp = keyCode;
		heldKeyCode = keyCode;
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

//		Logger.d("onKeyUp", "Key: " + keyCode + " repeat?: " + event.getRepeatCount());

		if (keyCode == gridPagingKeyCode) {
			final boolean wasHeld = gridPagingHeld;
			gridPagingKeyCode = 0;
			gridPagingHeld = false;
			// Only swallow the release while the grid is still up. If it went away in the meantime,
			// this is an ordinary key release again and must reach the normal handling below.
			if (isSymbolGridShown()) {
				if (!wasHeld) {
					pageSymbolGrid(1); // a tap: forwards
				}
				return true;
			}
		}

		// A key that was long-pressed is coming back up. Its tap action is skipped below as usual, but
		// hold-to-talk needs to know the hold is over.
		if (keyCode == heldKeyCode) {
			heldKeyCode = 0;
			onHeldKeyReleased(keyCode);
		}

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


	/**
	 * The ▲▼ key over an open SYM grid. Nothing happens on the way down, because a tap and a hold
	 * mean opposite directions: onKeyUp() pages forwards after a tap, onKeyLongPress() pages
	 * backwards once the hold registers, and further repeats here keep going back at a steady pace.
	 */
	private boolean onSymbolGridPagingKeyDown(int keyCode, KeyEvent event) {
		if (event.getRepeatCount() == 0) {
			gridPagingKeyCode = keyCode;
			gridPagingHeld = false;
			event.startTracking();
		} else if (gridPagingHeld && keyCode == gridPagingKeyCode && event.getEventTime() - gridPagingLastStep >= GRID_PAGING_REPEAT_INTERVAL) {
			gridPagingLastStep = event.getEventTime();
			pageSymbolGrid(-1);
		}

		return true;
	}


	/** The key that is currently held down past the long-press threshold, or 0. */
	protected int getHeldKey() {
		return heldKeyCode;
	}


	/** Called when a long-pressed key is released. */
	protected void onHeldKeyReleased(int keyCode) {}


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
