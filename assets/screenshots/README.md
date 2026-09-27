# Application Screenshots Catalog

This catalog documents the screens and workflows in **iTantra** built with **Jetpack Compose**, implementing the requirements for ISRO SIH PS 26173.

## Screen Categories

| Section | Description | Target Folder |
|---|---|---|
| **1. Push-to-Talk (Talk Screen)** | PTT button states (Idle, Transmitting, Receiving, Synthesizing), real-time waveform, auto-detected language indicator, peer contact card. | [`talk/`](talk/README.md) |
| **2. Radar & Peer Discovery** | Offline Wi-Fi Direct and Bluetooth Low Energy proximity radar, peer signal strength (RSSI), pairing state. | [`radar/`](radar/README.md) |
| **3. Emergency Alert System** | Non-interruptible high-priority emergency siren, pre-rendered alert templates, SOS broadcast overlay. | [`alerts/`](alerts/README.md) |
| **4. History & Inbox** | Stored transcriptions, translation toggle, playback speed controls, 30-day offline TTL queue manager. | [`history/`](history/README.md) |
| **5. Settings & Language Packs** | Profile avatar & name, 10 Indic language pack download manager, storage space calculator, UI theme. | [`settings/`](settings/README.md) |
| **6. Real-time Diagnostics** | Live RTF monitor, memory budget tracker (<250MB enforcement), packet transmission counter, audio focus state. | [`diagnostics/`](diagnostics/README.md) |

## Standard Resolution
- Format: PNG / WebP
- Display Ratio: 19.5:9 or 20:9 (Standard Android Handset)
- Themes: Dark Mode & Light Mode (Material 3 Dynamic Theming)
