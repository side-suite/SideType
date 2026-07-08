package io.github.sspanak.tt9.ime.helpers;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.commands.Command;
import io.github.sspanak.tt9.commands.CommandCollection;
import io.github.sspanak.tt9.languages.KeySequence;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.languages.LanguageCollection;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.util.Ternary;
import io.github.sspanak.tt9.util.Text;

public class Key {
	private static final HashMap<Integer, Ternary> handledKeys = new HashMap<>();


	public static void setHandled(int keyCode, Ternary handled) {
		handledKeys.put(keyCode, handled);
	}


	public static boolean setHandled(int keyCode, boolean handled) {
		handledKeys.put(keyCode, handled ? Ternary.TRUE : Ternary.FALSE);
		return handled;
	}


	public static boolean isHandled(int keyCode) {
		return handledKeys.containsKey(keyCode) && handledKeys.get(keyCode) == Ternary.TRUE;
	}


	public static boolean isHandledInSuper(int keyCode) {
		return handledKeys.containsKey(keyCode) && handledKeys.get(keyCode) == Ternary.ALTERNATIVE;
	}


	public static boolean exists(int keyCode) {
		return keyCode != KeyEvent.KEYCODE_UNKNOWN;
	}


	public static boolean isArrow(int keyCode) {
		return
			keyCode == KeyEvent.KEYCODE_DPAD_UP
			|| keyCode == KeyEvent.KEYCODE_DPAD_DOWN
			|| keyCode == KeyEvent.KEYCODE_DPAD_LEFT
			|| keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
	}


	public static boolean isArrowUp(int keyCode) {
		return keyCode == KeyEvent.KEYCODE_DPAD_UP;
	}


	public static boolean isArrowRight(int keyCode) {
		return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
	}


	public static boolean isArrowLeft(int keyCode) {
		return keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
	}


	public static boolean isBackspace(SettingsStore settings, int keyCode) {
		return isHardwareBackspace(keyCode) || keyCode == settings.getKeyBackspace();
	}


	public static boolean isHardwareBackspace(int keyCode) {
		return keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_CLEAR;
	}


	public static boolean isNumber(int keyCode) {
		return
			(keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)
			|| (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9)
			|| isCompactQwertyLetter(keyCode)
			// Sidephone: the "0 SPACE" key IS the T9 zero key — tap = space, hold = "0".
			|| keyCode == KeyEvent.KEYCODE_SPACE;
	}


	/**
	 * The Sidephone Compact QWERTY tile sends the LEFT-glyph letter keycode for each of its 14 letter
	 * buttons. These act as the ambiguous "typing keys" (like the 2-9 keys on a numpad), so they must
	 * be treated as numbers by the input pipeline. See codeToNumber() for the key-index mapping.
	 */
	public static boolean isCompactQwertyLetter(int keyCode) {
		return switch (keyCode) {
			case KeyEvent.KEYCODE_Q, KeyEvent.KEYCODE_E, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_U, KeyEvent.KEYCODE_O,
				KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_J, KeyEvent.KEYCODE_L,
				KeyEvent.KEYCODE_Z, KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_M -> true;
			default -> false;
		};
	}


	public static boolean isHotkey(SettingsStore settings, int keyCode) {
		for (Command cmd : CommandCollection.getHotkeyCommands()) {
			if (keyCode == settings.getFunctionKey(cmd.getId())) {
				return true;
			}
		}

		return false;
	}


	public static boolean isBack(int keyCode) {
		return keyCode == KeyEvent.KEYCODE_BACK;
	}


	public static boolean isPoundOrStar(int keyCode) {
		return keyCode == KeyEvent.KEYCODE_POUND || keyCode == KeyEvent.KEYCODE_STAR;
	}


	public static boolean isOK(int keyCode) {
		return
			keyCode == KeyEvent.KEYCODE_DPAD_CENTER
			|| keyCode == KeyEvent.KEYCODE_ENTER
			|| keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
	}


	public static int codeToNumber(SettingsStore settings, int keyCode) {
		return switch (keyCode) {
			case KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0, KeyEvent.KEYCODE_SPACE -> 0;
			case KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> settings.getUpsideDownKeys() ? 7 : 1;
			case KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> settings.getUpsideDownKeys() ? 8 : 2;
			case KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> settings.getUpsideDownKeys() ? 9 : 3;
			case KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4;
			case KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5;
			case KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> 6;
			case KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> settings.getUpsideDownKeys() ? 1 : 7;
			case KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> settings.getUpsideDownKeys() ? 2 : 8;
			case KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> settings.getUpsideDownKeys() ? 3 : 9;
			// Sidephone Compact QWERTY letter keys -> key indices 2..15 (must match the language layout order)
			case KeyEvent.KEYCODE_Q -> 2;
			case KeyEvent.KEYCODE_E -> 3;
			case KeyEvent.KEYCODE_T -> 4;
			case KeyEvent.KEYCODE_U -> 5;
			case KeyEvent.KEYCODE_O -> 6;
			case KeyEvent.KEYCODE_A -> 7;
			case KeyEvent.KEYCODE_D -> 8;
			case KeyEvent.KEYCODE_G -> 9;
			case KeyEvent.KEYCODE_J -> 10;
			case KeyEvent.KEYCODE_L -> 11;
			case KeyEvent.KEYCODE_Z -> 12;
			case KeyEvent.KEYCODE_C -> 13;
			case KeyEvent.KEYCODE_B -> 14;
			case KeyEvent.KEYCODE_M -> 15;
			default -> -1;
		};
	}


