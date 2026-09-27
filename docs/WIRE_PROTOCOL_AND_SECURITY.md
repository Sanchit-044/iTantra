# Wire Protocol & Security Architecture

iTantra utilizes a custom binary frame structure engineered for high packet delivery rates over lossy, low-bitrate physical links (Wi-Fi Direct, Bluetooth Low Energy, and P2P local sockets).

---

## 1. Binary Packet Layout (Zero Overhead Framing)

```text
 0                   1                   2                   3
 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|       Magic (0x49 0x54)       |    Version    |  Packet Type  |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|     Flags     |  Src Language |        Sequence Number        |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|          Payload Length       |           Reserved            |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
+                    Initialization Vector (12 Bytes)           +
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
+                    Encrypted Payload (Variable)               +
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
|                                                               |
+                    GCM Authentication Tag (16 Bytes)          +
|                                                               |
+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
```

### Header Field Specification

| Field | Size | Description |
|---|---|---|
| **Magic Bytes** | 2 bytes | `0x49 0x54` (`"IT"` for iTantra). Eliminates spurious noise packets. |
| **Version** | 1 byte | Protocol version (`0x01`). |
| **Packet Type** | 1 byte | `0x01` (PTT Speech), `0x02` (Alert), `0x03` (SOS Beacon), `0x04` (ACK), `0x0A` (Profile Exchange). |
| **Flags** | 1 byte | Bit 0: `FLAG_ALERT` (Emergency siren trigger), Bit 1: `FLAG_ACK_REQ`. |
| **Src Language** | 1 byte | Wire code of spoken text (e.g., `0x01` = Hindi, `0x02` = Tamil). |
| **Sequence No** | 2 bytes | Monotonically increasing sequence number for deduplication. |
| **Payload Length**| 2 bytes | Length of the encrypted payload in bytes. |
| **IV** | 12 bytes | Cryptographically random Nonce for AES-256-GCM. |
| **GCM Tag** | 16 bytes | 128-bit Poly1305/GCM authentication tag verifying ciphertext and AAD. |

---

## 2. Authenticated Encryption with Associated Data (AEAD)

iTantra employs **AES-256-GCM** natively implemented via the Android Keystore and JVM `javax.crypto.Cipher`.

### Security Defense: Anti-Tampering for Emergency Flags
A malicious actor on an unencrypted mesh network could alter the `FLAG_ALERT` byte to trigger false evacuation sirens across a region.

**How iTantra Prevents This**:
- The entire 12-byte packet header is passed as **Associated Authenticated Data (AAD)** into the GCM cipher.
- Any modification of the `Flags`, `Src Language`, or `Sequence No` invalidates the 16-byte GCM tag.
- The receiving handset rejects the packet immediately before any audio or alert processing takes place.

---

## 3. Bandwidth Comparison: Audio vs Text Transceiver

| Metric | Raw PCM Audio Stream | Opus Compressed Audio | iTantra Low-Bitrate Text Frame |
|---|---|---|---|
| **Payload per 5s speech** | ~160,000 Bytes (160 KB) | ~15,000 Bytes (15 KB) | **~65 Bytes** |
| **Link Requirement** | High bandwidth Wi-Fi | Medium 3G/4G | **Ultra-Low (BLE, LoRa, Satellite Link)** |
| **Packet Loss Resistance**| Poor (Clipping/choppy voice) | Moderate | **Near 100% (Single small packet retransmit)** |
| **Encryption Overhead** | High CPU overhead | Medium | **Sub-millisecond (< 1 ms)** |
