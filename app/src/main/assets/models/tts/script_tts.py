import os
import urllib.request
import ssl

EXPORT_DIR = "models-pack/src/main/assets/models/tts"
os.makedirs(EXPORT_DIR, exist_ok=True)

# Ignore Windows SSL certificate errors
ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def download_file(url, filepath):
    with urllib.request.urlopen(url, context=ctx) as response, open(filepath, 'wb') as out_file:
        out_file.write(response.read())

print("Downloading pre-compiled Piper VITS ONNX model for Hindi...")
download_file(
    "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/rohan/medium/hi_IN-rohan-medium.onnx",
    os.path.join(EXPORT_DIR, "vits-hi-int8.onnx")
)

print("Downloading Piper config and vocabulary...")
download_file(
    "https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/rohan/medium/hi_IN-rohan-medium.onnx.json",
    os.path.join(EXPORT_DIR, "vits-hi-vocab.json")
)

print("Done! The files have been successfully downloaded straight into the correct Android assets directory.")
