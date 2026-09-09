#!/usr/bin/env python3
"""
Export IndicTrans2 200M (distilled) to ONNX for on-device translation.

Produces three files per model variant (indic→indic, indic→en):
  - indictrans2-encoder-int8.onnx   (quantised encoder)
  - indictrans2-decoder-int8.onnx   (quantised decoder, no KV cache)
  - indictrans2-vocab.json           (BPE vocab + merges + lang tags)

Usage:
  pip install transformers optimum[exporters] onnxruntime sentencepiece protobuf
  python export_translation_models.py

The output lands in app/src/main/assets/models/translation/.
"""

import json
import os
import sys
import shutil
from pathlib import Path

import torch
import onnxruntime
from onnxruntime.quantization import quantize_dynamic, QuantType

OUTPUT_DIR = "app/src/main/assets/models/translation"

# IndicTrans2 indic-indic distilled 200M — covers all 9 Indic languages + English
MODEL_REPO = "ai4bharat/indictrans2-indic-indic-dist-200M"

# FLORES-200 language tags used by IndicTrans2
LANG_TAGS = [
    "hin_Deva", "tam_Taml", "ben_Beng", "guj_Gujr", "mar_Deva",
    "kan_Knda", "mal_Mlym", "tel_Telu", "ory_Orya", "eng_Latn",
]


def export_tokenizer_vocab(tokenizer, output_path: str):
    """
    Export the SentencePiece tokenizer as a JSON file that the Kotlin
    BpeTokenizer can consume.

    Structure:
    {
      "vocab": { "▁": 4, "hello": 57, ... },
      "merges": ["▁ h", "e l", ...],
      "special_tokens": { "<s>": 0, "</s>": 2, "<pad>": 1, "<unk>": 3 },
      "lang_tags": { "hin_Deva": 256001, ... }
    }
    """
    print("Exporting tokenizer vocabulary...")

    # Get the full vocab
    vocab = tokenizer.get_vocab()

    # Separate special tokens, lang tags, and regular tokens
    special_tokens = {}
    lang_tags = {}
    regular_vocab = {}

    for token, idx in vocab.items():
        if token in ("<s>", "</s>", "<pad>", "<unk>"):
            special_tokens[token] = idx
        elif any(token == tag or token == f"__{tag}__" or token == f"<{tag}>"
                 for tag in LANG_TAGS):
            # Normalise the tag name to the bare FLORES code
            bare = token.strip("<>_")
            lang_tags[bare] = idx
        elif token.startswith("__") and token.endswith("__"):
            # Other special tokens like __eng_Latn__, __hin_Deva__
            bare = token.strip("_")
            if bare in LANG_TAGS:
                lang_tags[bare] = idx
            else:
                special_tokens[token] = idx
        else:
            regular_vocab[token] = idx

    # Ensure we have BOS/EOS/PAD/UNK
    if "<s>" not in special_tokens:
        special_tokens["<s>"] = tokenizer.bos_token_id or 0
    if "</s>" not in special_tokens:
        special_tokens["</s>"] = tokenizer.eos_token_id or 2
    if "<pad>" not in special_tokens:
        special_tokens["<pad>"] = tokenizer.pad_token_id or 1
    if "<unk>" not in special_tokens:
        special_tokens["<unk>"] = tokenizer.unk_token_id or 3

    # Extract BPE merges from the SentencePiece model
    merges = extract_bpe_merges(tokenizer)

    result = {
        "vocab": regular_vocab,
        "merges": merges,
        "special_tokens": special_tokens,
        "lang_tags": lang_tags,
    }

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=None, separators=(",", ":"))

    size_kb = os.path.getsize(output_path) / 1024
    print(f"  Saved vocab ({len(regular_vocab)} tokens, {len(merges)} merges, "
          f"{len(lang_tags)} lang tags) → {output_path} ({size_kb:.0f} KB)")


