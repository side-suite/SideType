package io.github.sspanak.tt9.languages;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;

import io.github.sspanak.tt9.util.Logger;

/**
 * A "geometry" is the physical keypad's keycode -> key-index map, modelled as data (a shipped
 * <code>assets/geometries/*.yml</code> file) instead of the hardcoded switch that used to live in
 * {@link io.github.sspanak.tt9.ime.helpers.Key}. It is one of the orthogonal axes of the Keytile
 * layout platform (geometry / binding / variant / wordlist); see
 * <code>docs/adr/0005-keytile-layout-platform.md</code> (decision D2).
 *
 * <p>For P1 (SID-120) this is an <em>inert prefactor</em>: only the Compact QWERTY geometry is
 * shipped and active, so routing through it is byte-identical to the old hardcoded behaviour. The
 * numpad geometry and keycode-vocabulary auto-detection land later (P3).</p>
 *
 * <p>The YAML is parsed by hand (like {@link LanguageDefinition}) rather than with SnakeYAML, which
 * crashes on older Android versions.</p>
 */
public class Geometry {
	private static final String LOG_TAG = Geometry.class.getSimpleName();

	public static final String DETECT_LETTERS = "letters";
	public static final String DETECT_DIGITS = "digits";

	/**
	 * Names appearing in geometry <code>keymap:</code> blocks, resolved to their {@link KeyEvent}
	 * keycode. Built from inlined <code>KEYCODE_*</code> constants on purpose: it keeps geometry
	 * parsing free of any android <em>method</em> call (e.g. {@code KeyEvent.keyCodeFromString}), so
	 * it works identically on-device and in a plain-JVM unit test. Covers the Compact QWERTY letters
	 * plus the numpad vocabulary the later numpad geometry (P3) will reference.
	 */
	private static final HashMap<String, Integer> KEYCODE_NAMES = buildKeyCodeNames();

	private String id = "";
	private int keyCount = 0;
	private String detect = "";
	private final HashMap<Integer, Integer> codeToIndex = new HashMap<>();
	private final HashMap<Integer, Integer> indexToCode = new HashMap<>();
	private final HashMap<Integer, String> labels = new HashMap<>();

	private enum Section { NONE, KEYMAP, LABELS }


	private Geometry() {}


	/**
	 * Builds a geometry directly from code (no YAML), for the registry's correct-by-construction
	 * fallback. {@code keymap} is a list of {@code {keyCode, index}} pairs.
	 */
	@NonNull
	static Geometry builtIn(@NonNull String id, int keyCount, @NonNull String detect, @NonNull int[][] keymap) {
		Geometry geometry = new Geometry();
		geometry.id = id;
		geometry.keyCount = keyCount;
		geometry.detect = detect;
		for (int[] pair : keymap) {
			geometry.codeToIndex.put(pair[0], pair[1]);
			geometry.indexToCode.put(pair[1], pair[0]);
		}
		return geometry;
	}


	@NonNull
	public String getId() {
		return id;
	}

	public int getKeyCount() {
		return keyCount;
	}

	/** Highest valid key index for this geometry (<code>keyCount - 1</code>). */
	public int getMaxKey() {
		return keyCount - 1;
	}

	public boolean detectsLetters() {
		return DETECT_LETTERS.equals(detect);
	}

	public boolean detectsDigits() {
		return DETECT_DIGITS.equals(detect);
	}

	/** The key index this geometry assigns to {@code keyCode}, or -1 if the keycode is not one of its keys. */
	public int codeToIndex(int keyCode) {
		Integer index = codeToIndex.get(keyCode);
		return index == null ? -1 : index;
	}

	/** The keycode this geometry maps {@code index} to, or -1 if it has no such key. */
	public int indexToCode(int index) {
		Integer code = indexToCode.get(index);
		return code == null ? -1 : code;
	}

	/** True if {@code keyCode} is one of this geometry's typing keys. */
	public boolean hasKeyCode(int keyCode) {
		return codeToIndex.containsKey(keyCode);
	}

	/** Keycap label for a key index (for the D9 reference screen), or empty if none. */
	@NonNull
	public String getLabel(int index) {
		String label = labels.get(index);
		return label == null ? "" : label;
	}


	/**
	 * Parses one geometry YAML file. Returns null on a fatal error (missing id or no keys), so the
	 * registry can skip it rather than register a broken geometry.
	 */
	@Nullable
	static Geometry fromReader(@NonNull BufferedReader reader) {
		Geometry geometry = new Geometry();
		Section section = Section.NONE;

		try {
			String rawLine;
			while ((rawLine = reader.readLine()) != null) {
				String line = rawLine.replace("\r", "");
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.charAt(0) == '#') {
					continue;
				}

				int colon = trimmed.indexOf(':');
				if (colon == -1) {
					continue;
				}
				String key = trimmed.substring(0, colon).trim();
				String value = trimmed.substring(colon + 1).trim();

				boolean indented = line.charAt(0) == ' ' || line.charAt(0) == '\t';
				if (!indented) {
					section = geometry.setTopLevel(key, value);
				} else if (section == Section.KEYMAP) {
					geometry.addKey(key, value);
				} else if (section == Section.LABELS) {
					geometry.addLabel(key, value);
				}
			}
		} catch (IOException e) {
			Logger.e(LOG_TAG, "Failed reading geometry. " + e.getMessage());
			return null;
		}

