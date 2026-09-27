**PS 173 — iTantra Feature Document (Prototype)**

Version 1.0 | Internal SIH Prototype | Team Reference Document

---

## **1\. Product Overview**

iTantra is a fully offline Android application that enables real-time voice communication across 10 Indian languages over low-bandwidth wireless links (WiFi Direct / Bluetooth). The system converts speech to text on the sender's device, transmits the compressed text payload wirelessly, and reconstructs intelligible speech on the receiver's device. The application operates with zero internet dependency and zero proprietary SDKs.

**Primary use case:** Alert and distress communication in remote or infrastructure-limited environments where audio transmission is impractical due to bandwidth constraints but voice output is necessary for inclusivity (non-literate users).

**Secondary use case:** General walkie-talkie style communication between field units speaking different Indian languages.

---

## **2\. Scope of Prototype**

The prototype demonstrates the complete end-to-end loop on two physical Android devices. It is not a production release. The following are explicitly in scope for the prototype:

* STT for Hindi, Tamil, Bengali (3 of 10 languages — full 10 is stretch goal)  
* TTS for Hindi, Tamil, Bengali  
* WiFi Direct transport  
* Bluetooth transport  
* Push-to-talk walkie-talkie mode  
* Continuous call mode  
* Alert message mode  
* End-to-end AES-256 encrypted transport  
* Offline-only operation  
* Diagnostics screen with live metrics

The following are out of scope for the prototype but documented for the final submission:

* All 10 languages fully integrated  
* Over-the-air model updates  
* Multi-device mesh networking (3+ phones)  
* Voice activity detection without PTT

---

## **3\. Functional Features**

---

### **F-01: Speech to Text (STT) Engine**

**Description:** Converts live microphone input to text in real time using on-device Vosk models. Operates continuously while PTT is held or in call mode.

**Supported languages (prototype):** Hindi, Tamil, Bengali. English included as a baseline.

**Behaviour:**

* Activates on PTT button press or automatic in call mode  
* Streams partial recognition results word by word to the live text display  
* Detects sentence boundary on one of three triggers: 800ms of no new partial result, PTT button release, or punctuation-context heuristic  
* On sentence boundary: freezes current text, emits as complete sentence packet, resets recognizer buffer  
* Handles ambient noise without crashing or producing junk output — Vosk degrades gracefully on noise

**Language model loading:**

* Hindi and English models bundled in APK (assets/)  
* Tamil and Bengali models downloaded on first language selection, stored in internal storage  
* Only one STT model loaded in memory at a time  
* Model swap takes under 3 seconds on mid-range device

**Performance targets:**

* Word Error Rate: under 20% on clean audio, under 35% on moderate ambient noise  
* Latency from last word spoken to sentence emit: under 1000ms  
* CPU usage during active listening: under 30%  
* RAM footprint of loaded STT model: under 80MB

**Error handling:**

* If model file is missing or corrupt, show inline error with re-download option  
* If AudioRecord fails to open (permission denied or hardware busy), show permission rationale dialog  
* If STT produces empty output for 10 consecutive seconds, show "No speech detected" status

---

### **F-02: Text to Speech (TTS) Engine**

**Description:** Converts received text to natural-sounding speech using on-device VITS ONNX models. Plays audio through device speaker or connected earpiece.

**Supported languages (prototype):** Hindi, Tamil, Bengali. English via Android built-in TTS as fallback.

**Behaviour:**

* Activates automatically on receiving a text packet  
* Splits received text at natural pause points (comma, conjunction, sentence end) for chunk-by-chunk synthesis — does not wait for full sentence before starting playback  
* Plays synthesized audio through AudioTrack on STREAM\_VOICE\_CALL stream for normal messages  
* Applies text normalization before synthesis: expands digits, dates, abbreviations per language  
* Queues multiple incoming sentences if they arrive faster than playback — plays in order, no dropping

**Alert mode behaviour (see F-06):** Overrides normal playback stream, uses STREAM\_ALARM, non-interruptible.

**Performance targets:**

* Real Time Factor (RTF): under 0.5 (synthesize 2 seconds of audio in under 1 second)  
* Latency from text received to audio start: under 1200ms  
* TTS model size per language after INT8 quantization: under 35MB  
* Naturalness: human legibility score above 4.0 / 5.0 on MOS scale

