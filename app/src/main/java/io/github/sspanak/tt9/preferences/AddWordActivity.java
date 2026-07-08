package io.github.sspanak.tt9.preferences;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.db.DataStore;
import io.github.sspanak.tt9.hacks.InputType;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.languages.LanguageCollection;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.ui.EdgeToEdgeActivity;
import io.github.sspanak.tt9.ui.UI;

/**
 * A small modal-styled screen for typing a brand-new word to add to the dictionary. It is a real
 * Activity (not an IME-hosted dialog) so that multi-tap (ABC) input works — a new word cannot be
 * predicted, and multi-tap composing does not work inside dialogs shown from the IME.
 */
public class AddWordActivity extends EdgeToEdgeActivity {
	private SettingsStore settings;
	private Language language;
	private EditText input;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		settings = new SettingsStore(this);
		LanguageCollection.init(this);
		DataStore.init(this);

		language = LanguageCollection.getLanguage(settings.getInputLanguage());
		if (language == null) {
			language = LanguageCollection.getByLanguageCode("en");
		}

		setTitle(R.string.add_word_title);
		setContentView(buildLayout());
		input.requestFocus();
	}

	private View buildLayout() {
		LinearLayout root = new LinearLayout(this);
		root.setOrientation(LinearLayout.VERTICAL);
		int pad = dp(20);
		root.setPadding(pad, pad, pad, pad);

		TextView prompt = new TextView(this);
		prompt.setText(getString(R.string.add_word_type_prompt, language != null ? language.getName() : ""));
		prompt.setPadding(0, 0, 0, dp(12));
		root.addView(prompt);

		input = new EditText(this);
		input.setSingleLine(true);
		// Force multi-tap (ABC) mode for this field — a new word cannot be predicted.
		input.setPrivateImeOptions(InputType.OWN_ADD_WORD_FIELD_TAG);
		root.addView(input);

		LinearLayout buttons = new LinearLayout(this);
		buttons.setOrientation(LinearLayout.HORIZONTAL);
		buttons.setGravity(Gravity.END);
		buttons.setPadding(0, dp(16), 0, 0);

		Button cancel = new Button(this);
		cancel.setText(android.R.string.cancel);
		cancel.setOnClickListener(v -> finish());
		buttons.addView(cancel);

		Button add = new Button(this);
		add.setText(R.string.add_word_add);
		add.setOnClickListener(v -> onAdd());
		buttons.addView(add);

		root.addView(buttons);
		return root;
	}

	private void onAdd() {
		String word = input.getText().toString().trim();
		if (word.isEmpty() || language == null) {
			finish();
			return;
		}

		DataStore.put(
			(result) -> runOnUiThread(() -> {
				UI.toastLong(this, result.toHumanFriendlyString(this));
				finish();
			}),
			language,
			word
		);
	}

	private int dp(int value) {
		return Math.round(value * getResources().getDisplayMetrics().density);
	}
}
