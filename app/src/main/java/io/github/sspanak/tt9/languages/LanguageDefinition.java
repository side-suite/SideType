package io.github.sspanak.tt9.languages;

import android.content.res.AssetManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.sspanak.tt9.BuildConfig;
import io.github.sspanak.tt9.util.AssetFile;
import io.github.sspanak.tt9.util.Logger;

public class LanguageDefinition {
	private static final String LOG_TAG = LanguageDefinition.class.getSimpleName();

	private static final String LANGUAGES_DIR = "languages";
	private static final String DEFINITIONS_PATH = LANGUAGES_DIR + "/definitions.yml";
	private static final String YAML_SEPARATOR = "---";

	/** The variant a flat `layout:` maps to (back-compat: layouts.compact-qwerty.default). See SID-122. */
	public static final String DEFAULT_VARIANT = "default";

	public String abcString = "";
	public String currency = "";
	public String dictionaryFile = "";
	public boolean filterBySounds = false;
	public boolean hasABC = true;
	public boolean hasSpaceBetweenWords = true;
	public boolean hasUpperCase = true;
	public String iconABC = "";
	public String iconT9 = "";
	public boolean isTranscribed = false;
	// The active binding for the default combo (compact-qwerty, default), kept for back-compat: both
	// NaturalLanguage.setLayout and LanguageCollection's "== 16 keys" filter read it. Populated either
	// from a flat `layout:` or, for a nested-only definition, from layouts.compact-qwerty.<default>.
	public final ArrayList<ArrayList<String>> layout = new ArrayList<>();
	// SID-122: geometry -> variant -> binding. A binding is either a positional list (base variant) or a
	// sparse index->chars delta overlaid onto its geometry's base variant (D6/D7/D7a).
	private final LinkedHashMap<String, LinkedHashMap<String, VariantBinding>> layouts = new LinkedHashMap<>();
	// The language's letters, most frequent first. See Language.sortKeyCharsByLetterFrequency.
	public String letterFrequency = "";
	public String locale = "";
	public String name = "";
	public String ngramFile = "";
	@NonNull public final HashMap<Integer, String> numerals = new HashMap<>();
	// Characters dropped when generating a word's key sequence, but kept in the word itself (e.g. the
	// apostrophe, so "that's" is reachable by typing t-h-a-t-s). Declared once per language and consumed
	// by both encoders — the build-time Groovy one and NaturalLanguage.getDigitSequenceForWord. See SID-6.
	public String transparentChars = "";

	private boolean inLayout = false;
	private boolean inLayouts = false;
	@Nullable private String currentGeometry = null;
	@Nullable private String currentVariant = null;


	/**
	 * A single (geometry, variant) binding. Either a positional list (one entry per key index, a base
	 * variant) or a sparse delta ({@code index -> chars}) overlaid onto the geometry's base variant.
	 */
	private static class VariantBinding {
		boolean sparse = false;
		final ArrayList<ArrayList<String>> positional = new ArrayList<>();
		final HashMap<Integer, ArrayList<String>> delta = new HashMap<>();
	}


	/**
	 * Converts YAML definitions to a LanguageDefinition objects. All properties in the YAML are
	 * considered optional, so the LanguageDefinition defaults will be used when some property is omitted.
	 * Had to write all this, because the only usable library, SnakeYAML, works fine on Android 10+,
	 * but causes crashes on older devices.
	 */
	private LanguageDefinition() {}


	/**
	 * Returns a list of all language definitions contained in the asset at DEFINITIONS_PATH,
	 * or an empty list on error.
	 */
	public static ArrayList<LanguageDefinition> getAll(AssetManager assets) {
		String[] definitionLines = readDefinitions(assets);
		if (definitionLines.length == 0) {
			return new ArrayList<>();
		}

		ArrayList<LanguageDefinition> definitions = parseDefinitions(definitionLines);
		Logger.d("tt9.LanguageCollection", "Found " + definitions.size() + " languages");
		return definitions;
	}


