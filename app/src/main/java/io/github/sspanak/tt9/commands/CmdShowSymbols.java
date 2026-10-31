package io.github.sspanak.tt9.commands;

import androidx.annotation.Nullable;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.ime.TraditionalT9;

/**
 * The tap action of the Sidephone "SYM" key: opens the on-screen 5×3 symbol grid, a second tap
 * switches it to emojis, a third closes it. While it is open, the tile keys pick from the grid
 * (see CommandHandler.onSymbolGridKey()).
 */
public class CmdShowSymbols implements Command {
	public static final String ID = "key_show_symbols";
	@Override public String getId() { return ID; }
	@Override public int getIcon() { return R.drawable.ic_fn_show_emojis; }
	@Override public int getName() { return R.string.function_show_symbols; }


	@Override public boolean isAvailable(@Nullable TraditionalT9 tt9) {
		return tt9 != null && !tt9.shouldBeOff() && !tt9.isVoiceInputActive();
	}


	@Override
	public boolean run(@Nullable TraditionalT9 tt9) {
		if (tt9 == null || !isAvailable(tt9)) {
			return false;
		}

		tt9.toggleSymbolGrid();
		return true;
	}
}
