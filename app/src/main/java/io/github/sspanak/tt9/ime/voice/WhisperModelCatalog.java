package io.github.sspanak.tt9.ime.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import io.github.sspanak.tt9.languages.Language;

/**
 * The one Whisper model SideType downloads, and the languages it can transcribe.
 * <p>
 * Unlike the per-language models this replaced, a multilingual Whisper model covers every language
 * at once, so there is a single entry. The size and SHA-256 are transcribed from the Hugging Face
 * repository metadata, not estimated: the size is shown to the user before they agree to the
 * download and the hash is verified after it. To switch model (for example to the smaller, faster
 * {@code ggml-tiny-q8_0.bin}), replace all three values together.
 */
public final class WhisperModelCatalog {
	/** Whisper is trained on 16 kHz mono audio; the recorder captures at this rate. */
	static final int SAMPLE_RATE = 16000;

	/** The revision of https://huggingface.co/ggerganov/whisper.cpp the file below was read from. */
	private static final String REPO_REVISION = "5359861c739e955e79d9a303bcbc70fb988958b1";

	public static final class Entry {
		@NonNull public final String fileName;
		/** Exact download size in bytes. Shown to the user before downloading. */
		public final long sizeBytes;
		/** SHA-256 of the file. Verified after download; a mismatch discards the file. */
		@NonNull public final String sha256;

		private Entry(@NonNull String fileName, long sizeBytes, @NonNull String sha256) {
			this.fileName = fileName;
			this.sizeBytes = sizeBytes;
			this.sha256 = sha256;
		}

		@NonNull
		public String getUrl() {
			return "https://huggingface.co/ggerganov/whisper.cpp/resolve/" + REPO_REVISION + "/" + fileName;
		}

		/** The size as shown to the user, e.g. "81.8 MB". Derived from the pinned byte count. */
		@NonNull
		public String getFormattedSize() {
			return String.format(Locale.ROOT, "%.1f MB", sizeBytes / 1_000_000f);
		}
	}

	/** Whisper "base", multilingual, 8-bit quantised. */
	public static final Entry MODEL = new Entry(
		"ggml-base-q8_0.bin",
		81_768_585L,
		"c577b9a86e7e048a0b7eada054f4dd79a56bbfa911fbdacf900ac5b567cbb7d9"
	);

	/** The language codes the multilingual Whisper models were trained on. */
	private static final Set<String> LANGUAGES = new HashSet<>(Arrays.asList(
		"en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr", "pl", "ca", "nl", "ar", "sv", "it",
		"id", "hi", "fi", "vi", "he", "uk", "el", "ms", "cs", "ro", "da", "hu", "ta", "no", "th", "ur",
		"hr", "bg", "lt", "la", "mi", "ml", "cy", "sk", "te", "fa", "lv", "bn", "sr", "az", "sl", "kn",
		"et", "mk", "br", "eu", "is", "hy", "ne", "mn", "bs", "kk", "sq", "sw", "gl", "mr", "pa", "si",
		"km", "sn", "yo", "so", "af", "oc", "ka", "be", "tg", "sd", "gu", "am", "yi", "lo", "uz", "fo",
		"ht", "ps", "tk", "nn", "mt", "sa", "lb", "my", "bo", "tl", "mg", "as", "tt", "haw", "ln", "ha",
		"ba", "jw", "su", "yue"
	));

	private WhisperModelCatalog() {}

	/**
	 * The Whisper code for a SideType language, or null when Whisper cannot transcribe it. Java still
	 * reports a few languages under their retired ISO codes, hence the remapping.
	 */
	@Nullable
	public static String getLanguageCode(@Nullable Language language) {
		if (language == null) {
			return null;
		}

		String code = language.getLocale().getLanguage().toLowerCase(Locale.ROOT);
		switch (code) {
			case "iw": code = "he"; break;
			case "in": code = "id"; break;
			case "ji": code = "yi"; break;
			case "nb": code = "no"; break;
			case "fil": code = "tl"; break;
			default: break;
		}

		return LANGUAGES.contains(code) ? code : null;
	}

	public static boolean isSupported(@Nullable Language language) {
		return getLanguageCode(language) != null;
	}
}
