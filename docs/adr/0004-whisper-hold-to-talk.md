# 4. Whisper replaces Vosk, as hold-to-talk

Date: 2026-11-11

## Status

Accepted. Supersedes [0001](0001-on-device-asr-engine.md).

## Context

0001 chose Vosk because it was the only engine that could *stream* on the SP-01's Helio A22, and
ruled Whisper out as "batch-only in practice". That finding still stands. What changed is the
requirement: live word-by-word text is no longer wanted, and Vosk's cost for it was high. Its small
models return lowercase text with no punctuation, and it has no model at all for several languages
SideType cares about (Turkish was never added; Finnish, Norwegian and Danish do not exist).

A batch Whisper model was tried on a real SP-01 through another keyboard and judged usable.

## Decision

Use **whisper.cpp v1.9.5** (MIT), compiled from source by the app's own CMake build
(`app/src/main/cpp`), with the stock multilingual **Whisper base, 8-bit quantised** model
(`ggml-base-q8_0.bin`, 81.8 MB) downloaded on demand and verified by SHA-256.

Voice input is **hold-to-talk**: the microphone is open while the voice hotkey (default: hold SYM)
is down, and the recording is transcribed in one pass when the key is released. Vosk, its JNA
dependency and the per-language model catalogue are removed entirely.

On-device is the default and needs no account. **Online transcription is opt-in**: when the user
enters an API key in the voice settings, the recording is uploaded to an OpenAI-compatible
transcription endpoint (`RemoteWhisperClient`) instead, because the SP-01 is slow enough that a
server is markedly faster and more accurate. If the upload fails for any reason other than a
rejected key, the local model transcribes the recording when it is on disk. Without a key, nothing
is sent anywhere. This relaxes the "on-device only, non-negotiable" rule of 0001 to "on-device
unless the user supplies a key".

## Consequences

- One model covers every Whisper language; the keyboard language is passed as the language hint.
- No partial results. Text appears after release with Whisper's punctuation. Its capitals go
  through the keyboard's own text-case rules (Shift state, capital at the start of a sentence), so
  dictated text is cased like typed text.
- Recordings are capped at 30 s, the size of one Whisper window.
- To make short utterances fast enough, the encoder window is limited to the recorded length plus a
  margin, with a floor of 500 frames (`audio_ctx`, see `WhisperSpeechRecognizer`). The stock model
  was trained on full 30 s windows and repeats itself when the window is too short: at a floor of
  300 a two-second phrase was transcribed twice. Eleven test phrases matched the full-window
  result for repetition at 400 and above. **Speed of the base model on the SP-01 has not been
  measured.** If it is too slow, `ggml-tiny-q8_0.bin` is a three-value change in
  `WhisperModelCatalog`.
- The model is loaded while the user speaks and kept in memory for three minutes after the last use.
- The build now needs the Android NDK and CMake, and downloads the whisper.cpp source (pinned by
  hash) the first time it configures.
