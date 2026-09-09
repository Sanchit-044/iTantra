"""Export wav2vec2 CTC acoustic models to INT8 ONNX plus their vocabulary.

Run for every configured language, or for a subset:

    python export_models.py            # everything with a repo configured
    python export_models.py mr or      # just these

Like export_tts_models.py, this file used to be edited in place before each run and so
only ever recorded the most recent batch. The repo ids for the earlier batches were
overwritten and are no longer recoverable from the repository, which is why several
entries below are None: the vocabulary in assets proves the model was exported once,
but not from where. Fill an entry in before re-exporting that language rather than
deleting this note.

The two vocabulary conventions in assets are a useful hint when tracking one down:

  <pad>/<s>/</s>/<unk>   fairseq-style, as published by AI4Bharat  -- bn en gu hi or
  [PAD]/[UNK]            HuggingFace community XLSR fine-tunes    -- kn ml mr ta te
"""

import json
import os
import shutil
import sys

import torch
from huggingface_hub import hf_hub_download
from onnxruntime.quantization import QuantType, quantize_dynamic
from transformers import Wav2Vec2ForCTC

OUT_DIR = os.path.join("app", "src", "main", "assets", "models", "stt", "onnx")

# A private model needs $env:HF_TOKEN set; huggingface_hub picks it up automatically.
LANGUAGES = {
    "kn": "amoghsgopadi/wav2vec2-large-xlsr-kn",
    "ml": "gvs/wav2vec2-large-xlsr-malayalam",
    "te": "anuragshas/wav2vec2-large-xlsr-53-telugu",
    # Exported in earlier batches; the repo id was lost when this file was overwritten.
    "hi": None,
    "ta": None,
    "bn": None,
    "gu": None,
    "mr": None,
    "or": None,
    "en": None,
}

# One second of audio is enough to trace the graph; the sequence axis is dynamic.
TRACE_SAMPLES = 16_000


def export_vocab(repo_id, lang_code):
    dest = os.path.join(OUT_DIR, f"indicwav2vec-{lang_code}-vocab.json")
    path = hf_hub_download(repo_id=repo_id, filename="vocab.json")
    shutil.copy(path, dest)

    with open(dest, encoding="utf-8") as handle:
        vocab = json.load(handle)

    # The Android CTC decoder derives the blank id from whichever pad spelling is
    # present. A vocabulary with neither would silently decode against id 0, which is a
    # real character in most of these models and produces confident gibberish.
    pad = next((k for k in ("<pad>", "[PAD]") if k in vocab), None)
    if pad is None:
        raise RuntimeError(
            f"{repo_id} vocab.json has neither <pad> nor [PAD]; the CTC blank id "
            "cannot be determined and decoding would produce garbage"
        )
    if "|" not in vocab:
        print("  WARNING: no '|' word delimiter; decoded text will have no word breaks")

    print(f"  vocab -> {dest} ({len(vocab)} tokens, blank={pad} id={vocab[pad]})")
    return vocab


def export_model(repo_id, lang_code):
    model = Wav2Vec2ForCTC.from_pretrained(repo_id)
    model.eval()

    float32_path = f"stt-{lang_code}-float32.onnx"
    int8_path = os.path.join(OUT_DIR, f"indicwav2vec-{lang_code}-int8.onnx")

    print(f"  exporting ONNX -> {float32_path}")
    torch.onnx.export(
        model,
        (torch.randn(1, TRACE_SAMPLES),),
        float32_path,
        input_names=["input"],
        output_names=["output"],
        dynamic_axes={
            "input": {0: "batch", 1: "sequence_length"},
            "output": {0: "batch", 1: "sequence_length"},
        },
        opset_version=15,
    )

    # Restricted to MatMul/Gemm: quantising Mul/Add trips shape-inference bugs here.
    print(f"  quantising INT8 -> {int8_path}")
    quantize_dynamic(
        float32_path,
        int8_path,
        weight_type=QuantType.QUInt8,
        op_types_to_quantize=["MatMul", "Gemm"],
    )
    os.remove(float32_path)
    return int8_path


def export_stt(repo_id, lang_code):
    print(f"\n{'=' * 60}\nSTT {lang_code.upper()} from {repo_id}\n{'=' * 60}")
    export_vocab(repo_id, lang_code)
    path = export_model(repo_id, lang_code)
    size_mb = os.path.getsize(path) / 1024 / 1024
    print(f"  done ({size_mb:.1f} MB)")


def main(argv):
    os.makedirs(OUT_DIR, exist_ok=True)

    requested = argv or list(LANGUAGES)
    unknown = [code for code in requested if code not in LANGUAGES]
    if unknown:
        print(f"Unknown language code(s): {', '.join(unknown)}")
        print(f"Known: {', '.join(LANGUAGES)}")
        return 2

    unconfigured = [code for code in requested if LANGUAGES[code] is None]
    runnable = [code for code in requested if LANGUAGES[code] is not None]

    failures = []
    for code in runnable:
        try:
            export_stt(LANGUAGES[code], code)
        except Exception as exc:  # keep going; one bad repo must not stop the batch
            print(f"  FAILED {code}: {exc}")
            failures.append(code)

    done = [c for c in runnable if c not in failures]
    print(f"\nExported: {', '.join(done) if done else 'nothing'}")
    if failures:
        print(f"Failed:   {', '.join(failures)}")
    if unconfigured:
        print(
            f"Skipped:  {', '.join(unconfigured)} -- no repo id configured. "
            "Set it in LANGUAGES above before re-exporting these."
        )
    return 1 if failures else 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main(sys.argv[1:]))
