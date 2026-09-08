package `in`.gov.itantra.core.alert

import `in`.gov.itantra.core.Language

/**
 * Handles encoding and decoding of connectionless alert payloads for both BLE and Wi-Fi Direct.
 * Extracted from Android bindings to allow for JVM unit testing.
 */
object AlertCodec {

    /**
     * Encodes a template alert into a 3-byte payload for BLE advertising.
     * @return 3-byte array or null if the content is not a template (BLE cannot fit custom text).
     */
    fun encodeBlePayload(language: Language, content: AlertContent, sequence: Long): ByteArray? {
        if (content !is AlertContent.Template) {
            return null
        }
        
        val buffer = java.nio.ByteBuffer.allocate(3)
        buffer.put(language.wire)
        buffer.put(sequence.toByte())
        buffer.put(content.template.ordinal.toByte())
        return buffer.array()
    }

    /**
     * Decodes a 3-byte BLE payload back into an alert.
     */
    fun decodeBlePayload(payload: ByteArray): DecodedAlert? {
        if (payload.size != 3) return null

        try {
            val buffer = java.nio.ByteBuffer.wrap(payload)
            val langCode = buffer.get()
            val sequence = buffer.get().toInt()
            val templateOrdinal = buffer.get().toInt()
            
            val language = Language.fromWire(langCode) ?: return null
            val template = AlertTemplate.entries.getOrNull(templateOrdinal) ?: return null
            val content = AlertContent.Template(template)
            
            return DecodedAlert(language, content, sequence)
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Encodes any alert into a TXT record map for Wi-Fi Direct DNS-SD.
     */
    fun encodeWifiPayload(language: Language, content: AlertContent, sequence: Long, senderName: String? = null): Map<String, String> {
        val map = mutableMapOf(
            "lang" to language.code,
            "seq" to sequence.toString(),
            "payload" to content.toWirePayload()
        )
        if (senderName != null) {
            map["name"] = senderName
        }
        return map
    }

    /**
     * Decodes a Wi-Fi Direct DNS-SD TXT record map back into an alert.
     * Note: WifiP2pDnsSdServiceRequest returns a string map in setDnsSdTxtRecordListener, 
     * but sometimes we might get ByteArray based on the API used. Android's TxtRecordListener provides String keys/values.
     */
    fun decodeWifiPayload(record: Map<String, String>): DecodedAlert? {
        try {
            val langCode = record["lang"] ?: return null
            val sequenceStr = record["seq"] ?: return null
            val payloadStr = record["payload"] ?: return null
            val senderName = record["name"]
            
            val language = Language.fromCode(langCode) ?: return null
            val sequence = sequenceStr.toIntOrNull() ?: return null
            
            val content = AlertTemplate.fromWirePayload(payloadStr)
            return DecodedAlert(language, content, sequence, senderName)
        } catch (e: Exception) {
            return null
        }
    }

    data class DecodedAlert(
        val language: Language,
        val content: AlertContent,
        val sequence: Int,
        val senderName: String? = null
    )
}
