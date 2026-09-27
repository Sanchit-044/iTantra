# iTantra Protocol Specification

This document details the binary wire protocol, frame format, script packing, template codes, AEAD encryption, and relaying mechanism.

---

## 1. Frame Structure

All transmissions over Bluetooth Classic, BLE, Wi-Fi Direct, and serial/LoRa links use a unified binary frame:

```text
+-----------------------+-------------------+--------------------+--------------------+
| Magic Bytes (2B)      | Version (1B)      | Packet Type (1B)   | Flags (1B)         |
| 0x49 0x54 ("IT")      | 0x01              | 0x01 (PTT) / etc.  | 0x01 (Alert)       |
+-----------------------+-------------------+--------------------+--------------------+
| Src Language (1B)     | Sequence (2B)     | Payload Len (2B)   | Reserved (2B)      |
+-----------------------+-------------------+--------------------+--------------------+
| Initialization Vector (12B Nonce for AES-256-GCM)                                   |
+------------------------------------------------------------------------------------+
| Encrypted Payload (Variable: Text string or template code)                          |
+------------------------------------------------------------------------------------+
| GCM Authentication Tag (16B Poly1305/GCM Tag with Header as AAD)                   |
+------------------------------------------------------------------------------------+
```

---

## 2. Template Codes & Script Packing

- **Freeform Transcriptions**: Decoded text encoded in UTF-8 directly into the payload (~52 bytes encrypted for a 3-second utterance).
- **Template Alerts**: Pre-defined 1-byte operational codes (`0x01` Evacuate, `0x02` Medical SOS, `0x03` All Clear, etc.) requiring only **21 bytes encrypted** total frame size.
- **Cross-Language Resolution**: When a template alert is received, the target phone looks up the localized string in its own current language directly, achieving instantaneous zero-inference cross-language emergency broadcasts.

---

## 3. Relaying & Multi-Hop Flooding

- Every transmitted packet contains a hop limit (`TTL`) and monotonic sequence identifier.
- Nodes broadcast received frames to adjacent offline peers.
- Duplicates are filtered using the `(SenderID, SequenceNumber)` bloom filter in `InboundMessageInbox`.
