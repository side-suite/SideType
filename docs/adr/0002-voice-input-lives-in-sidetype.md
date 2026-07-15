# 2. Voice input lives in SideType, not a companion app

Date: 2026-07-15

## Status

Accepted. Supersedes the planned "SideVoice" companion app
(`fi.palonkorpi.sidevoice`), which was cancelled before any code was written.

## Context

Voice input was originally specced as a **separate app**: a standalone
`RecognitionService` that SideType would bind to explicitly via
`SpeechRecognizer.createSpeechRecognizer(context, ComponentName)`. The decisive
argument was size — Vosk's native libraries were measured at **+37.6 MB**, taking
SideType from 33.3 MB to 70.9 MB and more than doubling a *keyboard* for an
optional feature.

**That number was wrong.** It was the cost of **all four ABIs**. Measured from
the AAR:

| ABI | `libvosk.so` | JNA |
| --- | --- | --- |
| **arm64-v8a** | **8.5 MB** | 168 KB |
| armeabi-v7a | 7.9 MB | 120 KB |
| x86 | 9.2 MB | 120 KB |
| x86_64 | 9.2 MB | 116 KB |

The SP-01 is arm64-v8a. The spike simply set no `abiFilters`, so it shipped four
ABIs to a device that can use one. **The real cost is ~8.7 MB, not 37.6 MB** —
SideType lands around 42 MB, not 70.9 MB.

With the size argument reduced to ~9 MB, the remaining case for a separate app —
an engine-swap seam, and serving other keyboards — did not justify a new repo, a
new release pipeline, an Obtainium entry, an IPC boundary, and a
`RecognitionService` written from scratch (the spike had none). SideType targets
one device; it does not need to serve other keyboards.

The consent prompt cuts the same way. A ~40 MB download needs explicit consent,
and SideType can ask for it directly: `PopupBuilder.showFromIme` attaches a
dialog to the IME's own window token, as `AddWordDialog` already does. A
`RecognitionService` bound from another process has no such window, so it could
only prompt by throwing an Activity over whatever app the user is typing in.

## Decision

**Vosk runs inside SideType**, behind an `arm64-v8a` `abiFilter`.

**Vosk is the only backend.** The platform `SpeechRecognizer` path is deleted:
`SpeechRecognizerSupportLegacy`, `SpeechRecognizerSupportModern`,
`VoiceInputPickerActivity`, and the Google online/offline probes. The device has
no Google speech service, so this code was dead on the SP-01 and existed only to
serve hardware SideType does not target.

The usual objection — permanent divergence from upstream TT9 — does not apply
here. The `upstream` remote (`sspanak/tt9`) is configured but **has never been
fetched or merged**: there are zero merges in the history and `upstream/master`
does not resolve locally. The fork has never taken an upstream change.

## Consequences

**SideType is arm64-only.** Devices without an arm64-v8a ABI can no longer
install it. Acceptable: the SP-01 is the target and SideType is not intended to
run elsewhere. This is the first change to make that a hard requirement rather
than a preference.

**`isAvailable()` must mean "the catalog has a model for this language"**, not
"a model is on disk". Models download on demand, so a disk-based check deadlocks:
no model → `PreferenceVoiceInputHotkey.populate()` hides the control → the
download that would unhide it can never be triggered. (The SID-8 spike dodged
this by forcing `isAvailable()` to `true` unconditionally — do not carry that
over.)

**Voice input needs its own trigger.** The SP-01 defaults to `LAYOUT_TRAY`, which
renders no soft keys, and `hotkey_voice_input` defaults to `0` (unassigned) — so
voice had no trigger at all. A mic button joins the four existing tray quick
actions, shown only when the catalog covers the current language. The hotkey
stays unassigned; every physical key on the tile is already spoken for.

**Merging from upstream TT9 is now materially harder.** Deleting the platform
recognizer is a permanent divergence. This is a deliberate trade, made cheap only
because the fork has never merged upstream. If that ever changes, this ADR is the
reason the conflict exists.

See [ADR 1](0001-on-device-asr-engine.md) for why the engine is Vosk.
