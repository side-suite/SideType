package io.github.sspanak.tt9.db.sqlite;

import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import java.util.ArrayList;

import io.github.sspanak.tt9.languages.GeometryRegistry;
import io.github.sspanak.tt9.languages.Language;

public class Tables {

	static final String LANGUAGES_META = "languages_meta";
	static final String CUSTOM_WORDS = "custom_words";
	private static final String MIND_READER_NGRAMS_TABLE_BASE_NAME = "mind_reader_ngrams_";
	private static final String MIND_READER_TOKENS_TABLE_BASE_NAME = "mind_reader_tokens_";
	private static final String POSITIONS_TABLE_BASE_NAME = "word_positions_";
	private static final String WORDS_TABLE_BASE_NAME = "words_";
	private static final String WORD_PAIRS_TABLE_BASE_NAME = "word_pairs_";

	// SID-121: the word/position/pair tables (and their indexes) carry the active (geometry, variant),
	// because a word's stored key sequence depends on the binding. The suffix is derived from the active
	// tile's geometry and the language's selected variant (SID-124: e.g. German QWERTZ on compact-qwerty).
	// MindReader n-grams/tokens are word-context models (no key sequences), so they stay langId-only.
	// The suffix is sanitized to a valid SQLite identifier ("compact-qwerty" -> "compact_qwerty").
	private static String sanitize(@NonNull String name) {
		return name.replaceAll("[^A-Za-z0-9]", "_");
	}

	public static String activeGeometryId() {
		return GeometryRegistry.getActive().getId();
	}

	private static String activeSuffix(int langId) {
		return "_" + sanitize(activeGeometryId()) + "_" + sanitize(GeometryRegistry.getActiveVariant(langId));
	}

	static String getMindReaderNgrams(int langId) { return MIND_READER_NGRAMS_TABLE_BASE_NAME + langId; }
	static String getMindReaderTokens(int langId) { return MIND_READER_TOKENS_TABLE_BASE_NAME + langId; }

	static String getWords(int langId) { return WORDS_TABLE_BASE_NAME + langId + activeSuffix(langId); }
	static String getWordPositions(int langId) { return POSITIONS_TABLE_BASE_NAME + langId + activeSuffix(langId); }
	static String getWordPairs(int langId) { return WORD_PAIRS_TABLE_BASE_NAME + langId + activeSuffix(langId); }


	public static String[] getMindReaderCreateQueries(@NonNull ArrayList<Language> languages) {
		final int languageCount = languages.size();
		final String[] queries = new String[languageCount * 2];

		int queryId = 0;
		for (Language language : languages) {
			queries[queryId++] = createMindReaderNgrams(language.getId());
			queries[queryId++] = createMindReaderTokens(language.getId());
		}

		return queries;
	}


	static String[] getWordsCreateQueries(@NonNull ArrayList<Language> languages) {
		final int languageCount = languages.size();
		final String[] queries = new String[languageCount * 3 + 3];

		queries[0] = createCustomWords();
		queries[1] = createCustomWordsIndex();
		queries[2] = createLanguagesMeta();

		int queryId = 3;
		for (Language language : languages) {
			queries[queryId++] = createWordsTable(language.getId());
			queries[queryId++] = createWordPositions(language.getId());
			queries[queryId++] = createWordPairs(language.getId());
		}

		return queries;
	}


	/**
	 * Creates the word/position/pair tables for a language's ACTIVE (geometry, variant) combo, if absent.
	 * Used to lazily materialize a new combo (e.g. the numpad tile) on first use — onCreate only builds
	 * the combo that was active when the DB was first created. Idempotent (CREATE TABLE IF NOT EXISTS).
	 */
	public static void createWordTables(@NonNull SQLiteDatabase db, @NonNull Language language) {
		int langId = language.getId();
		CompiledQueryCache
			.execute(db, createWordsTable(langId))
			.execute(createWordPositions(langId))
			.execute(createWordPairs(langId));
	}


	public static void createWordIndex(@NonNull SQLiteDatabase db, @NonNull Language language) {
		CompiledQueryCache.execute(db, createWordsIndex(language.getId()));
	}