	/**
	 * Parses the merged definitions text (split into lines) into LanguageDefinition objects. Split out
	 * from {@link #getAll} so it is unit-testable without an AssetManager. Definitions are separated by
	 * a "---" line.
	 */
	static ArrayList<LanguageDefinition> parseDefinitions(String[] definitionLines) {
		ArrayList<LanguageDefinition> definitions = new ArrayList<>();

		LanguageDefinition definition = new LanguageDefinition();
		for (String line : definitionLines) {
			if (YAML_SEPARATOR.equals(line)) {
				definition.finalizeLayouts();
				definitions.add(definition);
				definition = new LanguageDefinition();
			} else if (!definition.setLayoutsEntry(line) && !definition.setLayoutEntry(line)) {
				definition.setProperty(line);
			}
		}

		definition.finalizeLayouts();
		definitions.add(definition);

		return definitions;
	}


	/**
	 * Reads the language definitions from DEFINITIONS_PATH and returns them as an array of strings.
	 */
	private static String[] readDefinitions(AssetManager assets) {
		try {
			BufferedReader reader = new AssetFile(assets, DEFINITIONS_PATH).getReader();
			StringBuilder contents = new StringBuilder();
			char[] buffer = new char[10000];
			int read;

			while ((read = reader.read(buffer)) != -1) {
				contents.append(buffer, 0, read);
			}

			return contents.toString().split("\n");
		} catch (IOException e) {
			Logger.e(LOG_TAG, "Failed reading language definitions from: '" + DEFINITIONS_PATH + "'. " + e.getMessage());
			return new String[0];
		}
	}


	/**
	 * Normalizes a YAML boolean to a Java boolean.
	 */
	private boolean parseYamlBoolean(@Nullable String value) {
		if (value == null) {
			return false;
		}

		return switch (value.toLowerCase()) {
			case "true", "on", "yes", "y" -> true;
			default -> false;
		};
	}


	/**
	 * Sets a property based on the key-value pair in a YAML line. If the key does not match any
	 * property, the line is ignored.
	 */
	private void setProperty(@NonNull String line) {
		int colonIndex = line.indexOf(':');
		if (colonIndex == -1) {
			return;
		}

		String key = line.substring(0, colonIndex).trim();
		String value = (colonIndex + 1 < line.length()) ? line.substring(colonIndex + 1).trim() : "";

		switch (key) {
			case "abcString":
				abcString = value;
				return;
			case "currency":
				currency = value;
				return;
			case "dictionaryFile":
				dictionaryFile = value.replaceFirst("\\.\\w+$", "." + BuildConfig.DICTIONARY_EXTENSION);
				return;
			case "filterBySounds":
				filterBySounds = parseYamlBoolean(value);
				return;
			case "hasABC":
				hasABC = parseYamlBoolean(value);
				return;
			case "hasSpaceBetweenWords":
				hasSpaceBetweenWords = parseYamlBoolean(value);
				return;
			case "hasUpperCase":
				hasUpperCase = parseYamlBoolean(value);
				return;
			case "iconABC":
				iconABC = value;
				return;
			case "iconT9":
				iconT9 = value;
				return;
			case "sounds":
				isTranscribed = true;
				return;
			case "letterFrequency":
				letterFrequency = value;
				return;
			case "locale":
				locale = value;
				return;
			case "name":
				name = value;
				return;
			case "numerals":
				setNumerals(value);
				return;
			case "ngramFile":
				ngramFile = value;
				return;
			case "transparentChars":
				transparentChars = value;
				return;
		}
	}


	/**
	 * Builds the (flat, legacy) key layout line by line. Returns true when a layout entry is successfully set.
	 */
	private boolean setLayoutEntry(@NonNull String line) {
		if (!inLayout) {
			return inLayout = "layout:".equals(line);
		}

		ArrayList<String> layoutEntry = parseList(line);
		if (layoutEntry == null) {
			inLayout = false;
		} else {
			layout.add(layoutEntry);
			return true;
		}

		return false;
	}


