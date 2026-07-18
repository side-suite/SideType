package io.github.sspanak.tt9.languages;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.sspanak.tt9.languages.exceptions.InvalidLanguageCharactersException;
import io.github.sspanak.tt9.preferences.screens.keychars.PreferenceChars2to9;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.util.Text;
import io.github.sspanak.tt9.util.chars.Characters;


public class NaturalLanguage extends TranscribedLanguage {
	protected final ArrayList<ArrayList<String>> layout = new ArrayList<>();
	protected ArrayList<ArrayList<String>> factoryLayout = new ArrayList<>();
	private final HashMap<Character, String> characterKeyMap = new HashMap<>();
	@NonNull private HashMap<Integer, String> numerals = new HashMap<>();
	// Characters kept in the word but skipped when generating its key sequence (e.g. the apostrophe).
	// The single source of truth is the language definition's "transparentChars"; the build-time encoder
	// reads the same declaration, so the two cannot structurally diverge. See SID-6.
	@NonNull private String transparentChars = "";
	// Letters most frequent first. See Language.sortKeyCharsByLetterFrequency.
	@NonNull private String letterFrequency = "";


	public static NaturalLanguage fromDefinition(LanguageDefinition definition) throws Exception {
		if (definition.dictionaryFile.isEmpty()) {
			throw new Exception("Invalid definition. Dictionary file must be set.");
		}

		NaturalLanguage lang = new NaturalLanguage();
		lang.abcString = definition.abcString.isEmpty() ? null : definition.abcString;
		lang.currency = definition.currency;
		lang.dictionaryFile = definition.getDictionaryFile();
		lang.hasABC = definition.hasABC;
		lang.hasSpaceBetweenWords = definition.hasSpaceBetweenWords;
		lang.hasUpperCase = definition.hasUpperCase;
		lang.hasTranscriptionsEmbedded = definition.filterBySounds;
		lang.iconABC = definition.iconABC;
		lang.iconT9 = definition.iconT9;
		lang.isTranscribed = definition.isTranscribed;
		lang.letterFrequency = definition.letterFrequency;
		lang.name = definition.name.isEmpty() ? lang.name : definition.name;
		lang.ngramsFile = definition.getNgramsFile();
		lang.numerals = definition.numerals;
		lang.transparentChars = definition.transparentChars;
		lang.setLocale(definition);
		lang.setLayout(definition);

		return lang;
	}


	private void setLocale(LanguageDefinition definition) throws Exception {
		if (definition.locale.isEmpty()) {
			throw new Exception("Invalid definition. Locale cannot be empty.");
		}

		locale = definition.locale.equals("en") ? Locale.ENGLISH : Locale.forLanguageTag(definition.locale);
	}


	private void setLayout(LanguageDefinition definition) {
		for (int key = 0; key <= KeySequence.MAX_KEY && key < definition.layout.size(); key++) {
				layout.add(
					key,
					key > 1 ? definition.layout.get(key) : generateSpecialChars(definition.layout.get(key))
				);
		}

		factoryLayout = new ArrayList<>(layout);

		generateCharacterKeyMap();
	}


