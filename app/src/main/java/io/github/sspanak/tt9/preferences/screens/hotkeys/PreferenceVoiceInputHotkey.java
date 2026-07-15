package io.github.sspanak.tt9.preferences.screens.hotkeys;

import android.content.Context;

import androidx.annotation.NonNull;

import io.github.sspanak.tt9.commands.Command;
import io.github.sspanak.tt9.ime.voice.VoiceInputOps;
import io.github.sspanak.tt9.languages.LanguageCollection;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;

public class PreferenceVoiceInputHotkey extends PreferenceHotkey {
	// PreferenceHotkey keeps its own copy private, so hold a second reference rather than widen it.
	@NonNull private final SettingsStore voiceSettings;

	public PreferenceVoiceInputHotkey(@NonNull Context context, @NonNull SettingsStore settings, @NonNull Command command) {
		super(context, settings, command);
		voiceSettings = settings;
	}

	@Override
	public void populate() {
		boolean isAvailable = isVoiceInputPossible();
		setVisible(isAvailable);
		if (isAvailable) {
			super.populate();
		}
	}

	/**
	 * Whether any enabled language has a voice model at all — the hotkey is global, so it is only
	 * worth showing if it could do something for at least one of the user's languages. A Finnish-only
	 * user has no voice input and never will (docs/adr/0001), so the hotkey stays hidden for them.
	 * <p>
	 * Note this asks the catalog, not the disk. Models download on demand, so gating on whether one
	 * is already downloaded would hide the control on a fresh install and leave no way to trigger the
	 * download that would bring it back.
	 */
	private boolean isVoiceInputPossible() {
		for (int languageId : voiceSettings.getEnabledLanguageIds()) {
			if (VoiceInputOps.isAvailable(LanguageCollection.getLanguage(languageId))) {
				return true;
			}
		}
		return false;
	}
}
