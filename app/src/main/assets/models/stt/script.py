import os
import json
import pathlib
original_unlink = pathlib.Path.unlink
def safe_unlink(self, *args, **kwargs):
    try:
        original_unlink(self, *args, **kwargs)
    except PermissionError:
        pass
pathlib.Path.unlink = safe_unlink

from transformers import Wav2Vec2Processor
from optimum.onnxruntime import ORTModelForCTC
from onnxruntime.quantization import quantize_dynamic, QuantType

MODEL_ID = "ai4bharat/indicwav2vec-hindi"
EXPORT_DIR = "indicwav2vec_onnx_export"

print(f"Downloading and exporting {MODEL_ID} to ONNX...")

from huggingface_hub import get_token
hf_token = get_token()

# 1. Export the PyTorch model to ONNX
# This downloads the PyTorch weights and converts the computational graph to ONNX
model = ORTModelForCTC.from_pretrained(MODEL_ID, export=True, token=hf_token)
model.save_pretrained(EXPORT_DIR)

# 2. Extract and format the vocab.json
# The Android app expects a simple JSON map of token to ID
print("Extracting vocabulary...")
processor = Wav2Vec2Processor.from_pretrained(MODEL_ID, token=hf_token)
vocab_dict = processor.tokenizer.get_vocab()

vocab_path = os.path.join(EXPORT_DIR, "indicwav2vec-hi-vocab.json")
with open(vocab_path, "w", encoding="utf-8") as f:
    json.dump(vocab_dict, f, ensure_ascii=False, indent=2)

# 3. (Optional but highly recommended for mobile) Quantize to INT8
print("Quantizing the ONNX model to INT8 (Mobile Friendly)...")
model_input = os.path.join(EXPORT_DIR, "model.onnx")
model_output = os.path.join(EXPORT_DIR, "model_quantized.onnx")

quantize_dynamic(
    model_input=model_input,
    model_output=model_output,
    weight_type=QuantType.QUInt8,
    op_types_to_quantize=["MatMul", "Attention"]
)

print("Export Complete!")
print(f"Now, copy the following files into your Android project at models-pack/src/main/assets/models/stt/onnx/ :")
print(f"  1. {EXPORT_DIR}/model_quantized.onnx -> rename it to indicwav2vec-hi-int8.onnx")
print(f"  2. {vocab_path} -> rename it to indicwav2vec-hi-vocab.json")
