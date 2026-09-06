# iTantra — agent notes

ISRO SIH PS 26173. Offline walkie-talkie: **speech → text on the sender, encrypted text over Wi-Fi Direct / Bluetooth / LAN, speech on the receiver.** No server, no user accounts, no cloud STT/TTS.

The Android app and Cursor brief live in this directory. Full agent context: `.cursor/PROJECT.md` and `.cursor/rules/`. Do not put project rules outside `iTantra/`.

## Hard constraints

- **Fully offline after language packs are on disk.** Do not add cloud speech APIs. `INTERNET` exists only to download packs the user selected.
- **Open-source inference only** for the shipping path (ONNX Runtime, AI4Bharat IndicWav2Vec / Indic-TTS). Android `TextToSpeech` is a hackathon fallback, not the product.
- **One STT model and one TTS voice in RAM** at a time. Unload before load.
- **minSdk 24, CPU only, ~2 GB RAM target.** Do not add GPU/NNAPI providers.
- Logic that can run without a device belongs in `:core` with JVM unit tests. Android bindings stay in `:android`. UI stays in `:app`.

## Language product rules (do not regress)

- Official set is **10**: Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English. Defined in `core/.../Language.kt`.
- **One current language per phone** — used for both speaking (STT) and listening (TTS). There is no separate “I speak” / “Play as.”
- **Hindi is the default.** Fresh setup pre-selects Hindi. An empty selection must become Hindi, not crash.
- User may **install several** languages (first screen + Settings). Only those packs are written under app files.
- **Detect only among installed languages**, only on the speaker, only when 2+ are installed. One installed language → skip LID. Unsure → keep current, or Hindi if still installed. Never return a language that is not installed.
- After a confident detect, **update current language** so the next incoming message is translated into that same language.
- **Translate only on receive**, and only when `packet.language != currentLanguage`. Send original text + source language. Same language → no translator.
- If translation is unavailable, **show an error / download prompt**. Do not run source-language text through the target TTS voice.
- Hindi / Tamil / Bengali **wire codes stay 0x01 / 0x02 / 0x03**. New languages use 0x04+.

App flow: first launch → language picker → download selected packs → **Talk | Alert | Analysis | Radar**. Later launches skip the picker. Settings reopens the same picker.

## Modules

| Module | Role |
|--------|------|
| `:core` | Language, STT/TTS/LID/translation, packets, crypto, PTT/alert/queue use cases, lexicons. JVM tests. |
| `:android` | ONNX, mic/speaker, transports, Keystore, pack paths, alert WAV/focus, queue files. |
| `:app` | Compose UI: setup, settings, Talk, Alert, Analysis. Hilt. |
| `:harness` | Device eval (B2/B4). Skips without models/corpus. |
| `:models-pack` | Planned asset pack. Not wired; do not assume it is in the APK. |

`settings.gradle.kts` includes `:android` / `:app` / `:harness` only when an Android SDK is present. `:core:test` must work without it.

## Key files

- Languages: `core/.../Language.kt`, `core/.../lang/LanguageSelection.kt`
- PTT: `StartPttTransmissionUseCase`, `ReceivePttTransmissionUseCase`, `FloorController`
- Alert: `SendAlertUseCase`, `AlertPlayer`, `AlertScreen`
- Queue: `OutboundMessageQueue`, `InboundMessageInbox`, `QueueTtl` (30 days)
- LID: `ScriptLanguageId`, `LanguageIdEngine.resolveSpokenLanguage`
- Translation: `TranslationEngine`, `DictionaryTranslationEngine` (demo phrases only)
- Packs: `LanguagePackManager`, `android/.../pack/LocalLanguagePackManager`
<<<<<<< HEAD
- UI: `ITantraApp` (Talk \| Alert \| Analysis \| Radar), `LanguageSelectionScreen`, `MainScreen`, `AlertScreen`, `DiagnosticsScreen`, `RadarScreen`, `MainViewModel`
- Persistence: `PrefsLanguageSettingsStore` (`setupDone`, `installed`, `current`, `uiLanguage`)
- App chrome: `UiStrings` keyed by `uiLanguage` (English ∪ installed). Speech `current` is separate.
=======
- UI: `ITantraApp` (Talk \| Alert \| Analysis), `ProfileScreen`, `LanguageSelectionScreen`, `MainScreen`, `AlertScreen`, `DiagnosticsScreen`, `MainViewModel`
- Persistence: `PrefsLanguageSettingsStore` (`setupDone`, `installed`, `current`); `FileProfileStore` (name + `filesDir/profile/avatar.jpg`)
- After pairing confirm: PROFILE `0x0A` (name + thumbnail). `UiState.localProfile` / `peerProfile` for Talk and the map branch.
>>>>>>> origin/feat/profile

## Docs vs code

`README.md` still says “no UI.” That is stale — `:app` exists. `docs/Features.md` is the old spec (Vosk, HMAC, separate speak/listen, translation out of scope). **Prefer the code and this file** when they disagree. Decision notes that are still valid: `docs/STT-BACKEND.md` (Vosk dropped), `docs/MEASUREMENTS.md` (nothing measured — do not invent WER/RAM/RTF).

## Engineering

- Kotlin, coroutines, Hilt in `:app`. Do not add Room unless a real schema is required.
- Packet auth is **AES-256-GCM with header as AAD**, not a second HMAC.
- When adding a language: enum + wire code, lexicons, registry paths, picker (enum-driven), tests that loop `Language.entries`.
- When changing receive/PTT/language: add or update `:core` tests. Do not land untested translation skip/detect-set rules.
- Tests: `./gradlew :core:test`. Do not quote performance numbers that are not in `docs/MEASUREMENTS.md`.

## Out of scope unless asked

Mesh (3+ phones), lock-screen / power SOS, true GPS map of peers, continuous call mode, meaning-preserving translation beyond the offline engine, bundling real ONNX weights in git.
