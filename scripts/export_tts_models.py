"""Export VITS (MMS-TTS) voices to INT8 ONNX plus the vocabulary the app expects.

Run for every language, or for a subset:

    python export_tts_models.py            # all ten
    python export_tts_models.py mr or gu   # just these

Why the full registry lives here
--------------------------------
This script used to be edited in place before each run, so it only ever held the
languages of the most recent batch. That is how the repository ended up with no
Gujarati vocabulary at all -- `vits-gu-vocab.json` has never existed -- and with a
Marathi vocabulary missing the `pad_id` and `interleave_pad` keys that every other
language has, because it was produced by an older revision of this file. Neither
failure was visible until a handset tried to speak.

The registry below is therefore complete and the language selection is a command-line
argument, so re-exporting one language cannot quietly discard the rest.
"""

import json
import os
import sys

import torch
from onnxruntime.quantization import QuantType, quantize_dynamic
from transformers import AutoTokenizer, VitsModel

OUT_DIR = os.path.join("app", "src", "main", "assets", "models", "tts")

# Meta's MMS-TTS checkpoints: VITS architecture, grapheme (character) input, so no
# phonemiser has to be shipped for ten languages. Repo ids follow ISO 639-3.
LANGUAGES = {
    "hi": "facebook/mms-tts-hin",
    "ta": "facebook/mms-tts-tam",
    "bn": "facebook/mms-tts-ben",
    "gu": "facebook/mms-tts-guj",
    "mr": "facebook/mms-tts-mar",
    "kn": "facebook/mms-tts-kan",
    "ml": "facebook/mms-tts-mal",
    "te": "facebook/mms-tts-tel",
    "or": "facebook/mms-tts-ory",
    "en": "facebook/mms-tts-eng",
}

# Tracing length. Large enough that the model's dynamic padding branches evaluate as
# True during tracing, so the padding logic is baked into the exported graph.
TRACE_LENGTH = 300


def _from_pretrained(loader, repo_id):
    """Load from the hub, falling back to the local cache when the network is blocked."""
    try:
        return loader.from_pretrained(repo_id)
    except Exception as exc:
        print(f"  hub load failed ({exc}); retrying from the local cache")
        return loader.from_pretrained(repo_id, local_files_only=True)


def export_vocab(repo_id, lang_code):
    """Write the character -> id map plus the metadata the Android tokenizer reads."""
    tokenizer = _from_pretrained(AutoTokenizer, repo_id)
    vocab = dict(tokenizer.get_vocab())

    if not vocab:
        raise RuntimeError(f"{repo_id} produced an empty vocabulary")

    # MMS-TTS interleaves the pad token between every character; the Android
    # VitsTokenizer needs to be told so explicitly rather than relying on a default.
    pad_id = tokenizer.pad_token_id
    if pad_id is None:
        pad_id = 0
    vocab["pad_id"] = int(pad_id)
    vocab["interleave_pad"] = True

    unk_id = tokenizer.unk_token_id
    if unk_id is not None and int(unk_id) != int(pad_id):
        # Lets the app substitute rather than silently drop a character it cannot map.
        vocab["unk_id"] = int(unk_id)

    dest = os.path.join(OUT_DIR, f"vits-{lang_code}-vocab.json")
    with open(dest, "w", encoding="utf-8") as handle:
        json.dump(vocab, handle, ensure_ascii=False, indent=2)

    symbols = len(vocab) - 2 - (1 if "unk_id" in vocab else 0)
    print(f"  vocab -> {dest} ({symbols} symbols, pad_id={vocab['pad_id']})")
    if " " not in vocab:
        print("  WARNING: no space symbol; word boundaries will be lost in synthesis")
    return vocab


class VitsExportWrapper(torch.nn.Module):
    """Exposes just the waveform output, which is all the Android engine consumes."""

    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_ids):
        return self.model(input_ids=input_ids).waveform


def export_model(repo_id, lang_code):
    model = _from_pretrained(VitsModel, repo_id)
    model.eval()

    wrapper = VitsExportWrapper(model)
    wrapper.eval()

    float32_path = f"tts-{lang_code}-float32.onnx"
    int8_path = os.path.join(OUT_DIR, f"vits-{lang_code}-int8.onnx")

    print(f"  exporting ONNX -> {float32_path}")
    torch.onnx.export(
        wrapper,
        (torch.ones((1, TRACE_LENGTH), dtype=torch.long),),
        float32_path,
        input_names=["text"],
        output_names=["output"],
        dynamic_axes={
            "text": {0: "batch", 1: "length"},
            "output": {0: "batch", 1: "audio_length"},
        },
        opset_version=15,
        dynamo=False,
    )

    print(f"  quantising INT8 -> {int8_path}")
    quantize_dynamic(float32_path, int8_path, weight_type=QuantType.QUInt8)
    os.remove(float32_path)
    return int8_path


def export_tts(repo_id, lang_code):
    print(f"\n{'=' * 60}\nTTS {lang_code.upper()} from {repo_id}\n{'=' * 60}")
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

    failures = []
    for code in requested:
        try:
            export_tts(LANGUAGES[code], code)
        except Exception as exc:  # keep going; one bad repo must not stop the batch
            print(f"  FAILED {code}: {exc}")
            failures.append(code)

    done = [c for c in requested if c not in failures]
    print(f"\nExported: {', '.join(done) if done else 'nothing'}")
    if failures:
        print(f"Failed:   {', '.join(failures)}")
        return 1
    return 0


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main(sys.argv[1:]))
