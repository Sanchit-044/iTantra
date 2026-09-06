# iTantra — full project brief

ISRO Smart India Hackathon **PS 26173**: *Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for low bitrate links.*

## What it is

An **offline Android walkie-talkie**. Raw audio is too heavy for weak radio. The app:

1. Turns speech into text on the sender (STT)
2. Sends **encrypted text** over Wi-Fi Direct, Bluetooth RFCOMM, or LAN (LAN exists in `:android`; Talk UI today is Wi-Fi Direct + Bluetooth)
3. Turns that text back into speech on the receiver (TTS)
4. If the two phones use different current languages, **translates on the receiver** into the listener’s language

No server, no login, no database, no cloud STT/TTS. Primary use: distress / alert comms in the field, including for people who cannot read. Secondary: field units who speak different Indian languages.

**One connected peer.** No 3-person mesh.

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
- `uiLanguage` — menus and buttons. Allowed set is **English ∪ installed**. English does not need an English speech pack. Unticking the UI language snaps `uiLanguage` to English. Speech `current` is independent (speak Hindi, menus English).
- `setupDone` — first-launch language picker already completed
- operator profile — compulsory display name + photo in app files (`filesDir/profile/avatar.jpg`). Not the Wi-Fi Direct / Bluetooth name. Not used by STT/TTS.

Hindi is default for speech. Empty selection → Hindi, never crash or empty set.

User may install several languages. Detection and STT only use that set. Whatever LID (or the user in Settings) picks becomes `current` for **speaking and listening**.

App chrome is a Kotlin table (`UiStrings`), not `res/values-hi`. Missing or blank strings fall back to English, never an empty button. First-launch setup chrome stays English until setup finishes. Alert **spoken** phrases follow `current`, not `uiLanguage`.

## App flow

```
Launch
  ├─ profile incomplete → Profile (name + photo required)
  ├─ setupDone = false → Language picker (Hindi pre-ticked), chrome in English
  │                      1) Tick speech packs  2) Show the app in: English ∪ ticked
  │                      Continue → install only selected packs
  │                      + shared translation pack marker
  │                      current = Hindi (unless Hindi unticked)
  │                      uiLanguage = chosen chrome language
  └─ both done         → Main shell (Talk | Alert | Analysis | Radar) in uiLanguage
                         Settings (gear / Settings) → hub: Profile | Languages (same picker + app-language switcher)

Talk
  Connect: Wi-Fi host/join, BT host/join
  Pairing: 6-digit code, both operators confirm (dialog is on the shell)
  After confirm: each phone sends PROFILE (0x0A) name + tiny JPEG. Talk shows “Talking to {peer}”.
  Map (other branch) reads the same `uiState.peerProfile` / `localProfile`.
  Live PTT: request floor → STT → send {text, source language}
  Offline PTT: if not connected, store text in outbound queue (not live send)
  Inbox: queued inbound text; Play / Dismiss; no autoplay

Alert (priority shout, packet 0x02)
  Five F-07 templates + free text. Send only when CONNECTED and pairing confirmed.
  Not queued. Peer auto-plays loud (AlertPlayer). No inbox Play button.

Analysis
  Diagnostics snapshot (poll). Share JSON. Not a second radio.

Radar
  Proximity plot (you in the center, near/mid/far rings from RSSI). Not GPS.
  Filter Wi-Fi / Bluetooth / Both. Tap a dot to join via existing connect + pairing.
  Other phone still Hosts on Talk. Scan/advertise only while the Radar tab is open.

Receive (live NORMAL)
  if source == current → TTS
  else translate → current TTS
  if translate fails → error, do not misuse TTS voice
  if an alert is playing → tryPlayNormal defers, then play after
```

## Speech pipeline

**STT (sender)**  
`OnnxCtcSttEngine` — IndicWav2Vec CTC on ONNX, 800 ms `SilenceEndpointer`. One model in RAM. Partials every ~600 ms (skip if previous still running). Final decode is a fresh full-utterance pass.

