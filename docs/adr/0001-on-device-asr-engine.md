# 1. Vosk is the on-device ASR engine

Date: 2026-07-15

## Status

Accepted.

## Context

SideType's voice input was only ever a thin wrapper around Android's platform
`SpeechRecognizer`. The SP-01 runs a minimal AOSP with no Google speech service,
so the feature self-hid and did nothing. A real backend was required, under two
product constraints: **on-device only** (no cloud STT — privacy-first, and
non-negotiable), and **models downloaded on demand**, never bundled.

The binding constraint is the hardware. The SP-01 is a **MediaTek MT6761
(Helio A22): 4× Cortex-A53, 3.97 GB RAM** — entry-level 2018 silicon. Measured
on the device (`SP01GE260600728`):

| Engine | Cold load | Resident | Verdict |
| --- | --- | --- | --- |
| **Vosk small (~40 MB)** | < 1 s | small | ✅ workable |
| Omnilingual ASR 300M (sherpa-onnx) | **15.1 s** | **783 MB** | ❌ unusable |
| Parakeet-TDT-0.6b-v3 (estimated) | ~30 s | ~1.2–1.5 GB | ❌ worse |

Transformer-class ASR can be neither loaded quickly nor held warm on this SoC.
Only Kaldi-class small models are viable. **Do not re-litigate without new
hardware.**

Alternatives investigated and ruled out:

- **Whisper** — streaming on Android runs ~5–7 s per 1 s of audio and compounds
  into an ANR; batch-only in practice. Stock `tiny` is ~59 WER on Swedish, worse
  on Finnish.
- **Parakeet-TDT-0.6b-v3** — covers Finnish (13.2% WER) and Swedish (15.1%),
  CC-BY-4.0, but 600M params is far past the MT6761's ceiling.
- **Omnilingual ASR 300M** — Apache-2.0, 1600 languages including Finnish, but
  15.1 s cold load and 783 MB resident. Also: sherpa-onnx's **Android binding
  exposes no `language` hint** (it exists in C++ only), so it blind-guesses
  across 1600 languages and lands on the wrong script for quiet audio.

## Decision

Use **Vosk 0.3.47** (Apache-2.0). Small Kaldi models, fully offline, true
streaming partial results.

Verified on real SP-01 hardware (SID-8, 2026-07-15): streaming latency
"workable", small-model accuracy "not mega-bad" — acceptable for short IME
dictation. `lib/arm64-v8a/libvosk.so` + `libjnidispatch.so` package and load
correctly.

## Consequences

**Finnish, Norwegian and Danish have no voice input, and cannot.** Vosk publishes
**no model at any size** for them — verified against `model-list.json` on
2026-07-15, not inferred. Finnish is a first-class SideType language, so this is
a real, accepted product limitation rather than an oversight. Its realistic path
is a second ASR backend once hardware or model sizes allow; that would be a new
ADR superseding this one.

**Language coverage is dictated by the catalog, not by SideType.** Of the bundled
languages, small models exist for `en-US`, `en-GB`, `de`, `fr`, `es`. Swedish's
only model is 303.5 MB (7× the others) with a WER that Vosk itself publishes as
"TBD" — deferred pending measurement on the SP-01, since 289 MB/303 MB sits
between the "workable" and "unusable" rows of the table above and has never been
tested.

**R8 will break the native load, silently.** Vosk binds to `libvosk.so` through
**JNA, which resolves classes reflectively from native code**. R8 renames them
(58 JNA classes were mangled) and the load fails with `UnsatisfiedLinkError`.
Release builds must keep `org.vosk.**` and `com.sun.jna.**`. Critically,
`compileFullReleaseJavaWithJavac` **does not run R8** — this class of bug is
invisible to a compile-only check and only a full `assembleFullRelease` plus an
on-device run catches it. This cost a debug cycle during SID-8; it will cost
another for any future native/reflective dependency.

See also [ADR 2](0002-voice-input-lives-in-sidetype.md), which decides where this
engine runs.