	public static int numberToCode(int number) {
		return numberToCode(null, number);
	}


	public static int numberToCode(@Nullable SettingsStore settings, int number) {
		if (number < 0 || number > KeySequence.MAX_KEY) {
			return -1;
		}

		// Compact QWERTY letter keys (indices 10-15 have no numpad-digit equivalent).
		if (number > 9) {
			return switch (number) {
				case 10 -> KeyEvent.KEYCODE_J;
				case 11 -> KeyEvent.KEYCODE_L;
				case 12 -> KeyEvent.KEYCODE_Z;
				case 13 -> KeyEvent.KEYCODE_C;
				case 14 -> KeyEvent.KEYCODE_B;
				case 15 -> KeyEvent.KEYCODE_M;
				default -> -1;
			};
		}

		int code = KeyEvent.KEYCODE_0 + number;
		if (settings != null && settings.getUpsideDownKeys()) {
			code = switch (code) {
				case KeyEvent.KEYCODE_1 -> KeyEvent.KEYCODE_7;
				case KeyEvent.KEYCODE_2 -> KeyEvent.KEYCODE_8;
				case KeyEvent.KEYCODE_3 -> KeyEvent.KEYCODE_9;
				case KeyEvent.KEYCODE_7 -> KeyEvent.KEYCODE_1;
				case KeyEvent.KEYCODE_8 -> KeyEvent.KEYCODE_2;
				case KeyEvent.KEYCODE_9 -> KeyEvent.KEYCODE_3;
				default -> code;
			};
		}

		return code;
	}


	@SuppressLint("GestureBackNavigation") // we are not handling anything here, the warning makes no sense
	public static String codeToName(@NonNull Context context, int keyCode) {
		return switch (keyCode) {
			case KeyEvent.KEYCODE_UNKNOWN -> context.getString(R.string.list_item_none);
			case KeyEvent.KEYCODE_POUND -> "#";
			case KeyEvent.KEYCODE_STAR -> "✱";
			case KeyEvent.KEYCODE_BACK -> context.getString(R.string.key_back);
			case KeyEvent.KEYCODE_CALL -> context.getString(R.string.key_call);
			case KeyEvent.KEYCODE_CHANNEL_DOWN -> context.getString(R.string.key_channel_down);
			case KeyEvent.KEYCODE_CHANNEL_UP -> context.getString(R.string.key_channel_up);
			case KeyEvent.KEYCODE_DPAD_UP -> context.getString(R.string.key_dpad_up);
			case KeyEvent.KEYCODE_DPAD_DOWN -> context.getString(R.string.key_dpad_down);
			case KeyEvent.KEYCODE_DPAD_LEFT -> context.getString(R.string.key_dpad_left);
			case KeyEvent.KEYCODE_DPAD_RIGHT -> context.getString(R.string.key_dpad_right);
			case KeyEvent.KEYCODE_MENU -> context.getString(R.string.key_menu);
			case KeyEvent.KEYCODE_NUMPAD_ADD -> "Num +";
			case KeyEvent.KEYCODE_NUMPAD_DIVIDE -> "Num /";
			case KeyEvent.KEYCODE_NUMPAD_DOT -> "Num .";
			case KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> "Num *";
			case KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> "Num -";
			case KeyEvent.KEYCODE_PROG_RED -> context.getString(R.string.key_red);
			case KeyEvent.KEYCODE_PROG_GREEN -> context.getString(R.string.key_green);
			case KeyEvent.KEYCODE_PROG_YELLOW -> context.getString(R.string.key_yellow);
			case KeyEvent.KEYCODE_PROG_BLUE -> context.getString(R.string.key_blue);
			case KeyEvent.KEYCODE_SOFT_LEFT -> context.getString(R.string.key_soft_left);
			case KeyEvent.KEYCODE_SOFT_RIGHT -> context.getString(R.string.key_soft_right);
			case KeyEvent.KEYCODE_VOLUME_MUTE -> context.getString(R.string.key_volume_mute);
			case KeyEvent.KEYCODE_VOLUME_DOWN -> context.getString(R.string.key_volume_down);
			case KeyEvent.KEYCODE_VOLUME_UP -> context.getString(R.string.key_volume_up);
			default -> codeToSystemName(context, keyCode);
		};
	}

	private static String codeToSystemName(@NonNull Context context, int keyCode) {
		String name = KeyEvent.keyCodeToString(keyCode).replace("KEYCODE_", "");

		if (new Text(name).isNumeric()) {
			return context.getString(R.string.key_key) + " #" + name;
		}

		Language english = LanguageCollection.getByLanguageCode("en");
		String[] parts = name.split("_");
		StringBuilder formattedName = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			formattedName.append(new Text(english, parts[i].toLowerCase()).capitalize());
			if (i < parts.length - 1) {
				formattedName.append(" ");
			}
		}

		return formattedName.toString();
	}
}
