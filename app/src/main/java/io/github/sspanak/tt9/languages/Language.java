package io.github.sspanak.tt9.languages;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import io.github.sspanak.tt9.languages.exceptions.InvalidLanguageCharactersException;
import io.github.sspanak.tt9.util.chars.Characters;

abstract public class Language {
	protected int id;
	protected String abcString;
	protected String code;
	protected String currency;
	protected String dictionaryFile;
	protected boolean hasABC = true;
	private Boolean hasLettersOnAllKeys = null;
	protected boolean hasSpaceBetweenWords = true;
	protected boolean hasUpperCase = true;
	protected boolean hasTranscriptionsEmbedded = false;
	protected String iconABC = "";
	protected String iconT9 = "";
	protected boolean isTranscribed = false;
	protected Locale locale = Locale.ROOT;
	protected String name;
	protected String ngramsFile = "";


	public int getId() {
		return id;
	}

	@NonNull public String getAbcString() {
		return abcString;
	}

	@NonNull public String getCode() {
		return code;
	}

	@NonNull public String getCurrency() {
		return currency;
	}

	@NonNull final public String getDictionaryFile() {
		return dictionaryFile;
	}

	@NonNull final public Locale getLocale() {
		return locale;
	}

	/**
	 * Returns the characters that the key would type in ABC or Predictive mode. For example,
	 * the key 2 in English would return A-B-C.
	 */
	@NonNull abstract public ArrayList<String> getKeyCharacters(int key);

	@NonNull public String getKeyNumeral(int key) {
		return String.valueOf(key);
	}

	@NonNull public String getName() {
		return name;
	}

	/**
	 * Characters that are kept in a word but skipped when generating its key sequence (e.g. the
	 * apostrophe in contractions). Empty for languages that do not use transparency. See SID-6.
	 */
	@NonNull public String getTransparentChars() {
		return "";
	}

	/**
	 * The language's letters, most frequent first. Purely a ranking hint for suggestions — it never
	 * changes which key types a letter, nor the order ABC/multi-tap cycles through a key. Empty for
	 * languages that do not declare it, which leaves every order exactly as the layout defines it.
	 */
	@NonNull public String getLetterFrequency() {
		return "";
	}

	/**
	 * The characters a key types as the language definition declares them, before any characters the
	 * user appended via PreferenceChars2to9. Languages without a factory/custom distinction have
	 * nothing to separate, so the live list is the factory list.
	 */
	@NonNull public ArrayList<String> getFactoryKeyCharacters(int key) {
		return getKeyCharacters(key);
	}

	/**
	 * Reorders a key's factory characters by getLetterFrequency(), most frequent first.
	 * <p>
	 * This exists because on a 2-letters-per-key layout the layout order is a statement about the
	 * physical keycap ("U/I" is printed in that order and ABC mode must cycle U then I), but it is a
	 * terrible ranking for predictions: a lone press of the U/I key never reaches the dictionary
	 * (see ReadOps.getWordPositions), so the layout list *is* the suggestion list, and the far more
	 * common "i" lost to "u" purely because of the silkscreen. Sorting only here keeps the keycap and
	 * the ranking as the separate concerns they are.
	 * <p>
	 * Only the factory prefix is sorted. Characters the user appended with PreferenceChars2to9 are
	 * appended *after* the factory letters by design (NaturalLanguage.updateKeyCharacters), and ranking
	 * them alongside would promote an extra "a" above the key's own letters.
	 * <p>
	 * The sort is stable and conservative: multi-character entries (combining marks) and letters
	 * missing from the frequency list keep their relative order and sink to the end of the prefix. An
	 * undeclared frequency list returns the input untouched.
	 */
	@NonNull final public ArrayList<String> sortKeyCharsByLetterFrequency(int key, @NonNull ArrayList<String> keyChars) {
		final String frequency = getLetterFrequency();
		final int factoryCount = Math.min(getFactoryKeyCharacters(key).size(), keyChars.size());
		if (frequency.isEmpty() || factoryCount < 2) {
			return keyChars;
		}

		// subList is a view onto the copy, so sorting it reorders the prefix in place
		ArrayList<String> sorted = new ArrayList<>(keyChars);
		Collections.sort(sorted.subList(0, factoryCount), (left, right) -> Integer.compare(rankLetter(frequency, left), rankLetter(frequency, right)));
		return sorted;
	}


	private int rankLetter(@NonNull String frequency, @NonNull String keyChar) {
		if (keyChar.length() != 1) {
			return Integer.MAX_VALUE;
		}

		int rank = frequency.indexOf(Character.toLowerCase(keyChar.charAt(0)));
		return rank == -1 ? Integer.MAX_VALUE : rank;
	}

	@NonNull public String getNgramsFile() {
		return ngramsFile;
	}

	final public boolean hasABC() {
		return hasABC;
	}

	final public boolean hasLettersOnAllKeys() {
		if (hasLettersOnAllKeys != null) {
			return hasLettersOnAllKeys;
		}

		boolean hasCharsOn0 = false;
		for (String ch : getKeyCharacters(0)) {
			if (Character.isAlphabetic(ch.charAt(0)) && !Characters.isOm(ch.charAt(0))) {
				hasCharsOn0 = true;
				break;
			}
		}

		boolean hasCharsOn1 = false;
		for (String ch : getKeyCharacters(1)) {
			if (Character.isAlphabetic(ch.charAt(0)) && !Characters.isOm(ch.charAt(0))) {
				hasCharsOn1 = true;
				break;
			}
		}

		return hasLettersOnAllKeys = hasCharsOn0 && hasCharsOn1;
	}

	final public boolean hasSpaceBetweenWords() {
		return hasSpaceBetweenWords;
	}

	final public boolean hasUpperCase() {
		return hasUpperCase;
	}

	final public boolean hasTranscriptionsEmbedded() {
		return hasTranscriptionsEmbedded;
	}

	final public String getIconABC() {
		return iconABC;
	}

	final public String getIconT9() {
		return iconT9;
	}

	final public boolean isTranscribed() {
		return isTranscribed;
	}

	@NonNull
	@Override
	final public String toString() {
		return getName();
	}


	/**
	 * Checks whether the given word contains characters outside the language alphabet.
	 */
	abstract public boolean isValidWord(String word);

	/**
	 * Converts a word to a sequence of digits based on the language's keyboard layout.
	 * For example: "food" -> "3663"
	 */
	@NonNull abstract public String getDigitSequenceForWord(String word) throws InvalidLanguageCharactersException;


	@Override
	public boolean equals(@Nullable Object obj) {
		return obj instanceof Language && ((Language) obj).getId() == getId();
	}


	/**
	 * For consistency with this.equals(), hash code must return the same value, not to break HashMap
	 * and similar data structures
	 */
	@Override
	public int hashCode() {
		return getId();
	}
}