	private ArrayList<String> generateSpecialChars(ArrayList<String> definitionChars) {
		final String SPECIAL_CHARS_PLACEHOLDER = "SPECIAL";
		final String PUNCTUATION_PLACEHOLDER = "PUNCTUATION";

		final Map<String, List<String>> specialChars = new HashMap<>();
		specialChars.put(SPECIAL_CHARS_PLACEHOLDER, new ArrayList<>(Characters.Special));
		specialChars.put(PUNCTUATION_PLACEHOLDER, Characters.PunctuationEnglish);
		specialChars.put(PUNCTUATION_PLACEHOLDER + "_AR", Characters.PunctuationArabic);
		specialChars.put(PUNCTUATION_PLACEHOLDER + "_BP", Characters.PunctuationChineseBopomofo);
		specialChars.put(PUNCTUATION_PLACEHOLDER + "_ZH", Characters.PunctuationChinese);
		specialChars.put(PUNCTUATION_PLACEHOLDER + "_FA", Characters.PunctuationFarsi);
		specialChars.put(PUNCTUATION_PLACEHOLDER + "_FR", Characters.PunctuationFrench);
		specialChars.put(PUNCTUATION_PLACEHOLDER + "_DE", Characters.PunctuationGerman);
		specialChars.put(PUNCTUATION_PLACEHOLDER + "_GR", Characters.PunctuationGreek);
		specialChars.put(PUNCTUATION_PLACEHOLDER + "_IE", Characters.PunctuationIrish);
		specialChars.put(PUNCTUATION_PLACEHOLDER + "_IN", Characters.PunctuationIndic);
		specialChars.put(PUNCTUATION_PLACEHOLDER + "_KR", Characters.PunctuationKorean);

		ArrayList<String> keyChars = new ArrayList<>();
		for (String defChar : definitionChars) {
			List<String> keySpecialChars = specialChars.getOrDefault(defChar, null);
			if (keySpecialChars != null) {
				keyChars.addAll(keySpecialChars);
			} else {
				keyChars.add(defChar);
			}
		}

		return keyChars;
	}


	/**
	 * generateId
	 * Uses the letters of the Locale to generate an ID for the language.
	 * Each letter is converted to uppercase and used as a 5-bit integer. Then the 5-bits
	 * are packed to form a 10-bit or a 20-bit integer, depending on the Locale length.
	 *
	 * Example (2-letter Locale)
	 * 	"en"
	 * 	-> "E" | "N"
	 * 	-> 5 | 448 (shift the 2nd number by 5 bits, so its bits would not overlap with the 1st one)
	 *	-> 543
	 *
	 * Example (4-letter Locale)
	 * 	"bg-BG"
	 * 	-> "B" | "G" | "B" | "G"
	 * 	-> 2 | 224 | 2048 | 229376 (shift each 5-bit number, not overlap with the previous ones)
	 *	-> 231650
	 *
	 * Minimum ID: "aa" -> 33
	 * Maximum ID: "zz-ZZ" -> 879450
	 */
	@Override
	public int getId() {
		if (id == 0) {
			String idString = new LocaleCompat(locale).toString();
			for (int i = 0; i < idString.length(); i++) {
				id |= (idString.codePointAt(i) & 31) << (i * 5);
			}
		}

		return id;
	}

	@Override
	protected String getSortingId() {
		if (isTranscribed) {
			return super.getSortingId();
		}

		if ("IN".equals(getLocale().getCountry()) && "en".equals(getLocale().getLanguage())) {
			return "hi";
		}

		return switch (getLocale().getLanguage()) {
			case "fi" -> "su";
			case "sw" -> "ki";
			case "zgh" -> "tam";
			default -> getLocale().toString();
		};
	}


	@NonNull
	@Override
	public String getAbcString() {
		if (abcString == null) {
			ArrayList<String> lettersList = getKeyCharacters(2);

			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < lettersList.size() && i < 3; i++) {
				sb.append(lettersList.get(i));
			}

			abcString = sb.toString();
		}

