package io.github.sspanak.tt9.ui.tray;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.util.Logger;

/**
 * SID-19 — App-name-aware prediction for host search fields (the session app dictionary).
 *
 * <p>When a trusted host (SideHome) marks the focused field as an app-launcher search, via the
 * {@code fi.palonkorpi.sidetype.appDict=1} token in {@link EditorInfo#privateImeOptions}, SideType
 * enumerates the device's launchable apps and biases predictive candidates toward those app names —
 * so typing on the ambiguous 2-letters-per-key tile lands on brand nouns (Revolut, Aegis,
 * Obtainium) instead of dictionary words. The bias is strictly session-scoped: it is re-derived from
 * scratch on every focus ({@link #update}) and cleared when input finishes (field {@code null}), so
 * app names can never leak into normal typing in other apps.
 *
 * <p>Transport + trust are shared with the SID-17 tray-color handshake (see {@link HostImeOptions}):
 * the same privateImeOptions token string, key namespace, and trusted-package gate — one parser, one
 * trust model. This class only adds the {@code appDict} flag and the app enumeration on top.
 *
 * <p>Enumeration self-heals: a {@link PackageManager} failure or an empty result simply leaves the
 * dictionary inactive, so prediction falls back to normal behavior and never crashes.
 *
 * <p>Threading: {@link #update} runs on the IME main thread at focus; {@link #getMatches} is called
 * from the word-loading worker thread (see {@code DataStore.getWords}). The mutable state is guarded
 * by the instance monitor, and {@code getMatches} iterates a snapshot taken under the lock.
 */
public class HostAppDictionary {
	private static final String LOG_TAG = HostAppDictionary.class.getSimpleName();

	/** The privateImeOptions flag that turns the app-name bias on for a field session. */
	private static final String KEY_APP_DICT = HostImeOptions.NS + "appDict";

	/** A defensive cap, so a device with an unusually large app list can't bloat the session dictionary. */
	private static final int MAX_APPS = 1000;

	private static final HostAppDictionary instance = new HostAppDictionary();

	/** Whether the current field asked for the app-name bias and enumeration yielded something. */
	private volatile boolean active = false;

	/** Raw launchable-app labels for the current session (e.g. "Google Maps", "Revolut"). */
	@NonNull private ArrayList<String> labels = new ArrayList<>();

	/** Per-word entries ({label word, its digit sequence}), cached per language and rebuilt lazily. */
	@Nullable private ArrayList<Entry> cachedEntries = null;
	private int cachedLanguageId = Integer.MIN_VALUE;


	private HostAppDictionary() {}


	public static HostAppDictionary getInstance() {
		return instance;
	}


	/**
	 * Re-derives the session app dictionary from the focused field. Clears any previous session first,
	 * so a stale app list from a previous field or a non-host app can never stick. The dictionary is
	 * (re)populated only when the trusted host set the {@code appDict} flag on this field; otherwise it
	 * stays empty and inactive.
	 *
	 * @param field the focused field's {@link EditorInfo}, or {@code null} when input is finishing
	 * @param ownPackageName SideType's own application id, for the debug-only self-test gate
	 * @param context any context able to reach the {@link PackageManager}; may be {@code null} on teardown
	 */
	public synchronized void update(@Nullable EditorInfo field, @Nullable String ownPackageName, @Nullable Context context) {
		active = false;
		labels = new ArrayList<>();
		cachedEntries = null;
		cachedLanguageId = Integer.MIN_VALUE;

		final Map<String, String> tokens = HostImeOptions.parseTrusted(field, ownPackageName);
		if (!HostImeOptions.isFlagOn(tokens.get(KEY_APP_DICT)) || context == null) {
			return;
		}

		final ArrayList<String> enumerated = enumerateLaunchableApps(context);
		if (enumerated.isEmpty()) {
			// PackageManager failed or returned nothing — fall back to normal prediction, silently.
			return;
		}

		labels = enumerated;
		active = true;
		Logger.d(LOG_TAG, "Host app dictionary active with " + labels.size() + " app labels");
	}


	/** Whether the app-name bias is currently in effect for the focused field. */
	public boolean isActive() {
		return active;
	}


