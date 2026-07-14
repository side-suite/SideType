package io.github.sspanak.tt9.ui.tray;

import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;

import java.util.Map;

/**
 * SID-17 — Candidate-bar theming handshake (the receiving half).
 *
 * <p>Holds a session-scoped color override that a trusted host app (SideHome) hands SideType via the
 * focused field's {@link EditorInfo#privateImeOptions}. Only the tray — the status bar and the
 * suggestion bar — consults it; the on-screen keyboard keys are never touched. Every channel is
 * independently optional: an absent or malformed value simply leaves that one channel at SideType's
 * own default, so a partial or broken hint degrades gracefully instead of producing a broken tray.
 *
 * <p>Lifecycle: {@link #update} is called from the IME's {@code setInputField} on every focus, so the
 * override is re-derived from scratch each time (a partial or absent hint self-heals to defaults) and
 * is cleared when input finishes (field {@code null}). That way a host tint can never stick into a
 * later field or another app.
 *
 * <p>Transport rationale (see the issue): the SideHome sender is Compose-native, and
 * {@code PlatformImeOptions} only exposes {@code privateImeOptions}, not {@code EditorInfo.extras}.
 * Encoding the hint as {@code privateImeOptions} tokens keeps both halves trivial for v1; the receiver
 * pays this small hex parser.
 *
 * <p>A single shared instance ({@link #getInstance()}) holds the state, mirroring how the active color
 * scheme is itself kept as process-wide state ({@code SettingsColors.colorScheme}). The IME serves one
 * input session at a time on the main thread, so no synchronization is needed.
 */
public class HostTrayTheme {
	private static final String NS = HostImeOptions.NS;
	private static final String KEY_BAR_BG = NS + "barBg";
	private static final String KEY_BAR_FG = NS + "barFg";
	private static final String KEY_SELECTED_BG = NS + "selectedBg";
	private static final String KEY_SELECTED_TEXT = NS + "selectedText";
	private static final String KEY_SEPARATOR = NS + "separator";

	private static final HostTrayTheme instance = new HostTrayTheme();

	@Nullable private Integer barBg;
	@Nullable private Integer barFg;
	@Nullable private Integer selectedBg;
	@Nullable private Integer selectedText;
	@Nullable private Integer separator;


	private HostTrayTheme() {}


	public static HostTrayTheme getInstance() {
		return instance;
	}


	/**
	 * Re-derives the override from the focused field. The hint is honored only when the sender is the
	 * trusted SideHome package (or, in debug builds, our own app — so the on-device test field can
	 * exercise the receiver before the SideHome sender exists). Any other package, a {@code null}/empty
	 * options string, or a string that carries none of our keys clears the override → default theme.
	 * Each recognized key is parsed on its own; a malformed value just leaves that one channel unset.
	 *
	 * @param field the focused field's {@link EditorInfo}, or {@code null} when input is finishing
	 * @param ownPackageName SideType's own application id, for the debug-only self-test gate
	 */
	public void update(@Nullable EditorInfo field, @Nullable String ownPackageName) {
		barBg = barFg = selectedBg = selectedText = separator = null;

		for (Map.Entry<String, String> token : HostImeOptions.parseTrusted(field, ownPackageName).entrySet()) {
			final Integer color = parseArgb(token.getValue());
			if (color == null) {
				continue;
			}

			switch (token.getKey()) {
				case KEY_BAR_BG -> barBg = color;
				case KEY_BAR_FG -> barFg = color;
				case KEY_SELECTED_BG -> selectedBg = color;
				case KEY_SELECTED_TEXT -> selectedText = color;
				case KEY_SEPARATOR -> separator = color;
				default -> { /* an unrelated private option (e.g. SID-19's appDict) — ignore it here */ }
			}
		}
	}


	/** Whole-tray background (SideHome's ambient surface), or {@code fallback} if the host omitted it. */
	public int barBackground(int fallback) {
		return barBg != null ? barBg : fallback;
	}

	/** Idle candidate text and status-bar text, or {@code fallback} if the host omitted it. */
	public int barForeground(int fallback) {
		return barFg != null ? barFg : fallback;
	}

	/** Selected-candidate pill background (SideHome's ambient accent), or {@code fallback} if omitted. */
	public int selectedBackground(int fallback) {
		return selectedBg != null ? selectedBg : fallback;
	}

	/** Selected-candidate text color, or {@code fallback} if the host omitted it. */
	public int selectedText(int fallback) {
		return selectedText != null ? selectedText : fallback;
	}

	/** Candidate divider color, or {@code fallback} if the host omitted it. */
	public int separator(int fallback) {
		return separator != null ? separator : fallback;
	}


	/**
	 * Parses one 8-digit ARGB hex value (upper or lower case). Returns {@code null} — leaving the channel
	 * at its default — for anything that is not exactly 8 hex digits, so a malformed value never throws.
	 */
	@Nullable
	private static Integer parseArgb(@Nullable String value) {
		if (value == null || value.length() != 8) {
			return null;
		}

		for (int i = 0; i < 8; i++) {
			final char c = value.charAt(i);
			final boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
			if (!isHex) {
				return null;
			}
		}

		return (int) Long.parseLong(value, 16);
	}


	/**
	 * A sample 5-color hint for the debug-only on-device test field, so the tint and its revert can be
	 * verified without the SideHome sender. Only honored in debug builds (see {@link HostImeOptions}).
	 */
	public static String debugSampleHint() {
		return KEY_BAR_BG + "=FF1A1B2E,"
			+ KEY_BAR_FG + "=FFECEFF4,"
			+ KEY_SELECTED_BG + "=FFEBCB8B,"
			+ KEY_SELECTED_TEXT + "=FF14151F,"
			+ KEY_SEPARATOR + "=FF3B4252";
	}
}
