# Changelog

Notable changes to SideType. SideType is a fork of
[Traditional T9](https://github.com/sspanak/tt9) adapted to the Sidephone SP-01
Compact QWERTY tile. Versions follow the signed APKs on the
[Releases page](https://github.com/oliverpalonkorp/SideType/releases).

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
