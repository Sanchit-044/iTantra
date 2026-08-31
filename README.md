# iTantra — core engine/service layer

Offline voice communication core for ISRO SIH PS 26173. Kotlin, Android, **no UI**.

This repository contains only the engine and service layer: STT, TTS, transport,
encryption, alert handling and diagnostics, each behind an interface that a separate
frontend can bind to later. There are no Activities, no Compose screens and no
`android.app.Activity` subclasses anywhere.

**Language scope: Hindi, Tamil, Bengali.** English is deliberately absent and must not
be added.

---

## Read this first

Two findings changed the shape of this work. Both were escalated rather than guessed
at, and both are now decided:

1. **[docs/STT-BACKEND.md](docs/STT-BACKEND.md)** — Vosk publishes no Tamil and no
   Bengali acoustic model, so it could serve only one of the three required languages.
   **Vosk has been dropped**; IndicWav2Vec CTC on ONNX Runtime is now the sole STT
   backend. This was the one finding that changed the architecture.
2. **[docs/MEASUREMENTS.md](docs/MEASUREMENTS.md)** — which numbers are measured, which
   are estimates, and which cannot be produced yet. **Nothing has been measured**: there
   are no models, no corpus and no target device. No figure here is presented as
   measured.

---

## Modules

| Module | What it is | Where | Status |
|---|---|---|---|
| B1 | STT engine wrapper | `core/…/stt`, `android/…/stt` | `OnnxCtcSttEngine` complete; endpointer tested; needs models |
| B2 | STT evaluation harness | `core/…/eval`, `harness/…/SttEvaluationTest` | Scoring/reporting complete and tested; needs corpus + models |
| B3 | TTS engine wrapper | `core/…/tts`, `android/…/tts` | Normalisation, chunking, pipelining complete and tested; needs models |
| B4 | Round-trip harness | `harness/…/RoundTripTest` | Written; runs on device once models exist |
| B5 | Transport layer | `core/…/transport`, `core/…/crypto`, `android/…/transport` | Complete and tested apart from on-radio behaviour |
| B6 | Alert handling | `core/…/alert`, `android/…/alert` | Complete and tested |
| B7 | Diagnostics | `core/…/diag`, `android/…/diag` | Complete |

## Project layout

```
core/        Pure Kotlin/JVM. Every piece of logic that can be device-independent.
             Builds and tests with no Android SDK. 133 unit tests.
android/     Android library. Platform bindings only: ONNX Runtime, AudioRecord,
             AudioTrack, AudioManager, WifiP2pManager, Bluetooth, Keystore.
harness/     Headless instrumented tests for B2 and B4. No UI, no Activity.
models-pack/ Install-time asset pack holding the bundled models (App Bundle).
```

The split is deliberate. Anything that can be tested without a handset lives in `core`,
which is why the endpointer, packet codec, crypto, text normalisation, clause chunking,
WER scoring, channel arbitration and alert sequencing all have real unit tests rather
than being verified by hand on a device.

## Building

```bash
./gradlew :core:test                     # 133 tests, no Android SDK required
./gradlew :android:assembleDebug         # needs ANDROID_HOME
./gradlew :harness:connectedAndroidTest  # needs a connected device + models
```

`settings.gradle.kts` only configures `:android`, `:harness` and `:models-pack` when an
Android SDK is present, so `:core:test` works on a bare machine.
See [docs/BUILD.md](docs/BUILD.md).

## The hard constraints, and how each is enforced

**Fully offline.** The Android manifest does not request `android.permission.INTERNET`.
That is not an oversight — without it the OS refuses every non-local socket, so the
constraint is enforced by the platform rather than by developer discipline. There is no
model-download code anywhere: all models ship in an install-time asset pack and are read
directly through `AssetManager`, never fetched and never unpacked to internal storage.

**Open source only.** ONNX Runtime (MIT), AI4Bharat IndicWav2Vec and Indic-TTS VITS
models.
`android.speech.tts.TextToSpeech` is referenced nowhere, including as a fallback: a TTS
failure raises `TtsException` for the caller to handle rather than silently degrading to
the closed-source platform engine.

**2 GB, no GPU.** Exactly one STT model and one TTS voice are resident at a time; every
model holder frees before it allocates. ONNX sessions run on CPU with no NNAPI or GPU
execution provider. `MemoryBudget` does build-time arithmetic on declared model sizes and
labels its own output `ESTIMATE`; the measured figure comes from Module B7 at runtime.
**No memory figure has been measured yet** — see `docs/MEASUREMENTS.md`.

## Design decisions worth knowing

- **`stop()` is a plain public method on `SttEngine`.** No push-to-talk logic lives in
  the STT module; a future PTT-release handler simply calls it.
- **The 800 ms endpointer is ours, not the backend's.** `SilenceEndpointer` lives in
  `:core` with no Android or inference-engine dependency, so the sentence-boundary rule
  is identical across backends and testable off-device. It is also why dropping Vosk,
  whose decoder has its own endpointer, cost nothing.
- **No HMAC on top of AES-GCM.** GCM's 128-bit tag already authenticates the ciphertext
  *and* the packet header, which travels as associated data. That header carries the
  alert flag, so authenticating it is what stops an attacker flipping a normal message
  into an alert and triggering a forced max-volume siren on every handset in range. A
  second MAC would add bytes and a second key while detecting nothing new. Tested in
  `PacketCodecTest`.
- **TTS chunking is at the text level, not intra-utterance.** VITS is
  non-autoregressive and cannot stream audio out of a single forward pass. Clause-level
  chunking with a pipelined producer is the honest way to start speaking early, and
  `ChunkedSpeakerTest` asserts that playback begins before the last chunk is synthesised.
- **Both alert paths share one audio-focus call site.** The pre-rendered WAV path and
  the TTS path are rendered inside the same `withForcedAlarmAudio` lambda, so the custom
  path cannot skip the escalation. `AlertPlayerTest` verifies this for both branches
  rather than assuming inheritance.
- **Alerts jump the send queue.** In `ChannelArbiter`, an alert queued behind three
  ordinary messages is a safety problem, so alerts go to the head. FIFO within each
  priority class.

## Known gaps

- No models, no evaluation corpus, no alert WAVs are bundled. Paths and loaders exist.
- The five alert templates in `AlertTemplate` are placeholders, kept deliberately: the
  brief did not name them and the real set is an operational decision. Swapping them
  costs a rename plus new WAV assets.
- Distribution is an Android App Bundle with an install-time asset pack
  (`:models-pack`), because the bundled models exceed the 150 MB plain-APK limit. The
  future `:app` module must declare `assetPacks += listOf(":models-pack")`.
- Tamil and Bengali number words and abbreviation expansions need native-speaker review
  — see [docs/LEXICON-REVIEW.md](docs/LEXICON-REVIEW.md). They are isolated as data so
  corrections need no code change.
- `:android`, `:harness` and `:models-pack` have **not been compiled** — there is no
  Android SDK on the authoring machine. They are written against the real platform APIs
  but are unverified. `:core` compiles and its 133 tests pass.
