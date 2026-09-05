package `in`.gov.itantra.core.transport

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.crypto.AesGcmSessionCrypto
import `in`.gov.itantra.core.crypto.AuthenticationFailedException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PacketCodecTest {

    private fun crypto() = AesGcmSessionCrypto(ByteArray(32) { it.toByte() })

    private fun samplePacket(
        type: MessageType = MessageType.NORMAL,
        language: Language = Language.HINDI,
        text: String = "बाढ़ का पानी बढ़ रहा है",
    ) = Packet.text(type, language, sequence = 42, text = text, timestampMs = 1_700_000_000_000L)

    @Test
    fun `floor control wire codes are unique`() {
        val types = MessageType.entries.map { it.wire }
        assertEquals(types.size, types.toSet().size)
        assertEquals(0x05, MessageType.FLOOR_REQUEST.wire)
        assertEquals(0x08, MessageType.FLOOR_RELEASE.wire)
    }

    @Test
    fun `round trips a queued store-and-forward packet`() {
        val c = crypto()
        val original = samplePacket(type = MessageType.QUEUED, text = "बाद में भेजें")
        val decoded = PacketCodec.decodeBody(
            FrameReader().offer(PacketCodec.encode(original, c)).single(),
            c,
        )
        assertEquals(original, decoded)
        assertTrue(decoded.isQueued)
        assertFalse(decoded.isAlert)
    }

    @Test
    fun `round trips through encode and decode`() {
        val c = crypto()
        val original = samplePacket()
        val frame = PacketCodec.encode(original, c)

        val bodies = FrameReader().offer(frame)
        assertEquals(1, bodies.size)

        val decoded = PacketCodec.decodeBody(bodies[0], c)
        assertEquals(original, decoded)
        assertEquals("बाढ़ का पानी बढ़ रहा है", decoded.text)
    }

    @Test
    fun `round trips floor control types`() {
        val c = crypto()
        for (type in listOf(
            MessageType.FLOOR_REQUEST,
            MessageType.FLOOR_GRANT,
            MessageType.FLOOR_DENY,
            MessageType.FLOOR_RELEASE,
        )) {
            val p = samplePacket(type = type, text = "")
            val body = FrameReader().offer(PacketCodec.encode(p, c)).single()
            assertEquals(type, PacketCodec.decodeBody(body, c).type)
        }
    }

    @Test
    fun `round trips all three languages`() {
        val c = crypto()
        for (lang in Language.entries) {
            val p = samplePacket(language = lang, text = "test ${lang.endonym}")
            val body = FrameReader().offer(PacketCodec.encode(p, c)).single()
            assertEquals(lang, PacketCodec.decodeBody(body, c).language)
        }
    }

    @Test
    fun `ciphertext does not contain the plaintext`() {
        val c = crypto()
        val frame = PacketCodec.encode(samplePacket(text = "SECRETPHRASE"), c)
        assertFalse(
            String(frame, Charsets.ISO_8859_1).contains("SECRETPHRASE"),
            "plaintext leaked into the encoded frame",
        )
    }

    /**
     * The security property the design rests on: the header is passed to GCM as
     * associated data, so flipping the message-type byte from NORMAL to ALERT -- which
     * would otherwise let an attacker trigger a forced max-volume siren on every
     * handset in range -- is detected and the packet discarded.
     */
    @Test
    fun `flipping the alert bit in the header fails authentication`() {
        val c = crypto()
        val frame = PacketCodec.encode(samplePacket(type = MessageType.NORMAL), c)
        val body = FrameReader().offer(frame).single()

        // Byte 1 of the body is messageType. Promote NORMAL to ALERT.
        assertEquals(MessageType.NORMAL.wire, body[1])
        body[1] = MessageType.ALERT.wire

        assertFailsWith<AuthenticationFailedException> { PacketCodec.decodeBody(body, c) }
    }

    @Test
    fun `tampering with the language byte fails authentication`() {
        val c = crypto()
        val body = FrameReader().offer(PacketCodec.encode(samplePacket(), c)).single()
        body[2] = Language.TAMIL.wire
        assertFailsWith<AuthenticationFailedException> { PacketCodec.decodeBody(body, c) }
    }

    @Test
    fun `tampering with the ciphertext fails authentication`() {
        val c = crypto()
        val body = FrameReader().offer(PacketCodec.encode(samplePacket(), c)).single()
        body[body.size - 1] = (body[body.size - 1].toInt() xor 0xFF).toByte()
        assertFailsWith<AuthenticationFailedException> { PacketCodec.decodeBody(body, c) }
    }

    @Test
    fun `a different session key cannot decrypt`() {
        val body = FrameReader().offer(PacketCodec.encode(samplePacket(), crypto())).single()
        val other = AesGcmSessionCrypto(ByteArray(32) { (it + 1).toByte() })
        assertFailsWith<AuthenticationFailedException> { PacketCodec.decodeBody(body, other) }
    }

    @Test
    fun `nonces never repeat within a session`() {
        val c = crypto()
        val seen = mutableSetOf<String>()
        repeat(1000) { i ->
            val sealed = c.seal("m$i".toByteArray(), ByteArray(16))
            assertTrue(
                seen.add(sealed.nonce.joinToString("") { "%02x".format(it) }),
                "GCM nonce repeated at message $i -- catastrophic for confidentiality",
            )
        }
    }
}

