# System Architecture & Protocol Diagrams

Visual technical diagrams illustrating the internal design, data pipeline, and security model of **iTantra**.

## Diagram Index

1. **End-to-End Transceiver Data Flow** (`architecture_pipeline.png`):
   Mic (PCM 16kHz) ➔ Silence Endpointer ➔ ONNX IndicWav2Vec (STT) ➔ AES-256-GCM Framing ➔ Wi-Fi Direct / BLE Radio ➔ AES-256 Decrypt ➔ ONNX IndicTrans2 (MT) ➔ Clause Chunking ➔ ONNX Indic-TTS (VITS) ➔ AudioTrack.

2. **Memory Arbitration & Lifecycle** (`memory_lifecycle.png`):
   Enforcement of the **Single Active Model in RAM** rule: unloading STT before loading TTS voice, keeping resident memory <250MB on 2GB RAM target hardware.

3. **Packet Protocol & Security Layout** (`packet_wire_format.png`):
   Binary layout of the low-bitrate frame showing 12-byte header (Magic, Version, Type, Flags, Channel, Seq, Length) + 12-byte IV + Encrypted Payload + 16-byte Poly1305/GCM Tag with Header AAD.

4. **Half-Duplex Floor State Machine** (`floor_controller_state.png`):
   Floor allocation, collision avoidance, and priority alert preemption states (`IDLE`, `REQUESTING`, `TALKING`, `LISTENING`, `ALERT_OVERRIDE`).
