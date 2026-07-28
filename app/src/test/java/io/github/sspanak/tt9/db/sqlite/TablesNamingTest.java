package io.github.sspanak.tt9.db.sqlite;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * SID-121: the word/position/pair tables (and their indexes) carry the active (geometry, variant)
 * suffix, sanitized to a valid SQLite identifier; MindReader tables stay langId-only. The registry is
 * uninitialised in a unit test, so the active geometry is the built-in compact-qwerty fallback.
 */
public class TablesNamingTest {
	private static final int LANG = 543; // "en"

	@Test
	public void wordTablesCarryTheActiveComboSuffix() {
		assertEquals("compact-qwerty", Tables.activeGeometryId());
		assertEquals("words_543_compact_qwerty_default", Tables.getWords(LANG));
		assertEquals("word_positions_543_compact_qwerty_default", Tables.getWordPositions(LANG));
		assertEquals("word_pairs_543_compact_qwerty_default", Tables.getWordPairs(LANG));
	}

	@Test
	public void mindReaderTablesStayLangIdOnly() {
		// n-gram context models don't depend on the keypad geometry, so they are not suffixed.
		assertEquals("mind_reader_ngrams_543", Tables.getMindReaderNgrams(LANG));
		assertEquals("mind_reader_tokens_543", Tables.getMindReaderTokens(LANG));
	}
}
