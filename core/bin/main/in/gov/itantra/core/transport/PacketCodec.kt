package `in`.gov.itantra.core.transport

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.crypto.SessionCrypto
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The single wire format shared by both transports (Module B5).
 *
 * Layout, big-endian, length-prefixed so it survives a byte-stream medium such as an
 * RFCOMM socket that has no message framing of its own:
 *
 *     u32  frameLength         number of bytes that follow this field
 *     ---- authenticated header (16 bytes, passed to AES-GCM as AAD) ----
 *     u8   version
 *     u8   messageType
 *     u8   language
 *     u8   flags
 *     u32  sequence
 *     u64  timestampMs
 *     ---- end of AAD ----
 *     u8[12] nonce             GCM IV, unique per packet per session
 *     u32  payloadLength       length of ciphertext including the 16-byte GCM tag
 *     u8[] payload             AES-256-GCM(ciphertext || tag)
 *
 * On the deliberate absence of an HMAC field
 * -----------------------------------------
 * AES-GCM is an AEAD construction: its 128-bit tag already authenticates both the
 * ciphertext and the associated data, and a decrypt with a wrong or tampered input
 * fails closed rather than returning garbage. Adding an HMAC on top would add 32 bytes
 * per packet and a second key to manage while detecting nothing GCM does not already
 * detect. It is not defence in depth; it is a second lock on the same door.
 *
 * The part that genuinely matters is that the header travels as AAD. [MessageType] is
 * in that header, and Module B6 escalates ALERT packets to forced maximum alarm
 * volume. If the header were sent unauthenticated, an attacker could flip a NORMAL
 * packet's type byte to ALERT and remotely trigger a max-volume siren on every handset
 * in range. Because the header is AAD, any such flip fails the tag check and the
 * packet is discarded. That property, not an extra MAC, is what secures this field.
 */
object PacketCodec {

    const val VERSION: Byte = 0x01
    const val HEADER_LEN = 16
    const val NONCE_LEN = 12
    const val LENGTH_PREFIX_LEN = 4

    /**
     * Upper bound on a single frame. A voice-derived text message is a few hundred
     * bytes; this cap exists so a hostile or corrupt peer cannot make us allocate an
     * arbitrary buffer on a 2 GB device.
     */
    const val MAX_FRAME_LEN = 64 * 1024

    class MalformedPacketException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    /** Serialises and encrypts [packet] into a complete length-prefixed frame. */
    fun encode(packet: Packet, crypto: SessionCrypto): ByteArray {
        val header = ByteBuffer.allocate(HEADER_LEN).order(ByteOrder.BIG_ENDIAN).apply {
            put(VERSION)
            put(packet.type.wire)
            put(packet.language.wire)
            put(packet.flags.toByte())
            putInt(packet.sequence)
            putLong(packet.timestampMs)
        }.array()

        val sealed = crypto.seal(plaintext = packet.payload, associatedData = header)

        require(sealed.nonce.size == NONCE_LEN) {
            "expected a $NONCE_LEN-byte nonce, got ${sealed.nonce.size}"
        }

        val bodyLen = HEADER_LEN + NONCE_LEN + 4 + sealed.ciphertext.size
        if (bodyLen > MAX_FRAME_LEN) {
            throw MalformedPacketException("frame of $bodyLen bytes exceeds MAX_FRAME_LEN")
        }

        return ByteBuffer.allocate(LENGTH_PREFIX_LEN + bodyLen).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(bodyLen)
            put(header)
            put(sealed.nonce)
            putInt(sealed.ciphertext.size)
            put(sealed.ciphertext)
        }.array()
    }

    /**
     * Decrypts and parses a frame *body* -- i.e. the bytes after the u32 length
     * prefix, as produced by [FrameReader].
     *
     * Throws [MalformedPacketException] for structural problems and
     * [in.gov.itantra.core.crypto.AuthenticationFailedException] when the GCM tag does
     * not verify. Callers must treat both as "discard and count", never as "retry".
     */
    fun decodeBody(body: ByteArray, crypto: SessionCrypto): Packet {
        if (body.size < HEADER_LEN + NONCE_LEN + 4) {
            throw MalformedPacketException("frame body too short: ${body.size} bytes")
        }
        val buf = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN)

        val header = ByteArray(HEADER_LEN)
        buf.get(header)

        val version = header[0]
        if (version != VERSION) {
            throw MalformedPacketException("unsupported protocol version $version")
        }
        val type = MessageType.fromWire(header[1])
            ?: throw MalformedPacketException("unknown message type ${header[1]}")
        val language = Language.fromWire(header[2])
            ?: throw MalformedPacketException("unknown language code ${header[2]}")
        val flags = header[3].toInt() and 0xFF
        val sequence = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.BIG_ENDIAN).int
        val timestampMs = ByteBuffer.wrap(header, 8, 8).order(ByteOrder.BIG_ENDIAN).long

        val nonce = ByteArray(NONCE_LEN)
        buf.get(nonce)

        val payloadLen = buf.int
        if (payloadLen < 0 || payloadLen > buf.remaining()) {
            throw MalformedPacketException(
                "declared payload length $payloadLen does not fit in ${buf.remaining()} remaining bytes"
            )
        }
        val ciphertext = ByteArray(payloadLen)
        buf.get(ciphertext)

        // Any tampering with the header above fails here, because the header is AAD.
        val plaintext = crypto.open(
            ciphertext = ciphertext,
            nonce = nonce,
            associatedData = header,
        )

        return Packet(type, language, sequence, timestampMs, plaintext, flags)
    }
}

