# iTantra — full project brief

ISRO Smart India Hackathon **PS 26173**: *Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for low bitrate links.*

## What it is

An **offline Android walkie-talkie**. Raw audio is too heavy for weak radio. The app:

1. Turns speech into text on the sender (STT)
2. Sends **encrypted text** over Wi-Fi Direct, Bluetooth RFCOMM, or LAN
3. Turns that text back into speech on the receiver (TTS)
4. If the two phones use different current languages, **translates on the receiver** into the listener’s language

No server, no login, no database, no cloud STT/TTS. Primary use: distress / alert comms in the field, including for people who cannot read. Secondary: field units who speak different Indian languages.

## Official languages (10)

Hindi (default), Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English.

Defined in `iTantra/core/src/main/kotlin/in/gov/itantra/core/Language.kt`.

Wire codes (do not reorder or reuse):

| Language | code | wire |
|----------|------|------|
| Hindi | hi | 0x01 |
| Tamil | ta | 0x02 |
| Bengali | bn | 0x03 |
| Gujarati | gu | 0x04 |
| Marathi | mr | 0x05 |
| Kannada | kn | 0x06 |
| Malayalam | ml | 0x07 |
| Telugu | te | 0x08 |
| Odia | or | 0x09 |
| English | en | 0x0A |

Hindi / Tamil / Bengali codes are frozen. English is in scope (ISRO list). There is **no 11th language** unless ISRO adds one.

## Product rule: one language per phone

There is **no separate “I speak” and “Play as.”**

Each phone stores:

- `installed` — languages the user ticked (packs on disk)
- `current` — the one language used for **both STT and TTS**
- `setupDone` — first-launch picker already completed

Hindi is default. Empty selection → Hindi, never crash or empty set.

User may install several languages. Detection and STT only use that set. Whatever LID (or the user in Settings) picks becomes `current` for **speaking and listening**.

## App flow

```
Launch
  ├─ setupDone = false → Language picker (Hindi pre-ticked)
  │                      Continue → install only selected packs
  │                      + shared translation pack marker
  │                      current = Hindi (unless Hindi unticked)
  └─ setupDone = true  → Walkie-talkie (MainScreen)
                         Settings → same picker (add/remove/set active)

Walkie-talkie
  Connect: Wi-Fi host/join, BT host/join, LAN host/join
  Pairing: 6-digit code, both operators confirm
  PTT: detect (if 2+ installed) → STT → send {text, source language}
  Receive: if source == current → TTS
           else translate → current TTS
           if translate fails → error, do not misuse TTS voice
```

## Speech pipeline

**STT (sender)**  
`OnnxCtcSttEngine` — IndicWav2Vec CTC on ONNX, 800 ms `SilenceEndpointer`. One model in RAM. Partials every ~600 ms (skip if previous still running). Final decode is a fresh full-utterance pass.

**LID (sender only)**  
`ScriptLanguageId` + `resolveSpokenLanguage`. Among **installed** only. One language → skip. Unsure → keep current, else Hindi if installed. After STT text, script (Tamil / Devanagari / Latin / …) can refine `current`. No LID on the receiver. No detect among all 10.

**Send**  
Original text + source language. **No translation on send.**

**Translation (receiver only)**  
`TranslationEngine` / `DictionaryTranslationEngine`. Same language → no-op. Demo phrase table (e.g. Hindi “मुझे मदद चाहिए” ↔ Tamil). Unknown sentence without a real model → `TranslationUnavailableException` → UI message. Never feed Hindi text to a Tamil TTS voice.

**TTS (receiver)**  
`VitsOnnxTtsEngine` intended: Indic-TTS VITS ONNX. **Current runtime:** Android `TextToSpeech` hackathon fallback if ONNX missing. `loadVoice` must not die when packs are absent; set locale from `Language.bcp47`. Text pipeline in `:core`: `TextNormalizer`, `NumberLexicon`, `AbbreviationLexicon`, `ClauseChunker`, `ChunkedSpeaker`. Receive path today synthesizes the whole utterance (chunked speaker exists but is not wired on receive).

## Packs / download

Only selected languages are installed. `LocalLanguagePackManager` writes under `filesDir/models/`:

- `stt/<code>/indicwav2vec-<code>-int8.onnx` (+ vocab, `.ready`)
- `tts/<code>/vits-<code>-int8.onnx` (+ vocab)
- `translation/indictrans2.onnx` (+ `.ready`)

Copy from APK assets if present, else optional HTTP if `baseUrl` is set (default empty). Marker lets setup finish before weights exist.

`INTERNET` is on the **app** manifest only for this. After packs are on disk, airplane mode must still run STT/TTS/radio.

ONNX weights are **not in git**. Vocab JSON for Hindi may exist. `:models-pack` is planned and **not wired**.

## Transport and security

Transports (same packet codec): Wi-Fi Direct, Bluetooth RFCOMM, LAN/hotspot TCP.

Handshake: ECDH P-256 (Android Keystore) → HKDF-SHA256 → AES-256-GCM. Header (including language + alert flag) is GCM AAD. **No extra HMAC** (docs that say HMAC are stale). 6-digit pairing SAS; user must confirm both phones match.

Half-duplex: `ChannelArbiter` — second talker sees busy, 1 s retry. Alerts jump the send queue (engine; not in UI).

No REST, no gRPC, no user auth.

## Alerts and diagnostics (engine only)

`:core` / `:android` have `AlertPlayer` (forced alarm focus, templates or TTS) and diagnostics collectors. **Not wired to the app.** No alert screen, no diagnostics screen, no continuous call mode, no foreground service.

