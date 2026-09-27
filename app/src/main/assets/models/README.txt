Bundled model assets: Hindi (Language.DEFAULT) ONLY. Populate before building:

  models/stt/onnx/indicwav2vec-hi-int8.onnx
  models/stt/onnx/indicwav2vec-hi-vocab.json
  models/tts/vits-hi-int8.onnx
  models/tts/vits-hi-vocab.json

The other nine languages (ta bn gu mr kn ml te or en) are NOT bundled in the APK --
see ../../../../../model-host/README.md at the repo root. Bundling all ten pushed the
install size past 3.5 GB, which contradicted this app's own documented 500 MB APK
budget (see MemoryBudget.APK_BUDGET_BYTES in core/.../stt/ModelRegistry.kt) and made
no sense for a field device most operators will only ever speak one or two languages
on. LanguagePackManager (core/src/main/kotlin/in/gov/itantra/core/pack/) downloads a
language's files into internal storage on demand instead; LocalLanguagePackManager
(android/.../pack/) is the implementation, and it still checks these bundled assets
first for whichever language ships in the APK, so switching the bundled language here
does not require touching that code.

The *-vocab.json files are committed; the *.onnx weights are not (see .gitignore)
and are produced by export_models.py (STT) and export_tts_models.py (TTS).

Current gaps
------------
  model-host/tts/vits-gu-vocab.json   MISSING -- Gujarati has never been exported.
                                  Until it exists, loadVoice() for Gujarati throws
                                  and the engine falls back to the Android system
                                  TTS voice. Run: python export_tts_models.py gu

  export_models.py has no repo id recorded for hi, ta, bn, gu, mr, or. Their
  vocabularies are present, so the models were exported at some point, but the
  script was overwritten each run and the sources were lost. Fill in LANGUAGES
  there before re-exporting one of those. (en's was recovered and filled in --
  see the comment there.)

Checking a language is complete
-------------------------------
Both files must be present for a language, and the vocab must match the model it
was exported with. A model paired with the wrong vocabulary does not fail loudly:
inference succeeds and the audio is noise. On device, logcat shows
"Vocabulary mismatch for <lang>" from iTantra-TTS when that happens.

Alert WAVs live in the :android module's assets, not here -- they are small.
