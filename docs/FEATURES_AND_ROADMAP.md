# Application Features & Technical Roadmap

iTantra delivers a comprehensive suite of mission-critical offline communication tools built for emergency response, field operations, and disaster management.

---

## 1. Feature Matrix

| Feature | Description | Status |
|---|---|---|
| **Push-to-Talk (PTT) Transceiver** | Half-duplex walkie-talkie mode with live waveform visualization, silence detection, and channel arbitration. | **Complete & Tested** |
| **10 Indic Languages** | Full support for Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, and English. | **Complete & Tested** |
| **Offline On-Device STT** | AI4Bharat IndicWav2Vec CTC inference running on CPU via ONNX Runtime INT8. | **Complete & Tested** |
| **Offline On-Device TTS** | AI4Bharat Indic-TTS VITS speech synthesis with clause-level streaming playback. | **Complete & Tested** |
| **On-Device Translation** | IndicTrans2 INT8 neural machine translation across supported scripts. | **Complete & Tested** |
| **Emergency SOS Siren** | Priority preemption and non-interruptible maximum-volume siren override (`withForcedAlarmAudio`). | **Complete & Tested** |
| **Proximity Radar** | Offline peer discovery using Wi-Fi Direct and Bluetooth Low Energy signal strength blips. | **Complete & Tested** |
| **30-Day Message Inbox** | Local persistent transmission queue with auto-retry and 30-day Time-To-Live (TTL). | **Complete & Tested** |
| **Telemetry & Diagnostics** | Live RTF monitoring, resident RAM tracking (<250MB), and packet loss statistics. | **Complete & Tested** |
| **AES-256-GCM AEAD Security** | Authenticated encryption with packet header as Associated Data to prevent spoofing. | **Complete & Tested** |

---

## 2. Navigation Architecture

```text
[ Bottom Navigation Bar / Navigation Rail (Tablet / Foldable >=600dp) ]
  ├── 1. Talk Screen         (PTT Walkie-Talkie, Live Transcripts, Voice Synthesis)
  ├── 2. Radar Screen        (P2P Peer Discovery, Proximity Blips, Pairing)
  ├── [!] Emergency FAB      (High-Priority SOS Broadcast & Pre-rendered Siren Templates)
  ├── 3. History Screen      (Outbound / Inbound Queue, Audio Replay)
  └── 4. Settings Screen     (Profile, Language Pack Manager, Theme, System Diagnostics)
```

---

## 3. Future Roadmap

- **Mesh Multi-Hop Relaying**: Opportunistic ad-hoc mesh routing across 3+ offline devices in rugged terrain.
- **LoRa & Satellite Transceiver Gateway**: Direct interface to external VHF/UHF/LoRa hardware radio modules via USB-OTG.
- **Hardware PTT Button Integration**: Interfacing with ruggedized device physical PTT side buttons and wired tactical headsets.
