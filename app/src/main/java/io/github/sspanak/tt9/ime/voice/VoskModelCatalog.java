package io.github.sspanak.tt9.ime.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import io.github.sspanak.tt9.languages.Language;

/**
 * Maps a SideType {@link Language} to the Vosk acoustic model that can transcribe it.
 * <p>
 * Every value here is transcribed from Vosk's published metadata
 * (<a href="https://alphacephei.com/vosk/models/model-list.json">model-list.json</a>), which carries
 * an exact byte size and an md5 per model. <b>Do not estimate any of it.</b> The SID-8 spike guessed
 * these sizes and got all five wrong — Swedish by 7x (it claimed ~41 MB; the model is 303.5 MB). The
 * numbers here are shown to the user in a download-consent prompt, so a wrong one means they agreed
 * to something other than what happened. Re-read model-list.json when adding a row.
 * <p>
 * See docs/adr/0003-voice-model-delivery.md.
 */
public final class VoskModelCatalog {
	/** Vosk small models are trained at 16 kHz mono; this is the rate the recognizer expects. */
	static final float SAMPLE_RATE = 16000f;

	public static final class Entry {
		/** The zip filename on the Vosk model server, e.g. {@code vosk-model-small-en-us-0.15.zip}. */
		@NonNull public final String zipFileName;
		/** The single top-level directory the zip unpacks into (Vosk zips always contain exactly one). */
		@NonNull public final String unpackedDirName;
		/** Exact download size in bytes, from model-list.json. Shown to the user before downloading. */
		public final long sizeBytes;
		/** md5 of the zip, from model-list.json. Verified after download; a mismatch discards the file. */
		@NonNull public final String md5;

		private Entry(@NonNull String unpackedDirName, long sizeBytes, @NonNull String md5) {
			this.zipFileName = unpackedDirName + ".zip";
			this.unpackedDirName = unpackedDirName;
			this.sizeBytes = sizeBytes;
			this.md5 = md5;
		}

		/**
		 * The size as shown to the user before they consent to the download, e.g. "42.8 MB". Derived
		 * from the pinned byte count — never write a size literal anywhere else.
		 */
		@NonNull
		public String getFormattedSize() {
			return String.format(Locale.ROOT, "%.1f MB", sizeBytes / 1_000_000f);
		}
	}

	/**
	 * Keyed by the <b>full</b> locale tag from the language definitions, not by
	 * {@code locale.getLanguage()}.
	 * <p>
	 * This matters twice over. English is {@code en} and English (UK) is {@code en-GB}, and they have
	 * <i>different</i> acoustic models — keying by language would collapse both to "en" and silently
	 * give British users the American model (the SID-8 spike did exactly that). In the other
	 * direction, SideType's tags are {@code de-DE}/{@code fr-FR}/{@code es-ES} while Vosk names its
	 * models {@code de}/{@code fr}/{@code es}, so the mapping cannot be derived from either side and
	 * has to be written out.
	 * <p>
	 * Verified against model-list.json on 2026-07-15: all current (`obsolete: false`), all
	 * `type: small`, all Apache-2.0. Vosk keeps obsolete models downloadable — `en-us-0.3`,
	 * `en-us-0.4`, `es-0.22` and `de-zamia-0.3` all still resolve — so check the flag, not just the
	 * name, when bumping a revision.
	 * <p>
	 * Absent, and not an oversight: <b>Finnish, Norwegian and Danish have no Vosk model at any
	 * size</b> — a permanent limitation (docs/adr/0001). <b>Swedish</b> is omitted pending SID-58:
	 * its only model is 303.5 MB with a WER Vosk itself publishes as "TBD".
	 */
	private static final Map<String, Entry> MODELS;
	static {
		Map<String, Entry> models = new LinkedHashMap<>();
		models.put("en",    new Entry("vosk-model-small-en-us-0.15", 41_205_931L, "09ab50ccd62b674cbaa231b825f9c1cb"));
		models.put("en-GB", new Entry("vosk-model-small-en-gb-0.15", 42_757_500L, "6afd611b04b2b47c129c3615dc502383"));
		models.put("de-DE", new Entry("vosk-model-small-de-0.15",    46_499_967L, "4f21f92c0897b48287ef8839420608eb"));
		models.put("fr-FR", new Entry("vosk-model-small-fr-0.22",    42_233_323L, "8873b1234503f6edd55f54bfff31cf3e"));
		models.put("es-ES", new Entry("vosk-model-small-es-0.42",    39_817_833L, "2d5c94f9859a84881a0ef744738ebd31"));
		MODELS = Collections.unmodifiableMap(models);
	}

	private VoskModelCatalog() {}

	/** The locale tag used as a catalog key, e.g. "en-GB". Mirrors the tags in the language definitions. */
	@Nullable
	private static String toKey(@Nullable Language language) {
		return language == null ? null : language.getLocale().toString().replace('_', '-');
	}

	@Nullable
	public static Entry getModel(@Nullable Language language) {
		String key = toKey(language);
		return key == null ? null : MODELS.get(key);
	}

	/**
	 * Whether voice input could ever work for this language — i.e. whether a model <i>exists</i>, not
	 * whether one has been downloaded.
	 * <p>
	 * This distinction is the whole point. Models arrive on demand, so a disk-based check deadlocks:
	 * with no model on disk the voice controls hide themselves, and the download that would unhide
	 * them can never be triggered. Ask {@link VoskModelManager#isModelReady} for the disk question.
	 */
	public static boolean isSupported(@Nullable Language language) {
		return getModel(language) != null;
	}
}
