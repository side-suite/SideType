# 3. Voice models download on demand, pinned by md5

Date: 2026-07-15

## Status

Accepted.

## Context

Vosk acoustic models are ~40 MB per language and must not be bundled — that was
the whole point of choosing a small-model engine ([ADR 1](0001-on-device-asr-engine.md)).
They have to arrive over the network, on demand.

The existing on-demand **dictionary** mechanism is the obvious model to copy, and
it is the wrong one to copy wholesale:

- Dictionaries download **silently**, with no confirmation. That is defensible
  for a small zip and user-hostile for 40 MB.
- **Nothing verifies integrity today.** There is no digest code anywhere in
  `app/src/main`; bytes off the network are parsed as-is, with only HTTPS for
  transport integrity. `props.yml` carries a `hash`, but it is used solely for
  staleness checks.
- The languages screen shows **no size and no download state**, and deletion is
  bulk-only (`ItemTruncateAll` / `ItemTruncateUnselected`).

The SID-8 spike's catalog invented its sizes. Every one was wrong, and one was
wrong by 7×: it claimed Swedish was ~41 MB when the model is 303.5 MB. **A wrong
number in a consent prompt is a trust bug** — the user agreed to something other
than what happened.

Vosk publishes `https://alphacephei.com/vosk/models/model-list.json`, which
carries per model: `name`, `url`, exact byte `size`, **`md5`**, `type`
(small/big), and an **`obsolete`** flag. Everything a pinned catalog needs
already exists upstream; nothing needs inventing or guessing.

## Decision

**Pin the catalog** — name, md5 and exact size — checked into the repo, keyed by
**full locale tag**, and transcribed from `model-list.json` rather than
estimated. Verify the md5 after download; a mismatch discards the file and
errors.

The v1 catalog (all current, non-obsolete, `type: small`):

| Locale | Model | Size | md5 | Licence |
| --- | --- | --- | --- | --- |
| en-US | `vosk-model-small-en-us-0.15` | 41.2 MB | `09ab50ccd62b674cbaa231b825f9c1cb` | Apache-2.0 |
| en-GB | `vosk-model-small-en-gb-0.15` | 42.8 MB | `6afd611b04b2b47c129c3615dc502383` | Apache-2.0 |
| de | `vosk-model-small-de-0.15` | 46.5 MB | `4f21f92c0897b48287ef8839420608eb` | Apache-2.0 |
| fr | `vosk-model-small-fr-0.22` | 42.2 MB | `8873b1234503f6edd55f54bfff31cf3e` | Apache-2.0 |
| es | `vosk-model-small-es-0.42` | 39.8 MB | `2d5c94f9859a84881a0ef744738ebd31` | Apache-2.0 |

**Fetch from the alphacephei origin.** No mirroring: md5 pinning makes integrity
independent of the host, so re-hosting would buy only availability, at the cost
of ~213 MB of release assets and a step in the release process. If origin
availability becomes a problem, mirror to **GitHub Releases** (2 GB/file, so
Swedish fits) — the licences permit redistribution with attribution. The repo's
`downloads/` directory is **not** an option: GitHub caps files at 100 MB, which
Swedish alone exceeds.

**Consent before any download**, via an Activity — an IME cannot show a dialog,
and `RequestPermissionDialog` is the working precedent for exactly this. The
first mic press prompts with the real size; subsequent presses go straight to
listening. A Voice settings panel surfaces per-language size, state, download and
delete.

## Consequences

**Key by locale, not language.** `locale.getLanguage()` returns `"en"` for
en-GB, which would have silently handed British users the US acoustic model.
en-GB is a bundled SideType language (SID-7) and has its own model, so the
catalog keys on the full tag. This is a live trap: the spike had the bug.

**The `obsolete` flag must be honoured.** `vosk-model-small-en-us-0.3`,
`en-us-0.4`, `es-0.22` and `de-zamia-0.3` are all still downloadable and all
dead. Pinning a name without checking `obsolete` silently ships a stale model —
the same failure mode as SID-51 (dead revision pinning), in a second place.

**The pinned catalog goes stale by design.** When Vosk publishes a new small
model, the catalog does not notice; someone must re-read `model-list.json`. That
is the intended trade — a pin that drifts is worse than a pin that is explicit —
but it needs an owner.

**md5 is an integrity check, not an authenticity one.** It defends against
corruption and truncation over HTTPS, not against a compromised origin (which
could rewrite both zip and JSON). Given the models are third-party artefacts
fetched over TLS, this is proportionate; it is not a signature.

**Swedish is excluded from v1** (303.5 MB, WER published as "TBD") and falls into
the same graceful "no model for this language" path as Finnish, Norwegian and
Danish. Unlike them, it is deferred pending measurement rather than impossible.
