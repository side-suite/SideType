package io.github.sspanak.tt9.ui.tray;

import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

import io.github.sspanak.tt9.BuildConfig;

/**
 * Shared transport + trust gate for the host handshake that SideHome uses to hand SideType session
 * hints via the focused field's {@link EditorInfo#privateImeOptions}. Both the SID-17 tray-color
 * handshake ({@link HostTrayTheme}) and the SID-19 app-name prediction bias
 * ({@link HostAppDictionary}) ride the SAME comma-separated {@code key=value} token string, the SAME
 * {@code fi.palonkorpi.sidetype.} key namespace, and the SAME trusted-package gate — so there is one
 * parser and one trust model, not two.
 *
 * <p>Trust: options are honored only from the SideHome package. In debug builds our own package is
 * also trusted, so the on-device test field can exercise the receivers before the SideHome sender
 * exists. Any other package, or an absent/empty options string, yields no tokens.
 */
public final class HostImeOptions {
	/** The key namespace both host features share. Established by SID-17; SID-19 extends it. */
	public static final String NS = "fi.palonkorpi.sidetype.";

	/**
	 * The one host SideType trusts to present these options. The public contract is host-agnostic —
	 * any app could set privateImeOptions — but for v1 SideType honors them only from SideHome.
	 */
	private static final String SIDEHOME_PACKAGE = "fi.palonkorpi.sidehome";


	private HostImeOptions() {}


	/**
	 * Parses the trusted {@code key=value} tokens carried by the focused field. Returns an empty map
	 * when the field is {@code null}, carries no options, or comes from an untrusted package — so a
	 * caller can treat "no hint" and "not allowed" identically. Malformed tokens (missing {@code =})
	 * are skipped; value interpretation is left to each caller.
	 *
	 * @param field the focused field's {@link EditorInfo}, or {@code null} when input is finishing
	 * @param ownPackageName SideType's own application id, for the debug-only self-test gate
	 */
	@NonNull
	public static Map<String, String> parseTrusted(@Nullable EditorInfo field, @Nullable String ownPackageName) {
		Map<String, String> tokens = new HashMap<>();

		if (field == null || field.privateImeOptions == null || field.privateImeOptions.isEmpty() || !isTrusted(field, ownPackageName)) {
			return tokens;
		}

		for (String token : field.privateImeOptions.split(",")) {
			final int eq = token.indexOf('=');
			if (eq < 1) {
				continue;
			}
			tokens.put(token.substring(0, eq).trim(), token.substring(eq + 1).trim());
		}

		return tokens;
	}


	/**
	 * Whether a boolean-ish flag value means "on". Accepts {@code 1} or {@code true} (case-insensitive);
	 * everything else — including {@code null} — is off.
	 */
	public static boolean isFlagOn(@Nullable String value) {
		return "1".equals(value) || "true".equalsIgnoreCase(value);
	}


	private static boolean isTrusted(@NonNull EditorInfo field, @Nullable String ownPackageName) {
		if (field.packageName == null) {
			return false;
		}
		if (SIDEHOME_PACKAGE.equals(field.packageName)) {
			return true;
		}
		// Debug affordance: the on-device test field lives inside SideType, so it can only ever present
		// our own package, never SideHome's. Trusting our own package in debug builds lets the receivers
		// be eyeballed before the SideHome sender exists. Never trusted in release builds.
		return BuildConfig.DEBUG && ownPackageName != null && ownPackageName.equals(field.packageName);
	}
}
