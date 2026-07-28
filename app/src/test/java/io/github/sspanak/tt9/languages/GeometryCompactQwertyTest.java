package io.github.sspanak.tt9.languages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SID-120 acceptance: the shipped Compact QWERTY geometry must reproduce the legacy hardcoded
 * {@code Key.codeToNumber} map exactly. This test is the guard that catches any transcription drift
 * in {@code assets/geometries/compact-qwerty.yml} (the source of truth) or the built-in fallback the
 * registry uses before it is initialised.
 *
 * <p>Runs on a plain JVM: {@link KeyEvent}{@code .KEYCODE_*} are inlined compile-time constants and
 * geometry parsing makes no android method calls, so no Robolectric is needed.</p>
 */
public class GeometryCompactQwertyTest {
	/** The 14 ambiguous letter keys of the Compact QWERTY tile → key index, exactly as the old switch had them. */
	private static final Map<Integer, Integer> LEGACY_CODE_TO_INDEX = buildLegacyMap();

	private Geometry yaml;

	private static Map<Integer, Integer> buildLegacyMap() {
		Map<Integer, Integer> map = new LinkedHashMap<>();
		map.put(KeyEvent.KEYCODE_Q, 2);
		map.put(KeyEvent.KEYCODE_E, 3);
		map.put(KeyEvent.KEYCODE_T, 4);
		map.put(KeyEvent.KEYCODE_U, 5);
		map.put(KeyEvent.KEYCODE_O, 6);
		map.put(KeyEvent.KEYCODE_A, 7);
		map.put(KeyEvent.KEYCODE_D, 8);
		map.put(KeyEvent.KEYCODE_G, 9);
		map.put(KeyEvent.KEYCODE_J, 10);
		map.put(KeyEvent.KEYCODE_L, 11);
		map.put(KeyEvent.KEYCODE_Z, 12);
		map.put(KeyEvent.KEYCODE_C, 13);
		map.put(KeyEvent.KEYCODE_B, 14);
		map.put(KeyEvent.KEYCODE_M, 15);
		return map;
	}

	@Before
	public void loadYaml() throws IOException {
		// The tracked source of truth; assets/geometries/ is only populated at build time.
		File file = locate("geometries/compact-qwerty.yml");
		try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
			yaml = Geometry.fromReader(reader);
		}
		assertNotNull("compact-qwerty.yml failed to parse", yaml);
	}

	private static File locate(String relative) {
		for (String base : new String[] {"", "app/"}) {
			File f = new File(base + relative);
			if (f.exists()) {
				return f;
			}
		}
		throw new IllegalStateException("Cannot find " + relative + " relative to " + new File(".").getAbsolutePath());
	}

	@Test
	public void yamlReproducesLegacyCodeToIndex() {
		for (Map.Entry<Integer, Integer> e : LEGACY_CODE_TO_INDEX.entrySet()) {
			assertEquals("keycode " + e.getKey(), (int) e.getValue(), yaml.codeToIndex(e.getKey()));
		}
	}

	@Test
	public void yamlReproducesLegacyIndexToCode() {
		for (Map.Entry<Integer, Integer> e : LEGACY_CODE_TO_INDEX.entrySet()) {
			assertEquals("index " + e.getValue(), (int) e.getKey(), yaml.indexToCode(e.getValue()));
		}
	}

	@Test
	public void yamlMetadata() {
		assertEquals("compact-qwerty", yaml.getId());
		assertEquals(16, yaml.getKeyCount());
		assertEquals(15, yaml.getMaxKey());
		assertTrue(yaml.detectsLetters());
		assertFalse(yaml.detectsDigits());
	}

	@Test
	public void spaceAndDigitsAreNotGeometryKeys() {
		// SPACE (index 0) and the numpad digits stay in Key.codeToNumber's fallback, not the geometry,
		// so isCompactQwertyLetter() reports exactly the 14 letter keys.
		assertFalse(yaml.hasKeyCode(KeyEvent.KEYCODE_SPACE));
		assertFalse(yaml.hasKeyCode(KeyEvent.KEYCODE_0));
		assertFalse(yaml.hasKeyCode(KeyEvent.KEYCODE_2));
		assertEquals(-1, yaml.codeToIndex(KeyEvent.KEYCODE_SPACE));
	}

	@Test
	public void builtInFallbackMatchesYaml() {
		// The registry is uninitialised here (no android Context), so getActive() returns the built-in
		// COMPACT_FALLBACK. It must be identical to the YAML, since it stands in before the YAML loads.
		Geometry fallback = GeometryRegistry.getActive();
		assertEquals(yaml.getId(), fallback.getId());
		assertEquals(yaml.getKeyCount(), fallback.getKeyCount());
		assertEquals(yaml.detectsLetters(), fallback.detectsLetters());
		for (int index = 0; index <= yaml.getMaxKey(); index++) {
			assertEquals("indexToCode " + index, yaml.indexToCode(index), fallback.indexToCode(index));
		}
		for (int code : LEGACY_CODE_TO_INDEX.keySet()) {
			assertEquals("codeToIndex " + code, yaml.codeToIndex(code), fallback.codeToIndex(code));
		}
	}
}