**LID (sender only)**  
`ScriptLanguageId` + `resolveSpokenLanguage`. Among **installed** only. One language → skip. Unsure → keep current, else Hindi if installed. After STT text, script can refine `current`. No LID on the receiver. No detect among all 10.

**Send**  
Original text + source language. **No translation on send.**

**Translation (receiver only)**  
`TranslationEngine` / `DictionaryTranslationEngine`. Same language → no-op. Demo phrase table. Unknown sentence without a real model → `TranslationUnavailableException` → UI message. Never feed Hindi text to a Tamil TTS voice.

**TTS (receiver)**  
`VitsOnnxTtsEngine` intended: Indic-TTS VITS ONNX. **Current runtime:** Android `TextToSpeech` is used when the ONNX graph is missing (hackathon fallback). `loadVoice` must not die when packs are absent. Text pipeline in `:core`: `TextNormalizer`, `NumberLexicon`, `AbbreviationLexicon`, `ClauseChunker`, `ChunkedSpeaker`. Receive path today synthesizes the whole utterance (chunked speaker exists but is not wired on receive).

## Packs / download

Only selected languages are installed. `LocalLanguagePackManager` writes under `filesDir/models/`:

- `stt/<code>/indicwav2vec-<code>-int8.onnx` (+ vocab, `.ready`)
- `tts/<code>/vits-<code>-int8.onnx` (+ vocab)
- `translation/indictrans2.onnx` (+ `.ready`)

Copy from APK assets if present, else optional HTTP if `baseUrl` is set (default empty). Marker lets setup finish before weights exist.

`INTERNET` is on the **app** manifest only for this. After packs are on disk, airplane mode must still run STT/TTS/radio.

ONNX weights are **not in git**. Vocab JSON for Hindi may exist. `:models-pack` is planned and **not wired**.

## Transport and security

Transports (same packet codec): Wi-Fi Direct, Bluetooth RFCOMM, `LanTransport` (not on the Talk picker yet).

Handshake: ECDH P-256 (Android Keystore) → HKDF-SHA256 → AES-256-GCM. Header (including language + type) is GCM AAD. **No extra HMAC** (docs that say HMAC are stale). 6-digit pairing SAS; user must confirm both phones match. `StreamTransport.send` refuses content until `confirmPairing`.

**Floor control** (`FloorController`, packets `0x05`–`0x08`): live PTT requests the floor before STT. Host wins simultaneous requests. Denied → “Channel busy.” `ChannelArbiter` still prefers alerts on the send path.

**Message types (do not renumber):**

| Type | wire |
|------|------|
| NORMAL | 0x01 |
| ALERT | 0x02 |
| HEARTBEAT | 0x03 |
| ACK | 0x04 |
| FLOOR_REQUEST | 0x05 |
| FLOOR_GRANT | 0x06 |
| FLOOR_DENY | 0x07 |
| FLOOR_RELEASE | 0x08 |
| QUEUED | 0x09 |
| PROFILE | 0x0A |

PROFILE is identity only — never TTS. Old APKs that do not know 0x0A discard it; Talk then keeps the radio peer name.

No REST, no gRPC, no user auth.

## Alerts (wired)

F-07 templates: Emergency — need assistance, All clear, Evacuate immediately, Stay in position, Medical help needed. Wire payload `tpl:<assetKey>` or trimmed custom text (`SendAlertUseCase`, max 200 chars).

`AlertPlayer`: STREAM_ALARM, max volume, then restore. Template WAV at `alerts/<lang>/<assetKey>.wav` if present; **missing file → TTS of the phrase**. A second alert **queues** and plays after (does not cut). Incoming NORMAL waits via `tryPlayNormal` (ViewModel defers up to 8).

No triple-press power, no lock-screen SOS, no widget.

## Queue / inbox (wired)

