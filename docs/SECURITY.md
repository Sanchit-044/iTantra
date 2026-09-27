# Security Architecture & Threat Model

---

## 1. Threat Model

In disaster and remote tactical communication scenarios, adversaries may attempt:
1. **False Alarm Injection**: Injecting fake evacuation orders or distress alerts causing panic.
2. **Eavesdropping**: Intercepting plaintext spoken transmissions over open radio frequencies.
3. **Replay Attacks**: Capturing previous legitimate messages and replaying them to disrupt operations.
4. **Header Flipping**: Altering packet metadata (e.g., flipping a regular voice packet into a forced max-volume alert).

---

## 2. Cryptographic Defense: AES-256-GCM AEAD

- **Confidentiality**: 256-bit symmetric key encryption protecting speech transcripts and location payloads.
- **Header Authentication (AAD)**: The entire 12-byte unencrypted packet header is supplied as **Associated Authenticated Data (AAD)** to the GCM cipher. If an attacker modifies the `Flags` byte to forge an alert, GCM tag verification fails immediately, and the packet is dropped before audio focus is altered.
- **Replay Protection**: High-water mark sequence validation and sliding time windows reject duplicate or replayed frames.
- **Key Provisioning**: Pre-shared session keys provisioned via local QR code / NFC tap during peer setup.
