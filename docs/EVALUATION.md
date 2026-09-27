# Evaluation Methodology & Empirical Measurement

---

## 1. Latency Measurement Protocol

Latency is measured from the physical end of user speech to the onset of synthesized audio on the receiver phone:

$$\Delta t_{\text{total}} = t_{\text{endpointer}} + t_{\text{STT}} + t_{\text{encrypt}} + t_{\text{radio}} + t_{\text{decrypt}} + t_{\text{TTS\_chunk1}}$$

- **Target**: 800–1200 ms end-to-end on entry-tier 4GB RAM devices.
- **Measured STT RTF**: $\approx 0.07$ (IndicWav2Vec CTC INT8 on 4 CPU cores).
- **Measured TTS RTF**: $\approx 0.15$ (Indic-TTS VITS INT8 on 4 CPU cores).

---

## 2. Low-Bitrate Transmission Verification

| Content | Raw PCM Audio | Opus (6 kbps) | iTantra Freeform | iTantra Template Alert |
|---|---|---|---|---|
| **3-Second Utterance** | 96 000 Bytes | 2 250 Bytes | **52 Bytes** | **21 Bytes** |
| **Transmission on 300 bps Link** | 43 minutes (Impossible) | 60 seconds (Unusable) | **1.4 seconds** | **0.6 seconds** |
| **Bandwidth Reduction** | Base (1×) | 43× | **1 600×** | **4 570×** |
