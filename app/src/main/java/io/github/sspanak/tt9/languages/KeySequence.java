package io.github.sspanak.tt9.languages;

/**
 * Central definition of the "digit sequence" token alphabet.
 *
 * TT9 represents each dictionary word as a String where every character is a single-char token for
 * one keypad key. Historically the keypad had 10 keys (0-9) and the tokens were the literal digit
 * characters '0'..'9'. To support wider keypads (e.g. the Sidephone Compact QWERTY, which has 14
 * letter keys → key indices up to 15), we extend the token alphabet CONTIGUOUSLY:
 *
 *   token(key) = (char)('0' + key)
 *
 *   keys  0..9  -> '0'..'9'   (ASCII 48..57)  — identical to before (numpad stays byte-compatible)
 *   keys 10..15 -> ':;<=>?'   (ASCII 58..63)  — all strictly below 'A' (65), so they never collide
 *                                               with dictionary word characters (letters/apostrophe)
 *
 * Because the tokens are contiguous and ordered, the existing decode (`char - '0'`) and the SQLite
 * lexical range scans keep working; only the upper bound char and the digit-vs-word boundary test
 * need to know the alphabet's extent. This class is the single source of truth for all of that.
 */
public final class KeySequence {
	/** Highest supported key index. 15 keeps the max token at '?' (63), strictly below 'A' (65). */
	public static final int MAX_KEY = 15;

	/** Lowest token character ('0'). */
	public static final char MIN_TOKEN = '0';

	/** Highest token character for MAX_KEY (currently '?'). */
	public static final char MAX_TOKEN = (char) ('0' + MAX_KEY);

	/**
	 * One character past MAX_TOKEN ('@'). Used as an exclusive upper bound for SQLite prefix range
	 * scans: every child sequence of a prefix P satisfies  P <= sequence < P+RANGE_UPPER_BOUND.
	 */
	public static final char RANGE_UPPER_BOUND = (char) (MAX_TOKEN + 1);

	private KeySequence() {}

	/** Encodes a key index (0..MAX_KEY) as its single-character sequence token. */
	public static char keyToToken(int key) {
		return (char) ('0' + key);
	}

	/** Same as {@link #keyToToken(int)} but returns a String, for convenient concatenation. */
	public static String keyToTokenString(int key) {
		return String.valueOf((char) ('0' + key));
	}

	/** Decodes a sequence token back to its key index. */
	public static int tokenToKey(char token) {
		return token - '0';
	}

	/** True if {@code c} is a valid sequence token (i.e. represents a key, not a word character). */
	public static boolean isToken(char c) {
		return c >= MIN_TOKEN && c <= MAX_TOKEN;
	}
}
