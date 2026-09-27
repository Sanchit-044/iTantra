# Environment Setup & Build Instructions

## Prerequisites
- **JDK 17+**
- **Android SDK** (API 24 to API 35)
- **Python 3.10+** (for ONNX model conversion)

## Quick Build Commands
```bash
# 1. Run pure JVM unit tests (Fast, no Android SDK needed)
./gradlew :core:test

# 2. Build Debug APK
./gradlew assembleDebug

# 3. Fetch/Export language models
python export_models.py --lang hi,en

# 4. Install onto connected Android device
./gradlew installDebug
```
