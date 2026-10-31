package io.github.sspanak.tt9.commands;

import androidx.annotation.Nullable;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.ime.TraditionalT9;

/**
 * Opens the Sidephone SYM grid straight on its emoji pages. Unassigned by default; a SYM tap on an
 * open symbol grid gets there too (see {@link CmdShowSymbols}).
 */
public class CmdShowEmojis implements Command {
	public static final String ID = "key_show_emojis";
	@Override public String getId() { return ID; }
	@Override public int getIcon() { return R.drawable.ic_fn_show_emojis; }
	@Override public int getName() { return R.string.function_show_emojis; }


	@Override public boolean isAvailable(@Nullable TraditionalT9 tt9) {
		return tt9 != null && !tt9.shouldBeOff() && !tt9.isVoiceInputActive();
	}


	@Override
	public boolean run(@Nullable TraditionalT9 tt9) {
		if (tt9 == null || !isAvailable(tt9)) {
			return false;
		}

		tt9.showSymbolGrid(true);
		return true;
	}
}