		if (geometry.id.isEmpty() || geometry.codeToIndex.isEmpty()) {
			Logger.e(LOG_TAG, "Ignoring geometry with no id or no keys: '" + geometry.id + "'");
			return null;
		}

		return geometry;
	}


	/** Handles an un-indented line. Returns the section a "keymap:"/"labels:" header opens, else NONE. */
	@NonNull
	private Section setTopLevel(@NonNull String key, @NonNull String value) {
		switch (key) {
			case "id":
				id = value;
				return Section.NONE;
			case "keyCount":
				keyCount = parseInt(value, 0);
				return Section.NONE;
			case "detect":
				detect = value;
				return Section.NONE;
			case "keymap":
				return Section.KEYMAP;
			case "labels":
				return Section.LABELS;
			default:
				return Section.NONE;
		}
	}


	private void addKey(@NonNull String keyCodeName, @NonNull String indexValue) {
		Integer keyCode = KEYCODE_NAMES.get(keyCodeName);
		if (keyCode == null) {
			Logger.e(LOG_TAG, "Unknown keycode name in geometry '" + id + "': " + keyCodeName);
			return;
		}

		int index = parseInt(indexValue, -1);
		if (index < 0) {
			Logger.e(LOG_TAG, "Invalid key index in geometry '" + id + "': " + indexValue);
			return;
		}

		codeToIndex.put(keyCode, index);
		indexToCode.put(index, keyCode);
	}


	private void addLabel(@NonNull String indexValue, @NonNull String label) {
		int index = parseInt(indexValue, -1);
		if (index >= 0) {
			labels.put(index, unquote(label));
		}
	}


	private static int parseInt(@NonNull String value, int fallback) {
		try {
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}


	@NonNull
	private static String unquote(@NonNull String value) {
		if (value.length() >= 2 && (value.charAt(0) == '"' || value.charAt(0) == '\'') && value.charAt(value.length() - 1) == value.charAt(0)) {
			return value.substring(1, value.length() - 1);
		}
		return value;
	}


	@NonNull
	private static HashMap<String, Integer> buildKeyCodeNames() {
		HashMap<String, Integer> names = new HashMap<>();
		// Compact QWERTY letter keys.
		names.put("KEYCODE_Q", KeyEvent.KEYCODE_Q);
		names.put("KEYCODE_E", KeyEvent.KEYCODE_E);
		names.put("KEYCODE_T", KeyEvent.KEYCODE_T);
		names.put("KEYCODE_U", KeyEvent.KEYCODE_U);
		names.put("KEYCODE_O", KeyEvent.KEYCODE_O);
		names.put("KEYCODE_A", KeyEvent.KEYCODE_A);
		names.put("KEYCODE_D", KeyEvent.KEYCODE_D);
		names.put("KEYCODE_G", KeyEvent.KEYCODE_G);
		names.put("KEYCODE_J", KeyEvent.KEYCODE_J);
		names.put("KEYCODE_L", KeyEvent.KEYCODE_L);
		names.put("KEYCODE_Z", KeyEvent.KEYCODE_Z);
		names.put("KEYCODE_C", KeyEvent.KEYCODE_C);
		names.put("KEYCODE_B", KeyEvent.KEYCODE_B);
		names.put("KEYCODE_M", KeyEvent.KEYCODE_M);
		// Numpad vocabulary — for the numpad geometry shipped in P3.
		names.put("KEYCODE_0", KeyEvent.KEYCODE_0);
		names.put("KEYCODE_1", KeyEvent.KEYCODE_1);
		names.put("KEYCODE_2", KeyEvent.KEYCODE_2);
		names.put("KEYCODE_3", KeyEvent.KEYCODE_3);
		names.put("KEYCODE_4", KeyEvent.KEYCODE_4);
		names.put("KEYCODE_5", KeyEvent.KEYCODE_5);
		names.put("KEYCODE_6", KeyEvent.KEYCODE_6);
		names.put("KEYCODE_7", KeyEvent.KEYCODE_7);
		names.put("KEYCODE_8", KeyEvent.KEYCODE_8);
		names.put("KEYCODE_9", KeyEvent.KEYCODE_9);
		names.put("KEYCODE_STAR", KeyEvent.KEYCODE_STAR);
		names.put("KEYCODE_POUND", KeyEvent.KEYCODE_POUND);
		names.put("KEYCODE_SPACE", KeyEvent.KEYCODE_SPACE);
		return names;
	}
}
