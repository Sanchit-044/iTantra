# Talk & Push-to-Talk (PTT) UI Flow

Screenshots showcasing the real-time offline half-duplex voice transceiver interface:

- `01_talk_idle.png`: Idle state displaying selected current language (Hindi default), active connected peer, floor state (`FLOOR_AVAILABLE`).
- `02_talk_recording.png`: User holds PTT button; dynamic audio waveform visualizer; microphone streaming raw 16kHz PCM to on-device IndicWav2Vec CTC inference.
- `03_talk_transcribed.png`: Real-time text output on sender device with silence detection (800ms endpointer) and script-based language identification.
- `04_talk_receiving.png`: Receiver device UI showing incoming encrypted packet reception, on-device IndicTrans2 translation (if applicable), and Indic-TTS voice synthesis.
- `05_talk_floor_busy.png`: Visual arbitration indicator when peer holds the channel floor (`FLOOR_BUSY`).