	/**
	 * Returns the app-name words whose digit sequence starts with {@code digitSequence} (i.e. the app
	 * words the user could be typing on the predictive tile), most-relevant order preserved from the
	 * launcher enumeration. Each word keeps the app's own capitalization. When a {@code stem} filter is
	 * active, only words starting with it are returned. Returns an empty list when the dictionary is
	 * inactive — the caller then behaves exactly as it does without this feature.
	 *
	 * @param language the current input language, used to map app words to digit sequences
	 * @param digitSequence the keys pressed so far
	 * @param stem an optional stem filter (from word recomposing/filtering), or empty/null for none
	 * @param max the maximum number of app words to return
	 */
	@NonNull
	public ArrayList<String> getMatches(@Nullable Language language, @Nullable String digitSequence, @Nullable String stem, int max) {
		final ArrayList<String> matches = new ArrayList<>();
		if (!active || language == null || digitSequence == null || digitSequence.isEmpty() || max <= 0) {
			return matches;
		}

		final List<Entry> entries = getEntries(language);
		final String stemLower = stem == null ? "" : stem.toLowerCase(language.getLocale());
		final HashSet<String> seen = new HashSet<>();

		for (Entry entry : entries) {
			if (matches.size() >= max) {
				break;
			}
			if (!entry.sequence.startsWith(digitSequence)) {
				continue;
			}

			final String wordLower = entry.word.toLowerCase(language.getLocale());
			if (!stemLower.isEmpty() && !wordLower.startsWith(stemLower)) {
				continue;
			}
			if (seen.add(wordLower)) {
				matches.add(entry.word);
			}
		}

		return matches;
	}


	/**
	 * Builds (or returns the cached) per-word digit-sequence entries for the given language. App labels
	 * are split into words, because a user types one word at a time on a predictive tile, and words that
	 * cannot be encoded in this language (non-Latin scripts, symbols, etc.) are simply dropped.
	 */
	@NonNull
	private synchronized List<Entry> getEntries(@NonNull Language language) {
		final int languageId = language.getId();
		if (cachedEntries != null && cachedLanguageId == languageId) {
			return cachedEntries;
		}

		final ArrayList<Entry> entries = new ArrayList<>();
		final HashSet<String> seenWords = new HashSet<>();

		for (String label : labels) {
			for (String word : label.split("\\s+")) {
				if (word.isEmpty() || !seenWords.add(word.toLowerCase(language.getLocale()))) {
					continue;
				}
				try {
					final String sequence = language.getDigitSequenceForWord(word);
					if (!sequence.isEmpty()) {
						entries.add(new Entry(word, sequence));
					}
				} catch (Exception e) {
					// A word this language can't type (foreign script, punctuation-only, ...) — skip it.
				}
			}
		}

		cachedEntries = entries;
		cachedLanguageId = languageId;
		return entries;
	}


	/**
	 * Enumerates the device's launchable app labels via a launcher intent query. Any failure yields an
	 * empty list rather than throwing, so the feature degrades to normal prediction. Requires the
	 * matching {@code <queries>} entry in the manifest to see other packages on API 30+.
	 */
	@NonNull
	private ArrayList<String> enumerateLaunchableApps(@NonNull Context context) {
		final ArrayList<String> result = new ArrayList<>();
		try {
			final PackageManager pm = context.getPackageManager();
			if (pm == null) {
				return result;
			}

			final Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
			launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

			final List<ResolveInfo> apps = pm.queryIntentActivities(launcherIntent, 0);
			final HashSet<String> seen = new HashSet<>();
			for (ResolveInfo info : apps) {
				if (result.size() >= MAX_APPS) {
					break;
				}
				final CharSequence rawLabel = info.loadLabel(pm);
				if (rawLabel == null) {
					continue;
				}
				final String label = rawLabel.toString().trim();
				if (!label.isEmpty() && seen.add(label)) {
					result.add(label);
				}
			}
		} catch (Exception e) {
			Logger.w(LOG_TAG, "Failed enumerating launchable apps: " + e.getMessage());
			return new ArrayList<>();
		}

		return result;
	}


	/** A sample flag for the debug-only on-device test field, so the bias can be verified without SideHome. */
	public static String debugFlag() {
		return KEY_APP_DICT + "=1";
	}


	/** One app-name word paired with its precomputed digit sequence for the active language. */
	private static final class Entry {
		@NonNull final String word;
		@NonNull final String sequence;

		Entry(@NonNull String word, @NonNull String sequence) {
			this.word = word;
			this.sequence = sequence;
		}
	}
}
