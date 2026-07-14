package io.github.sspanak.tt9.preferences.screens.deleteWords;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.db.entities.CustomWord;
import io.github.sspanak.tt9.languages.NaturalLanguage;
import io.github.sspanak.tt9.preferences.custom.PreferencePlainText;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;

class DeletableWordsList {
	private final PreferenceScreen screen;
	private final boolean largeFont;
	private final ArrayList<Preference> dynamicItems = new ArrayList<>();
	private Preference summary;
	private int currentWords = 0;
	private long totalWords = 0;

	DeletableWordsList(SettingsStore settings, Preference preference) {
		screen = preference instanceof PreferenceScreen ? (PreferenceScreen) preference : null;
		largeFont = settings.getSettingsFontSize() == SettingsStore.FONT_SIZE_LARGE;
	}

	private void clear() {
		if (screen == null) {
			return;
		}

		for (Preference item : dynamicItems) {
			screen.removePreference(item);
		}
		dynamicItems.clear();
		summary = null;
	}

	void delete(PreferenceDeletableWord wordItem) {
		if (screen == null) {
			return;
		}

		PreferenceGroup category = wordItem.getParent();
		if (category != null) {
			category.removePreference(wordItem);
			// drop the language header once its last word is gone
			if (category.getPreferenceCount() == 0) {
				screen.removePreference(category);
				dynamicItems.remove(category);
			}
		}

		currentWords = Math.max(0, currentWords - 1);
		setTotalWords(totalWords - 1);
	}

	private PreferencePlainText newPlainText() {
		PreferencePlainText pref = new PreferencePlainText(screen.getContext());
		pref.setLayoutResource(largeFont ? R.layout.pref_plain_text_large : R.layout.pref_plain_text);
		return pref;
	}

	private void addSummary() {
		if (screen == null) {
			return;
		}

		summary = newPlainText();
		screen.addPreference(summary);
		dynamicItems.add(summary);
	}

	private void addNoResult(boolean noSearchTerm) {
		if (screen == null) {
			return;
		}

		PreferencePlainText pref = newPlainText();
		pref.setSummary(noSearchTerm ? "--" : screen.getContext().getString(R.string.search_results_void));
		screen.addPreference(pref);
		dynamicItems.add(pref);
	}

	private void addLanguageGroup(NaturalLanguage language, ArrayList<CustomWord> words) {
		PreferenceCategory category = new PreferenceCategory(screen.getContext());
		category.setTitle(language.getName());
		screen.addPreference(category); // must be attached to the screen before adding children
		dynamicItems.add(category);

		for (CustomWord word : words) {
			PreferenceDeletableWord pref = new PreferenceDeletableWord(screen.getContext());
			pref.setParent(this);
			pref.setWord(word);
			pref.setLayoutResource(largeFont ? pref.getLargeLayout() : pref.getDefaultLayout());
			category.addPreference(pref);
		}
	}

	void addWords(ArrayList<CustomWord> words) {
		// group the (already word-sorted) results by language, then order the groups by language name
		LinkedHashMap<NaturalLanguage, ArrayList<CustomWord>> byLanguage = new LinkedHashMap<>();
		for (CustomWord word : words) {
			byLanguage.computeIfAbsent(word.language, key -> new ArrayList<>()).add(word);
		}

		ArrayList<NaturalLanguage> languages = new ArrayList<>(byLanguage.keySet());
		languages.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

		for (NaturalLanguage language : languages) {
			addLanguageGroup(language, byLanguage.get(language));
		}
	}

	void setResult(@NonNull String searchTerm, ArrayList<CustomWord> words) {
		clear();
		addSummary();

		if (words == null || words.isEmpty()) {
			addNoResult(searchTerm.isEmpty());
		} else {
			addWords(words);
		}

		currentWords = words == null ? 0 : words.size();
		setResultCount();
	}

	void setTotalWords(long total) {
		totalWords = total > 0 ? total : 0;
		setResultCount();
	}

	private void setResultCount() {
		if (summary != null) {
			// pref_plain_text renders the summary slot only, so the count goes there rather than in the title
			String results = " (" + currentWords + "/" + totalWords + ")";
			summary.setSummary(summary.getContext().getString(R.string.search_results) + results);
		}
	}
}
