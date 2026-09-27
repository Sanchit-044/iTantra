# iTantra Documentation Index

Welcome to the technical documentation repository for **iTantra** — an offline multilingual voice transceiver developed for **ISRO Smart India Hackathon (Problem Statement ID: 26173)**.

---

## Quick Navigation for SIH Evaluators & Judges

| Document | Description | Key Focus Area |
|---|---|---|
| [**SIH Evaluation Guide**](SIH_EVALUATION_GUIDE.md) | Step-by-step judge runbook, live demo procedure, and scoring rubric mapping. | **SIH Scoring & Verification** |
| [**ISRO Problem Statement (PS 26173)**](ISRO_SIH_PROBLEM_STATEMENT.md) | Official PS breakdown, requirements matrix, and constraint compliance. | **Compliance & Scope** |
| [**System Architecture**](ARCHITECTURE.md) | Full architectural design, module boundaries (`:core`, `:android`, `:app`), and state machines. | **Engineering Architecture** |
| [**Models & Inference Engine**](MODELS_AND_INFERENCE.md) | Open-source ONNX INT8 models (AI4Bharat IndicWav2Vec, Indic-TTS, IndicTrans2). | **TinyML & Offline AI** |
| [**Wire Protocol & Security**](WIRE_PROTOCOL_AND_SECURITY.md) | Ultra-low bitrate packet structure, AES-256-GCM encryption, anti-tamper AAD. | **Security & Networking** |
| [**Benchmarks & Performance Metrics**](BENCHMARKS_AND_METRICS.md) | Real-Time Factor (RTF), memory footprints (<250MB RAM), latency budgets. | **Efficiency (20%) & Latency (20%)** |
| [**Features & Capabilities**](FEATURES_AND_ROADMAP.md) | Push-to-Talk, 10 Indic languages, high-priority siren, proximity radar. | **Accuracy (40%) & UX** |
| [**Build & Setup Guide**](BUILD_AND_SETUP.md) | Build instructions, Gradle configuration, physical device pairing. | **Deployment & Testing** |

---

## Repository Structure Overview

```text
iTantra/
├── core/                    # Pure Kotlin/JVM domain & business logic (133+ unit tests, zero Android SDK dependency)
│   ├── src/main/kotlin/     # STT/TTS interfaces, Language enum, Packets, Crypto, PTT/Alert Use Cases, Lexicons
│   └── src/test/kotlin/     # Comprehensive unit tests verifying logic off-device
├── android/                 # Android platform library
│   └── src/main/kotlin/     # ONNX Runtime bindings, AudioRecord/AudioTrack, Wi-Fi Direct, BLE, Keystore
├── app/                     # Android application with Jetpack Compose UI
│   └── src/main/kotlin/     # UI screens (Talk, Radar, Alert, History, Settings, Diagnostics), Hilt DI
├── harness/                 # Headless instrumentation tests for STT/TTS evaluation and round-trip verification
├── docs/                    # Complete technical and evaluation documentation
├── assets/                  # High-res screenshots, demo videos, diagrams, and branding
│   ├── diagrams/
│   ├── screenshots/
│   └── videos/
└── scripts/                 # Python ONNX export and INT8 quantization toolchains
```

---

## Key Design Principles

1. **100% Offline After Pack Download**: Zero cloud API dependencies; `INTERNET` permission is restricted strictly to voluntary language pack downloads.
2. **Open-Source Only**: Pure ONNX Runtime, AI4Bharat IndicWav2Vec (CTC), and Indic-TTS (VITS) models. No proprietary SDKs.
3. **2 GB RAM & Low-End CPU Target**: Strict "One Model in RAM" policy (STT unloads before TTS loads) to maintain total memory usage under 250 MB.
4. **End-to-End Encryption**: AES-256-GCM with packet header authenticated as AAD, preventing packet alteration or priority escalation.
5. **Ultra-Low Bitrate Link Optimization**: Converts voice to compressed text for transmission (<100 bytes per utterance), enabling crystal-clear voice communication over bandwidth-constrained channels.