	public static void createPositionIndex(@NonNull SQLiteDatabase db, @NonNull Language language) {
		CompiledQueryCache.execute(db, createWordsPositionsIndex(language.getId()));
	}


	public static void dropWordsIndexes(@NonNull SQLiteDatabase db, @NonNull Language language) {
		CompiledQueryCache
			.execute(db, dropWordsIndex(language.getId()))
			.execute(dropWordPositionsIndex(language.getId()));
	}


	private static String createWordsTable(int langId) {
		return
			"CREATE TABLE IF NOT EXISTS " + getWords(langId) + " (" +
				"frequency INTEGER NOT NULL DEFAULT 0, " +
				"position INTEGER NOT NULL, " +
				"word TEXT NOT NULL" +
			")";
	}

	private static String wordsIndexName(int langId) {
		return "idx_position_" + langId + activeSuffix(langId);
	}

	private static String createWordsIndex(int langId) {
		return "CREATE INDEX IF NOT EXISTS " + wordsIndexName(langId) + " ON " + getWords(langId) + " (position, word)";
	}

	private static String dropWordsIndex(int langId) {
		return "DROP INDEX IF EXISTS " + wordsIndexName(langId);
	}

	private static String createWordPositions(int langId) {
		return
			"CREATE TABLE IF NOT EXISTS " + getWordPositions(langId) + " (" +
				"sequence TEXT NOT NULL, " +
				"start INTEGER NOT NULL, " +
				"end INTEGER NOT NULL" +
			")";
	}

	private static String positionsIndexName(int langId) {
		return "idx_sequence_start_" + langId + activeSuffix(langId);
	}

	private static String createWordsPositionsIndex(int langId) {
		return "CREATE INDEX IF NOT EXISTS " + positionsIndexName(langId) + " ON " + getWordPositions(langId) + " (sequence, `start`)";
	}

	private static String dropWordPositionsIndex(int langId) {
		return "DROP INDEX IF EXISTS " + positionsIndexName(langId);
	}

	private static String createCustomWords() {
		return "CREATE TABLE IF NOT EXISTS " + CUSTOM_WORDS + " (" +
			"id INTEGER PRIMARY KEY, " +
			"langId INTEGER NOT NULL, " +
			"sequence TEXT NOT NULL, " +
			"word INTEGER NOT NULL " +
		")";
	}

	private static String createCustomWordsIndex() {
		return "CREATE INDEX IF NOT EXISTS idx_langId_sequence ON " + CUSTOM_WORDS + " (langId, sequence)";
	}

	private static String createWordPairs(int langId) {
		return "CREATE TABLE IF NOT EXISTS " + getWordPairs(langId) + " (" +
			"word1 TEXT NOT NULL, " +
			"word2 TEXT NOT NULL, " +
			"sequence2 TEXT NOT NULL " +
		")";
	}

	private static String createLanguagesMeta() {
		return "CREATE TABLE IF NOT EXISTS " + LANGUAGES_META + " (" +
			"langId INTEGER UNIQUE NOT NULL, " +
			"positionsToNormalize TEXT NULL," +
			"fileHash TEXT NOT NULL DEFAULT 0 " +
		")";
	}

	private static String createMindReaderNgrams(int langId) {
		return "CREATE TABLE IF NOT EXISTS " + getMindReaderNgrams(langId) + " (" +
			"idx INTEGER NOT NULL DEFAULT 0, " +
			"before INTEGER NOT NULL DEFAULT 0, " +
			"next INTEGER NOT NULL DEFAULT 0, " +
			"PRIMARY KEY (idx) " +
		") WITHOUT ROWID";
	}

	private static String createMindReaderTokens(int langId) {
		return "CREATE TABLE IF NOT EXISTS " + getMindReaderTokens(langId) + " (" +
			"idx INTEGER NOT NULL, " +
			"token TEXT NOT NULL, " +
			"PRIMARY KEY (idx) " +
		") WITHOUT ROWID";
	}
}
