package io.github.sspanak.tt9.ui.tray;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.ui.EmojiData;
import io.github.sspanak.tt9.util.chars.Characters;

/**
 * What the SYM grid shows, split into pages of {@link SymbolGrid#CELLS}.
 * <p>
 * Emojis start with the most recently used ones. Symbols: the language's own punctuation first (the "1" key list and its extra group, then the
 * currency group), followed by the curated table in {@link EmojiData}. Emojis: every group of the
 * Unicode list in {@link EmojiData}, in order. Both are deduplicated and keep only single glyphs, which also drops
 * the "(…)" show-more markers the suggestion bar uses. The first symbol page follows the keycaps.
 */
public class SymbolGridPages {
	public static final int PAGE_SIZE = SymbolGrid.CELLS;

	@Nullable private static ArrayList<String> emojiCache;


	/**
	 * Sidephone: on the first symbol page the symbols printed on the tile sit on their own keys, so
	 * the grid matches the keycaps: QW = !, OP = ., AS = ?, L = ,, ZX = @. The numerals printed on the
	 * other keys are not symbols and are not placed. Cell indices are those of SymbolGrid.
	 */
	private static final String[] FIRST_PAGE_PINNED = new String[SymbolGrid.CELLS];
	static {
		FIRST_PAGE_PINNED[0] = "!";  // QW
		FIRST_PAGE_PINNED[4] = ".";  // OP
		FIRST_PAGE_PINNED[5] = "?";  // AS
		FIRST_PAGE_PINNED[9] = ",";  // L
		FIRST_PAGE_PINNED[10] = "@"; // ZX
	}


	@NonNull
	public static ArrayList<String> symbols(@NonNull SettingsStore settings, @Nullable Language language) {
		LinkedHashSet<String> unique = new LinkedHashSet<>();

		if (language != null) {
			addGlyphs(unique, settings.getChars1AsList(language));
			addGlyphs(unique, settings.getCharsExtraAsList(language, SettingsStore.CHARS_GROUP_1));
			addGlyphs(unique, settings.getCharsExtraAsList(language, SettingsStore.CHARS_GROUP_0));
		}
		addGlyphs(unique, List.of(EmojiData.SYMBOLS));

		// first page: the pinned symbols on their keys, the rest of the list filling the other cells
		for (String pinned : FIRST_PAGE_PINNED) {
			if (pinned != null) {
				unique.remove(pinned);
			}
		}

		ArrayList<String> rest = new ArrayList<>(unique);
		ArrayList<String> all = new ArrayList<>(rest.size() + SymbolGrid.CELLS);
		int next = 0;
		for (int cell = 0; cell < SymbolGrid.CELLS; cell++) {
			if (FIRST_PAGE_PINNED[cell] != null) {
				all.add(FIRST_PAGE_PINNED[cell]);
			} else {
				all.add(next < rest.size() ? rest.get(next++) : "");
			}
		}
		all.addAll(rest.subList(Math.min(next, rest.size()), rest.size()));

		return all;
	}


	/**
	 * The most recently picked emojis fill the first page on their own; the Unicode catalogue (see
	 * EmojiData) starts on the next page, in its official group order. The flattened list is built
	 * once per process.
	 */
	@NonNull
	public static ArrayList<String> emojis(@NonNull Context context, @NonNull SettingsStore settings) {
		if (emojiCache == null) {
			LinkedHashSet<String> unique = new LinkedHashSet<>();
			for (EmojiData.Category category : EmojiData.getCategories(context)) {
				unique.addAll(category.emojis());
			}
			emojiCache = new ArrayList<>(unique);
		}

		// The recent emojis are a page of their own: however many there are, the catalogue starts on
		// page 2, so the first page is always "recent" and nothing else.
		ArrayList<String> all = new ArrayList<>(settings.getRecentEmojis());
		if (!all.isEmpty()) {
			while (all.size() % PAGE_SIZE != 0) {
				all.add("");
			}
		}
		all.addAll(emojiCache);
		return all;
	}


	public static int pageCount(@NonNull List<String> all) {
		return Math.max(1, (all.size() + PAGE_SIZE - 1) / PAGE_SIZE);
	}


	@NonNull
	public static List<String> page(@NonNull List<String> all, int page) {
		final int start = Math.min(page * PAGE_SIZE, all.size());
		final int end = Math.min(start + PAGE_SIZE, all.size());
		return all.subList(start, end);
	}


	private static void addGlyphs(@NonNull LinkedHashSet<String> target, @NonNull List<String> source) {
		for (String s : source) {
			if (
				s != null
				&& !s.isEmpty()
				&& s.codePointCount(0, s.length()) == 1
				&& !Character.isWhitespace(s.codePointAt(0))
				&& !Characters.PLACEHOLDER.equals(s)
			) {
				target.add(s);
			}
		}
	}
}
