package io.github.sspanak.tt9.preferences.screens.voice;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceCategory;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.commands.CmdVoiceInput;
import io.github.sspanak.tt9.commands.Command;
import io.github.sspanak.tt9.commands.CommandCollection;
import io.github.sspanak.tt9.ime.voice.VoiceInputOps;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.languages.LanguageCollection;
import io.github.sspanak.tt9.preferences.PreferencesActivity;
import io.github.sspanak.tt9.preferences.screens.BaseScreenFragment;
import io.github.sspanak.tt9.preferences.screens.hotkeys.PreferenceVoiceInputHotkey;
import io.github.sspanak.tt9.util.Logger;

/**
 * Everything voice, in one screen: which languages have a model, how big it really is, whether it is
 * downloaded, and the hotkey.
 * <p>
 * The hotkey lives here rather than under Settings → Hotkeys so there is exactly one definition of
 * it. The known cost is that a user scanning the Hotkeys screen for "voice" now finds nothing; this
 * screen is meant to be the obvious home for it instead. See SID-57.
 */
public class VoiceInputScreen extends BaseScreenFragment {
	final public static String NAME = "VoiceInput";

	public VoiceInputScreen() { super(); }
	public VoiceInputScreen(@Nullable PreferencesActivity activity) { super(activity); }

	@Override public String getName() { return NAME; }
	@Override protected int getTitle() { return R.string.pref_voice_input; }
	@Override protected int getXml() { return R.xml.prefs_screen_voice_input; }

	@Override
	protected void onCreate() {
		createModelList();
		createHotkey();
		resetFontSize(false);
	}

	private void createModelList() {
		if (activity == null) {
			return;
		}

		PreferenceCategory category = findPreference("category_voice_models");
		if (category == null) {
			Logger.w(NAME, "Cannot append voice models to a NULL category");
			return;
		}

		// Only the user's own languages — listing all 32 of Vosk's would be noise. Unsupported ones
		// are included so the absence is explained rather than silently missing.
		VoiceInputOps voiceInputOps = new VoiceInputOps(activity, null, null, null, null);
		for (int languageId : activity.getSettings().getEnabledLanguageIds()) {
			Language language = LanguageCollection.getLanguage(languageId);
			if (language != null) {
				category.addPreference(new PreferenceVoiceModel(activity, voiceInputOps, language));
			}
		}
	}

	private void createHotkey() {
		if (activity == null) {
			return;
		}

		PreferenceCategory category = findPreference("category_voice_hotkey");
		if (category == null) {
			Logger.w(NAME, "Cannot append the voice hotkey to a NULL category");
			return;
		}

		Command voiceInput = CommandCollection.getById(CommandCollection.COLLECTION_HOTKEYS, CmdVoiceInput.ID);
		if (voiceInput == null) {
			return;
		}

		PreferenceVoiceInputHotkey hotkey = new PreferenceVoiceInputHotkey(activity, activity.getSettings(), voiceInput);
		category.addPreference(hotkey);
		hotkey.populate();
	}
}
