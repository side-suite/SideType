package io.github.sspanak.tt9.ui;

import android.content.Context;
import android.graphics.Paint;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.util.Logger;

/**
 * The emoji and symbol catalogue behind the SYM grid (ui.tray.SymbolGridPages) and the key-binds
 * editor (EmojiBindsActivity).
 * <p>
 * Emojis come from Unicode's own list, emoji-test.txt, bundled as res/raw/emoji_list.txt by
 * scripts/generate-emoji-list.py: the official order and groups, every fully-qualified emoji
 * including the multi-character ones (families, flags, keycaps), minus skin-tone variants. Android
 * exposes no emoji catalogue, and the hand-written code-point ranges this replaced had gaps (the
 * hearts, for one). Whatever the phone's font cannot draw is dropped at load time, once per process.
 */
public class EmojiData {
	private static final String LOG_TAG = EmojiData.class.getSimpleName();
	private static final char GROUP_MARKER = '=';

	/** One group of the Unicode list: a tab icon and its emojis, in official order. */
	public record Category(@NonNull String icon, @NonNull String name, @NonNull List<String> emojis) {}

	// Curated punctuation / symbols that are awkward or impossible to reach on the 2-letter tile.
	public static final String[] SYMBOLS = {
		".", ",", "?", "!", "'", "\"", "-", "_", ":", ";",
		"@", "#", "$", "%", "&", "*", "+", "=", "/", "\\",
		"(", ")", "[", "]", "{", "}", "<", ">", "|", "~",
		"^", "`", "•", "…", "€", "£", "¥", "¢", "°", "§",
		"©", "®", "™", "±", "×", "÷", "≈", "≠", "√", "∞",
		"→", "←", "↑", "↓", "★", "☆", "♥", "♦", "♣", "♠",
		"✓", "✗", "«", "»", "¿", "¡", "µ", "π", "Ω", "∆",
	};

	private static List<Category> categories = null;

	private EmojiData() {}


	@NonNull
	public static synchronized List<Category> getCategories(@NonNull Context context) {
		if (categories == null) {
			categories = load(context);
		}

		return categories;
	}


	@NonNull
	public static List<String> getEmojiForCategory(@NonNull Context context, int index) {
		final List<Category> all = getCategories(context);
		return index >= 0 && index < all.size() ? all.get(index).emojis() : Collections.emptyList();
	}


	@NonNull
	private static List<Category> load(@NonNull Context context) {
		final ArrayList<Category> result = new ArrayList<>();
		final Paint paint = new Paint();
		int skipped = 0;

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getResources().openRawResource(R.raw.emoji_list), StandardCharsets.UTF_8))) {
			String icon = null;
			String name = null;
			ArrayList<String> emojis = new ArrayList<>();

			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				if (line.isEmpty()) {
					continue;
				}

				if (line.charAt(0) == GROUP_MARKER) {
					if (icon != null && !emojis.isEmpty()) {
						result.add(new Category(icon, name, Collections.unmodifiableList(emojis)));
					}
					final int tab = line.indexOf('\t');
					icon = tab > 0 ? line.substring(1, tab) : line.substring(1);
					name = tab > 0 ? line.substring(tab + 1) : "";
					emojis = new ArrayList<>();
				} else if (paint.hasGlyph(line)) {
					emojis.add(line);
				} else {
					skipped++;
				}
			}

			if (icon != null && !emojis.isEmpty()) {
				result.add(new Category(icon, name, Collections.unmodifiableList(emojis)));
			}
		} catch (IOException e) {
			Logger.e(LOG_TAG, "Failed reading the emoji list. " + e.getMessage());
		}

		Logger.d(LOG_TAG, "Loaded " + result.size() + " emoji groups; " + skipped + " emojis the font cannot draw were skipped.");
		return Collections.unmodifiableList(result);
	}
}