	/**
	 * SID-122: parses the nested `layouts:` block (D6). Indentation drives nesting: geometry at 2 spaces,
	 * variant at 4, entries at 6+. An entry is either positional ("- [q, w]") for a base variant, or
	 * sparse ("4: [t, z]") for a delta variant. Returns true while the line belongs to this block.
	 */
	private boolean setLayoutsEntry(@NonNull String line) {
		String trimmed = line.trim();

		if (!inLayouts) {
			if ("layouts:".equals(trimmed)) {
				inLayouts = true;
				currentGeometry = null;
				currentVariant = null;
				return true;
			}
			return false;
		}

		if (trimmed.isEmpty()) {
			return true; // stay inside the block, ignore blank lines
		}

		int indent = leadingSpaces(line);
		if (indent == 0) {
			inLayouts = false; // a dedent to the top level ends the block; let the caller re-handle the line
			return false;
		}

		if (indent <= 2) {
			currentGeometry = stripColon(trimmed);
			currentVariant = null;
			layouts.computeIfAbsent(currentGeometry, k -> new LinkedHashMap<>());
			return true;
		}

		if (currentGeometry == null) {
			return true; // malformed — a variant/entry before any geometry
		}

		if (indent <= 4) {
			currentVariant = stripColon(trimmed);
			layouts.get(currentGeometry).computeIfAbsent(currentVariant, k -> new VariantBinding());
			return true;
		}

		// Entry line (indent >= 6).
		if (currentVariant == null) {
			return true; // malformed — an entry before any variant
		}
		VariantBinding binding = layouts.get(currentGeometry).get(currentVariant);
		if (trimmed.startsWith("-")) {
			ArrayList<String> chars = parseList(trimmed);
			if (chars != null) {
				binding.positional.add(chars);
			}
		} else {
			int colon = trimmed.indexOf(':');
			if (colon > 0) {
				try {
					int index = Integer.parseInt(trimmed.substring(0, colon).trim());
					ArrayList<String> chars = parseList(trimmed.substring(colon + 1));
					if (chars != null) {
						binding.sparse = true;
						binding.delta.put(index, chars);
					}
				} catch (NumberFormatException e) {
					Logger.e(LOG_TAG, "Invalid sparse layout index in '" + name + "': " + trimmed);
				}
			}
		}
		return true;
	}


	/**
	 * Reconciles the flat and nested layout representations once a definition is fully parsed:
	 * a flat `layout:` becomes the (compact-qwerty, default) binding; conversely, a nested-only
	 * definition backfills the legacy `layout` field from its (compact-qwerty, default variant) so the
	 * "== 16 keys" filter and NaturalLanguage still work.
	 */
	private void finalizeLayouts() {
		if (!layout.isEmpty()) {
			LinkedHashMap<String, VariantBinding> compact = layouts.computeIfAbsent(GeometryRegistry.COMPACT_QWERTY, k -> new LinkedHashMap<>());
			if (!compact.containsKey(DEFAULT_VARIANT)) {
				VariantBinding binding = new VariantBinding();
				for (ArrayList<String> row : layout) {
					binding.positional.add(new ArrayList<>(row));
				}
				compact.put(DEFAULT_VARIANT, binding);
			}
		} else if (layouts.containsKey(GeometryRegistry.COMPACT_QWERTY)) {
			layout.addAll(getLayout(GeometryRegistry.COMPACT_QWERTY, null));
		}
	}


	private void setNumerals(@NonNull String yamlList) {
		ArrayList<String> numberList = parseList(yamlList);
		// Accept either the classic 10-key numpad list or a wider list (e.g. the 16-key Compact QWERTY,
		// where each entry is the character printed above the key and typed on hold: numbers + ! . ? @).
		if (numberList == null || numberList.size() < 10) {
			return;
		}

		for (int i = 0; i < numberList.size(); i++) {
			numerals.put(i, numberList.get(i));
		}
	}



	/**
	 * Validates a YAML line as an array and returns the character list to be assigned to a given key (a layout entry).
	 * If the YAML line is invalid, NULL will be returned.
	 */
	@Nullable
	private ArrayList<String> parseList(@NonNull String yamlLine) {
		int start = yamlLine.indexOf('[');
		int end = yamlLine.indexOf(']');
		if (start == -1 || end == -1 || start >= end) {
			return null;
		}

		String entryTxt = yamlLine.substring(start + 1, end).replace(" ", "");

		ArrayList<String> entry = new ArrayList<>();
		int last = 0, len = entryTxt.length();

		for (int i = 0; i < len; i++) {
			if (entryTxt.charAt(i) == ',') {
				entry.add(entryTxt.substring(last, i));
				last = i + 1;
			}
		}

		if (last < len) {
			entry.add(entryTxt.substring(last));
		}

		return entry;
	}


