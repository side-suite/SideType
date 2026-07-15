package io.github.sspanak.tt9.ui.dialogs;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.ime.TraditionalT9;
import io.github.sspanak.tt9.ime.voice.VoskModelCatalog;
import io.github.sspanak.tt9.languages.Language;

/**
 * Asks before downloading a voice model. Models are tens of megabytes and are fetched on demand, so
 * a mic press must never start that download on its own — the user agrees first, with the real size
 * in front of them.
 * <p>
 * The size comes from the pinned catalog rather than a literal. The SID-8 spike showed a guessed
 * "~41 MB" for a model that is 303.5 MB; a consent prompt carrying a wrong number means the user
 * agreed to something other than what happened. See docs/adr/0003.
 * <p>
 * This is a plain IME-attached dialog ({@link PopupDialog}), not an Activity. An earlier plan had it
 * as an Activity on the theory that an IME cannot show a dialog — untrue, as
 * {@link io.github.sspanak.tt9.ui.PopupBuilder#showFromIme} and {@link AddWordDialog} demonstrate.
 * {@link RequestPermissionDialog} is an Activity because requesting a runtime permission requires
 * one, which is a different problem. Staying a dialog keeps the input connection intact, so there is
 * no composing-state round trip to get wrong.
 */
public class VoiceModelDownloadDialog extends PopupDialog {
	@NonNull private final TraditionalT9 tt9;
	@NonNull private final Language language;


	public VoiceModelDownloadDialog(@NonNull TraditionalT9 tt9, @NonNull Language language, @NonNull VoskModelCatalog.Entry model) {
		super(tt9, R.style.TTheme_AddWord);

		this.tt9 = tt9;
		this.language = language;

		title = tt9.getString(R.string.voice_model_download_title);
		OKLabel = tt9.getString(R.string.voice_model_download_confirm);
		message = tt9.getString(R.string.voice_model_download_message, language.getName(), model.getFormattedSize());
	}


	private void onOK() {
		close();
		tt9.downloadVoiceModelAndListen(language);
	}


	/** Shows the prompt. Returns false if the dialog could not be attached to the IME window. */
	public boolean show() {
		return render(this::onOK, null, null);
	}


	/**
	 * Prompts for the given language's model if one is needed. Returns true if a prompt was shown, so
	 * the caller knows not to carry on and start listening.
	 */
	public static boolean showIfModelMissing(@NonNull TraditionalT9 tt9, @Nullable Language language) {
		if (language == null || !tt9.getVoiceInputOps().isModelMissing(language)) {
			return false;
		}

		VoskModelCatalog.Entry model = tt9.getVoiceInputOps().getModel(language);
		if (model == null) {
			return false;
		}

		return new VoiceModelDownloadDialog(tt9, language, model).show();
	}
}
