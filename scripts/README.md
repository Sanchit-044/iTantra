# Model Export, Quantization & Build Scripts

This directory documents the Python toolchains and automation scripts used to export, convert, and quantize open-source neural network weights into lightweight, INT8 ONNX models optimized for on-device CPU execution on Android.

## Scripts Overview

| Script | Purpose | Output Location |
|---|---|---|
| [`export_models.py`](../export_models.py) | Downloads and exports AI4Bharat IndicWav2Vec CTC acoustic models to ONNX float32 and INT8. | `app/src/main/assets/models/stt/` |
| [`export_tts_models.py`](../export_tts_models.py) | Exports AI4Bharat Indic-TTS VITS models into quantized ONNX graphs and character vocabularies. | `app/src/main/assets/models/tts/` |
| [`export_translation_models.py`](../export_translation_models.py) | Downloads IndicTrans2 320M, exports Encoder + Decoder to ONNX, quantizes to INT8, and builds BPE vocabularies. | `app/src/main/assets/models/translation/` |

## Setup & Prerequisites

```bash
# Recommended Python 3.10+ virtual environment
python -m venv .venv
source .venv/bin/activate  # Or on Windows: .venv\Scripts\activate

# Install required dependencies
pip install torch torchaudio transformers optimum onnx onnxruntime sentencepiece protobuf
```

## Running Model Conversion

```bash
# 1. Export STT Acoustic Models
python export_models.py --lang hi

# 2. Export TTS Voice Synthesizers
python export_tts_models.py --lang hi

# 3. Export Translation Models (Hindi <-> Marathi)
python export_translation_models.py
```
