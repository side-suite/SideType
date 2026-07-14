package io.github.sspanak.tt9.preferences.screens.deleteWords;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceViewHolder;

import io.github.sspanak.tt9.R;

/**
 * A per-language section header for the "Manage added words" screen. Looks like a normal category
 * title, but carries a "+" button on the right that quick-adds a word to that language.
 */
public class PreferenceCategoryAddWord extends PreferenceCategory {
	private Runnable onAddWord;

	public PreferenceCategoryAddWord(@NonNull Context context) {
		super(context);
		setLayoutResource(R.layout.pref_category_add_word);
		setSelectable(false);
	}

	void setOnAddWord(Runnable onAddWord) {
		this.onAddWord = onAddWord;
	}

	@Override
	public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
		super.onBindViewHolder(holder);

		View button = holder.findViewById(R.id.add_word_button);
		if (button != null) {
			button.setOnClickListener(v -> {
				if (onAddWord != null) {
					onAddWord.run();
				}
			});
		}
	}
}
