# model-host

The nine language packs the APK does not bundle (everything except Hindi, the
default language -- see `app/src/main/assets/models/README.txt`). These are the same
STT (`indicwav2vec-<lang>-int8.onnx` + `-vocab.json`) and TTS
(`vits-<lang>-int8.onnx` + `-vocab.json`) files that used to be bundled for every
language; they were moved here so the APK stays under the app's own 500 MB budget
(`MemoryBudget.APK_BUDGET_BYTES`, `core/.../stt/ModelRegistry.kt`).

`LocalLanguagePackManager` (`android/src/main/kotlin/in/gov/itantra/android/pack/`)
downloads a language's four files from `<baseUrl>/<filename>` the first time the
operator selects it in Language Setup, and caches them under the app's private
storage (`context.filesDir/models/...`) from then on -- fully offline after that
first download, same as the bundled Hindi pack.

**This directory is intentionally flat** -- no `stt/`/`tts/` subfolders. A GitHub
Release (the recommended host, see below) serves every asset at
`.../releases/download/<tag>/<filename>` with no subpaths, and the two file families
never collide (`indicwav2vec-*` vs `vits-*`), so there's no reason for a
locally-served copy to use a different layout than the release does.

The `.onnx` weights are gitignored (`*.onnx` in the root `.gitignore`) and are not
present in a fresh clone; they are produced locally by `export_models.py` (STT) and
`export_tts_models.py` (TTS) at the repo root. The `-vocab.json` files here are
committed, same as the ones still bundled for Hindi.

## Recommended: host on a GitHub Release

A release's assets are stored separately from the git history (they don't bloat
`git clone`), support files up to 2 GB each (ours top out around 357 MB), and are
reachable over plain internet from any device -- no dependency on a laptop staying
on the same Wi-Fi as the demo phones, which venue networks can block via client
isolation.

```bash
# One-time: install the GitHub CLI, then authenticate (opens a browser)
winget install GitHub.cli
gh auth login

# From the repo root, create a release and upload every file in this directory
# (only run this once you actually want it public/team-visible -- it uploads ~3 GB)
gh release create model-pack-v1 model-host/*.onnx model-host/*.json \
  --title "Model pack v1" \
  --notes "STT/TTS models for all languages except bundled Hindi."
```

The base URL to give the app is then:

```
https://github.com/<owner>/<repo>/releases/download/model-pack-v1
```

Re-running `export_models.py`/`export_tts_models.py` later and wanting to replace a
file: `gh release upload model-pack-v1 model-host/indicwav2vec-gu-int8.onnx --clobber`.

## Alternative: local server for your own dev testing

No hosting needed at all while iterating on your own machine -- just don't rely on
this for the actual judged demo (see the risks above):

```bash
cd model-host
python -m http.server 8000
```

Base URL: `http://<this-machine's-LAN-IP>:8000` -- phone and laptop must be on the
same Wi-Fi, with no client isolation.

## Safest option for the actual judged demo

Whichever host you use, don't depend on it being reachable *during* judging: after
building with a real `modelPackBaseUrl`, open Language Setup on the demo phone(s)
ahead of time (backstage, over any working Wi-Fi) and download every language you
intend to show. Once a pack's four files are cached in `filesDir`, the app never
touches the network for it again -- the live demo then needs no connectivity at all,
which is the stronger and more honest version of this app's own offline pitch.

## Configuring the base URL

`LocalLanguagePackManager` takes `baseUrl` in its constructor; empty (the default)
means "bundled assets only, no network fetch" -- language packs beyond Hindi simply
never become available. Set it via the `MODEL_PACK_BASE_URL` field in
`app/build.gradle.kts` (a `buildConfigField`, so it can differ per build variant
without touching Kotlin source) -- pass it at build time rather than editing the
file, so nobody accidentally commits a release tag or LAN IP as a default:

```bash
./gradlew installDebug -PmodelPackBaseUrl=https://github.com/<owner>/<repo>/releases/download/model-pack-v1
```

See `app/src/main/kotlin/in/gov/itantra/di/AppModule.kt` for how it's wired through
Hilt.

## Verifying a language pack before hosting it

Both files must be present for a language, and the vocab must match the model it was
exported with -- a mismatched pair does not fail loudly (see the STT vocab/model
size-mismatch class of bug fixed for English; the same check applies to every
language here). Before hosting a freshly exported pair, confirm the vocabulary's
entry count matches the ONNX graph's output dimension (STT) or embedding table row
count (TTS) rather than assuming the export succeeded silently.