When disconnected, Talk PTT stores **text** outbound (`OutboundMessageQueue`). After pairing confirm, `FlushQueuedMessagesUseCase` sends `QUEUED` (0x09). Receiver puts it in `InboundMessageInbox` — **no auto TTS**. Talk shows Play / Dismiss. Optional notification. TTL **30 days** (`QueueTtl`). Cap and persist: `FileQueueStore` (`filesDir/queue-outbound.tsv`, `queue-inbox.tsv`). Sent outbound items are deleted; inbox stays until dismiss.

Alerts are **not** queued.

## Analysis (wired)

`DiagnosticsScreen` + `AndroidDiagnosticsService`. Snapshot of STT/TTS/transport/system. Do not invent WER/RTF/RAM numbers in UI copy or docs.

## Architecture

```
:app        Compose + Hilt. Setup / Settings / Talk / Alert / Analysis.
:android    ONNX, AudioRecord/Track, WifiP2p, Bluetooth, LAN, Keystore, pack IO, WAV templates, queue files.
:core       Pure Kotlin/JVM. Interfaces + business logic + unit tests.
:harness    Instrumented B2 STT eval, B4 round-trip. Skips without models/corpus.
:models-pack  Not in settings.gradle. Do not assume it is in the APK.
```

`settings.gradle.kts` includes `:app` / `:android` / `:harness` only if Android SDK is present. **`:core:test` must work on a bare JDK.**

### Tech

Kotlin 2.1, Gradle 8.13, AGP 8.13, minSdk 24, compileSdk 35, JDK 17, Compose Material 3, Hilt, coroutines, ONNX Runtime Android. Room is a leftover dependency — unused, do not add a schema unless asked.

### Persistence

`PrefsLanguageSettingsStore` — SharedPreferences `itantra_language`: `setup_done`, `installed` (comma codes), `current`, `ui_language`. Missing `ui_language` → English.

`FileProfileStore` — SharedPreferences `itantra_profile`: `profile_done`, `profile_name`. Photo: `filesDir/profile/avatar.jpg`. Peer thumbnail cache: `cacheDir/peer-avatar.jpg`.

## UI map

| Screen | File | When |
|--------|------|------|
| Profile | `ProfileScreen` | First launch (before languages) and Settings |
| Language picker | `LanguageSelectionScreen` | First launch after profile, and Settings |
| Settings hub | `SettingsHubScreen` | Profile + Languages |
| Router | `ITantraApp` + `AppViewModel` | profile / languages / MAIN / settings |
| Main shell | `ITantraApp.MainContent` | Bottom nav Talk \| Alert \| Analysis \| Radar; pairing dialog here |
| Talk | `MainScreen` | Connect, PTT, inbox, Settings, talking-to peer |
| Alert | `AlertScreen` | 5 templates + free text; disabled until paired |
| Analysis | `DiagnosticsScreen` | Diagnostics snapshot |
| Radar | `RadarScreen` | Wi-Fi Direct + iTantra BLE proximity plot; tap to join |

Single Activity (`MainActivity`). Permissions: mic, BT, nearby Wi-Fi / location. Live PTT needs CONNECTED + floor. Alert send needs CONNECTED + pairing confirmed. Offline PTT queues text.

## Key paths (under `iTantra/`)

