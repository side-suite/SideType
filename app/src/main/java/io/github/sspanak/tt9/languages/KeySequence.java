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
 *
 * The alphabet's extent is the active geometry's key count (SID-120): the Compact QWERTY geometry has
 * 16 keys, so {@link #getMaxKey()} is 15 and the top token is '?' — byte-identical to the previous
 * hardcoded constant. A narrower geometry (e.g. the numpad, P3) yields a shorter alphabet.
 */
public final class KeySequence {
	/** Lowest token character ('0'). */
	public static final char MIN_TOKEN = '0';

	private KeySequence() {}

	/** Highest supported key index — the active geometry's {@code keyCount - 1}. */
	public static int getMaxKey() {
		return GeometryRegistry.getActive().getMaxKey();
	}

	/** Highest token character for {@link #getMaxKey()} (currently '?' for the 16-key compact tile). */
	public static char getMaxToken() {
		return (char) ('0' + getMaxKey());
	}

	/**
	 * One character past {@link #getMaxToken()} ('@' for the compact tile). Used as an exclusive upper
	 * bound for SQLite prefix range scans: every child sequence of a prefix P satisfies
	 * P <= sequence < P+RANGE_UPPER_BOUND.
	 */
	public static char getRangeUpperBound() {
		return (char) (getMaxToken() + 1);
	}

	/** Encodes a key index (0..getMaxKey()) as its single-character sequence token. */
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
		return c >= MIN_TOKEN && c <= getMaxToken();
	}
}
