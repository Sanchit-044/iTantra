#!/usr/bin/env python3
"""
Export IndicTrans2 indic-indic distilled 320M to ONNX for on-device translation.

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

import numpy as np
import torch
import onnxruntime
from onnxruntime.quantization import quantize_dynamic, QuantType

OUTPUT_DIR = "app/src/main/assets/models/translation"

# IndicTrans2 indic-indic distilled 320M -- covers all 9 Indic languages + English.
# Despite the "200M" naming of the individual indic-en / en-indic distilled models,
# the indic-indic variant is a 320M model stitched from both of them; there is no
# indictrans2-indic-indic-dist-200M repo on HuggingFace.
MODEL_REPO = "ai4bharat/indictrans2-indic-indic-dist-320M"

# FLORES-200 language tags used by IndicTrans2
LANG_TAGS = [
    "hin_Deva", "tam_Taml", "ben_Beng", "guj_Gujr", "mar_Deva",
    "kan_Knda", "mal_Mlym", "tel_Telu", "ory_Orya", "eng_Latn",
]


def _split_vocab(raw_vocab: dict) -> tuple[dict, dict, dict]:
    """Partition one flat token->id dict into (regular, special, lang_tags)."""
    special_tokens = {}
    lang_tags = {}
    regular_vocab = {}

    for token, idx in raw_vocab.items():
        if token in ("<s>", "</s>", "<pad>", "<unk>"):
            special_tokens[token] = idx
        elif any(token == tag or token == f"__{tag}__" or token == f"<{tag}>"
                 for tag in LANG_TAGS):
            bare = token.strip("<>_")
            lang_tags[bare] = idx
        elif token.startswith("__") and token.endswith("__"):
            bare = token.strip("_")
            if bare in LANG_TAGS:
                lang_tags[bare] = idx
            else:
                special_tokens[token] = idx
        else:
            regular_vocab[token] = idx

    return regular_vocab, special_tokens, lang_tags


def export_tokenizer_vocab(tokenizer, output_path: str):
    """
    Export the SentencePiece tokenizer as a JSON file that the Kotlin
    BpeTokenizer can consume.

    IndicTrans2's tokenizer is NOT one shared vocabulary -- it pairs a single
    SentencePiece BPE model (identical `model.SRC` / `model.TGT`, so the merge
    rules are shared) with *two different* token->id dictionaries
    (`src_encoder` / `tgt_encoder`, ~122.7k entries each, agreeing on fewer than
    50 ids). Encoding source text must use `src_encoder`'s ids (that is what the
    encoder's embedding table was trained against); decoding the model's output
    and constructing decoder_input_ids must use `tgt_encoder`'s ids instead. Using
    one dict for both -- what an earlier version of this script did -- silently
    feeds the encoder/decoder wrong ids for every ambiguous token.

    Structure:
    {
      "merges": ["▁ h", "e l", ...],
      "src": {
        "vocab": { "▁": 4, "hello": 57, ... },
        "special_tokens": { "<s>": 0, "</s>": 2, "<pad>": 1, "<unk>": 3 },
        "lang_tags": { "hin_Deva": 256001, ... }
      },
      "tgt": { "vocab": {...}, "special_tokens": {...}, "lang_tags": {...} }
    }
    """
    print("Exporting tokenizer vocabulary...")

    if not (hasattr(tokenizer, "src_encoder") and hasattr(tokenizer, "tgt_encoder")):
        raise AttributeError(
            f"{type(tokenizer).__name__} has no separate src_encoder/tgt_encoder -- "
            "this exporter is written for IndicTrans2's dual-vocabulary tokenizer. "
            "If this is a different model family, use tokenizer.get_vocab() instead."
        )

    src_regular, src_special, src_lang = _split_vocab(dict(tokenizer.src_encoder))
    tgt_regular, tgt_special, tgt_lang = _split_vocab(dict(tokenizer.tgt_encoder))

    # Ensure BOS/EOS/PAD/UNK are present on both sides even if _split_vocab's
    # literal-name match missed a differently-spelled token in either dict.
    for regular, special, prefix in ((src_regular, src_special, "src"), (tgt_regular, tgt_special, "tgt")):
        encoder = tokenizer.src_encoder if prefix == "src" else tokenizer.tgt_encoder
        for name, attr in (("<s>", "bos_token"), ("</s>", "eos_token"),
                           ("<pad>", "pad_token"), ("<unk>", "unk_token")):
            if name not in special:
                token_str = getattr(tokenizer, attr, name)
                if token_str in encoder:
                    special[name] = encoder[token_str]

    merges = extract_bpe_merges(tokenizer)

    result = {
        "merges": merges,
        "src": {"vocab": src_regular, "special_tokens": src_special, "lang_tags": src_lang},
        "tgt": {"vocab": tgt_regular, "special_tokens": tgt_special, "lang_tags": tgt_lang},
    }

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=None, separators=(",", ":"))

    size_kb = os.path.getsize(output_path) / 1024
    print(f"  Saved vocab (src={len(src_regular)} tok/{len(src_lang)} tags, "
          f"tgt={len(tgt_regular)} tok/{len(tgt_lang)} tags, {len(merges)} shared merges) "
          f"→ {output_path} ({size_kb:.0f} KB)")


def _find_spm_processor(tokenizer):
    """
    Locate the underlying SentencePieceProcessor, whatever this tokenizer class
    calls it. `sp_model` is the common attribute name across most HF wrappers;
    IndicTrans2's custom tokenizer instead exposes `src_spm` / `tgt_spm` (see
    export_tokenizer_vocab's docstring for why these two are used, not merged).
    """
    if hasattr(tokenizer, "sp_model"):
        return tokenizer.sp_model
    if hasattr(tokenizer, "src_spm"):
        if hasattr(tokenizer, "tgt_spm"):
            src, tgt = tokenizer.src_spm, tokenizer.tgt_spm
            same_size = src.GetPieceSize() == tgt.GetPieceSize()
            same_pieces = same_size and all(
                src.IdToPiece(i) == tgt.IdToPiece(i) for i in range(src.GetPieceSize())
            )
            if not same_pieces:
                print(
                    "  Warning: src_spm and tgt_spm segment text differently -- merges "
                    "extracted from src_spm only will not describe tgt_spm's BPE merges."
                )
        return tokenizer.src_spm
    return None


def extract_bpe_merges(tokenizer):
    """
    Extract ordered BPE merge rules from the tokenizer's SentencePiece model.

    SentencePiece BPE does not store an explicit (left, right) merge list the way
    GPT-2's merges.txt does -- it stores pieces in an order where a piece's id is
    lower than any piece it can be built from (lower id = higher merge priority,
    same idea as GPT-2's merge rank). For every multi-character piece we search
    all ways to split it into two pieces that are *both* already in the
    vocabulary and take the split whose higher-id half is lowest -- the split
    that would have been merged first during training.
    """
    sp = _find_spm_processor(tokenizer)
    if sp is not None:
        try:
            vocab_pieces = [
                (i, sp.IdToPiece(i))
                for i in range(sp.GetPieceSize())
                if not sp.IsByte(i) and not sp.IsControl(i) and not sp.IsUnknown(i)
                and len(sp.IdToPiece(i)) > 1
            ]
            vocab_pieces.sort(key=lambda x: x[0])

            merges = []
            for _, piece in vocab_pieces:
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

    # Fallback: a merges.txt file next to the vocab, GPT-2-tokenizer style.
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


def _remove_with_external_data(onnx_path: str):
    """
    Remove an ONNX file plus its `.data` companion, if any.

    torch.onnx's dynamo-based exporter writes large tensors as an external-data
    file next to the small graph-only `.onnx` file rather than embedding them
    inline. `os.remove(onnx_path)` alone silently leaves that companion (hundreds
    of MB to over a GB for a model this size) behind on every run.
    """
    os.remove(onnx_path)
    data_path = onnx_path + ".data"
    if os.path.exists(data_path):
        os.remove(data_path)


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
    # IndicTransConfig declares an attribute_map claiming "hidden_size" aliases
    # "d_model", but never actually sets either -- only encoder_embed_dim /
    # decoder_embed_dim are real fields on this model. Try the generic names first
    # for compatibility with other encoder-decoder configs, then fall back to
    # IndicTrans2's actual field (the encoder's output width, which is what the
    # decoder's cross-attention consumes).
    config = model.config
    hidden_dim = (
        getattr(config, "d_model", None)
        or getattr(config, "hidden_size", None)
        or getattr(config, "encoder_embed_dim", None)
    )
    if hidden_dim is None:
        raise AttributeError(
            "Could not determine the model's hidden dimension from its config "
            f"(checked d_model, hidden_size, encoder_embed_dim on {type(config).__name__})"
        )
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
    _remove_with_external_data(encoder_fp32)
    print(f"  Encoder INT8: {os.path.getsize(encoder_int8) / 1e6:.1f} MB")

    print("Quantising decoder to INT8...")
    quantize_dynamic(
        decoder_fp32, decoder_int8,
        weight_type=QuantType.QUInt8,
        op_types_to_quantize=["MatMul", "Gemm"],
    )
    _remove_with_external_data(decoder_fp32)
    print(f"  Decoder INT8: {os.path.getsize(decoder_int8) / 1e6:.1f} MB")

    total = (os.path.getsize(encoder_int8) + os.path.getsize(decoder_int8)) / 1e6
    print(f"\n  Total model size: {total:.1f} MB")


def verify_onnx(tokenizer):
    """
    Run one real translation through the quantised ONNX graphs end to end --
    not just "the files load" but "feeding real input through both graphs with
    the real dual src/tgt tokenizer produces a plausible sentence." This is the
    same encoder-input framing and greedy loop IndicTransOnnxTranslationEngine.kt
    implements; if this produces garbage, the Kotlin side will too.
    """
    print("\nVerifying ONNX models...")

    encoder_path = os.path.join(OUTPUT_DIR, "indictrans2-encoder-int8.onnx")
    decoder_path = os.path.join(OUTPUT_DIR, "indictrans2-decoder-int8.onnx")

    enc_session = onnxruntime.InferenceSession(encoder_path)
    dec_session = onnxruntime.InferenceSession(decoder_path)

    print(f"  Encoder inputs:  {[i.name for i in enc_session.get_inputs()]}")
    print(f"  Encoder outputs: {[o.name for o in enc_session.get_outputs()]}")
    print(f"  Decoder inputs:  {[i.name for i in dec_session.get_inputs()]}")
    print(f"  Decoder outputs: {[o.name for o in dec_session.get_outputs()]}")

    src_lang, tgt_lang = "hin_Deva", "ben_Beng"
    test_text = "मुझे मदद चाहिए"
    print(f"\n  Test translate ({src_lang} -> {tgt_lang}): '{test_text}'")

    # [src_lang_tag] [tgt_lang_tag] [BPE tokens] [EOS] -- see
    # IndicTransOnnxTranslationEngine.kt's class doc for why both tags, not one.
    src_eos_id = tokenizer.src_encoder["</s>"]
    # add_special_tokens=False: encode() would otherwise append its own EOS via
    # build_inputs_with_special_tokens, doubling up with the explicit one below --
    # mirrors exactly what BpeTokenizer.encode()/IndicTransOnnxTranslationEngine.kt do.
    input_ids = np.array(
        [tokenizer.encode(f"{src_lang} {tgt_lang} {test_text}", add_special_tokens=False) + [src_eos_id]],
        dtype=np.int64,
    )
    attention_mask = np.ones_like(input_ids)
    print(f"  Encoder input ids: {input_ids[0].tolist()}")

    (encoder_hidden,) = enc_session.run(
        None, {"input_ids": input_ids, "attention_mask": attention_mask}
    )

    # IndicTrans2 has no decoder-side language tag (no forced_bos_token_id, no
    # per-language start token in modeling_indictrans.py, and tgt_encoder carries
    # no language tags at all) -- the target language is communicated entirely by
    # the tag already embedded in the encoder input above. The decoder seed is
    # just decoder_start_token_id, which equals this side's own </s> id.
    tgt_eos_id = tokenizer.tgt_encoder["</s>"]
    tgt_pad_id = tokenizer.tgt_encoder["<pad>"]
    generated = [tgt_eos_id]

    for _ in range(30):
        decoder_input_ids = np.array([generated], dtype=np.int64)
        (logits,) = dec_session.run(
            None,
            {
                "decoder_input_ids": decoder_input_ids,
                "encoder_hidden_states": encoder_hidden,
                "encoder_attention_mask": attention_mask,
            },
        )
        next_id = int(np.argmax(logits[0, -1]))
        if next_id in (tgt_eos_id, tgt_pad_id):
            break
        generated.append(next_id)

    pieces = [tokenizer.tgt_decoder.get(i, "<unk>") for i in generated[1:]]
    result = "".join(pieces).replace("▁", " ").strip()
    print(f"  Decoded ({len(generated) - 1} tokens): '{result}'")
    if not result:
        raise RuntimeError(
            "ONNX round-trip produced an empty translation -- export or framing is still wrong."
        )
    print("  ✅ ONNX models verified successfully!")


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
