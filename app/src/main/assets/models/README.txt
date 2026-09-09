Bundled model assets. Populate before building:

  models/stt/onnx/indicwav2vec-<lang>-int8.onnx
  models/stt/onnx/indicwav2vec-<lang>-vocab.json
  models/tts/vits-<lang>-int8.onnx
  models/tts/vits-<lang>-vocab.json

<lang> is one of the ten in core Language.kt:

  hi  ta  bn  gu  mr  kn  ml  te  or  en

The *-vocab.json files are committed; the *.onnx weights are not (see .gitignore)
and are produced by export_models.py (STT) and export_tts_models.py (TTS).

Current gaps
------------
  models/tts/vits-gu-vocab.json   MISSING -- Gujarati has never been exported.
                                  Until it exists, loadVoice() for Gujarati throws
                                  and the engine falls back to the Android system
                                  TTS voice. Run: python export_tts_models.py gu

  export_models.py has no repo id recorded for hi, ta, bn, gu, mr, or, en. Their
  vocabularies are present, so the models were exported at some point, but the
  script was overwritten each run and the sources were lost. Fill in LANGUAGES
  there before re-exporting one of those.

Checking a language is complete
-------------------------------
Both files must be present for a language, and the vocab must match the model it
was exported with. A model paired with the wrong vocabulary does not fail loudly:
inference succeeds and the audio is noise. On device, logcat shows
"Vocabulary mismatch for <lang>" from iTantra-TTS when that happens.

Alert WAVs live in the :android module's assets, not here -- they are small.
