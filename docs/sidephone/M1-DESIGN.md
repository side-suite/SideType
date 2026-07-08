# M1 Design — Compact QWERTY input mode + English layout

## Encoding scheme (the key idea)

TT9 stores each word as a `digitSequence`: a String of single-char key tokens. Keep that, and encode
**key index `k` → token `(char)('0' + k)`**:

- keys 0–9  → `'0'..'9'`  (unchanged → fully backward compatible with numpad languages)
- keys 10–14 → `':' ';' '<' '=' '>'` (ASCII 58–62)

Because decode is `char - '0'` (already used in `InputMode.getFirstKey()` line 172), extended tokens
decode correctly for free. Only *encoding* sites that append a key as its decimal string
(`sequence + intKey` → `"10"`) must switch to appending the char. (Confirm full list from the
Explore inventory before editing.)

## Logical key assignment (Compact QWERTY → engine key index)

Reserve 0/1 for space/punctuation as upstream does; letters on indices 2–15:

| Physical key | KEYCODE | idx | letters |
|---|---|---|---|
| Q W | KEYCODE_Q (45) | 2 | q w |
| E R | KEYCODE_E (33) | 3 | e r |
| T Y | KEYCODE_T (48) | 4 | t y |
| U I | KEYCODE_U (49) | 5 | u i |
| O P | KEYCODE_O (43) | 6 | o p |
| A S | KEYCODE_A (29) | 7 | a s |
| D F | KEYCODE_D (32) | 8 | d f |
| G H | KEYCODE_G (35) | 9 | g h |
| J K | KEYCODE_J (38) | 10 | j k |
| L   | KEYCODE_L (40) | 11 | l |
| Z X | KEYCODE_Z (54) | 12 | z x |
| C V | KEYCODE_C (31) | 13 | c v |
| B N | KEYCODE_B (30) | 14 | b n |
| M   | KEYCODE_M (41) | 15 | m |

Function keys: SPACE=KEYCODE_SPACE→commit/space; DEL=backspace; ENTER=commit; SHIFT_LEFT=case cycle;
ALT_LEFT(SYM)=symbol layer (later); CTRL_LEFT(▲▼)=candidate nav (later).

## Layout derivation (works for ALL Latin languages, no per-language yml)

Given a language's character set (already known to TT9), build the 14-key groups by rule:
1. Each of the 26 base letters → its fixed QWERTY key above.
2. Every extra/accented char → rides along on the key of its **base letter** (strip diacritics via
   `java.text.Normalizer` NFD + remove combining marks; plus explicit map for ø→o, ł→l, ð→d, þ→t,
   ß→s, æ→a, œ→o, etc.).
   - Finnish/Swedish/German: ä,å→A key (idx 7), ö→O key (idx 6), ü→U key (idx 5).
   - French: é,è,ê→E; ç→C; à,â→A; ù,û→U; ï,î→U/I.
3. Non-Latin scripts (Cyrillic/Greek/…) don't fit this rule → deferred to M6 (bespoke layout).

Dictionaries are reused UNCHANGED — sequences are recomputed on-device at load time from the new
layout (`getDigitSequenceForWord`), so no dictionary/DB rebuild.

## Minimal change set (pending Explore inventory confirmation)

1. `Key.codeToNumber` — map the 14 letter keycodes → idx 2–15 (Compact-QWERTY mode).
2. `Key.isNumber` — treat those 14 keycodes as typing keys.
3. `Key.numberToCode` — inverse (for hotkeys/settings; on-screen keypad unused in tray mode).
4. `NaturalLanguage.setLayout` — lift `key <= 9` cap to layout size.
5. `NaturalLanguage.generateCharacterKeyMap` — single-char token `(char)('0'+key)` for key>9.
6. Sequence *append* sites that use `+ intKey` decimal (e.g. ModeWords ~L431) → append char.
7. `db/words/DictionaryLoader.java:278` — lift `key 2..9` loop to maxKey.
8. New Compact-QWERTY layout provider (derivation rule above) + a way to activate it (setting,
   default ON for Sidephone).
9. UI: use existing tray layout (no on-screen numpad work).

## Validation

Enable IME, open a text field, type on the tile: `t-e-s-t` (keys T,E,A/S,T) → predicts "test".
Then Finnish (M2): `k-i-s-s-a` etc. with ä/ö riding along.
