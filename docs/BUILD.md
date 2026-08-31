# Building

## Requirements

| Target | Needs |
|---|---|
| `:core` | JDK 17 or newer. No Android SDK. |
| `:android`, `:harness` | Android SDK (compileSdk 35, build-tools), JDK 17 |
| `:harness:connectedAndroidTest` | A connected device, plus bundled models |

`:core` targets JVM 17 bytecode but does not pin a Gradle toolchain, so it compiles on
any JDK ≥ 17 without triggering a toolchain download. The Android modules do require a
17 toolchain, which is what Android Gradle Plugin 8.7 expects.

## Module configuration is conditional

`settings.gradle.kts` includes `:android` and `:harness` **only** when an Android SDK is
detected (via `ANDROID_HOME`, `ANDROID_SDK_ROOT`, or `sdk.dir` in `local.properties`).

This is so `./gradlew :core:test` works on a machine with no Android tooling — CI, a
reviewer's laptop, or a teammate who only needs to run the logic tests. Without it, the
whole build would fail to configure over an SDK that most of the code does not need.

If you expect `:android` to be there and it is not, the configuration log says so:

```
[itantra] Android SDK not found -- only :core is configured.
```

## Commands

```bash
./gradlew :core:test              # 133 unit tests, no device
./gradlew :core:test --tests '*SilenceEndpointer*'
./gradlew :android:assembleDebug
./gradlew :harness:connectedAndroidTest
./gradlew bundleRelease            # App Bundle, once an :app module exists
```

HTML test report: `core/build/reports/tests/test/index.html`.

## Bundling models

Models are **bundled at install time**. There is no download path and none may be added.

Large models go in the `:models-pack` install-time asset pack; the small alert WAVs stay
in the `:android` module.

```
models-pack/src/main/assets/
  models/stt/onnx/indicwav2vec-<lang>-int8.onnx
  models/stt/onnx/indicwav2vec-<lang>-vocab.json
  models/tts/vits-<lang>-int8.onnx
  models/tts/vits-<lang>-vocab.json

android/src/main/assets/
  alerts/<lang>/<template>.wav                  # 5 templates x 3 languages
```

The future `:app` module must declare the pack:

```kotlin
android {
    assetPacks += listOf(":models-pack")
}
```

Install-time packs are present before first launch and resolve through the ordinary
`AssetManager`, so `context.assets.open("models/...")` works unchanged. **Never** switch
the pack to `fast-follow` or `on-demand` delivery — both fetch over the network after
install and would break the offline constraint.

`<lang>` is `hi`, `ta` or `bn`. Paths come from `ModelRegistry`,
`VitsVoiceDescriptor` and `AlertTemplate.assetPath`; a unit test asserts none of them
resolves to a URL or escapes the assets root.

`noCompress` is set for `.onnx`, `.wav` and `.json` so ONNX Runtime can memory-map
straight out of the package rather than unpacking a second copy to internal storage,
which halves peak disk use on a constrained device. Nothing is unpacked to internal
storage at runtime.

## Evaluation corpus

Test-only, and never shipped in the product APK:

```
harness/src/androidTest/assets/
  corpus/<lang>/<id>.wav      # 16 kHz mono 16-bit PCM
  corpus/<lang>/<id>.txt      # UTF-8 reference transcript
  roundtrip/<lang>-sample.wav
```

See `CORPUS.md`. The corpus is intentionally not committed: recordings of real speakers
are personal data.
