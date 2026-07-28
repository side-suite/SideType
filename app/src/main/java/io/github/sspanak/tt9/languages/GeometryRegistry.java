package io.github.sspanak.tt9.languages;

import android.content.Context;
import android.content.res.AssetManager;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import io.github.sspanak.tt9.util.AssetFile;
import io.github.sspanak.tt9.util.Logger;

/**
 * Loads the shipped keypad geometries (<code>assets/geometries/*.yml</code>) and tracks which one is
 * active. A geometry is the keycode -> key-index map for a physical Keytile; see {@link Geometry} and
 * <code>docs/adr/0005-keytile-layout-platform.md</code> (D2).
 *
 * <p>Modelled on {@link LanguageCollection}: a lazily-initialised static singleton fed a
 * {@link Context} once, so the static {@link io.github.sspanak.tt9.ime.helpers.Key} helpers and
 * {@link KeySequence} can reach the active geometry without threading a Context through every call.</p>
 *
 * <p>For P1 (SID-120) only <code>compact-qwerty</code> is registered and active, so nothing switches
 * yet — {@link #classify(int)} is the hook the P3 auto-detection will build on.</p>
 */
public class GeometryRegistry {
	private static final String LOG_TAG = GeometryRegistry.class.getSimpleName();
	private static final String GEOMETRIES_DIR = "geometries";

	public static final String COMPACT_QWERTY = "compact-qwerty";

	/**
	 * Correct-by-construction Compact QWERTY geometry, used as the active geometry only when the
	 * registry has not been initialised yet (e.g. a code path that runs before {@link #init}, or a
	 * unit test). It mirrors <code>assets/geometries/compact-qwerty.yml</code> so that key mapping
	 * and {@link KeySequence#getMaxKey()} stay byte-identical to the legacy behaviour even before the
	 * YAML is loaded — a keyboard that maps a letter key to "nothing" is catastrophic, so the safety
	 * net is deliberate. {@code GeometryCompactParityTest} asserts this stays identical to the YAML.
	 */
	private static final Geometry COMPACT_FALLBACK = Geometry.builtIn(
		COMPACT_QWERTY, 16, Geometry.DETECT_LETTERS,
		new int[][] {
			{KeyEvent.KEYCODE_Q, 2}, {KeyEvent.KEYCODE_E, 3}, {KeyEvent.KEYCODE_T, 4},
			{KeyEvent.KEYCODE_U, 5}, {KeyEvent.KEYCODE_O, 6}, {KeyEvent.KEYCODE_A, 7},
			{KeyEvent.KEYCODE_D, 8}, {KeyEvent.KEYCODE_G, 9}, {KeyEvent.KEYCODE_J, 10},
			{KeyEvent.KEYCODE_L, 11}, {KeyEvent.KEYCODE_Z, 12}, {KeyEvent.KEYCODE_C, 13},
			{KeyEvent.KEYCODE_B, 14}, {KeyEvent.KEYCODE_M, 15},
		}
	);

	private static GeometryRegistry self;

	private final HashMap<String, Geometry> geometries = new HashMap<>();
	private Geometry active;


	private GeometryRegistry(@NonNull Context context) {
		load(context.getAssets());
		active = geometries.getOrDefault(COMPACT_QWERTY, COMPACT_FALLBACK);
	}


	public static synchronized void init(@NonNull Context context) {
		if (self == null) {
			self = new GeometryRegistry(context);
		}
	}


	/**
	 * The active geometry. Falls back to the built-in Compact QWERTY geometry when the registry is not
	 * yet initialised, so callers never see null and behaviour stays byte-identical pre-init.
	 */
	@NonNull
	public static Geometry getActive() {
		return self != null && self.active != null ? self.active : COMPACT_FALLBACK;
	}


	@Nullable
	public static Geometry byId(@NonNull String id) {
		if (self == null) {
			return COMPACT_QWERTY.equals(id) ? COMPACT_FALLBACK : null;
		}
		return self.geometries.get(id);
	}


	/**
	 * The id of the geometry whose keycode vocabulary {@code keyCode} belongs to, or null if no
	 * registered geometry claims it (e.g. a neutral key like space/backspace/DPAD). This is the
	 * seam the P3 tile auto-detection uses; in P1 it is not wired to any switching.
	 */
	@Nullable
	public static String classify(int keyCode) {
		for (Geometry geometry : all()) {
			if (geometry.hasKeyCode(keyCode)) {
				return geometry.getId();
			}
		}
		return null;
	}


	@NonNull
	private static Iterable<Geometry> all() {
		if (self == null || self.geometries.isEmpty()) {
			ArrayList<Geometry> fallback = new ArrayList<>();
			fallback.add(COMPACT_FALLBACK);
			return fallback;
		}
		return self.geometries.values();
	}


	private void load(@NonNull AssetManager assets) {
		String[] files;
		try {
			files = assets.list(GEOMETRIES_DIR);
		} catch (IOException e) {
			Logger.e(LOG_TAG, "Failed listing '" + GEOMETRIES_DIR + "'. " + e.getMessage());
			files = null;
		}

		if (files == null || files.length == 0) {
			Logger.e(LOG_TAG, "No geometries found in assets; falling back to the built-in Compact QWERTY geometry.");
			return;
		}

		for (String file : files) {
			if (!file.endsWith(".yml")) {
				continue;
			}
			try {
				Geometry geometry = Geometry.fromReader(new AssetFile(assets, GEOMETRIES_DIR + "/" + file).getReader());
				if (geometry != null) {
					geometries.put(geometry.getId(), geometry);
				}
			} catch (IOException e) {
				Logger.e(LOG_TAG, "Failed reading geometry '" + file + "'. " + e.getMessage());
			}
		}

		Logger.d(LOG_TAG, "Loaded " + geometries.size() + " geometries");
	}
}
