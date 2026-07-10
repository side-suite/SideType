package io.github.sspanak.tt9.ui;

import android.graphics.Paint;

import java.util.ArrayList;

/**
 * Shared emoji + symbol catalogue used by both the on-screen drawer ({@link EmojiDrawer}) and the
 * key-binds editor (EmojiBindsActivity). Android exposes no categorized emoji API, so the ranges are
 * curated here; hasGlyph() filters out anything the current device font can't actually draw.
 */
public class EmojiData {
	// Emoji categories, each a representative icon (tab label) + the Unicode code-point ranges that
	// mostly fall under it. Ranges are disjoint to avoid duplicates.
	public static final Object[][] CATEGORIES = {
		// smileys (faces only)
		{"😀", new int[][]{{0x1F600, 0x1F644}, {0x1F910, 0x1F917}, {0x1F920, 0x1F92F}, {0x1F970, 0x1F97A}, {0x1F9D0, 0x1F9D0}}},
		// people, hands & gestures
		{"👍", new int[][]{{0x1F440, 0x1F450}, {0x1F466, 0x1F487}, {0x1F595, 0x1F596}, {0x1F645, 0x1F64F}, {0x1F918, 0x1F91F}, {0x1F930, 0x1F93A}, {0x1F9B5, 0x1F9BB}, {0x1F9D1, 0x1F9DD}, {0x261D, 0x261D}, {0x270A, 0x270D}}},
		// animals & nature
		{"🐻", new int[][]{{0x1F400, 0x1F43F}, {0x1F980, 0x1F9AE}, {0x1F330, 0x1F344}}},
		// food & drink
		{"🍔", new int[][]{{0x1F345, 0x1F37F}, {0x1F32D, 0x1F32F}, {0x1F950, 0x1F96F}, {0x1F9C0, 0x1F9CB}}},
		// activity & sport
		{"⚽", new int[][]{{0x1F380, 0x1F3D3}, {0x1F3C0, 0x1F3C9}, {0x1F93C, 0x1F93F}, {0x26BD, 0x26BE}}},
		// travel & places
		{"🚗", new int[][]{{0x1F680, 0x1F6FF}, {0x1F3E0, 0x1F3F0}, {0x1F30D, 0x1F320}, {0x1F5FB, 0x1F5FF}}},
		// objects & clothing
		{"👕", new int[][]{{0x1F451, 0x1F465}, {0x1F4A0, 0x1F4FF}, {0x1F510, 0x1F5FA}, {0x1F9F0, 0x1F9FF}}},
		// symbols (dingbats/weather; hands 270A-270D excluded — they live under People)
		{"❤", new int[][]{{0x2600, 0x2638}, {0x263A, 0x26FF}, {0x2700, 0x2709}, {0x270E, 0x27BF}, {0x1F500, 0x1F50F}}},
	};

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

	private EmojiData() {}

	public static ArrayList<String> getEmojiForCategory(int catIndex) {
		Paint paint = new Paint();
		ArrayList<String> all = new ArrayList<>();
		int[][] ranges = (int[][]) CATEGORIES[catIndex][1];
		for (int[] range : ranges) {
			for (int cp = range[0]; cp <= range[1]; cp++) {
				String emoji = new String(Character.toChars(cp));
				if (paint.hasGlyph(emoji)) {
					all.add(emoji);
				}
			}
		}
		return all;
	}
}