**Text normalization rules (Hindi example):**

* Digits: 42 → "बयालीस"  
* Time: 3:30 → "तीन बजकर तीस मिनट"  
* Common abbreviations: km → "किलोमीटर", Dr → "डॉक्टर"  
* Unknown tokens: pass through as-is, do not crash

**Error handling:**

* If ONNX inference fails, fall back to Android built-in TextToSpeech for that language  
* If audio output device is unavailable, queue text and retry when device becomes available  
* Log every fallback occurrence for eval dashboard

---

### **F-03: Transport Layer — WiFi Direct**

**Description:** Peer-to-peer text packet transmission between two devices using Android WiFi Direct (WifiP2pManager). No router, hotspot, or internet required.

**Behaviour:**

* On "Connect via WiFi" tap: app scans for nearby devices running iTantra (identified by service name "itantra\_sih\_2026")  
* Displays list of discovered devices with signal strength indicator  
* On device selection: initiates WifiP2pManager connection, one device becomes group owner (server), other becomes client  
* Once connected: opens TCP socket (ServerSocket on owner, Socket on client) on port 49152  
* Keeps connection alive with heartbeat ping every 5 seconds  
* Auto-reconnects if connection drops, up to 3 retries before showing "Connection lost" status

**Packet format:**

\[4 bytes: payload length\] \[1 byte: language code\] \[1 byte: message type\] \[N bytes: UTF-8 text\] \[32 bytes: HMAC-SHA256 signature\]

Message types: 0x01 \= normal, 0x02 \= alert, 0x03 \= heartbeat, 0x04 \= ACK

**Performance targets:**

* Connection establishment time: under 8 seconds  
* Packet transmission latency: under 100ms on local WiFi Direct link  
* Maximum packet size: 512 bytes (sufficient for any sentence in any Indian language)

**Error handling:**

* Malformed packet (length mismatch): discard and log, do not crash  
* HMAC verification failure: discard packet, log security event, show "Tampered packet discarded" in diagnostics  
* Repeated HMAC failures (3+): disconnect and alert user

---

### **F-04: Transport Layer — Bluetooth**

**Description:** Peer-to-peer text transmission using Android Bluetooth Classic RFCOMM channel. Fallback when WiFi Direct is unavailable or range is insufficient.

**Behaviour:**

* On "Connect via Bluetooth" tap: app scans for paired devices. Only shows devices that have iTantra installed (identified by custom UUID: 550e8400-e29b-41d4-a716-446655440173)  
* Opens RFCOMM BluetoothSocket on the custom UUID  
* Same packet format as WiFi Direct transport (F-03) — transport layer is abstracted, upper layers don't distinguish between WiFi and Bluetooth  
* Bluetooth range: effective up to \~10 metres indoors

**Performance targets:**

* Packet transmission latency: under 200ms on Bluetooth Classic  
* Connection establishment: under 12 seconds including pairing if needed

**Error handling:**

* Bluetooth adapter disabled: prompt user to enable, do not crash  
* Device out of range: show signal lost warning, attempt reconnect every 10 seconds  
* RFCOMM connection refused: show "Other device not ready" message

---

### **F-05: Push-to-Talk (PTT) Walkie-Talkie Mode**

**Description:** Classic walkie-talkie interaction. Hold button to speak, release to send. One device is in STT mode (speaking), the other is in TTS mode (listening) at any given moment. Roles can switch.

**Behaviour:**

* User taps "PTT Mode" toggle on home screen  
* Active call screen shows large circular PTT button (minimum 120dp diameter for field usability)  
* Press and hold PTT: STT activates, waveform animates, status shows "Speaking..."  
* Release PTT: sentence boundary triggered immediately regardless of pause detection, complete recognized text emitted as packet  
* Receiving device: text packet arrives, TTS synthesizes and plays automatically, status shows "Receiving..."  
* Half-duplex: if one device is transmitting and the other attempts to transmit simultaneously, second device shows "Channel busy" and queues transmission for 1 second then retries

**UI states:**

* Idle: PTT button grey, status "Connected — ready"  
* Speaking: PTT button red with pulse animation, status "Speaking — \[live text\]"  
* Transmitting: PTT button orange, status "Sending..."  
* Receiving: speaker icon animates, status "Playing: \[received text\]"  
* Channel busy: PTT button grey with lock icon, status "Channel busy — queued"

