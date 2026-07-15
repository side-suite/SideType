package io.github.sspanak.tt9.preferences.screens.voice;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.ime.voice.VoiceInputOps;
import io.github.sspanak.tt9.ime.voice.VoskModelCatalog;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.ui.UI;

/**
 * One language's voice model: its real size, whether it is downloaded, and a tap to download or
 * delete it.
 * <p>
 * Tapping "download" here <i>is</i> the consent — the size is on the row the user is pressing. That
 * is also why this screen exists at all: without it there is no way to delete a model, and tens of
 * megabytes per language would sit in filesDir forever with nothing to remove them.
 */
class PreferenceVoiceModel extends Preference {
	@NonNull private final VoiceInputOps voiceInputOps;
	@NonNull private final Language language;
	@Nullable private final VoskModelCatalog.Entry model;

	PreferenceVoiceModel(@NonNull Context context, @NonNull VoiceInputOps voiceInputOps, @NonNull Language language) {
		super(context);

		this.voiceInputOps = voiceInputOps;
		this.language = language;
		this.model = voiceInputOps.getModel(language);

		setKey("voice_model_" + language.getId());
		setTitle(language.getName());
		setPersistent(false);
		setIconSpaceReserved(false);

		refresh();
		setOnPreferenceClickListener(p -> { onRowTapped(); return true; });
	}

	/**
	 * Languages with no model are listed rather than hidden, and say so. Finnish, Norwegian and Danish
	 * have no Vosk model at any size (docs/adr/0001) — a user who simply doesn't see their language
	 * assumes a bug, so the absence is stated instead.
	 */
	private void refresh() {
		if (model == null) {
			setSummary(getContext().getString(R.string.voice_model_state_unsupported));
			setEnabled(false);
			return;
		}

		setEnabled(true);
		setSummary(getContext().getString(
			voiceInputOps.isModelReady(language) ? R.string.voice_model_state_downloaded : R.string.voice_model_state_available,
			model.getFormattedSize()
		));
	}

	/** Named to avoid colliding with {@link Preference#onClick()}, which means something else. */
	private void onRowTapped() {
		if (model == null) {
			return;
		}

		if (voiceInputOps.isModelReady(language)) {
			voiceInputOps.deleteModel(language);
			UI.toast(getContext(), getContext().getString(R.string.voice_model_deleted, language.getName()));
			refresh();
			return;
		}

		if (voiceInputOps.isDownloadingModel()) {
			return;
		}

		setEnabled(false);
		voiceInputOps.downloadModel(
			language,
			() -> { refresh(); UI.toast(getContext(), getContext().getString(R.string.voice_model_ready, language.getName())); },
			percent -> setSummary(getContext().getString(
				percent >= 100 ? R.string.voice_model_unpacking : R.string.voice_model_downloading_short,
				percent >= 100 ? language.getName() : String.valueOf(percent)
			)),
			error -> { refresh(); UI.toastLong(getContext(), error.toString()); }
		);
	}
}