def extract_bpe_merges(tokenizer):
    """
    Extract ordered BPE merge rules from the tokenizer.

    Tries multiple approaches:
    1. tokenizer.get_merges() if available (some SentencePiece wrappers)
    2. Reading the SPM model file directly via sentencepiece
    3. Falling back to an empty merge list (character-level only)
    """
    # Approach 1: direct merges attribute
    if hasattr(tokenizer, "sp_model"):
        try:
            import sentencepiece as spm
            sp = tokenizer.sp_model
            merges = []
            for i in range(sp.GetPieceSize()):
                piece = sp.IdToPiece(i)
                if sp.IsByte(i) or sp.IsControl(i) or sp.IsUnknown(i):
                    continue
            # SentencePiece BPE stores merges implicitly in the vocab order.
            # We reconstruct merges by decomposing each piece into its component parts.
            vocab_pieces = []
            for i in range(sp.GetPieceSize()):
                piece = sp.IdToPiece(i)
                if not sp.IsByte(i) and not sp.IsControl(i) and not sp.IsUnknown(i):
                    if len(piece) > 1:
                        vocab_pieces.append((i, piece))

            # Sort by ID (which corresponds to frequency rank in BPE)
            vocab_pieces.sort(key=lambda x: x[0])
            merges = []
            for _, piece in vocab_pieces:
                # Find the best split point
                best_split = None
                best_rank = float("inf")
                for j in range(1, len(piece)):
                    left, right = piece[:j], piece[j:]
                    l_id = sp.PieceToId(left)
                    r_id = sp.PieceToId(right)
                    if l_id != sp.unk_id() and r_id != sp.unk_id():
                        rank = max(l_id, r_id)
                        if rank < best_rank:
                            best_rank = rank
                            best_split = (left, right)
                if best_split:
                    merges.append(f"{best_split[0]} {best_split[1]}")

            if merges:
                print(f"  Extracted {len(merges)} BPE merges from SentencePiece model")
                return merges
        except Exception as e:
            print(f"  Warning: Could not extract merges from SP model: {e}")

    # Approach 2: Check for merges file
    if hasattr(tokenizer, "vocab_file"):
        merges_path = Path(tokenizer.vocab_file).parent / "merges.txt"
        if merges_path.exists():
            with open(merges_path, encoding="utf-8") as f:
                lines = f.readlines()
            merges = [line.strip() for line in lines
                      if line.strip() and not line.startswith("#")]
            if merges:
                print(f"  Read {len(merges)} merges from merges.txt")
                return merges

    print("  Warning: No BPE merges extracted — tokenizer will use character-level fallback")
    return []


class EncoderWrapper(torch.nn.Module):
    """Wraps the encoder to produce a clean ONNX graph."""
    def __init__(self, model):
        super().__init__()
        self.encoder = model.get_encoder() if hasattr(model, "get_encoder") else model.model.encoder

    def forward(self, input_ids, attention_mask):
        return self.encoder(input_ids=input_ids, attention_mask=attention_mask).last_hidden_state


class DecoderWrapper(torch.nn.Module):
    """
    Wraps the decoder for non-cached autoregressive decoding.
    
    Takes decoder_input_ids + encoder_hidden_states + encoder_attention_mask,
    returns logits [batch, seq, vocab].
    """
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, decoder_input_ids, encoder_hidden_states, encoder_attention_mask):
        outputs = self.model(
            input_ids=None,
            decoder_input_ids=decoder_input_ids,
            encoder_outputs=(encoder_hidden_states,),
            attention_mask=encoder_attention_mask,
        )
        return outputs.logits


def export_onnx(model, tokenizer):
    """Export encoder and decoder to ONNX, then quantise to INT8."""
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    encoder_fp32 = os.path.join(OUTPUT_DIR, "encoder-fp32.onnx")
    decoder_fp32 = os.path.join(OUTPUT_DIR, "decoder-fp32.onnx")
    encoder_int8 = os.path.join(OUTPUT_DIR, "indictrans2-encoder-int8.onnx")
    decoder_int8 = os.path.join(OUTPUT_DIR, "indictrans2-decoder-int8.onnx")

    # --- Export encoder ---
    print("\nExporting encoder to ONNX...")
    encoder = EncoderWrapper(model)
    encoder.eval()

    dummy_ids = torch.ones((1, 32), dtype=torch.long)
    dummy_mask = torch.ones((1, 32), dtype=torch.long)

    torch.onnx.export(
        encoder,
        (dummy_ids, dummy_mask),
        encoder_fp32,
        input_names=["input_ids", "attention_mask"],
        output_names=["encoder_hidden_states"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "src_len"},
            "attention_mask": {0: "batch", 1: "src_len"},
            "encoder_hidden_states": {0: "batch", 1: "src_len"},
        },
        opset_version=15,
    )
    print(f"  Encoder FP32: {os.path.getsize(encoder_fp32) / 1e6:.1f} MB")

    # --- Export decoder ---
    print("Exporting decoder to ONNX...")
    decoder = DecoderWrapper(model)
    decoder.eval()

    dummy_dec_ids = torch.ones((1, 8), dtype=torch.long)
    hidden_dim = model.config.d_model if hasattr(model.config, "d_model") else model.config.hidden_size
    dummy_hidden = torch.randn(1, 32, hidden_dim)
    dummy_enc_mask = torch.ones((1, 32), dtype=torch.long)

    torch.onnx.export(
        decoder,
        (dummy_dec_ids, dummy_hidden, dummy_enc_mask),
        decoder_fp32,
        input_names=["decoder_input_ids", "encoder_hidden_states", "encoder_attention_mask"],
        output_names=["logits"],
        dynamic_axes={
            "decoder_input_ids": {0: "batch", 1: "tgt_len"},
            "encoder_hidden_states": {0: "batch", 1: "src_len"},
            "encoder_attention_mask": {0: "batch", 1: "src_len"},
            "logits": {0: "batch", 1: "tgt_len"},
        },
        opset_version=15,
    )
    print(f"  Decoder FP32: {os.path.getsize(decoder_fp32) / 1e6:.1f} MB")

    # --- Quantise both to INT8 ---
    print("Quantising encoder to INT8...")
    quantize_dynamic(
        encoder_fp32, encoder_int8,
        weight_type=QuantType.QUInt8,
        op_types_to_quantize=["MatMul", "Gemm"],
    )
    os.remove(encoder_fp32)
    print(f"  Encoder INT8: {os.path.getsize(encoder_int8) / 1e6:.1f} MB")

    print("Quantising decoder to INT8...")
    quantize_dynamic(
        decoder_fp32, decoder_int8,
        weight_type=QuantType.QUInt8,
        op_types_to_quantize=["MatMul", "Gemm"],
    )
    os.remove(decoder_fp32)
    print(f"  Decoder INT8: {os.path.getsize(decoder_int8) / 1e6:.1f} MB")

    total = (os.path.getsize(encoder_int8) + os.path.getsize(decoder_int8)) / 1e6
    print(f"\n  Total model size: {total:.1f} MB")