---

### **F-06: Continuous Call Mode**

**Description:** When PTT mode is toggled off, the app operates like a phone call. STT runs continuously on one end, TTS plays continuously on the other. Both devices can transmit and receive, managed by voice activity detection.

**Behaviour:**

* STT runs permanently in background service  
* Sentence packets emitted automatically on pause detection (800ms silence threshold)  
* Receiving device plays each incoming sentence as it arrives  
* Full duplex: both devices can send simultaneously. Playback on receiver is not interrupted by its own STT transmission  
* Background service keeps STT alive even when screen is off — uses Android foreground service with persistent notification

**Battery consideration:** Call mode uses significantly more battery than PTT mode due to continuous STT. Show battery usage warning when call mode is activated on devices below 20% battery.

---

### **F-07: Alert Message Mode**

**Description:** Pre-defined or typed alert messages that play at maximum volume, non-interruptible, overriding any ongoing audio on the receiver's device.

**Behaviour:**

* Sender can tap "Send Alert" from active call screen  
* Shows a modal with: 5 pre-defined alert templates \+ free text input field  
* Pre-defined templates (in selected language): "Emergency — need assistance", "All clear", "Evacuate immediately", "Stay in position", "Medical help needed"  
* Alert packet transmitted with message type 0x02  
* Receiver: on receiving 0x02 packet, immediately requests audio focus with AUDIOFOCUS\_GAIN\_TRANSIENT (ducks all other audio), sets volume to maximum on STREAM\_ALARM, synthesizes and plays alert TTS, releases audio focus after playback completes  
* Alert playback cannot be interrupted by incoming normal messages — normal messages queue behind it  
* Alert playback CAN be interrupted by a subsequent alert message (higher priority)  
* Pre-defined alert phrases pre-rendered as WAV files at install time — zero TTS inference latency for these specific phrases

**Pre-render targets:** All 5 templates × 3 prototype languages \= 15 WAV files, each under 4 seconds, bundled in APK assets.

---

### **F-08: Language Selection**

**Description:** User selects STT language (what they speak) and TTS language (what they want to hear) independently. They can be different languages — this enables cross-language communication.

**Behaviour:**

* Home screen shows two dropdowns: "I speak" and "Play as"  
* Prototype offers: Hindi, Tamil, Bengali, English  
* Selecting a new language triggers model load sequence: unload current model, load new model, show loading spinner with progress  
* Model load progress: "Loading Hindi STT... 45%" using file read progress  
* If selected language model is not downloaded, show download prompt with model size ("Download Tamil TTS — 28MB")  
* Downloaded models cached in app internal storage permanently, never re-downloaded unless manually cleared

**Cross-language example:** Sender speaks Hindi ("I speak: Hindi"), receiver has "Play as: Tamil" — Hindi speech recognized as Hindi text, transmitted, receiver plays Tamil TTS. Note: this requires translation which is out of scope for prototype. For prototype, cross-language selection plays TTS in the same language as the text received. Document this limitation explicitly.

---

### **F-09: Security — Encrypted Transport**

**Description:** All text packets transmitted between devices are AES-256 encrypted with a session key derived via ECDH key exchange at pairing time.

**Behaviour:**

* At connection establishment, both devices generate ephemeral EC keypairs (P-256 curve) using Android Keystore  
* Devices exchange public keys over the raw transport channel  
* Each device derives the shared secret using ECDH, derives AES-256 key using HKDF-SHA256 with session timestamp as salt  
* All subsequent packets encrypted with AES-256-GCM, authenticated with HMAC-SHA256  
* Session key valid for the duration of one connection. New connection \= new keypair \+ new key exchange  
* 6-digit pairing PIN displayed on both devices at connection time — user must visually confirm they match before communication begins. Prevents man-in-the-middle on the key exchange

**Security guarantees:**

* Intercepted packets are unreadable without the session key  
* Tampered packets are detected and discarded via HMAC verification  
* No audio data ever leaves the device — only text is transmitted  
* No data written to external storage at any point

---

### **F-10: Diagnostics Screen**

**Description:** Real-time performance metrics panel visible to judges and developers. Accessible via a settings icon on the active call screen.

**Metrics displayed:**

STT section:

