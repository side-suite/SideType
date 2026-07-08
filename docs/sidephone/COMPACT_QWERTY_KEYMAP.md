# Sidephone Compact QWERTY — Hardware Key Map & Fork Spec

Reverse-engineered from device `SP01_FE_GE` (serial `SP01GE260600728`), Android 12 / API 31,
MediaTek. Captured live with `adb shell getevent -lt` on 2026-07-08.

## The tile

- Physical keypad tile reports as Linux input device **`gxa535_keyboard`** (`/dev/input/event3`,
  bus 0x0018).
- It uses the stock **`/system/usr/keylayout/Generic.kl`** + `Generic.kcm` — no custom key layout.
- **Each physical button emits the keycode of its LEFT glyph only.** The second (right) letter of a
  pair, and all accented characters (å ä ö é ü ñ …), have **no hardware keycode**. They exist only in
  software. The tile's full hardware capability set is exactly the 14 left-glyph letters + specials —
  confirmed via `getevent -i /dev/input/event3`.
- This is a BlackBerry SureType-style *ambiguous* layout: 2 candidate letters per key, resolved by a
  predictive engine. Structurally identical to what T9 does (just 2-per-key instead of 3).

## Definitive button → keycode → letters map

Letter keys (`gxa535_keyboard`, all standard Android letter keycodes):

| Button | Linux key      | Android KEYCODE   | int | Letter group (English) |
|--------|----------------|-------------------|-----|------------------------|
| Q W    | KEY_Q          | KEYCODE_Q         | 45  | q w                    |
| E R    | KEY_E          | KEYCODE_E         | 33  | e r                    |
| T Y    | KEY_T          | KEYCODE_T         | 48  | t y                    |
| U I    | KEY_U          | KEYCODE_U         | 49  | u i                    |
| O P    | KEY_O          | KEYCODE_O         | 43  | o p                    |
| A S    | KEY_A          | KEYCODE_A         | 29  | a s                    |
| D F    | KEY_D          | KEYCODE_D         | 32  | d f                    |
| G H    | KEY_G          | KEYCODE_G         | 35  | g h                    |
| J K    | KEY_J          | KEYCODE_J         | 38  | j k                    |
| L      | KEY_L          | KEYCODE_L         | 40  | l                      |
| Z X    | KEY_Z          | KEYCODE_Z         | 54  | z x                    |
| C V    | KEY_C          | KEYCODE_C         | 31  | c v                    |
| B N    | KEY_B          | KEYCODE_B         | 30  | b n                    |
| M      | KEY_M          | KEYCODE_M         | 41  | m                      |

Function / modifier keys:

| Button          | Linux key       | Android KEYCODE       | int | Intended role                    |
|-----------------|-----------------|-----------------------|-----|----------------------------------|
| SPACE (0)       | KEY_SPACE       | KEYCODE_SPACE         | 62  | space / accept top candidate     |
| Backspace ⌫     | KEY_BACKSPACE   | KEYCODE_DEL           | 67  | delete                           |
| Enter ↵         | KEY_ENTER       | KEYCODE_ENTER         | 66  | enter / commit                   |
| Shift (aA #)    | KEY_LEFTSHIFT   | KEYCODE_SHIFT_LEFT    | 59  | case shift (abc/Abc/ABC)         |
| SYM (* +)       | KEY_LEFTALT     | KEYCODE_ALT_LEFT      | 57  | symbol layer                     |
| Arrows ▲▼       | KEY_LEFTCTRL    | KEYCODE_CTRL_LEFT     | 113 | candidate scroll / cursor        |

Top navigation/call row (route through `gxa535_keyboard`, useful as global hotkeys — NOT text input):

| Button         | Linux key          | Android role            |
|----------------|--------------------|-------------------------|
| 📞 Call (green) | KEY_PHONE          | KEYCODE_CALL            |
| — (left)       | KEY_BACK           | Back                    |
| ● (center)     | KEY_HOMEPAGE       | Home                    |
| — (right)      | KEY_APPSELECT      | Recent apps             |
| ✖ End (red)    | KEY_PICKUP_PHONE   | call control / endcall  |

## Why the fork is clean

- **JakeType** (`com.sidephone.jaketype/.JakeTypeService`) is a normal Android IME → a forked TT9 IME
  registers into the identical slot. JakeType internally is an AOSP-LatinIME-style Kotlin app:
  `PredictionEngine`, `DictionaryManager`, `ContactsDictionary`, `UserDictionary`, `CandidateView`,
  `handleLetterKey`/`handleBackspace`, shipping one `en_US_wordlist.combined` (AOSP `.combined`
  format). English-only, no ÅÄÖ. That's the whole gap we're filling.
- **TT9** already has 48 languages, each defined as data: a `.yml` layout mapping keys→characters +
  a frequency wordlist + an n-gram model. Finnish (`Finnish.yml` → `fi-utf8.csv` + `fi-ngrams.zip`)
  already includes å ä ö by *riding them along* on nearby keys (key 2 = `[a,b,c,ä,å]`, key 6 =
  `[m,n,o,ö]`). We reuse that trick, just remapped onto qwerty keys.

## The fork: what changes in TT9

1. **New input mode "Compact QWERTY"** — listen for the 14 letter KEYCODEs above (plus the function
   keys) instead of the numeric keypad. TT9 already has a configurable hardware-key → logical-key
   layer; extend its logical-key count from 12 (numpad) to the 14 qwerty keys.
2. **New per-language layout tables** mapping the 14 keys → letter groups. For Latin scripts this is
   natural (qwerty pairs + accents riding on their base key: å/ä on the A key, ö on the O key, é on
   E, ü on U, ñ on B/N key, ç on C key, …). Non-Latin scripts (Russian, Greek, Arabic, Thai, CJK…)
   need a bespoke 14-key assignment — engine + dictionaries still reuse unchanged, only the layout
   table is new, so they can land incrementally.
3. **Modifiers**: map SHIFT→case cycling (TT9 already has this), ALT(SYM)→symbol layer, CTRL(arrows)
   →candidate navigation. Reuse TT9's multi-tap so double-press of a key forces the 2nd/3rd letter.
4. **On-screen**: keep TT9's suggestion/candidate bar; the drawn numpad can be hidden since input is
   physical.

## License note

TT9 (sspanak/tt9) is copyleft (GPL-family). A fork published for the Sidephone community must stay
open-source under the same license — fine for this project, just can't be made proprietary.

## Reproduce the capture

```
adb shell getevent -lt            # then press each button once, read the KEY_* names
adb shell getevent -i /dev/input/event3   # full hardware capability set of the tile
```
