# iTantra Media & Assets Directory

This directory contains visual media, demonstration videos, architecture diagrams, and application screenshots for the **ISRO Smart India Hackathon (PS 26173)** evaluation.

## Directory Structure

```text
assets/
├── diagrams/                # System architecture, protocol framing, state flow diagrams
│   └── README.md
├── screenshots/             # High-resolution screenshots of the Android application
│   ├── README.md
│   ├── talk/                # Push-to-Talk (PTT), live transcription, voice synthesized playback
│   ├── radar/               # Offline peer discovery, Wi-Fi Direct & Bluetooth mesh radar
│   ├── alerts/              # High-priority emergency SOS, sirens, pre-rendered alerts
│   ├── history/             # Outbound/Inbound message inbox, 30-day TTL queue
│   ├── settings/            # Profile configuration, 10 Indic language pack management
│   └── diagnostics/         # Live RTF, memory telemetry, packet loss, battery tracking
├── videos/                  # Video walkthroughs & live PTT walkie-talkie demonstration recordings
│   └── README.md
└── branding/                # Project logo, vector graphics, and presentation banners
```

## SIH Evaluation Reference

All visual assets linked in this directory serve as proof of functionality and validation under strict hardware and software constraints:
- **Zero Cloud / 100% Offline operation**
- **Low-bitrate text transmission (<100 bytes/packet)**
- **Sub-800ms Real-Time Factor (RTF)**
- **One STT/TTS model in RAM limit (<250MB)**
