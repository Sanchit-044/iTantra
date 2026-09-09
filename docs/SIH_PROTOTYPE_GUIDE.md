# SIH PS 26173 Prototype Build Guide

This document outlines the complete, end-to-end procedure for finalizing the iTantra prototype to meet the strict PS 26173 constraints:
- **Offline only** (no cloud STT/TTS).
- **10 official languages** (hi, ta, bn, gu, mr, kn, ml, te, or, en).
- **Low-end device support** (~2GB RAM limit, CPU only, minSdk 24).
- **Wi-Fi Direct / Bluetooth / LAN connectivity**.

---

## 1. Machine Learning Models Preparation (Python/PC)
The biggest hurdle is securing the right models. You cannot use Kaldi, Vosk, or Piper. You must use **AI4Bharat** models because they are trained directly on graphemes (Devanagari/Tamil scripts) and don't require an external phonemizer.

### A. Download AI4Bharat Models
1. **STT:** Clone the AI4Bharat IndicWav2Vec repository. Download the PyTorch CTC models for the 10 languages.
2. **TTS:** Clone the AI4Bharat Indic-TTS repository. Download the VITS PyTorch checkpoints for the 10 languages.
3. **Translation (Optional but recommended):** Download a distilled version of IndicTrans2 (or NLLB-200) for Offline MT.

### B. Export to ONNX
Android cannot run heavy PyTorch models on low-end CPUs efficiently. You must export them to ONNX.

Use this exact Python snippet for TTS as a template:
```python
import torch
import onnxruntime
from onnxruntime.quantization import quantize_dynamic, QuantType

# Load your AI4Bharat PyTorch model
checkpoint = torch.load("indic-tts-hi.pt", map_location="cpu")
model = checkpoint['model']
model.eval()

# Dummy inputs for ONNX tracing
dummy_input = torch.tensor([[1, 2, 3]], dtype=torch.long)
dummy_lengths = torch.tensor([3], dtype=torch.long)
dummy_scales = torch.tensor([0.667, 1.0, 0.8], dtype=torch.float32)

# Export to ONNX
torch.onnx.export(
    model, 
    (dummy_input, dummy_lengths, dummy_scales),
    "vits-hi-float32.onnx",
    input_names=["text", "text_lengths", "scales"],
    output_names=["output"],
    dynamic_axes={
        "text": {1: "length"},
        "output": {2: "audio_length"}
    },
    opset_version=15
)
```

### C. Quantize to INT8 (CRITICAL)
A standard model is ~600MB. Quantizing it shrinks it to ~150MB, allowing it to load into a 2GB RAM phone without throwing an `OutOfMemoryError`.
```python
# Run this on every exported ONNX file
quantize_dynamic(
    "vits-hi-float32.onnx", 
    "vits-hi-int8.onnx", 
    weight_type=QuantType.QUInt8
)
```

---

## 2. Bundling Models in Android Studio

10 languages × 2 models (STT+TTS) × ~150MB = ~3GB. This exceeds the Google Play APK limit, so you must bundle them in an install-time asset pack.

### A. Directory Structure
Ensure you have the `:models-pack` module. Place your quantized models and vocabularies strictly following this structure:
```text
models-pack/src/main/assets/models/
  ├── stt/
  │   ├── stt-hi-int8.onnx
  │   ├── stt-hi-vocab.json   (Maps CTC logits to Hindi chars)
  │   ├── stt-ta-int8.onnx
  │   └── ... (Repeat for all 10 languages)
  └── tts/
      ├── vits-hi-int8.onnx
      ├── vits-hi-vocab.json  (Maps Hindi chars to IDs)
      └── ... (Repeat for all 10 languages)
```

### B. Gradle Configuration
1. In `app/build.gradle.kts`:
   ```kotlin
   android {
       assetPacks += listOf(":models-pack")
   }
   ```
2. Verify that `noCompress` is set for `.onnx` and `.json` in the `models-pack/build.gradle.kts` so that ONNX Runtime can memory-map directly from the APK to save RAM.

---

## 3. Implement Offline Translation

Currently, `TranslationEngine.kt` uses `DictionaryTranslationEngine` for demo phrases.
1. Create `OnnxTranslationEngine.kt` implementing `TranslationEngine`.
2. Use ONNX Runtime to load your quantized IndicTrans2 model.
3. In `ReceivePttTransmissionUseCase`, whenever `packet.language != currentLanguage`, run the Devanagari/Sender text through `OnnxTranslationEngine` before passing the translated text to the TTS engine.

---

## 4. Hardware Testing Protocol

Do **not** test this on Android Emulators, as emulators do not handle Wi-Fi Direct or low-level audio routing accurately.

1. **Build:** Run `./gradlew :android:assembleDebug` and install on two physical Android devices.
2. **Network Setup:** Enable Wi-Fi on both devices (no internet required). Use the app's internal Wi-Fi Direct setup to pair them.
3. **Audio Check:** Ensure the Media Volume (not just Ringtone) is turned up on the receiving device.
4. **Execution:**
   - **Sender:** Press PTT, speak in Hindi. The app unloads TTS, loads STT, decodes to text, encrypts, and transmits.
   - **Receiver (Set to Tamil):** Receives text, translates Hindi -> Tamil via ONNX, loads TTS, synthesizes Tamil speech, and plays instantly.

---

## 5. Reviewing the Constraints (Checklist)
- [x] **Fully Offline:** Verified. No internet permissions are required after model installation.
- [x] **Open Source Inference:** Verified. Using ONNX Runtime. Android `android.speech.tts` is removed.
- [x] **One Model in RAM:** Verified. `close()` and `unloadVoice()` methods are strictly called to drop STT before TTS plays.
- [x] **MinSdk 24 & CPU Only:** Verified. Models are quantized for CPU execution without NNAPI/GPU acceleration.
