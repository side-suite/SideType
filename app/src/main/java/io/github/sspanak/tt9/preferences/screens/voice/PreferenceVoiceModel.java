package io.github.sspanak.tt9.preferences.screens.voice;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.preference.Preference;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.ime.voice.VoiceInputOps;
import io.github.sspanak.tt9.ui.UI;

/**
 * The voice model: its real size, whether it is downloaded, and a tap to download or delete it.
 * <p>
 * Tapping "download" here <i>is</i> the consent, since the size is on the row being pressed. It is
 * also the only place the model can be deleted.
 */
class PreferenceVoiceModel extends Preference {
	@NonNull private final VoiceInputOps voiceInputOps;

	PreferenceVoiceModel(@NonNull Context context, @NonNull VoiceInputOps voiceInputOps) {
		super(context);

		this.voiceInputOps = voiceInputOps;

		setKey("voice_model");
		setTitle(R.string.voice_model_name);
		setPersistent(false);
		setIconSpaceReserved(false);

		refresh();
		setOnPreferenceClickListener(p -> { onRowTapped(); return true; });
	}

	private void refresh() {
		setEnabled(true);
		setSummary(getContext().getString(
			voiceInputOps.isModelReady() ? R.string.voice_model_state_downloaded : R.string.voice_model_state_available,
			voiceInputOps.getModel().getFormattedSize()
		));
	}

	/** Named to avoid colliding with {@link Preference#onClick()}, which means something else. */
	private void onRowTapped() {
		if (voiceInputOps.isModelReady()) {
			voiceInputOps.deleteModel();
			UI.toast(getContext(), R.string.voice_model_deleted);
			refresh();
			return;
		}

		if (voiceInputOps.isDownloadingModel()) {
			return;
		}

		setEnabled(false);
		voiceInputOps.downloadModel(
			() -> { refresh(); UI.toast(getContext(), R.string.voice_model_ready); },
			percent -> setSummary(getContext().getString(R.string.voice_model_downloading, percent)),
			error -> { refresh(); UI.toastLong(getContext(), error.toString()); }
		);
	}
}
