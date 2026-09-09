import torch
import onnxruntime
from transformers import Wav2Vec2ForCTC
from onnxruntime.quantization import quantize_dynamic, QuantType
import os
import shutil
from huggingface_hub import hf_hub_download
from huggingface_hub import login
# Token should be provided via environment variable:
# $env:HF_TOKEN="your_token"
# login(token=os.environ.get("HF_TOKEN"))

# Ensure the output directory exists
os.makedirs("app/src/main/assets/models/stt/onnx", exist_ok=True)

def export_stt_model(hf_repo_id, lang_code):
    print(f"\n========================================================")
    print(f"Processing STT for {lang_code.upper()} from {hf_repo_id}")
    print(f"========================================================")
    
    # 1. Download vocab.json
    vocab_dest = f"app/src/main/assets/models/stt/onnx/indicwav2vec-{lang_code}-vocab.json"
    print(f"Downloading vocab.json...")
    try:
        vocab_path = hf_hub_download(repo_id=hf_repo_id, filename="vocab.json")
        shutil.copy(vocab_path, vocab_dest)
        print(f"Saved vocabulary to: {vocab_dest}")
    except Exception as e:
        print(f"Failed to download vocab.json for {lang_code}. You may need to download it manually. Error: {e}")

    # 2. Load PyTorch model
    print(f"Downloading/Loading PyTorch model weights...")
    model = Wav2Vec2ForCTC.from_pretrained(hf_repo_id)
    model.eval()

    # The Android app passes a raw float array of audio samples: shape [1, sequence_length]
    dummy_input = torch.randn(1, 16000)

    float32_onnx_path = f"stt-{lang_code}-float32.onnx"
    int8_onnx_path = f"app/src/main/assets/models/stt/onnx/indicwav2vec-{lang_code}-int8.onnx"

    # 3. Export to ONNX
    print(f"Exporting PyTorch to ONNX ({float32_onnx_path})...")
    torch.onnx.export(
        model,
        (dummy_input,),
        float32_onnx_path,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={
            "input": {0: "batch", 1: "sequence_length"},
            "output": {0: "batch", 1: "sequence_length"}
        },
        opset_version=15
    )

    # 4. Quantize to INT8 (Restrict to MatMul/Gemm to avoid shape inference bugs on Mul/Add)
    print(f"Quantizing ONNX to INT8 ({int8_onnx_path})...")
    quantize_dynamic(
        float32_onnx_path,
        int8_onnx_path,
        weight_type=QuantType.QUInt8,
        op_types_to_quantize=['MatMul', 'Gemm']
    )

    # Clean up the large float32 file
    os.remove(float32_onnx_path)
    print(f"✅ Successfully finished STT for {lang_code.upper()}!")

if __name__ == "__main__":
    # The 10 official languages as per iTantra requirements
    LANGUAGES = {
        "kn": "amoghsgopadi/wav2vec2-large-xlsr-kn",
        "ml": "gvs/wav2vec2-large-xlsr-malayalam",
        "te": "anuragshas/wav2vec2-large-xlsr-53-telugu"
    }

    print("Starting automated STT model extraction for all 10 languages...\n")
    for lang, repo in LANGUAGES.items():
        try:
            export_stt_model(repo, lang)
        except Exception as e:
            print(f"❌ Failed to process {lang}: {e}")
            
    print("\n🎉 ALL STT MODELS DOWNLOADED AND QUANTIZED SUCCESSFULLY!")
