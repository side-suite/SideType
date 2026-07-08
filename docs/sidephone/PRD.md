# PRD — Compact QWERTY Predictive Keyboard for Sidephone (TT9 fork)

**Status:** Draft / in execution · **Owner:** Oliver · **Date:** 2026-07-08
**Codename:** working name "SideType" (final name TBD)

## 1. Summary

Fork the open-source [TT9](https://github.com/sspanak/tt9) predictive keyboard into an Android IME
for the **Sidephone Compact QWERTY** keypad tile, providing multilingual predictive text (all 48
languages TT9 ships, incl. **ÅÄÖ** and other diacritics) as a free, open-source replacement for the
stock **JakeType** IME, which is **English-US only**. Release it to the Sidephone community.

## 2. Problem

The Compact QWERTY tile is a BlackBerry SureType-style layout (2 letters per key, resolved by
prediction). Its only IME, JakeType, supports **English (US) only** and cannot type Finnish or any
language needing å/ä/ö or other accented characters. Non-English Sidephone users effectively can't
use the tile for their own language.

## 3. Goals

- G1. Predictive text on the Compact QWERTY tile for **Finnish** (primary validation language) with
  correct å/ä/ö.
- G2. Support **all Latin-script languages TT9 has** (English, Finnish, Swedish, German, French,
  Spanish, Italian, Estonian, Polish, Turkish, …) via reusable per-language layout tables.
- G3. Architecture that supports **all 48 TT9 languages** incl. non-Latin (added incrementally).
- G4. Ship as an installable APK; **open source**, published for the Sidephone community. (TT9 is
  Apache-2.0, so we're free to choose the license; we'll stay open-source with attribution.)
- G5. Typing feel comparable to or better than JakeType (fluid candidate selection, case, symbols).

## 4. Non-goals

- Not building a full unambiguous QWERTY / not a from-scratch engine.
- Not reflashing firmware or modifying the tile hardware / `.kl` files (works with stock keycodes).
- Not shipping to Google Play initially (sideload / community distribution first).
- Not reverse-engineering or reusing JakeType code (JakeType is only a behavioral reference).

## 5. Users & context

- Sidephone SP-01 owners who need a non-English (or accent-requiring) language on the Compact QWERTY.
- Primary tester: Oliver (Finnish). Device on hand: `SP01_FE_GE`, Android 12 / API 31.

## 6. Background — hardware facts (verified on device)

See `COMPACT_QWERTY_KEYMAP.md` for the full table. Key facts:

- Tile = Linux input device `gxa535_keyboard`, stock `Generic.kl`.
- **Each button emits only its LEFT glyph's standard Android keycode.** 2nd letters and all accents
  have no hardware code — resolved purely in software by the IME.
- 14 letter keys: `Q E T U O / A D G J L / Z C B M`. Function keys: SPACE, DEL, ENTER, SHIFT_LEFT
  (aA), ALT_LEFT (SYM), CTRL_LEFT (▲▼ arrows). Top row = Call/Back/Home/Recents/End.
- JakeType is a normal IME (`com.sidephone.jaketype/.JakeTypeService`) → our fork registers into the
  same slot and the user switches to it like any keyboard.

## 7. Functional requirements

- FR1. Register as an Android IME selectable in system keyboard settings.
- FR2. In "Compact QWERTY" input mode, consume the 14 letter keycodes + modifiers from the tile.
- FR3. Predictive (T9-style) disambiguation of each key's letter group against the active language's
  dictionary + n-gram model; show a candidate bar; SPACE/ENTER commit; a key re-press (multi-tap)
  forces the 2nd/3rd letter.
- FR4. Accents ride along on their base key per language (å/ä→A key, ö→O key, é→E, ü→U, ñ→B/N, ç→C…);
  no dedicated hardware keys needed.
- FR5. SHIFT (aA) cycles case (abc → Abc → ABC). ALT (SYM) opens a symbol layer. Arrows (CTRL) move
  through candidates / cursor. DEL deletes, ENTER commits/newline.
- FR6. Language switching in-app; ship downloadable/bundled dictionaries (reuse TT9's).
- FR7. On-screen suggestion/candidate strip visible; drawn on-screen numpad hidden (input is
  physical).

## 8. Technical approach (fork of TT9)

Reused unchanged: prediction engine, n-gram predictor, all 48 dictionaries, IME scaffolding,
settings, multi-tap/case logic. Changes:

1. **New input mode "Compact QWERTY"**: extend TT9's hardware-key → logical-key layer from the 12-key
   numpad to the 14 qwerty keycodes; add the modifier bindings above.
2. **New per-language layout tables** mapping the 14 keys → letter groups (Latin scripts first;
   non-Latin bespoke, incremental). Data-only, mirroring TT9's existing `.yml` `layout:` format.
3. Hide on-screen numpad; keep candidate strip. Rebrand app id/name so it installs alongside TT9.

## 9. Milestones

- **M0 — Env & fork.** Build tooling ready; TT9 cloned; stock TT9 APK builds & installs on device.
- **M1 — Compact QWERTY English.** New input mode + English 14-key layout; predictive typing works on
  the tile end-to-end (verified on device).
- **M2 — Finnish + ÅÄÖ.** Finnish layout with ride-along å/ä/ö validated by real typing.
- **M3 — Latin languages.** Swedish, German, French, Spanish, Estonian, etc. layout tables.
- **M4 — UX polish.** Symbol layer, case cycling, candidate navigation, settings, hide numpad.
- **M5 — Release.** Rebrand, license/attribution, README, publish repo for the Sidephone community.
- **M6 (stretch) — Non-Latin.** Russian/Greek/etc. 14-key layouts.

## 10. Success metrics

- Type a Finnish sentence with å/ä/ö on the tile with prediction, no touchscreen.
- ≥3 Latin languages selectable and working.
- APK installs cleanly on a stock SP-01; community can build from source.

## 11. Risks & mitigations

- **TT9's 12-key assumption is baked deep** → first spike is the input-mode/logical-key extension;
  if too tangled, isolate a thin key-adapter layer. (Decidable early in M1.)
- **Modifier semantics** (does holding SHIFT/ALT+letter emit combos?) → verify with `getevent` if a
  key behaves unexpectedly. Baseline capture shows plain keycodes.
- **Non-Latin layout design** → deferred to M6; does not block Latin-language delivery.
- **Copyleft license** → keep fork open source; preserve attribution/NOTICE.

## 12. Licensing

TT9 is **Apache-2.0** (permissive) — confirmed in M0. We may license the fork as we like; we'll keep
it open source and carry TT9's LICENSE/NOTICE + attribution forward.

## 13. Out of scope (now)

Play Store release, swipe typing, GIFs/stickers, firmware changes, non-Sidephone hardware.