class FrameReaderTest {

    private fun crypto() = AesGcmSessionCrypto(ByteArray(32) { 7 })

    private fun frame(text: String, seq: Int, c: AesGcmSessionCrypto) =
        PacketCodec.encode(
            Packet.text(MessageType.NORMAL, Language.BENGALI, seq, text, 1L),
            c,
        )

    @Test
    fun `reassembles a frame delivered one byte at a time`() {
        val c = crypto()
        val f = frame("বন্যার জল বাড়ছে", 1, c)
        val reader = FrameReader()

        val collected = mutableListOf<ByteArray>()
        for (b in f) collected += reader.offer(byteArrayOf(b))

        assertEquals(1, collected.size, "expected exactly one frame after the last byte")
        assertEquals("বন্যার জল বাড়ছে", PacketCodec.decodeBody(collected[0], c).text)
    }

    @Test
    fun `splits multiple frames coalesced into one read`() {
        val c = crypto()
        val combined = frame("one", 1, c) + frame("two", 2, c) + frame("three", 3, c)

        val bodies = FrameReader().offer(combined)
        assertEquals(3, bodies.size)
        assertContentEquals(
            listOf("one", "two", "three"),
            bodies.map { PacketCodec.decodeBody(it, c).text },
        )
    }

    @Test
    fun `handles a length prefix split across two reads`() {
        val c = crypto()
        val f = frame("split header", 1, c)

        val reader = FrameReader()
        // Two bytes of the four-byte length prefix, then the rest.
        assertTrue(reader.offer(f, 0, 2).isEmpty(), "emitted a frame from a partial length prefix")
        val bodies = reader.offer(f, 2, f.size - 2)
        assertEquals(1, bodies.size)
        assertEquals("split header", PacketCodec.decodeBody(bodies[0], c).text)
    }

    @Test
    fun `rejects an absurd declared frame length`() {
        val hostile = byteArrayOf(0x7F, 0x7F, 0x7F, 0x7F, 0, 0, 0, 0)
        assertFailsWith<PacketCodec.MalformedPacketException> { FrameReader().offer(hostile) }
    }

    @Test
    fun `rejects a zero length frame`() {
        assertFailsWith<PacketCodec.MalformedPacketException> {
            FrameReader().offer(byteArrayOf(0, 0, 0, 0, 1))
        }
    }

    @Test
    fun `retains a partial frame across offers`() {
        val c = crypto()
        val f = frame("partial", 1, c)
        val reader = FrameReader()
        reader.offer(f, 0, f.size - 3)
        assertTrue(reader.buffered > 0)
        assertEquals(1, reader.offer(f, f.size - 3, 3).size)
        assertEquals(0, reader.buffered, "buffer should be empty after a complete frame")
    }
}
