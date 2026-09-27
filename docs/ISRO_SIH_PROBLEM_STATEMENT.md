# ISRO Smart India Hackathon — Problem Statement (PS 26173)

**Organization:** Indian Space Research Organisation (ISRO)  
**Department:** Department of Space  
**Theme:** Smart Automation / Software  
**Problem Statement ID:** `26173`  
**Title:** `iTantra — Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for low bitrate links`

---

## 1. Problem Background & Motivation

In critical, remote, and disaster scenarios, radio and satellite communication channels suffer from extremely constrained bandwidth and low data rate capacity. Transmitting raw voice audio streams requires significant bandwidth (typically 8–64 kbps per stream), leading to dropped calls, heavy packet loss, and communication breakdown.

Furthermore, in alert and distress scenarios, audio is critical for inclusivity—ensuring citizens and personnel across diverse literacy levels can instantly understand emergency instructions.

---

## 2. Problem Statement Scope & Requirements

1. **Android Application for Low-Power Devices**:
   - Run locally on low-end mobile devices (~2GB RAM target, minSdk 24, CPU-only inference).
2. **10 Official Indian Languages**:
   - Hindi (`hi`), Gujarati (`gu`), Marathi (`mr`), Kannada (`kn`), Malayalam (`ml`), Tamil (`ta`), Telugu (`te`), Odia (`or`), Bengali (`bn`), and English (`en`).
3. **Neural Speech-to-Text (STT) Module**:
   - Activated via Push-to-Talk (PTT), detect sentence boundaries and pauses (silence endpointer), transcribe spoken audio into text locally, and transmit compact text over low-bitrate Wi-Fi/Bluetooth links.
4. **Neural Text-to-Speech (TTS) Module**:
   - Receive incoming text packets, synthesize natural, intelligible voice audio on-device, and play it back as a voice note.
5. **High-Priority Emergency Alerts**:
   - Dedicated alert broadcast mode that overrides background audio, forces maximum device volume, and plays non-interruptible sirens and emergency broadcasts.
6. **Walkie-Talkie Mode Verification**:
   - Real-time PTT operation between two physical phones over direct Wi-Fi Direct or Bluetooth connections.

---

## 3. Evaluation Criteria & Weightage Breakdown

| Metric Category | Weightage | Evaluation Metric | iTantra Implementation & Defense |
|---|---|---|---|
| **Accuracy** | **40%** | • Low Word Error Rate (WER) for STT<br>• High human legibility and natural prosody for TTS | AI4Bharat IndicWav2Vec CTC models + AI4Bharat Indic-TTS VITS models trained natively on Indic graphemes; Lexicon normalization for numbers and abbreviations. |
| **Efficiency** | **20%** | • RAM/Flash memory footprint<br>• Model compression<br>• Idle CPU usage | Dynamic INT8 ONNX quantization (~150MB/model); Strict "One Model in RAM" memory lifecycle (<250MB active RAM); Zero CPU consumption in idle listening. |
| **Latency** | **20%** | • STT transcription delay<br>• TTS synthesis delay & RTF<br>• End-to-end voice-to-voice delta | Sub-800ms silence endpointer; Clause-level streaming pipelining for TTS playback; Minimal text packet transmission (<100 bytes/message). |
| **Architectural Robustness** | **20%** | • Offline compliance<br>• Open-source adherence<br>• Reliability over lossy links | 100% open-source TinyML (ONNX Runtime, PyTorch); Zero cloud APIs; AES-256-GCM AEAD encryption; Outbound retry queue with 30-day TTL. |

---

## 4. Software & Framework Restrictions Compliance Matrix

| Rule | Requirement | iTantra Compliance |
|---|---|---|
| **Open-Source Only** | Strict ban on proprietary SDKs (e.g., Google Cloud STT/TTS, Vosk proprietary models, ElevenLabs). | **COMPLIANT**: 100% open-source AI4Bharat IndicWav2Vec, Indic-TTS, and ONNX Runtime (MIT). |
| **Allowed Frameworks** | Open-source ML / TinyML (ONNX Runtime, PyTorch Mobile, TFLite). | **COMPLIANT**: Pure ONNX Runtime for CPU execution with INT8 dynamic quantization. |
| **Fully Offline** | Must work completely without internet connection. | **COMPLIANT**: Verified zero runtime internet dependencies; models bundled in asset packs or local app storage. |
| **Hardware Boundary** | Low to mid-range devices (~2GB RAM, Android 7.0+). | **COMPLIANT**: minSdk 24, CPU-only thread pool (no GPU/NNAPI requirements), memory budgeted <250MB. |
