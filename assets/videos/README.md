# Demonstration & Evaluation Videos

**Official Live Screen Recording & Demo:** https://www.youtube.com/watch?v=nuAakThrO1o

This directory houses demonstration recordings showcasing **iTantra** executing on real physical Android devices for SIH judges and evaluators.

## Video Demonstrations

| Video File | Target Scenario | Key Evaluation Highlight |
|---|---|---|
| `01_walkie_talkie_ptt_demo.mp4` | Two phones connected via Wi-Fi Direct in PTT mode. Sender speaks in Hindi, receiver plays voice note. | Minimal latency, half-duplex arbitration, 100% offline. |
| `02_multilingual_translation_demo.mp4` | Sender speaks in Hindi; Receiver set to Marathi. Shows automated on-device translation + speech synthesis. | Script identification, INT8 translation model, native TTS flow. |
| `03_emergency_alert_siren_demo.mp4` | High-priority SOS alert triggered on Device A. Device B preempts background audio and plays max-volume siren. | Highest volume non-interruptible alert, priority queue jumping. |
| `04_offline_radar_mesh_demo.mp4` | Peer discovery without active Wi-Fi AP or Cellular data. | P2P direct socket creation, zero-internet security handshake. |
| `05_diagnostics_and_memory_demo.mp4` | Live screen recording showing RAM staying under 250MB and CPU usage during idle/active states. | Memory budget compliance, model unload before load enforcement. |

## Recommended Recording Guidelines for SIH
- Format: MP4 (H.264, 1080p 60fps)
- Dual-Device Setup: Place two physical Android phones side-by-side to capture real-world acoustic latency and synchronized screen reactions.
- Audio: Clear stereo audio capturing both microphone input on Sender and speaker output on Receiver.
