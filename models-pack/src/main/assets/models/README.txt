Bundled model assets. Populate before building:

  models/stt/onnx/indicwav2vec-<lang>-int8.onnx
  models/stt/onnx/indicwav2vec-<lang>-vocab.json
  models/tts/vits-<lang>-int8.onnx
  models/tts/vits-<lang>-vocab.json

<lang> is hi, ta or bn. See docs/BUILD.md.
Alert WAVs live in the :android module's assets, not here -- they are small.
