package io.github.sspanak.tt9.commands;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.ime.TraditionalT9;
import io.github.sspanak.tt9.ime.helpers.Key;
import io.github.sspanak.tt9.ime.modes.InputMode;
import io.github.sspanak.tt9.ime.modes.InputModeKind;

/**
 * Shows the symbols / punctuation row (the characters of the "1" key: . , ? ! * + etc.).
 *
 * This is the tap action for the Sidephone Compact QWERTY "SYM" key. It is the sibling of
 * {@link CmdShowEmojis}: emojis are reached by pressing the "1" key TWICE (sequence "11"), while a
 * SINGLE "1" press stays on the punctuation/symbols level ("1"). Pressing SYM repeatedly therefore
 * naturally cycles through the punctuation groups (and, if held via {@link CmdShowEmojis}, emojis).
 */
public class CmdShowSymbols implements Command {
	public static final String ID = "key_show_symbols";
	@Override public String getId() { return ID; }
	@Override public int getIcon() { return R.drawable.ic_fn_show_emojis; }
	@Override public int getName() { return R.string.function_show_symbols; }


	@Override public boolean isAvailable(@Nullable TraditionalT9 tt9) {
		return
			tt9 != null
			&& isAvailableStd(tt9)
			&& InputModeKind.isPredictive(tt9.getInputMode())
			&& !tt9.areEmojiCategoriesVisible()
			&& !tt9.isTouchExplorationEnabled();
	}


	@Override
	public boolean run(@Nullable TraditionalT9 tt9) {
		if (tt9 == null || !isAvailable(tt9)) {
			return false;
		}

		// If the symbols/punctuation list is already showing, cycle to the NEXT symbol instead of
		// pressing "1" again. A second "1" press would make the sequence "11", which is the emoji
		// sequence (jumping to emojis). We detect "showing symbols" by looking at the currently
		// selected suggestion: a single non-alphanumeric character. This reflects the live selection
		// and survives scrolling, so repeated SYM presses walk through the ENTIRE symbol row.
		InputMode mode = tt9.getInputMode();
		String current = tt9.getSuggestionOps() != null ? tt9.getSuggestionOps().getCurrent() : null;
		boolean showingSymbols =
			current != null && current.length() == 1
			&& !Character.isLetterOrDigit(current.charAt(0))
			&& !Character.isWhitespace(current.charAt(0));
		if (mode != null && showingSymbols && !mode.containsEmojis()) {
			CmdSuggestionNext.scrollSuggestions(tt9, false);
			if (tt9.getMainView() != null) {
				tt9.getMainView().renderDynamicKeys();
			}
			return true;
		}

		final int keyCode = Key.numberToCode(tt9.getSettings(), 1);

		// A single "1" press. When the language keeps letters on the 1-key (rare for Latin layouts),
		// punctuation lives on the long-press, mirroring CmdShowEmojis.firstPress1().
		final boolean useHold = tt9.getLanguage() != null && tt9.getLanguage().hasLettersOnAllKeys();
		if (useHold) {
			tt9.onKeyLongPress(keyCode, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
		} else {
			tt9.onKeyDown(keyCode, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
		}
		tt9.onKeyUp(keyCode, new KeyEvent(KeyEvent.ACTION_UP, keyCode));

		return true;
	}
}
