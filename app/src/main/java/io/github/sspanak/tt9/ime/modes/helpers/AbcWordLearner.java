package io.github.sspanak.tt9.ime.modes.helpers;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.sspanak.tt9.db.DataStore;
import io.github.sspanak.tt9.db.entities.AddWordResult;
import io.github.sspanak.tt9.db.words.DictionaryLoader;
import io.github.sspanak.tt9.hacks.InputType;
import io.github.sspanak.tt9.ime.helpers.InputConnectionAsync;
import io.github.sspanak.tt9.ime.helpers.TextField;
import io.github.sspanak.tt9.ime.modes.InputMode;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.languages.NullLanguage;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.util.Logger;
import io.github.sspanak.tt9.util.Text;

/**
 * Remembers the words typed letter-by-letter in ABC mode and adds them to the added words
 * (the same list as "Add Word"), so Predictive mode can suggest them next time.
 *
 * ABC mode never sees a whole word, only one accepted letter at a time, so this collects the
 * letters as they are accepted and learns the word when something that is not a letter ends it:
 * a space, punctuation, a numeral, an emoji, Enter, or switching to another input mode.
 *
 * It only ever learns a word it has watched being typed in full. Before saving, the collected
 * letters are compared with the text actually before the cursor; any drift (held Backspace, a
 * text selection, the host app editing the field) makes the word fail that check and it is
 * silently dropped, because a misspelled word in the dictionary is worse than a missed one.
 */
public class AbcWordLearner {
	private static final String LOG_TAG = AbcWordLearner.class.getSimpleName();
	private static final int MIN_WORD_LENGTH = 2;

	@NonNull private final SettingsStore settings;
	@Nullable private final TextField textField;
	private final boolean isAllowedField;

	@NonNull private final StringBuilder word = new StringBuilder();
	private boolean isTainted = false;


	public AbcWordLearner(@NonNull SettingsStore settings, @Nullable InputType inputType, @Nullable TextField textField) {
		this.settings = settings;
		this.textField = textField;
		// The "Add Word" screen adds the word itself; learning it there too would be pointless.
		this.isAllowedField = inputType == null || !inputType.isOwnAddWordField();
	}


	/**
	 * Call for every suggestion ABC mode accepts. Letters extend the current word; anything else
	 * ends it and triggers learning.
	 */
	public void onAccept(@NonNull Language language, @NonNull String accepted) {
		if (accepted.isEmpty()) {
			return;
		}

		if (new Text(language, accepted).isAlphabetic()) {
			if (!isTainted) {
				word.append(accepted);
			}
		} else {
			learn(language, accepted);
			discard();
		}
	}


	/**
	 * Call before a single character is deleted. Deleting past the start of the current word means
	 * the user is editing text we did not watch being typed, so nothing is learned until the next
	 * word boundary.
	 */
	public void onBackspace() {
		if (word.length() > 0) {
			word.setLength(word.length() - Character.charCount(word.codePointBefore(word.length())));
		} else {
			isTainted = true;
		}
	}


	/**
	 * Call when the word is ended by something other than typing a character: Enter, an editor
	 * action, or switching the input mode.
	 */
	public void onFinishWord(@NonNull Language language) {
		learn(language, "");
		discard();
	}


	/**
	 * Forget the current word without learning it, e.g. when the cursor moves elsewhere.
	 */
	public void discard() {
		word.setLength(0);
		isTainted = false;
	}


	/**
	 * @param trailing The non-letter text that ended the word. It may or may not have reached the
	 *                 text field yet, so it is tolerated, but not required, right before the cursor.
	 */
	private void learn(@NonNull Language language, @NonNull String trailing) {
		final String typed = word.toString();

		if (
			isTainted
			|| textField == null
			|| !isAllowedField
			|| typed.codePointCount(0, typed.length()) < MIN_WORD_LENGTH
			|| !settings.getLearnAbcWords()
			|| !settings.getPredictiveMode()
			|| language instanceof NullLanguage
			|| language.isTranscribed()
			|| DictionaryLoader.isRunning()
			|| !language.isValidWord(typed)
			|| !isWholeWordBeforeCursor(typed, trailing)
		) {
			return;
		}

		final String normalized = normalizeCase(language, typed);
		DataStore.put(
			(result) -> Logger.d(LOG_TAG, "Learned '" + normalized + "' (" + language.getCode() + "): " + describe(result)),
			language,
			normalized
		);
	}


	/**
	 * The collected letters must be exactly the word before the cursor: not a suffix of a longer
	 * word, not the beginning of one the cursor sits inside, and not stale after an edit we missed.
	 */
	private boolean isWholeWordBeforeCursor(@NonNull String typed, @NonNull String trailing) {
		String before = textField.getStringBeforeCursor(typed.length() + trailing.length() + 1);
		if (before.equals(InputConnectionAsync.TIMEOUT_SENTINEL)) {
			return false;
		}

		if (!trailing.isEmpty() && before.endsWith(trailing)) {
			before = before.substring(0, before.length() - trailing.length());
		}

		if (!before.endsWith(typed)) {
			return false;
		}

		final int wordStart = before.length() - typed.length();
		if (wordStart > 0 && Character.isLetter(before.codePointBefore(wordStart))) {
			return false;
		}

		final String after = textField.getStringAfterCursor(1);
		return after.isEmpty() || !Character.isLetter(after.codePointAt(0));
	}


	/**
	 * A capital at the start of a sentence, or Caps Lock, is not part of the word. Genuinely mixed
	 * case ("iPhone") is kept as typed.
	 */
	@NonNull
	private static String normalizeCase(@NonNull Language language, @NonNull String typed) {
		final int textCase = new Text(language, typed).getTextCase();
		return textCase == InputMode.CASE_DICTIONARY ? typed : typed.toLowerCase(language.getLocale());
	}


	@NonNull
	private static String describe(@NonNull AddWordResult result) {
		return switch (result.statusCode()) {
			case AddWordResult.CODE_SUCCESS -> "added";
			case AddWordResult.CODE_WORD_EXISTS -> "already known";
			default -> "failed with code " + result.statusCode();
		};
	}
}