- `core/.../Language.kt` — enum + wire codes
- `core/.../lang/` — `LanguageSelection`, `LanguageSettings`, `LanguageSettingsStore`, `UiLanguage`, `UiStrings`
- `core/.../profile/` — `OperatorProfile`, `ProfileStore`, `ProfileCodec`
- `core/.../stt/` — `SttEngine`, `SilenceEndpointer`, `ModelRegistry`, `LanguageIdEngine`, `ScriptLanguageId`
- `core/.../tts/` — engines, normalizer, lexicons, chunker
- `core/.../translate/` — `TranslationEngine`, `DictionaryTranslationEngine`
- `core/.../pack/LanguagePackManager.kt`
- `core/.../usecase/` — start/stop/receive PTT, `SendAlertUseCase`, `FlushQueuedMessagesUseCase`
- `core/.../transport/` — `Packet`, `PacketCodec`, `ChannelArbiter`, `FloorController`
- `core/.../crypto/` — ECDH, AES-GCM
- `core/.../discover/` — `NearbyPeer`, `RssiBand` (radar rings)
- `core/.../alert/` — templates + `AlertPlayer`
- `core/.../queue/` — outbound, inbox, TTL
- `core/.../diag/`, `eval/` — diagnostics model / harness
- `android/.../stt/OnnxCtcSttEngine.kt`, `OnnxCtcDecoder.kt`
- `android/.../tts/VitsOnnxTtsEngine.kt`
- `android/.../discover/NearbyDiscovery.kt` — Wi-Fi P2P + BLE scan/advertise
- `android/.../alert/` — `WavTemplateSource`, `AndroidForcedAudioFocus`
- `android/.../queue/FileQueueStore.kt`
- `android/.../pack/` — `LanguagePackPaths`, `LocalLanguagePackManager`
- `android/.../transport/` — WifiDirect, Bluetooth, Lan, StreamTransport
- `app/.../ui/` — Activity, ITantraApp, Main, Alert, Diagnostics, Radar, LanguageSelection, ViewModels
- `app/.../lang/PrefsLanguageSettingsStore.kt`
- `app/.../profile/FileProfileStore.kt`
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
| `.cursor/PROJECT.md` + `rules/` | Current product — **keep in sync with `:app` UI** |
| `AGENTS.md` | Short reminder |
| `docs/STT-BACKEND.md` | Valid: Vosk dropped, IndicWav2Vec ONNX |
| `docs/MEASUREMENTS.md` | Valid: no measurements |
| `iTantra/README.md` | Stale (“no UI”) |
| `docs/Features.md` | Stale (Vosk, HMAC, speak/listen split, translation out of scope, 3-language prototype) |

Prefer **code + this folder** when they disagree.

## What works vs incomplete

**In code / tested in `:core`:** packets (incl. floor + queued), GCM, pairing math, endpointer, lexicons, LID set rules, dictionary translate, receive skip-when-same, channel arbiter, floor controller, alert player, send-alert refuses before pairing, queue TTL, WER harness.

**Wired in the app:** language picker, persist, pack install markers, Talk connect + pairing, live PTT + floor, offline queue + inbox, Alert tab, Analysis tab, Radar tab (proximity join), receive + translate hook, current-language header.

**Not ready for a measured demo:** ONNX weights not in repo; TTS often system engine; LID is script/heuristic not a neural model; translation is a phrase table; Tamil/Bengali lexicons need native review; extra languages use placeholder number tokens; spoken alert WAVs may be missing (TTS fallback); `:models-pack` unwired; Room unused; LAN not on Talk picker.

## Tests and commands

```bash
cd iTantra
./gradlew :core:test
# needs ANDROID_HOME:
./gradlew :app:assembleDebug
./gradlew :harness:connectedAndroidTest
```

When changing language / PTT / receive / packets / alerts / queue: update `:core` tests. Loops should use `Language.entries`, not “exactly 3.”

## Adding a language

1. `Language` enum + new wire code (do not reuse 0x01–0x0A)
2. Number + abbreviation lexicons
3. ModelRegistry / VITS paths (enum-driven — usually automatic)
4. Picker is enum-driven
5. Alert `phrase()` arms for that language
6. Dictionary pairs only if you have demo phrases
7. Tests that iterate `Language.entries`

## Out of scope unless explicitly asked

Mesh (3+ phones), lock-screen / power-button SOS, true GPS map of peers, continuous call / FGS, OTA model store, real IndicTrans2 ONNX in git, cross-language meaning beyond the translation engine, inventing eval numbers.
