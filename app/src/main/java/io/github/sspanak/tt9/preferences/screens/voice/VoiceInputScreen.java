package io.github.sspanak.tt9.preferences.screens.voice;

import android.text.InputType;

import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceCategory;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.commands.CmdVoiceInput;
import io.github.sspanak.tt9.commands.Command;
import io.github.sspanak.tt9.commands.CommandCollection;
import io.github.sspanak.tt9.ime.voice.RemoteWhisperClient;
import io.github.sspanak.tt9.ime.voice.VoiceInputOps;
import io.github.sspanak.tt9.preferences.PreferencesActivity;
import io.github.sspanak.tt9.preferences.screens.BaseScreenFragment;
import io.github.sspanak.tt9.preferences.screens.hotkeys.PreferenceHotkey;
import io.github.sspanak.tt9.util.Logger;

/**
 * Everything voice, in one screen: the model (its real size, whether it is downloaded, a tap to
 * download or delete it) and the hotkey.
 * <p>
 * The hotkey lives here rather than under Settings → Hotkeys so there is exactly one definition of
 * it. Bound to holding a key it is hold-to-talk; bound to a plain press it is a toggle.
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
		createModelRow();
		setUpOnlineTranscription();
		createHotkey();
		resetFontSize(false);
	}

	private void createModelRow() {
		if (activity == null) {
			return;
		}

		PreferenceCategory category = findPreference("category_voice_models");
		if (category == null) {
			Logger.w(NAME, "Cannot append the voice model to a NULL category");
			return;
		}

		category.addPreference(new PreferenceVoiceModel(activity, new VoiceInputOps(activity, null, null, null, null)));
	}

	private void setUpOnlineTranscription() {
		EditTextPreference key = findPreference("voice_api_key");
		if (key != null) {
			// A password field, so the key is masked and the keyboard neither predicts nor learns it.
			key.setOnBindEditTextListener(input -> {
				input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
				input.setSelection(input.getText().length());
			});
			key.setSummaryProvider(p -> getString(
				activity != null && !activity.getSettings().getVoiceApiKey().isEmpty() ? R.string.pref_voice_api_key_set : R.string.pref_voice_api_key_unset
			));
		}

		EditTextPreference url = findPreference("voice_api_url");
		if (url != null) {
			url.setOnBindEditTextListener(input -> {
				input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
				input.setHint(RemoteWhisperClient.DEFAULT_URL);
			});
			url.setSummaryProvider(p -> activity != null ? activity.getSettings().getVoiceApiUrl() : RemoteWhisperClient.DEFAULT_URL);
		}

		EditTextPreference model = findPreference("voice_api_model");
		if (model != null) {
			model.setOnBindEditTextListener(input -> {
				input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
				input.setHint(RemoteWhisperClient.DEFAULT_MODEL);
			});
			model.setSummaryProvider(p -> activity != null ? activity.getSettings().getVoiceApiModel() : RemoteWhisperClient.DEFAULT_MODEL);
		}
	}

	// The row is added here, not by a Preference subclass overriding populate(): PreferenceHotkey's
	// constructor calls populate(), so an override reading a subclass field runs before that field is
	// assigned and dereferences null.
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
			category.setVisible(false);
			return;
		}

		category.addPreference(new PreferenceHotkey(activity, activity.getSettings(), voiceInput));
	}
}
