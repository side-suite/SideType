package io.github.sspanak.tt9.ui.dialogs;

import androidx.annotation.NonNull;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.ime.TraditionalT9;

/**
 * Asks before downloading the voice model. It is tens of megabytes and fetched on demand, so the
 * voice key must never start that download on its own: the user agrees first, with the real size
 * (taken from the pinned catalogue, never a literal) in front of them.
 * <p>
 * This is a plain IME-attached dialog ({@link PopupDialog}), not an Activity, which keeps the input
 * connection intact while it is up.
 */
public class VoiceModelDownloadDialog extends PopupDialog {
	@NonNull private final TraditionalT9 tt9;


	public VoiceModelDownloadDialog(@NonNull TraditionalT9 tt9) {
		super(tt9, R.style.TTheme_AddWord);

		this.tt9 = tt9;

		title = tt9.getString(R.string.voice_model_download_title);
		OKLabel = tt9.getString(R.string.voice_model_download_confirm);
		message = tt9.getString(R.string.voice_model_download_message, tt9.getVoiceInputOps().getModel().getFormattedSize());
	}


	private void onOK() {
		close();
		tt9.downloadVoiceModel();
	}


	/** Shows the prompt. Returns false if the dialog could not be attached to the IME window. */
	public boolean show() {
		return render(this::onOK, null, null);
	}


	/**
	 * Prompts for the model if it is not on disk yet. Returns true if a prompt was shown, so the
	 * caller knows not to carry on and start listening.
	 */
	public static boolean showIfModelMissing(@NonNull TraditionalT9 tt9) {
		if (tt9.getVoiceInputOps().isModelReady()) {
			return false;
		}

		return new VoiceModelDownloadDialog(tt9).show();
	}
}
