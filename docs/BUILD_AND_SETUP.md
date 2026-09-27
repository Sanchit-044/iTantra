# Build & Developer Setup Guide

This document provides instructions for compiling, testing, and deploying **iTantra** from source.

---

## 1. Prerequisites

- **JDK 17 or JDK 21**
- **Android Studio Ladybug (or newer)**
- **Android SDK Platform 34 / 35**
- **Android NDK** (for ONNX Runtime C++ runtime JNI bindings)
- **Physical Android Phones** (Running Android 7.0 / API 24 or above)

---

## 2. Compiling the Project

### Running Unit Tests (Pure JVM, No Android SDK Needed)
The entire domain logic, cryptographic codec, silence endpointer, floor controller, and normalizers can be verified off-device in seconds:

```bash
./gradlew :core:test
```

### Assembling the Debug APK
```bash
./gradlew :app:assembleDebug
```
The output APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Installing on Connected Devices
```bash
# Install on all connected ADB devices
adb devices | grep "device$" | awk '{print $1}' | xargs -I {} adb -s {} install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 3. Pairing Physical Devices via Wi-Fi Direct

1. Install the APK on Phone A and Phone B.
2. Ensure Wi-Fi is enabled on both phones (No Wi-Fi access point or internet connection is required).
3. Open **iTantra** on both phones.
4. Navigate to the **Radar Tab**.
5. Tap on the peer device blip to initiate the direct P2P pairing handshake.
6. Once connected, switch to the **Talk Tab** to begin Push-to-Talk communication.