/**
 * Reassembles length-prefixed frames from a byte stream.
 *
 * Bluetooth RFCOMM and a Wi-Fi Direct TCP socket both deliver an unframed stream: a
 * single read can return half a packet, or two and a half packets. This class absorbs
 * that. It is pure and synchronous so the framing logic is unit-testable without any
 * socket at all -- including the awkward cases (split length prefix, coalesced frames)
 * that are painful to reproduce on a real radio.
 */
class FrameReader(
    private val maxFrameLen: Int = PacketCodec.MAX_FRAME_LEN,
) {
    private var buffer = ByteArray(INITIAL_CAPACITY)
    private var size = 0

    /** Bytes currently held pending a complete frame. Exposed for diagnostics. */
    val buffered: Int get() = size

    /**
     * Append [count] bytes from [data] and return every complete frame body that is
     * now available, in arrival order. Returns an empty list when more data is needed.
     */
    fun offer(data: ByteArray, offset: Int = 0, count: Int = data.size - offset): List<ByteArray> {
        ensureCapacity(size + count)
        System.arraycopy(data, offset, buffer, size, count)
        size += count

        val out = mutableListOf<ByteArray>()
        var consumed = 0
        while (true) {
            val available = size - consumed
            if (available < PacketCodec.LENGTH_PREFIX_LEN) break

            val frameLen = readInt(buffer, consumed)
            if (frameLen <= 0 || frameLen > maxFrameLen) {
                throw PacketCodec.MalformedPacketException(
                    "declared frame length $frameLen is out of range (1..$maxFrameLen)"
                )
            }
            if (available - PacketCodec.LENGTH_PREFIX_LEN < frameLen) break

            val start = consumed + PacketCodec.LENGTH_PREFIX_LEN
            out += buffer.copyOfRange(start, start + frameLen)
            consumed = start + frameLen
        }

        if (consumed > 0) {
            System.arraycopy(buffer, consumed, buffer, 0, size - consumed)
            size -= consumed
        }
        return out
    }

    fun reset() {
        size = 0
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= buffer.size) return
        var cap = buffer.size
        while (cap < needed) cap *= 2
        // Guard against a peer that streams a valid-looking prefix forever.
        if (cap > maxFrameLen * 2) {
            throw PacketCodec.MalformedPacketException("frame reassembly buffer exceeded $cap bytes")
        }
        buffer = buffer.copyOf(cap)
    }

    private fun readInt(b: ByteArray, at: Int): Int =
        ((b[at].toInt() and 0xFF) shl 24) or
            ((b[at + 1].toInt() and 0xFF) shl 16) or
            ((b[at + 2].toInt() and 0xFF) shl 8) or
            (b[at + 3].toInt() and 0xFF)

    private companion object {
        const val INITIAL_CAPACITY = 2048
    }
}
