package `in`.gov.itantra.core.transport

import `in`.gov.itantra.core.Language

/**
 * Wire message types. Values are explicit so the enum can be reordered safely.
 */
enum class MessageType(val wire: Byte) {
    /** Ordinary voice-derived text. */
    NORMAL(0x01),

    /**
     * Priority traffic. Module B6 forces audio focus and maximum alarm volume for
     * these, so this field is security-relevant: see [PacketCodec] on why the header
     * is authenticated.
     */
    ALERT(0x02),

    /** Liveness probe, also used to measure round-trip latency for Module B7. */
    HEARTBEAT(0x03),

    /** Acknowledgement of a previously received sequence number. */
    ACK(0x04),
    ;

    companion object {
        fun fromWire(b: Byte): MessageType? = entries.firstOrNull { it.wire == b }
    }
}

/**
 * A decoded, plaintext application message.
 *
 * The ciphertext form is produced by [PacketCodec]; this class is what the rest of the
 * app sees. [sequence] is per-session and monotonically increasing, which both drives
 * ACK matching and gives the receiver a cheap replay check.
 */
data class Packet(
    val type: MessageType,
    val language: Language,
    val sequence: Int,
    val timestampMs: Long,
    /** UTF-8 text for NORMAL/ALERT; for ACK the acknowledged sequence as text. */
    val payload: ByteArray,
    val flags: Int = 0,
) {
    val text: String get() = payload.toString(Charsets.UTF_8)

    val isAlert: Boolean get() = type == MessageType.ALERT

    val requiresAck: Boolean get() = flags and FLAG_REQUIRES_ACK != 0

    // ByteArray needs structural equals/hashCode to make this data class behave.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Packet) return false
        return type == other.type &&
            language == other.language &&
            sequence == other.sequence &&
            timestampMs == other.timestampMs &&
            flags == other.flags &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var r = type.hashCode()
        r = 31 * r + language.hashCode()
        r = 31 * r + sequence
        r = 31 * r + timestampMs.hashCode()
        r = 31 * r + flags
        r = 31 * r + payload.contentHashCode()
        return r
    }

    companion object {
        const val FLAG_REQUIRES_ACK = 0x01

        fun text(
            type: MessageType,
            language: Language,
            sequence: Int,
            text: String,
            timestampMs: Long = System.currentTimeMillis(),
            flags: Int = 0,
        ): Packet = Packet(type, language, sequence, timestampMs, text.toByteArray(Charsets.UTF_8), flags)
    }
}
