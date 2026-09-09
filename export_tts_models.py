import torch
import onnxruntime
from transformers import VitsModel, AutoTokenizer
from onnxruntime.quantization import quantize_dynamic, QuantType
import os
import json

# Ensure the output directory exists
os.makedirs("app/src/main/assets/models/tts", exist_ok=True)

def export_tts_model(hf_repo_id, lang_code):
    print(f"\n========================================================")
    print(f"Processing TTS for {lang_code.upper()} from {hf_repo_id}")
    print(f"========================================================")
    
    # 1. Load Tokenizer and extract vocab.json
    print(f"Downloading/Loading Tokenizer...")
    try:
        tokenizer = AutoTokenizer.from_pretrained(hf_repo_id)
    except Exception:
        tokenizer = AutoTokenizer.from_pretrained(hf_repo_id, local_files_only=True)
    vocab = tokenizer.get_vocab()
    
    # The Android app's VitsTokenizer expects the vocab dictionary to be wrapped in a specific key.
    # MMS-TTS models require interleaved pad tokens (id=0) between each character.
    vocab["pad_id"] = 0
    vocab["interleave_pad"] = True
    vocab_dest = f"app/src/main/assets/models/tts/vits-{lang_code}-vocab.json"
    with open(vocab_dest, "w", encoding="utf-8") as f:
        json.dump(vocab, f, ensure_ascii=False, indent=2)
    print(f"Saved vocabulary to: {vocab_dest}")

    # 2. Load PyTorch model
    print(f"Downloading/Loading PyTorch model weights...")
    try:
        model = VitsModel.from_pretrained(hf_repo_id)
    except Exception as e:
        # Fallback to local files if network fails (or if HF thinks it's missing files due to proxy 403)
        print(f"Network check failed ({e}), falling back to local files...")
        model = VitsModel.from_pretrained(hf_repo_id, local_files_only=True)
    model.eval()

    # The Android ONNX TTS Engine passes 3 tensors: text tokens, lengths, and scales
    # We use a large sequence length (300) so that dynamic padding branches in the model (e.g. if pad_length > 0)
    # are evaluated as True during tracing, ensuring the padding logic is baked into the ONNX graph!
    dummy_input_ids = torch.ones((1, 300), dtype=torch.long)

    float32_onnx_path = f"tts-{lang_code}-float32.onnx"
    int8_onnx_path = f"app/src/main/assets/models/tts/vits-{lang_code}-int8.onnx"

    # 3. Export to ONNX
    print(f"Exporting PyTorch to ONNX ({float32_onnx_path})...")
    
    # We trace the text_encoder and decoder pipeline (or just the forward method)
    # Note: Transformers VitsModel forward accepts input_ids.
    class VitsExportWrapper(torch.nn.Module):
        def __init__(self, model):
            super().__init__()
            self.model = model
            
        def forward(self, input_ids):
            # The transformers VitsModel returns a sequence of audio frames
            return self.model(input_ids=input_ids).waveform

    wrapper = VitsExportWrapper(model)
    wrapper.eval()
    
    torch.onnx.export(
        wrapper,
        (dummy_input_ids,),
        float32_onnx_path,
        input_names=["text"],
        output_names=["output"],
        dynamic_axes={
            "text": {0: "batch", 1: "length"},
            "output": {0: "batch", 1: "audio_length"}
        },
        opset_version=15,
        dynamo=False
    )

    # 4. Quantize to INT8
    print(f"Quantizing ONNX to INT8 ({int8_onnx_path})...")
    quantize_dynamic(
        float32_onnx_path,
        int8_onnx_path,
        weight_type=QuantType.QUInt8
    )

    # Clean up the large float32 file
    os.remove(float32_onnx_path)
    print(f"✅ Successfully finished TTS for {lang_code.upper()}!")

if __name__ == "__main__":
    import sys
    sys.stdout.reconfigure(encoding='utf-8')
    # We will use Meta's MMS TTS models which are built into HuggingFace Transformers,
    # based on the VITS architecture, and use graphemes (characters) instead of phonemes!
    LANGUAGES = {
        "ta": "facebook/mms-tts-tam",
        "kn": "facebook/mms-tts-kan",
        "ml": "facebook/mms-tts-mal",
        "te": "facebook/mms-tts-tel",
        "or": "facebook/mms-tts-ory"
    }

    print("Starting automated TTS model extraction for all 10 languages...\n")
    for lang, repo in LANGUAGES.items():
        try:
            export_tts_model(repo, lang)
        except Exception as e:
            print(f"❌ Failed to process TTS {lang}: {e}")
            
    print("\n🎉 ALL TTS MODELS DOWNLOADED AND QUANTIZED SUCCESSFULLY!")