		return abcString;
	}


	@NonNull
	@Override
	public String getCode() {
		if (code == null) {
			code = new LocaleCompat(locale).getUniqueLanguageCode();
		}

		return code;
	}


	@NonNull
	@Override
	public String getName() {
		if (name == null) {
			name = new Text(this, locale.getDisplayLanguage(locale)).capitalize();
		}

		return name;
	}


	private void generateCharacterKeyMap() {
		characterKeyMap.clear();
		for (int key = 0; key < layout.size(); key++) {
			// Only the classic numpad keys 0-9 have a printed numeral that also types on the key.
			// Higher keys (e.g. Compact QWERTY keys 10-15) have no numeral; mapping getKeyNumeral()
			// for them would produce a multi-char string whose charAt(0) collides with key 1.
			if (key <= 9) {
				characterKeyMap.put(getKeyNumeral(key).charAt(0), KeySequence.keyToTokenString(key));
			}
			for (String keyChar : getKeyCharacters(key)) {
				characterKeyMap.put(keyChar.charAt(0), KeySequence.keyToTokenString(key));
			}
		}
	}


	@NonNull
	@Override
	public ArrayList<String> getKeyCharacters(int key) {
		if (key < 0 || key >= layout.size()) {
			return new ArrayList<>();
		}

		return new ArrayList<>(layout.get(key));
	}


	@NonNull
	@Override
	public ArrayList<String> getFactoryKeyCharacters(int key) {
		if (key < 0 || key >= factoryLayout.size()) {
			return new ArrayList<>();
		}

		return new ArrayList<>(factoryLayout.get(key));
	}


	@NonNull
	public String getKeyNumeral(int key) {
		String digit = numerals.getOrDefault(key, null);
		return  digit != null ? digit : super.getKeyNumeral(key);
	}


	@NonNull
	@Override
	public String getTransparentChars() {
		return transparentChars;
	}


	@NonNull
	@Override
	public String getLetterFrequency() {
		return letterFrequency;
	}


	@NonNull
	public String getDigitSequenceForWord(String word) throws InvalidLanguageCharactersException {
		StringBuilder sequence = new StringBuilder();
		String lowerCaseWord = word.toLowerCase(locale);

		for (int i = 0; i < lowerCaseWord.length(); i++) {
			char letter = lowerCaseWord.charAt(i);
			// Transparent characters (e.g. the apostrophe in a contraction) stay in the word but produce
			// no key token, so "that's" encodes to the same sequence as "thats". See SID-6.
			if (transparentChars.indexOf(letter) >= 0) {
				continue;
			}
			if (!characterKeyMap.containsKey(letter)) {
				throw new InvalidLanguageCharactersException(this, "Failed generating digit sequence for word: '" + word);
			}

			sequence.append(characterKeyMap.get(letter));
		}

		return sequence.toString();
	}


	/**
	 * Removes this language's transparent characters (e.g. the apostrophe) from a word. Used to test
	 * whether a word carries "real" punctuation once the transparent characters — which are allowed
	 * inside words such as contractions — are set aside.
	 */
	@NonNull
	public String stripTransparentChars(@NonNull String word) {
		if (transparentChars.isEmpty()) {
			return word;
		}

		StringBuilder stripped = new StringBuilder(word.length());
		for (int i = 0; i < word.length(); i++) {
			char c = word.charAt(i);
			if (transparentChars.indexOf(c) < 0) {
				stripped.append(c);
			}
		}
		return stripped.toString();
	}



	public boolean isValidWord(String word) {
		if (
			word == null
			|| word.isEmpty()
			|| (word.length() == 1 && Character.isDigit(word.charAt(0)))
		) {
			return true;
		}

		if (isTranscribed) {
			return super.isValidWord(word);
		}

		String lowerCaseWord = word.toLowerCase(locale);

		for (int i = 0; i < lowerCaseWord.length(); i++) {
			if (!characterKeyMap.containsKey(lowerCaseWord.charAt(i))) {
				return false;
			}
		}

		return true;
	}


	public void updateKeyCharacters(int key, @NonNull String newCharacters, boolean updateReverseMapping) {
		if (key < 0 || key >= layout.size()) {
			return;
		}

		final ArrayList<String> newKeyCharacters = new ArrayList<>(factoryLayout.get(key));
		for (int i = 0; i < newCharacters.length(); i++) {
			newKeyCharacters.add(String.valueOf(newCharacters.charAt(i)));
		}

		layout.set(key, newKeyCharacters);

		if (updateReverseMapping) {
			generateCharacterKeyMap();
		}
	}


	public void updateKeyCharacters(@NonNull SettingsStore settings) {
		for (int key = 2; key <= KeySequence.MAX_KEY && key < layout.size(); key++) {
			updateKeyCharacters(
				key,
				settings.getCharsExtra(this, PreferenceChars2to9.NAME_PREFIX + key),
				false
			);
		}

		generateCharacterKeyMap();
	}
}
