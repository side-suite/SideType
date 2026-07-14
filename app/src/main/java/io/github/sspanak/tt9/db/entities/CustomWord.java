package io.github.sspanak.tt9.db.entities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.languages.LanguageCollection;
import io.github.sspanak.tt9.languages.NaturalLanguage;
import io.github.sspanak.tt9.languages.exceptions.InvalidLanguageCharactersException;
import io.github.sspanak.tt9.util.TextTools;

public class CustomWord {
	@NonNull public final NaturalLanguage language;
	@NonNull public final String word;
	@NonNull public final String sequence;

	public CustomWord(@NonNull String word, @NonNull String sequence, int langId) throws IllegalArgumentException {
		NaturalLanguage lang = LanguageCollection.getLanguage(langId);
		if (lang == null || word.isEmpty() || sequence.isEmpty()) {
			throw new IllegalArgumentException("Cannot create CustomWord out of language: " + lang + ", word: '" + word + "', sequence: '" + sequence + "'");
		}

		// Transparent characters (e.g. the apostrophe in a contraction) are allowed inside custom words;
		// they are kept in the word but skipped in the sequence, exactly like dictionary words. See SID-6.
		if (TextTools.containsPunctuation(lang.stripTransparentChars(word))) {
			throw new IllegalArgumentException("Custom word: '" + word + "' contains punctuation.");
		}

		this.language = lang;
		this.sequence = sequence;
		this.word = word;
	}

	public CustomWord(@NonNull String word, @Nullable NaturalLanguage language) throws InvalidLanguageCharactersException, IllegalArgumentException {
		if (word.isEmpty() || language == null) {
			throw new IllegalArgumentException("Word and language must be provided.");
		}

		this.word = word;
		this.language = language;
		this.sequence = language.getDigitSequenceForWord(word);

		// A word made up entirely of transparent characters (e.g. a lone "'") encodes to nothing; reject
		// it, mirroring the empty-sequence guard in the other constructor.
		if (this.sequence.isEmpty()) {
			throw new IllegalArgumentException("Custom word: '" + word + "' produced an empty key sequence.");
		}

		// See the note above: transparent characters (e.g. the apostrophe) are permitted in custom words.
		if (TextTools.containsPunctuation(language.stripTransparentChars(word))) {
			throw new IllegalArgumentException("Custom word: '" + word + "' contains punctuation.");
		}
	}

	@NonNull
	public static ArrayList<CustomWord> guessLanguage(@NonNull String word, @NonNull ArrayList<Integer> languageIds) {
		ArrayList<CustomWord> results = new ArrayList<>();

		for (Language lang : LanguageCollection.getAll(languageIds)) {
			try {
				results.add(new CustomWord(word, (NaturalLanguage) lang));
			} catch (InvalidLanguageCharactersException | IllegalArgumentException ignored) {}
		}

		return results;
	}


	@NonNull
	@Override
	public String toString() {
		return language.getId() + ",'" + word + "'," + sequence;
	}
}
