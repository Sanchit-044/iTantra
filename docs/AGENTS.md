# iTantra — Agent Notes & Implementation Constraints

ISRO SIH PS 26173. Offline walkie-talkie: **speech → text on the sender, encrypted text over Wi-Fi Direct / Bluetooth / LAN, speech on the receiver.** No server, no user accounts, no cloud STT/TTS.

---

## Hard constraints

- **Fully offline after language packs are on disk.** Do not add cloud speech APIs. `INTERNET` exists only to download packs the user selected.
- **Open-source inference only** for the shipping path (ONNX Runtime, AI4Bharat IndicWav2Vec / Indic-TTS). Android `TextToSpeech` is a hackathon fallback, not the product.
- **One STT model and one TTS voice in RAM** at a time. Unload before load.
- **minSdk 24, CPU only, ~2 GB RAM target.** Do not add GPU/NNAPI providers.
- Logic that can run without a device belongs in `:core` with JVM unit tests. Android bindings stay in `:android`. UI stays in `:app`.

---

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

App flow: first launch → profile → language picker → download selected packs → main screen with bottom nav **Talk | Radar | [Alert FAB] | History | Settings** (navigation rail on ≥600dp). No drawer. Later launches skip setup. Settings tab holds profile, speech languages, app language, theme, and **Analysis** (diagnostics) as pushed screens.

---

## Modules

| Module | Role |
|---|---|
| `:core` | Language, STT/TTS/LID/translation, packets, crypto, PTT/alert/queue use cases, lexicons. JVM tests. |
| `:android` | ONNX, mic/speaker, transports, Keystore, pack paths, alert WAV/focus, queue files. |
| `:app` | Compose UI: setup, settings, Talk, Alert, Analysis. Hilt. |
| `:harness` | Device eval (B2/B4). Skips without models/corpus. |
| `:models-pack` | Planned asset pack. Not wired; do not assume it is in the APK. |

---

## Key files

- Languages: `core/.../Language.kt`, `core/.../lang/LanguageSelection.kt`
- PTT: `StartPttTransmissionUseCase`, `ReceivePttTransmissionUseCase`, `FloorController`
- Alert: `SendAlertUseCase`, `AlertPlayer`, `AlertScreen`
- Queue: `OutboundMessageQueue`, `InboundMessageInbox`, `QueueTtl` (30 days)
- LID: `ScriptLanguageId`, `LanguageIdEngine.resolveSpokenLanguage`
- Translation: `ChainedTranslationEngine` → `DictionaryTranslationEngine` (8 phrases, instant) → `IndicTransOnnxTranslationEngine` (IndicTrans2 320M, **Hindi↔Marathi only** -- pivots through Devanagari internally, no on-device transliteration for other scripts yet). `BpeTokenizer` handles SentencePiece BPE tokenization.
- Packs: `LanguagePackManager`, `android/.../pack/LocalLanguagePackManager`
- UI: `ITantraApp` (tabs Talk \| Radar \| Alert FAB \| History \| Settings; routes for profile / languages / display / analysis), shared components in `ui/components/` (`Common.kt`, `Scaffolding.kt`), `ProfileScreen`, `LanguageSelectionScreen`, `MainScreen`, `AlertScreen`, `DiagnosticsScreen`, `RadarScreen`, `MainViewModel`
- Persistence: `PrefsLanguageSettingsStore` (`setupDone`, `installed`, `current`, `uiLanguage`); `FileProfileStore` (name + `filesDir/profile/avatar.jpg`); `PrefsThemeStore` (`SYSTEM` / `LIGHT` / `DARK`, default System)
- App chrome: `UiStrings` keyed by `uiLanguage` (English ∪ installed). Speech `current` is separate.
- After pairing confirm: PROFILE `0x0A` (name + thumbnail). `UiState.localProfile` / `peerProfile` for Talk and the map branch.

---

## Translation model setup

Run `python export_translation_models.py` from the repo root to download IndicTrans2 320M, export encoder + decoder to ONNX, quantize to INT8, and save vocab JSON. Requires: `pip install transformers optimum onnxruntime sentencepiece protobuf`. Output lands in `app/src/main/assets/models/translation/`.
