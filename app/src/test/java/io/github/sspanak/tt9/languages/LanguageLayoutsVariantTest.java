package io.github.sspanak.tt9.languages;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * SID-122: the nested {@code layouts.{geometry}.{variant}} format parses correctly, a flat {@code layout:}
 * stays byte-identical (mapped to compact-qwerty/default), and a sparse delta variant (QWERTZ) overlays
 * onto its base. Runs on a plain JVM via the package-private {@link LanguageDefinition#parseDefinitions}.
 */
public class LanguageLayoutsVariantTest {
	private static final String COMPACT = GeometryRegistry.COMPACT_QWERTY;

	private static List<String> row(String... chars) {
		return Arrays.asList(chars);
	}

	@Test
	public void flatLayoutIsCompactDefault() {
		String[] lines = {
			"locale: en",
			"dictionaryFile: en-utf8.csv",
			"layout:",
			"  - [SPECIAL]",
			"  - [PUNCTUATION]",
			"  - [q, w]",
			"  - [e, r]",
		};
		LanguageDefinition def = LanguageDefinition.parseDefinitions(lines).get(0);

		// Back-compat: the flat layout is registered as (compact-qwerty, default), and the legacy field
		// still holds it, so both readers (NaturalLanguage + the "16 keys" filter) are unchanged.
		assertEquals("default", def.getDefaultVariant(COMPACT));
		assertTrue(def.hasGeometry(COMPACT));
		assertEquals(row("q", "w"), def.getLayout(COMPACT, "default").get(2));
		assertEquals(row("q", "w"), def.getLayout(COMPACT, null).get(2));   // null -> default
		assertEquals(row("e", "r"), def.getLayout(COMPACT, "default").get(3));
		assertEquals(def.layout.size(), def.getLayout(COMPACT, "default").size());
	}

	@Test
	public void qwertzDeltaOverlaysGermanBase() {
		// A 16-key qwerty base plus a sparse QWERTZ delta swapping keys 4 and 12 (the runbook's example).
		ArrayList<String> lines = new ArrayList<>(Arrays.asList(
			"locale: de-DE",
			"dictionaryFile: de-utf8.csv",
			"layouts:",
			"  compact-qwerty:",
			"    qwerty:"
		));
		String[][] base = {
			{"SPECIAL"}, {"PUNCTUATION_DE"}, {"q", "w"}, {"e", "r"}, {"t", "y"}, {"u", "i"},
			{"o", "p"}, {"a", "s"}, {"d", "f"}, {"g", "h"}, {"j", "k"}, {"l"},
			{"z", "x"}, {"c", "v"}, {"b", "n"}, {"m"}
		};
		for (String[] keyChars : base) {
			lines.add("      - [" + String.join(", ", keyChars) + "]");
		}
		lines.add("    qwertz:");
		lines.add("      4: [t, z]");
		lines.add("      12: [y, x]");

		LanguageDefinition def = LanguageDefinition.parseDefinitions(lines.toArray(new String[0])).get(0);

		// Variants are discovered in declaration order; the first (qwerty) is the default/base.
		assertEquals(Arrays.asList("qwerty", "qwertz"), def.getVariants(COMPACT));
		assertEquals("qwerty", def.getDefaultVariant(COMPACT));

		// Base variant is the plain positional list.
		assertEquals(row("t", "y"), def.getLayout(COMPACT, "qwerty").get(4));
		assertEquals(row("z", "x"), def.getLayout(COMPACT, "qwerty").get(12));

		// Delta variant overlays only keys 4 and 12; everything else tracks the base.
		ArrayList<ArrayList<String>> qwertz = def.getLayout(COMPACT, "qwertz");
		assertEquals(row("t", "z"), qwertz.get(4));
		assertEquals(row("y", "x"), qwertz.get(12));
		assertEquals(row("q", "w"), qwertz.get(2));    // unchanged
		assertEquals(row("u", "i"), qwertz.get(5));    // unchanged
		assertEquals(row("m"), qwertz.get(15));        // unchanged
		assertEquals(16, qwertz.size());

		// Nested-only definition still backfills the legacy `layout` (from the default variant) so the
		// "== 16 keys" filter keeps it.
		assertEquals(16, def.layout.size());
		assertEquals(row("t", "y"), def.layout.get(4));
	}

	@Test
	public void multipleDefinitionsParseIndependently() {
		String[] lines = {
			"locale: en",
			"layout:",
			"  - [SPECIAL]",
			"  - [PUNCTUATION]",
			"  - [q, w]",
			"---",
			"locale: fi",
			"layout:",
			"  - [SPECIAL]",
			"  - [PUNCTUATION]",
			"  - [a, b, c]",
		};
		ArrayList<LanguageDefinition> defs = LanguageDefinition.parseDefinitions(lines);
		assertEquals(2, defs.size());
		assertEquals("en", defs.get(0).locale);
		assertEquals("fi", defs.get(1).locale);
		assertEquals(row("q", "w"), defs.get(0).getLayout(COMPACT, null).get(2));
		assertEquals(row("a", "b", "c"), defs.get(1).getLayout(COMPACT, null).get(2));
		assertFalse(defs.get(1).getLayout(COMPACT, null).get(2).equals(defs.get(0).getLayout(COMPACT, null).get(2)));
	}
}