	/**
	 * The resolved positional binding for a (geometry, variant): the character list per key index. A
	 * sparse delta variant is overlaid onto its geometry's base (first positional) variant. Falls back
	 * to the default variant, then to the legacy flat {@link #layout}, so callers always get something
	 * usable. A null {@code variant} selects the geometry's default variant. See SID-122.
	 */
	@NonNull
	public ArrayList<ArrayList<String>> getLayout(@NonNull String geometry, @Nullable String variant) {
		LinkedHashMap<String, VariantBinding> geo = layouts.get(geometry);
		if (geo == null || geo.isEmpty()) {
			return deepCopy(layout);
		}

		VariantBinding binding = variant != null ? geo.get(variant) : null;
		if (binding == null) {
			binding = geo.get(getDefaultVariant(geometry));
		}
		if (binding == null) {
			return deepCopy(layout);
		}

		if (!binding.sparse) {
			return deepCopy(binding.positional);
		}

		ArrayList<ArrayList<String>> resolved = deepCopy(getBasePositional(geo));
		for (Map.Entry<Integer, ArrayList<String>> override : binding.delta.entrySet()) {
			int index = override.getKey();
			while (resolved.size() <= index) {
				resolved.add(new ArrayList<>());
			}
			resolved.set(index, new ArrayList<>(override.getValue()));
		}
		return resolved;
	}


	/** The default (first-declared) variant name for a geometry, or {@link #DEFAULT_VARIANT} if none. */
	@NonNull
	public String getDefaultVariant(@NonNull String geometry) {
		LinkedHashMap<String, VariantBinding> geo = layouts.get(geometry);
		if (geo == null || geo.isEmpty()) {
			return DEFAULT_VARIANT;
		}
		return geo.keySet().iterator().next();
	}


	/** The geometries this language defines a binding for, in declaration order. */
	@NonNull
	public ArrayList<String> getGeometries() {
		return new ArrayList<>(layouts.keySet());
	}


	/** The variant names declared for a geometry, in declaration order (empty if the geometry is absent). */
	@NonNull
	public ArrayList<String> getVariants(@NonNull String geometry) {
		LinkedHashMap<String, VariantBinding> geo = layouts.get(geometry);
		return geo == null ? new ArrayList<>() : new ArrayList<>(geo.keySet());
	}


	/** True if this language defines any binding for the given geometry. */
	public boolean hasGeometry(@NonNull String geometry) {
		LinkedHashMap<String, VariantBinding> geo = layouts.get(geometry);
		return geo != null && !geo.isEmpty();
	}


	@NonNull
	private static ArrayList<ArrayList<String>> getBasePositional(@NonNull LinkedHashMap<String, VariantBinding> geometry) {
		for (VariantBinding binding : geometry.values()) {
			if (!binding.sparse) {
				return binding.positional;
			}
		}
		return new ArrayList<>();
	}


	@NonNull
	private static ArrayList<ArrayList<String>> deepCopy(@NonNull ArrayList<ArrayList<String>> source) {
		ArrayList<ArrayList<String>> copy = new ArrayList<>(source.size());
		for (ArrayList<String> row : source) {
			copy.add(new ArrayList<>(row));
		}
		return copy;
	}


	private static int leadingSpaces(@NonNull String line) {
		int count = 0;
		while (count < line.length() && line.charAt(count) == ' ') {
			count++;
		}
		return count;
	}


	@NonNull
	private static String stripColon(@NonNull String header) {
		int colon = header.indexOf(':');
		return colon == -1 ? header : header.substring(0, colon).trim();
	}


	public String getDictionaryFile() {
		return LANGUAGES_DIR + "/dictionaries/" + dictionaryFile;
	}


	public String getNgramsFile() {
		return LANGUAGES_DIR + "/ngrams/" + ngramFile;
	}
}