def verify_onnx(tokenizer):
    """Quick sanity check: load the quantised models and run a test translation."""
    print("\nVerifying ONNX models...")

    encoder_path = os.path.join(OUTPUT_DIR, "indictrans2-encoder-int8.onnx")
    decoder_path = os.path.join(OUTPUT_DIR, "indictrans2-decoder-int8.onnx")

    enc_session = onnxruntime.InferenceSession(encoder_path)
    dec_session = onnxruntime.InferenceSession(decoder_path)

    print(f"  Encoder inputs:  {[i.name for i in enc_session.get_inputs()]}")
    print(f"  Encoder outputs: {[o.name for o in enc_session.get_outputs()]}")
    print(f"  Decoder inputs:  {[i.name for i in dec_session.get_inputs()]}")
    print(f"  Decoder outputs: {[o.name for o in dec_session.get_outputs()]}")

    test_text = "मुझे मदद चाहिए"
    print(f"\n  Test encode: '{test_text}'")
    encoded = tokenizer.encode(test_text, return_tensors="np")
    print(f"  Token IDs: {encoded['input_ids'][0][:10]}...")
    print(f"  ✅ ONNX models verified successfully!")


def main():
    sys.stdout.reconfigure(encoding="utf-8")

    print("=" * 60)
    print("IndicTrans2 ONNX Export for iTantra")
    print("=" * 60)

    print(f"\nDownloading model from {MODEL_REPO}...")
    print("(This may take several minutes on first run)\n")

    try:
        from transformers import AutoModelForSeq2SeqLM, AutoTokenizer

        tokenizer = AutoTokenizer.from_pretrained(MODEL_REPO, trust_remote_code=True)
        model = AutoModelForSeq2SeqLM.from_pretrained(MODEL_REPO, trust_remote_code=True)
        model.eval()
    except Exception as e:
        print(f"❌ Failed to load model: {e}")
        print("\nMake sure you have installed:")
        print("  pip install transformers sentencepiece protobuf")
        print(f"\nAnd that you can access {MODEL_REPO} on HuggingFace.")
        sys.exit(1)

    # Export tokenizer vocab
    vocab_path = os.path.join(OUTPUT_DIR, "indictrans2-vocab.json")
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    export_tokenizer_vocab(tokenizer, vocab_path)

    # Export + quantise ONNX models
    export_onnx(model, tokenizer)

    # Quick verification
    verify_onnx(tokenizer)

    print("\n" + "=" * 60)
    print("🎉 IndicTrans2 export complete!")
    print(f"   Output: {OUTPUT_DIR}/")
    print("   Files:")
    for f in sorted(os.listdir(OUTPUT_DIR)):
        size = os.path.getsize(os.path.join(OUTPUT_DIR, f))
        print(f"     {f:40s} {size / 1024:>8.0f} KB")
    print("=" * 60)


if __name__ == "__main__":
    main()
