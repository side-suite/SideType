package io.github.sspanak.tt9.ime.modes;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import io.github.sspanak.tt9.hacks.InputType;
import io.github.sspanak.tt9.ime.helpers.TextField;
import io.github.sspanak.tt9.ime.modes.helpers.AbcWordLearner;
import io.github.sspanak.tt9.ime.modes.helpers.AutoSpace;
import io.github.sspanak.tt9.ime.modes.helpers.AutoTextCase;
import io.github.sspanak.tt9.ime.modes.helpers.Sequences;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.languages.LanguageCollection;
import io.github.sspanak.tt9.languages.LanguageKind;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.util.Text;
import io.github.sspanak.tt9.util.chars.Characters;

class ModeABC extends InputMode {
	private final ArrayList<ArrayList<String>> KEY_CHARACTERS = new ArrayList<>();

	private boolean shouldSelectNextLetter = false;

	// text analysis
	@NonNull private final AutoSpace autoSpace;
	@NonNull private final AutoTextCase autoTextCase;
	@NonNull private final AbcWordLearner wordLearner;
	@Nullable private final InputType inputType;
	private final int textFieldTextCase;

	@Override public int getId() { return MODE_ABC; }
	@Override @NonNull public String toAccessibilityString(@NonNull Context c) { return language.getName() + ", " + language.getAbcString(); }


	protected ModeABC(@NonNull SettingsStore settings, @NonNull Language lang, @Nullable InputType inputType, @Nullable TextField textField) {
		super(settings, inputType);
		autoSpace = new AutoSpace(settings);
		autoTextCase = new AutoTextCase(settings, new Sequences(), inputType);
		wordLearner = new AbcWordLearner(settings, inputType, textField);
		this.inputType = inputType;
		textFieldTextCase = inputType == null ? CASE_UNDEFINED : inputType.determineTextCase();

		setLanguage(lang);
		defaultTextCase();
	}


	@Override
	public boolean onBackspace() {
		if (!suggestions.isEmpty()) {
			reset();
		}

		return false;
	}


	@Override
	public boolean onNumber(int number, boolean hold, int repeat, @NonNull String[] s) {
		return onNumber(number, hold, repeat);
	}


	private boolean onNumber(int number, boolean hold, int repeat) {
		if (hold) {
			reset();
			autoAcceptTimeout = 0;
			digitSequence = String.valueOf(number);
			shouldSelectNextLetter = false;

			ArrayList<String> newSuggestions = new ArrayList<>(1);
			newSuggestions.add(language.getKeyNumeral(number));
			suggestions = newSuggestions;
		} else {
			// Sidephone: only the key's letters rotate. The printed numeral or symbol (! 1 2 . ? ' @ …)
			// is typed by holding the key, and punctuation lives in the SYM grid.
			final ArrayList<String> keyChars = new ArrayList<>(KEY_CHARACTERS.size() > number ? KEY_CHARACTERS.get(number) : settings.getOrderedKeyChars(language, number));

			if (repeat > 0 && !suggestions.isEmpty() && keyChars.size() > 1) {
				// a repeat press cycles the key's letters
				autoAcceptTimeout = settings.getAutoAcceptTimeoutAbc();
				shouldSelectNextLetter = true;
			} else {
				// A fresh press, or a repeat on a one-letter key (L, M), where there is nothing to cycle:
				// the pending letter is accepted and the key types again at once, so "ll" needs no wait.
				reset();
				autoAcceptTimeout = settings.getAutoAcceptTimeoutAbc();
				digitSequence = String.valueOf(number);
				shouldSelectNextLetter = false;
				suggestions = keyChars;
			}
		}

		return true;
	}


	/******** TEXT CASE ********/
	@Override
	protected String adjustSuggestionTextCase(String word, int newTextCase) {
		if (language.hasUpperCase()) {
			return newTextCase == CASE_LOWER ? word.toLowerCase(language.getLocale()) : word.toUpperCase(language.getLocale());
		} else {
			return word;
		}
	}


	@Override
	public void determineNextWordTextCase(@Nullable String beforeCursor, int nextDigit) {
		if (settings.getAutoTextCaseAbc()) {
			textCase = autoTextCase.determineNextLetterTextCase(language, textFieldTextCase, beforeCursor);
		}
	}


	@Override
	public boolean nextTextCase(@Nullable String currentWord, int displayTextCase) {
		if (suggestions.isEmpty()) {
			return super.nextTextCase(currentWord, displayTextCase);
		}

		for (int newTextCase : allowedTextCases) {
			if (newTextCase != textCase && newTextCase != InputMode.CASE_CAPITALIZE) {
				textCase = newTextCase;
				return true;
			}
		}

		return false;
	}


