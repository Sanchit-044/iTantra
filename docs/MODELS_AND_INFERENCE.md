# Machine Learning Models & On-Device Inference Pipeline

iTantra utilizes state-of-the-art open-source neural models from **AI4Bharat**, optimized and quantized to INT8 ONNX graphs for low-latency CPU execution on Android devices.

---

## 1. Supported Languages & Model Architectures

| Language | ISO Code | Wire Code | STT Architecture | TTS Architecture | Machine Translation |
|---|---|---|---|---|---|
| **Hindi** | `hi` | `0x01` | IndicWav2Vec CTC | Indic-TTS VITS | IndicTrans2 (320M) |
| **Tamil** | `ta` | `0x02` | IndicWav2Vec CTC | Indic-TTS VITS | IndicTrans2 (320M) |
| **Bengali** | `bn` | `0x03` | IndicWav2Vec CTC | Indic-TTS VITS | IndicTrans2 (320M) |
| **Gujarati** | `gu` | `0x04` | IndicWav2Vec CTC | Indic-TTS VITS | IndicTrans2 (320M) |
| **Marathi** | `mr` | `0x05` | IndicWav2Vec CTC | Indic-TTS VITS | IndicTrans2 (320M) |
| **Kannada** | `kn` | `0x06` | IndicWav2Vec CTC | Indic-TTS VITS | IndicTrans2 (320M) |
| **Malayalam** | `ml` | `0x07` | IndicWav2Vec CTC | Indic-TTS VITS | IndicTrans2 (320M) |
| **Telugu** | `te` | `0x08` | IndicWav2Vec CTC | Indic-TTS VITS | IndicTrans2 (320M) |
| **Odia** | `or` | `0x09` | IndicWav2Vec CTC | Indic-TTS VITS | IndicTrans2 (320M) |
| **English** | `en` | `0x0A` | Wav2Vec2 CTC | FastSpeech2 / VITS | IndicTrans2 (320M) |

---

## 2. Model Quantization & Size Reduction

Raw PyTorch models are ~600MB–1.2GB each, which would quickly exhaust mobile flash storage and RAM. iTantra applies **Dynamic INT8 Quantization** via `onnxruntime.quantization.quantize_dynamic`:

```text
Full Precision Float32 Model (~620 MB)
              │
              ▼ Dynamic Quantization (QUInt8 Weights)
Quantized INT8 ONNX Model (~148 MB)  [76% Size Reduction]
```

### Memory Footprint Comparison

| Component | Uncompressed FP32 | INT8 Quantized | Resident RAM (Active) |
|---|---|---|---|
| **IndicWav2Vec CTC (STT)** | ~380 MB | **~95 MB** | ~140 MB |
| **Indic-TTS VITS (TTS)** | ~450 MB | **~112 MB** | ~160 MB |
| **IndicTrans2 (MT)** | ~1.2 GB | **~240 MB** | ~220 MB (Transient) |

---

## 3. Clause Chunking & TTS Pipelining

VITS is a non-autoregressive acoustic generator that synthesizes speech for entire sentences. Waiting for an entire paragraph to synthesize before playing creates an unacceptable delay.

iTantra implements **Clause-Level Pipelining**:
1. Incoming text is split into clauses using punctuation boundaries (`।`, `.`, `,`, `?`, `!`, `;`).
2. Chunk 1 is immediately sent to `OnnxVitsTtsEngine` and begins playback via `AudioTrack`.
3. While Chunk 1 is playing, Chunk 2 is synthesized in parallel.
4. This reduces initial speech playback latency to **< 400 ms**.

```text
[ Text: "हम पहुंच गए हैं। कृपया स्थिति बताएं।" ]
             │
             ├── Chunk 1: "हम पहुंच गए हैं।"  ──► [ Synth 150ms ] ──► [ PLAYING AUDIO ]
             └── Chunk 2: "कृपया स्थिति बताएं।" ──► [ Synth 180ms (Parallel) ] ──► [ PLAYING AUDIO ]
```

---

## 4. Number Normalization & Lexicon Review

Raw CTC speech recognition outputs numbers as digits or words depending on acoustic context. Before synthesis, iTantra routes text through language-specific rule engines in `:core`:
- Expands numerical digits (e.g., `100` ➔ `एक सौ` in Hindi / `நூறு` in Tamil).
- Expands common military and civilian distress abbreviations.
- Preserves zero-width joiners and Indic vowel matras.