Do not claim these are demo-ready.

## Architecture

```
:app        Compose + Hilt. Setup / Settings / Main / PTT.
:android    ONNX, AudioRecord/Track, WifiP2p, Bluetooth, LAN, Keystore, pack IO.
:core       Pure Kotlin/JVM. Interfaces + business logic + unit tests.
:harness    Instrumented B2 STT eval, B4 round-trip. Skips without models/corpus.
:models-pack  Not in settings.gradle. Do not assume it is in the APK.
```

`settings.gradle.kts` includes `:app` / `:android` / `:harness` only if Android SDK is present. **`:core:test` must work on a bare JDK.**

### Tech

Kotlin 2.1, Gradle 8.13, AGP 8.13, minSdk 24, compileSdk 35, JDK 17, Compose Material 3, Hilt, coroutines, ONNX Runtime Android. Room is a leftover dependency — unused, do not add a schema unless asked.

### Persistence

`PrefsLanguageSettingsStore` — SharedPreferences `itantra_language`: `setup_done`, `installed` (comma codes), `current`.

## UI map

| Screen | File | When |
|--------|------|------|
| Language picker | `LanguageSelectionScreen` | First launch and Settings |
| Walkie-talkie | `MainScreen` | After setup |
| Router | `ITantraApp` + `AppViewModel` | SETUP / MAIN / SETTINGS |

Single Activity (`MainActivity`). Permissions: mic, BT, nearby Wi-Fi / location. PTT enabled only when `CONNECTED`. Header shows current language + Settings.

## Key paths (under `iTantra/`)

- `core/.../Language.kt` — enum + wire codes
- `core/.../lang/` — `LanguageSelection`, `LanguageSettings`, `LanguageSettingsStore`
- `core/.../stt/` — `SttEngine`, `SilenceEndpointer`, `ModelRegistry`, `LanguageIdEngine`, `ScriptLanguageId`
- `core/.../tts/` — engines, normalizer, lexicons, chunker
- `core/.../translate/` — `TranslationEngine`, `DictionaryTranslationEngine`
- `core/.../pack/LanguagePackManager.kt`
- `core/.../usecase/` — start/stop/receive PTT
- `core/.../transport/` — `Packet`, `PacketCodec`, `ChannelArbiter`
- `core/.../crypto/` — ECDH, AES-GCM
- `core/.../alert/`, `diag/`, `eval/` — engine / harness, not app UI
- `android/.../stt/OnnxCtcSttEngine.kt`, `OnnxCtcDecoder.kt`
- `android/.../tts/VitsOnnxTtsEngine.kt`
- `android/.../pack/` — `LanguagePackPaths`, `LocalLanguagePackManager`
- `android/.../transport/` — WifiDirect, Bluetooth, Lan, StreamTransport
- `app/.../ui/` — Activity, ITantraApp, Main, LanguageSelection, ViewModels
- `app/.../lang/PrefsLanguageSettingsStore.kt`
- `app/.../di/AppModule.kt`

## Constraints (ISRO / design)

- Offline after packs installed. No cloud speech.
- Open-source shipping STT/TTS. System TTS is fallback only.
- One STT + one TTS resident. CPU only. ~2 GB phone, ~350 MB app RAM budget (estimate).
- No INTERNET for inference. Pack download is the only intended WAN use.
- Do not invent WER, RTF, RAM, or latency. `docs/MEASUREMENTS.md`: **nothing measured**.

## Docs vs code

| Doc | Trust |
|-----|--------|
| `.cursor/PROJECT.md` + `rules/` | Current product |
| `AGENTS.md` | Short reminder |
| `docs/STT-BACKEND.md` | Valid: Vosk dropped, IndicWav2Vec ONNX |
| `docs/MEASUREMENTS.md` | Valid: no measurements |
| `iTantra/README.md` | Stale (“no UI”) |
| `docs/Features.md` | Stale (Vosk, HMAC, speak/listen split, translation out of scope, 3-language prototype) |

Prefer **code + this folder** when they disagree.

## What works vs incomplete

**In code / tested in `:core`:** packets, GCM, pairing math, endpointer, lexicons (HI/TA/BN + extras), LID set rules, dictionary translate, receive skip-when-same, channel arbiter, alert player (unit), WER harness.

**Wired in the app:** language picker, persist, pack install markers, PTT, three transports, pairing dialog, receive + translate hook, current-language header.

**Not ready for a measured demo:** ONNX weights not in repo; TTS is system engine; LID is script/heuristic not a neural model; translation is a phrase table; Tamil/Bengali lexicons need native review; extras languages use placeholder number tokens; alerts/diagnostics/call mode not in UI; `:models-pack` unwired; Room unused.

## Tests and commands

```bash
cd iTantra
./gradlew :core:test
# needs ANDROID_HOME:
./gradlew :app:assembleDebug
./gradlew :harness:connectedAndroidTest
```

When changing language / PTT / receive / packets: update `:core` tests. Loops should use `Language.entries`, not “exactly 3.”

## Adding a language

1. `Language` enum + new wire code (do not reuse 0x01–0x0A)
2. Number + abbreviation lexicons
3. ModelRegistry / VITS paths (enum-driven — usually automatic)
4. Picker is enum-driven
5. Dictionary pairs only if you have demo phrases
6. Tests that iterate `Language.entries`

## Out of scope unless explicitly asked

Alert UI, diagnostics screen, continuous call / FGS, mesh (3+ phones), OTA model store, real IndicTrans2 ONNX in git, cross-language meaning beyond the translation engine, inventing eval numbers.