	@Override
	public void skipNextTextCaseDetection() {
		autoTextCase.skipNext();
	}


	/******** AUTO-SPACE ********/
	@Override
	public boolean shouldAddTrailingSpace(@NonNull String previousChars, @NonNull String nextChars, boolean isWordAcceptedManually, int nextKey) {
		return autoSpace.shouldAddTrailingSpace(inputType, this, previousChars, nextChars, isWordAcceptedManually, nextKey);
	}


	@Override
	public boolean shouldAddPrecedingSpace(@NonNull String previousChars) {
		return autoSpace.shouldAddBeforePunctuation(inputType, previousChars);
	}


	@Override
	public boolean shouldDeletePrecedingSpace(@NonNull String previousChars) {
		return autoSpace.shouldDeletePrecedingSpace(inputType, previousChars);
	}


	private void refreshSuggestions() {
		if (digitSequence.isEmpty()) {
			suggestions = new ArrayList<>();
		} else {
			onNumber(digitSequence.charAt(0) - '0', false, 0);
		}
	}


	@Override
	public boolean setLanguage(@Nullable Language newLanguage) {
		if (newLanguage != null && !newLanguage.hasABC()) {
			return false;
		}

		super.setLanguage(newLanguage);

		autoSpace.setLanguage(newLanguage);

		allowedTextCases.clear();
		allowedTextCases.add(CASE_LOWER);
		if (language.hasUpperCase()) {
			if (settings.getAutoTextCaseAbc() && inputType != null && !inputType.isSpecialized()) {
				allowedTextCases.add(CASE_CAPITALIZE);
			}
			allowedTextCases.add(CASE_UPPER);
		}

		KEY_CHARACTERS.clear();
		if (isEmailMode) {
			// Asian punctuation can not be used in email addresses, so we need to use the English locale.
			Language lang = LanguageKind.isCJK(language) ? LanguageCollection.getByLocale("en") : language;
			KEY_CHARACTERS.add(Characters.orderByList(Characters.Email, settings.getOrderedKeyChars(lang, 0), true));
			KEY_CHARACTERS.add(Characters.orderByList(Characters.Email, settings.getOrderedKeyChars(lang, 1), true));
		}

		refreshSuggestions();
		shouldSelectNextLetter = true; // do not accept any previous suggestions after loading the new ones

		return true;
	}


	@Override
	public void setSequence(@NonNull String sequence) {
		super.setSequence(sequence);
		refreshSuggestions();
		shouldSelectNextLetter = true;
	}


	/**
	 * Every letter is a suggestion of its own here, so this is where the word learner watches the
	 * word grow. Anything that is not a letter ends the word and gets it learned.
	 */
	@Override
	public void onAcceptSuggestion(@NonNull String w, boolean preserveWordList) {
		wordLearner.onAccept(language, w);
		if (!preserveWordList) {
			reset();
		}
	}


	@Override
	public void onCursorMove(@NonNull String word) {
		super.onCursorMove(word);
		wordLearner.discard(); // the user went elsewhere; whatever we collected is no longer before the cursor
	}


	@Override
	public void beforeDeleteText() {
		// While a letter is still pending (not yet accepted), Backspace removes that letter, which the
		// learner has not collected. Only an accepted letter counts as one of the word's.
		if (suggestions.isEmpty()) {
			wordLearner.onBackspace();
		}
	}


	@Override
	public void onFinishWord() {
		wordLearner.onFinishWord(language);
	}


	@Override
	public boolean shouldAcceptPreviousSuggestion(String word) {
		return
			!shouldSelectNextLetter
			&& word != null && !word.isEmpty()
			&& !Characters.PLACEHOLDER.equals(word);
	}


	@Override
	public boolean shouldSelectNextSuggestion() {
		return shouldSelectNextLetter;
	}


	@Override
	public void reset() {
		super.reset();
		digitSequence = "";
		shouldSelectNextLetter = false;
	}


	@NonNull
	@Override
	public String toString() {
		String modeString = language.getAbcString();

		// There are many languages written using the same alphabet, so if the user has
		// enabled multiple ones, make it clear which one is it, by appending the unique
		// country or language code to "ABC" or "АБВ".
		if (LanguageKind.isArabicBased(language) || LanguageKind.isCyrillic(language) || LanguageKind.isHebrew(language) || LanguageKind.isLatinBased(language)) {
			modeString += " / " + language.getCode();
		}

		return new Text(language, modeString).toTextCase(textCase);
	}
}
