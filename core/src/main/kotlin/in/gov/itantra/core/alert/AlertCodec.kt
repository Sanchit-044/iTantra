package `in`.gov.itantra.core.alert

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.pack.ScriptPacker

/**
 * Handles encoding and decoding of connectionless alert payloads for BLE and Wi-Fi Direct.
 * Supports multi-hop TTL forwarding and 3x ScriptPacker compression for custom alerts.
 */
object AlertCodec {

    private const val FLAG_CUSTOM = 0x80
    private const val FLAG_PACKED = 0x40
    private const val TTL_MASK = 0x30
    private const val TTL_SHIFT = 4
    private const val LANG_MASK = 0x0F

    /**
     * Encodes an alert (Template or ScriptPacked Custom) into a compact byte payload for BLE advertising.
     */
    fun encodeBlePayload(
        language: Language,
        content: AlertContent,
        sequence: Long,
        senderName: String? = null,
        ttl: Int = 3,
    ): ByteArray? {
        val clampedTtl = ttl.coerceIn(0, 3)
        val nameBytes = senderName?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        val nameLen = Math.min(nameBytes.size, 8)

        return when (content) {
            is AlertContent.Template -> {
                val header = ((clampedTtl shl TTL_SHIFT) and TTL_MASK) or (language.wire.toInt() and LANG_MASK)
                val buffer = java.nio.ByteBuffer.allocate(3 + nameLen)
                buffer.put(header.toByte())
                buffer.put(sequence.toByte())
                buffer.put(content.template.ordinal.toByte())
                if (nameLen > 0) {
                    buffer.put(nameBytes, 0, nameLen)
                }
                buffer.array()
            }
            is AlertContent.Custom -> {
                val packed = if (ScriptPacker.isWorthPacking(content.text, language)) {
                    ScriptPacker.pack(content.text, language) to true
                } else {
                    content.text.toByteArray(Charsets.UTF_8) to false
                }
                val textBytes = packed.first
                val isPacked = packed.second
                // BLE advertisement data payload budget max 22 bytes
                if (4 + textBytes.size + Math.min(nameLen, 4) > 24) {
                    return null
                }
                var header = FLAG_CUSTOM or ((clampedTtl shl TTL_SHIFT) and TTL_MASK) or (language.wire.toInt() and LANG_MASK)
                if (isPacked) header = header or FLAG_PACKED

                val finalNameLen = Math.min(nameLen, 4)
                val buffer = java.nio.ByteBuffer.allocate(3 + textBytes.size + finalNameLen)
                buffer.put(header.toByte())
                buffer.put(sequence.toByte())
                buffer.put(textBytes.size.toByte())
                buffer.put(textBytes)
                if (finalNameLen > 0) {
                    buffer.put(nameBytes, 0, finalNameLen)
                }
                buffer.array()
            }
        }
    }

    /**
     * Decodes a BLE payload back into an alert.
     */
    fun decodeBlePayload(payload: ByteArray): DecodedAlert? {
        if (payload.size < 3) return null

        try {
            val buffer = java.nio.ByteBuffer.wrap(payload)
            val header = buffer.get().toInt() and 0xFF
            val isCustom = (header and FLAG_CUSTOM) != 0
            val isPacked = (header and FLAG_PACKED) != 0
            val ttl = (header and TTL_MASK) shr TTL_SHIFT
            val langCode = (header and LANG_MASK).toByte()

            val sequence = buffer.get().toInt() and 0xFF

            if (!isCustom) {
                // Template Alert
                val templateOrdinal = buffer.get().toInt() and 0xFF
                val language = Language.fromWire(langCode) ?: Language.DEFAULT
                val template = AlertTemplate.entries.getOrNull(templateOrdinal) ?: return null
                val content = AlertContent.Template(template)

                val senderName = if (buffer.hasRemaining()) {
                    val nameBytes = ByteArray(buffer.remaining())
                    buffer.get(nameBytes)
                    String(nameBytes, Charsets.UTF_8).trim()
                } else {
                    null
                }

                return DecodedAlert(language, content, sequence, senderName, ttl)
            } else {
                // Custom Alert (ScriptPacked or UTF-8)
                val textLen = buffer.get().toInt() and 0xFF
                if (buffer.remaining() < textLen) return null
                val textBytes = ByteArray(textLen)
                buffer.get(textBytes)

                val language = Language.fromWire(langCode) ?: Language.DEFAULT
                val text = if (isPacked) {
                    ScriptPacker.unpack(textBytes, language)
                } else {
                    String(textBytes, Charsets.UTF_8)
                }
                val content = AlertContent.Custom(text)

                val senderName = if (buffer.hasRemaining()) {
                    val nameBytes = ByteArray(buffer.remaining())
                    buffer.get(nameBytes)
                    String(nameBytes, Charsets.UTF_8).trim()
                } else {
                    null
                }

                return DecodedAlert(language, content, sequence, senderName, ttl)
            }
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Encodes any alert into a TXT record map for Wi-Fi Direct DNS-SD.
     */
    fun encodeWifiPayload(
        language: Language,
        content: AlertContent,
        sequence: Long,
        senderName: String? = null,
        ttl: Int = 3,
    ): Map<String, String> {
        val map = mutableMapOf(
            "lang" to language.code,
            "seq" to sequence.toString(),
            "payload" to content.toWirePayload(),
            "ttl" to ttl.toString(),
        )
        if (senderName != null) {
            map["name"] = senderName
        }
        return map
    }

    /**
     * Decodes a Wi-Fi Direct DNS-SD TXT record map back into an alert.
     */
    fun decodeWifiPayload(record: Map<String, String>): DecodedAlert? {
        try {
            val langCode = record["lang"] ?: return null
            val sequenceStr = record["seq"] ?: return null
            val payloadStr = record["payload"] ?: return null
            val senderName = record["name"]
            val ttl = record["ttl"]?.toIntOrNull() ?: 3

            val language = Language.fromCode(langCode) ?: return null
            val sequence = sequenceStr.toIntOrNull() ?: return null

            val content = AlertTemplate.fromWirePayload(payloadStr)
            return DecodedAlert(language, content, sequence, senderName, ttl)
        } catch (e: Exception) {
            return null
        }
    }

    data class DecodedAlert(
        val language: Language,
        val content: AlertContent,
        val sequence: Int,
        val senderName: String? = null,
        val ttl: Int = 3,
    )
}
