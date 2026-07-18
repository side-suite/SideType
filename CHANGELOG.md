# Changelog

Notable changes to SideType. SideType is a fork of
[Traditional T9](https://github.com/sspanak/tt9) adapted to the Sidephone SP-01
Compact QWERTY tile. Versions follow the signed APKs on the
[Releases page](https://github.com/side-suite/SideType/releases).

## [2.1.1] — 2026-07-18

### Fixed
- **Pressing a key on its own now suggests the more common letter first.** Tapping
  **U/I** once offered "u" before "i", even though "i" is a whole word and far more
  common — and the same went for every other key. The order was simply the order the
  letters are printed on the physical key, because a lone key press never reaches the
  dictionary and so had nothing else to rank by. English and English (UK) now rank
  those letters by how common they are. The keycaps, ABC mode, and which key types a
  letter are all unchanged.

## [2.1] — 2026-07-17

### Added
- **A proper app icon.** SideType now has its own launcher icon — the Compact
  QWERTY keycap on a soft silver ground — so it's easy to spot among your apps
  instead of wearing the inherited Traditional T9 mark.
- **A way to support SideType.** A new **Support SideType** entry in **Settings →
  About** opens the project's GitHub Sponsors page. SideType stays free and asks
  for nothing on its own; this is only there if you'd like to chip in.

### Changed
- The project moved to the **SideSuite** organisation on GitHub. Existing installs
  keep updating (the old links redirect), and language-dictionary downloads now
  come from the new location.

## [2.0] — 2026-07-15

### Added
- **Offline voice input.** Press the 🎤 on the on-screen strip and talk — words
  appear as you speak. It runs entirely on the phone: **nothing you say is sent
  anywhere**, and it works with no signal at all. Powered by
  [Vosk](https://alphacephei.com/vosk/) (Apache-2.0).

  Each language needs a one-off **~40 MB voice model**, downloaded only after you
  say yes, with the real size shown up front. Manage them under **Settings →
  Voice input** — download, see what's on disk, delete what you don't want.

  Available for **English (US), English (UK), German, French and Spanish.**
  **Finnish, Norwegian and Danish are not supported** — Vosk publishes no model
  for them at any size, so there is nothing to ship. This is a real limitation,
  not an oversight; see `docs/adr/0001-on-device-asr-engine.md`. Swedish is on
  hold: its only model is 303 MB, seven times the others, with accuracy its
  authors haven't measured.
- **English (UK) dictionary.** Selectable alongside English, with British
  spellings ranked above their American twins, so mid-word prediction offers
  *colour*, *organise* and *centre* instead of quietly steering you American.
  American spellings still resolve as fallbacks. Marked `[beta]`.
- **Contractions type naturally.** Type `thats`, `dont`, `youre` and get
  *that's*, *don't*, *you're*. The apostrophe is now transparent to prediction,
  so you no longer need the hold-L detour. Holding L still types a literal `'`
  when you want one.
- **Manage added words** is now a top-level Settings entry, grouped by language,
  with a per-language **+** to add a word without leaving the screen.

### Changed
- **The keyboard is now arm64-only.** ⚠️ **Breaking.** This is what keeps the
  voice engine's native libraries from tripling the download — one architecture
  costs ~9 MB, all four cost ~35 MB. The SP-01 is arm64, so nothing changes for
  it, but SideType will **no longer install on 32-bit devices**. If you were
  running it on some other phone, 1.1.1 is the last version that will fit.
- **Download is ~44 MB**, up from ~32 MB — the voice engine's cost. The voice
  *models* are not bundled and never will be; they arrive only if you ask.
- **The on-screen strip has been rearranged** to make room for 🎤 without
  squeezing your suggestions. Emoji moved to the far left, and **the gear is
  gone — long-press the language chip (`EN` / `FI`) to open Settings.** The
  language chip now always shows, even with one language enabled. Suggestions
  keep exactly as much room as they had before voice existed.
- **The voice hotkey stays unbound** by default; every physical key on the tile
  already does something. Bind one under Settings → Voice input if you want it.

### Removed
- **The old cloud/Google voice path.** SideType used to be a thin wrapper around
  whatever speech service the phone provided — which, on the SP-01's minimal
  AOSP, is none, so the feature silently did nothing. It's replaced wholesale by
  the offline engine above. Voice input is now on-device or not at all.

### Fixed
- **Typing certain words could wipe the whole field.** On English, a word whose
  prefix was itself a dictionary word (the original report: typing `Cla` in a
  search box) could clear everything you'd typed. A stem was left pointing at a
  row that was never added, so the keyboard committed an empty string over your
  text. Inherited from upstream TT9.
- Suggestions are now ordered by how many keys you actually pressed.

## [1.1.1] — 2026-07-10

### Fixed
- **On-demand dictionaries now download.** After the 1.1 slim-down, any language
  that isn't one of the eight bundled dictionaries (Dutch, Italian, Polish, …)
  failed to load with a `SecurityException`: the app was missing the `INTERNET`
  permission, and the download URL pointed at a stale location. Both are fixed.
  Bundled languages such as English were never affected.

## [1.1] — 2026-07-10

### Added
- **Emoji & symbol drawer** — an on-screen picker with Emoji/Symbols tabs and
  categories. Because the drawer covers the text field, it has a **live preview
  bar** so you can still see what you're typing, plus haptic and visual feedback
  on every tap.
- **Bind symbols to keys** — the SYM + key bind picker now has an Emoji/Symbols
  toggle, so you can assign any symbol (`@`, `#`, `€`, `—`, …), not just emoji,
  to a key.

### Changed
- **Download size cut ~7× (219 MB → 32 MB).** Eight common dictionaries
  (English, German, French, Spanish, Finnish, Swedish, Norwegian, Danish) ship
  bundled; the other 20+ Latin-script languages download on demand the first
  time you pick them.
- **On-screen keyboard drag-to-resize now defaults off** — you type on the
  physical tile. Re-enable it in Settings if you want the on-screen keyboard.

### Fixed
- Emoji/symbol drawer no longer extends behind the system navigation bar.

### Removed
- Non-Latin-script dictionaries (Cyrillic, Greek, Arabic, Hebrew, CJK, etc.),
  which weren't typeable on the Latin Compact QWERTY tile anyway. SideType now
  focuses on Latin-script languages, which is what shrank the download.

### Notes
- Predictive-typing latency was profiled on the SP-01 hardware and confirmed
  well within budget, so no changes were needed there.

## [1.0] — 2026-07-08

- First public release. Predictive typing for the Sidephone SP-01 Compact QWERTY
  tile, with correct accented characters (å ä ö é ü ñ ç …) that the tile's
  English-only keyboards can't produce. Installs alongside your existing
  keyboard. All dictionaries bundled (hence the large ~219 MB download, slimmed
  down in 1.1).