* Current WER% (computed against last 10 recognized sentences vs expected, using a test mode)  
* STT model name and size (MB)  
* Average STT latency (ms) — rolling 10-sentence average  
* Current language

TTS section:

* TTS model name and size (MB)  
* Average TTS synthesis time (ms)  
* Average RTF (real-time factor) — rolling value  
* Fallback count (how many times Android built-in TTS was used)

Transport section:

* Connection type (WiFi Direct / Bluetooth)  
* Round-trip latency (ms) — measured via ACK packets  
* Packets sent / received this session  
* Packets discarded (HMAC failure or malformed)

System section:

* App RAM usage (MB)  
* Total model RAM loaded (MB)  
* CPU usage % (sampled every 2 seconds)  
* Device model and Android version  
* APK size (read from PackageInfo)

All metrics update every 2 seconds. Metrics exportable as JSON via share button — useful for submitting evaluation data with the prototype.

---

## **4\. Non-Functional Requirements**

**Offline operation:** Zero network calls in any mode. AndroidManifest declares no INTERNET permission. Verified via packet capture — app emits zero external traffic.

**APK size:** Under 150MB including bundled Hindi and English models.

**Cold start time:** Under 8 seconds from tap to home screen ready on a Snapdragon 660-class device.

**Minimum supported device:** Android 7.0 (API 24), 2GB RAM, no GPU required.

**Target device:** Android 10+, 3–4GB RAM, Snapdragon 660 or equivalent.

**Battery:** PTT mode idle CPU under 15%. Active STT under 30%. Call mode under 50%. App does not acquire wake lock except during active transmission.

**Permissions required:**

* RECORD\_AUDIO — microphone access for STT  
* BLUETOOTH, BLUETOOTH\_ADMIN, BLUETOOTH\_CONNECT, BLUETOOTH\_SCAN — Bluetooth transport  
* ACCESS\_WIFI\_STATE, CHANGE\_WIFI\_STATE, ACCESS\_FINE\_LOCATION, NEARBY\_WIFI\_DEVICES — WiFi Direct  
* FOREGROUND\_SERVICE — continuous STT in call mode  
* No INTERNET, no READ\_EXTERNAL\_STORAGE, no WRITE\_EXTERNAL\_STORAGE

---

## **5\. Out of Scope (Prototype)**

* Languages 4–10 (Gujarati, Kannada, Malayalam, Telugu, Odia, Marathi) — architecture supports them, models not integrated in prototype  
* Cross-language translation (Hindi speech → Tamil TTS with meaning preserved)  
* Multi-device mesh (3+ phones in a network)  
* Voice activity detection replacing PTT entirely  
* Over-the-air model updates  
* User accounts or message history  
* SMS or cellular fallback

---

## **6\. Evaluation Mapping**

Every judging criterion maps to a specific feature:

Efficiency (20%) → F-10 diagnostics screen shows model size, APK size, RAM, CPU live. Judges see exact numbers.

Accuracy (40%) → F-01 WER table across 3 languages prepared by AI/ML person from offline evaluation. F-02 MOS scores for TTS naturalness. Both presented as a table in the demo.

Latency (20%) → F-10 shows STT latency, TTS synthesis time, RTF, and round-trip latency live during demo. The walkie-talkie loop (F-05) demonstrates end-to-end latency visually.

Security (implicit) → F-09 covers encrypted transport, judges from ISRO will ask. One slide in the deck covers the ECDH flow.

---

## **7\. Demo Script (Day of Presentation)**

Step 1: Show both phones. Confirm airplane mode on both. Show WiFi Direct connecting — under 8 seconds.

Step 2: Speak a Hindi sentence into Phone A. Phone B plays it back in Hindi within 1.5 seconds. Show diagnostics screen — WER, latency numbers visible.

Step 3: Switch to Tamil on Phone A. Repeat. Switch to Bengali. Repeat. Three languages, all offline.

Step 4: Send an alert message from Phone A. Phone B overrides everything and plays alert at full volume.

Step 5: Switch to Bluetooth. Repeat step 2\. Show it works on both transports.

Step 6: Show diagnostics screen. Walk judges through WER table, RTF values, RAM usage, APK size.

Step 7: If anything fails live, switch to pre-recorded demo clips immediately without hesitation. Pre-recorded clips are prepared for all 3 languages, both PTT and alert modes.

