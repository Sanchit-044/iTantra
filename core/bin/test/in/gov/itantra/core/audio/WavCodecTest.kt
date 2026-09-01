package `in`.gov.itantra.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WavCodecTest {

    private fun tone(samples: Int = 400): AudioClip =
        AudioClip(ShortArray(samples) { (it * 37 % 3000 - 1500).toShort() }, AudioFormat(22_050))

    @Test
    fun `round trips through encode and decode`() {
        val original = tone()
        val decoded = WavCodec.decode(WavCodec.encode(original))

        assertEquals(original.format.sampleRate, decoded.format.sampleRate)
        assertContentEquals(original.pcm, decoded.pcm)
    }

    @Test
    fun `preserves duration`() {
        val original = AudioClip(ShortArray(22_050), AudioFormat(22_050))
        assertEquals(1000L, WavCodec.decode(WavCodec.encode(original)).durationMs)
    }

    /**
     * The failure this parser exists to avoid. Many encoders insert a LIST chunk
     * between "fmt " and "data"; a parser that reads samples from the fixed offset 44
     * emits a burst of noise instead of the alert. Walking the chunk table is the fix.
     */
    @Test
    fun `handles a LIST chunk between fmt and data`() {
        val clip = tone(100)
        val canonical = WavCodec.encode(clip)

        // Splice a 12-byte LIST chunk in just before the "data" chunk at offset 36.
        val listChunk = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("LIST".toByteArray(Charsets.US_ASCII))
            putInt(4)
            put("INFO".toByteArray(Charsets.US_ASCII))
        }.array()

        val spliced = canonical.copyOfRange(0, 36) + listChunk + canonical.copyOfRange(36, canonical.size)
        // Fix up the RIFF size field so the file stays well-formed.
        ByteBuffer.wrap(spliced).order(ByteOrder.LITTLE_ENDIAN).putInt(4, spliced.size - 8)

        val decoded = WavCodec.decode(spliced, "spliced.wav")
        assertContentEquals(clip.pcm, decoded.pcm)
    }

    @Test
    fun `handles an odd sized chunk with its pad byte`() {
        val clip = tone(50)
        val canonical = WavCodec.encode(clip)

        // A 5-byte payload chunk: 8 header + 5 body + 1 pad = 14 bytes.
        val oddChunk = ByteBuffer.allocate(14).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("cue ".toByteArray(Charsets.US_ASCII))
            putInt(5)
            put(byteArrayOf(1, 2, 3, 4, 5))
            put(0)
        }.array()

        val spliced = canonical.copyOfRange(0, 36) + oddChunk + canonical.copyOfRange(36, canonical.size)
        ByteBuffer.wrap(spliced).order(ByteOrder.LITTLE_ENDIAN).putInt(4, spliced.size - 8)

        assertContentEquals(clip.pcm, WavCodec.decode(spliced, "odd.wav").pcm)
    }

    @Test
    fun `rejects a non riff file`() {
        assertFailsWith<WavCodec.MalformedWavException> {
            WavCodec.decode(ByteArray(64) { 0x41 }, "notawav")
        }
    }

    @Test
    fun `rejects a truncated file`() {
        assertFailsWith<WavCodec.MalformedWavException> {
            WavCodec.decode(ByteArray(10), "tiny")
        }
    }

    @Test
    fun `rejects stereo audio`() {
        val canonical = WavCodec.encode(tone(50))
        // Channel count sits at offset 22 in the canonical header.
        ByteBuffer.wrap(canonical).order(ByteOrder.LITTLE_ENDIAN).putShort(22, 2)
        assertFailsWith<WavCodec.MalformedWavException> { WavCodec.decode(canonical, "stereo.wav") }
    }

    @Test
    fun `rejects non pcm encoding`() {
        val canonical = WavCodec.encode(tone(50))
        // Audio format tag sits at offset 20; 3 is IEEE float.
        ByteBuffer.wrap(canonical).order(ByteOrder.LITTLE_ENDIAN).putShort(20, 3)
        assertFailsWith<WavCodec.MalformedWavException> { WavCodec.decode(canonical, "float.wav") }
    }

    @Test
    fun `tolerates a data chunk whose declared size overruns the file`() {
        val canonical = WavCodec.encode(tone(100))
        // Claim far more data than is present; the reader must clamp, not crash.
        ByteBuffer.wrap(canonical).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(40, Int.MAX_VALUE - 1)
        val decoded = WavCodec.decode(canonical, "overrun.wav")
        assertEquals(100, decoded.pcm.size)
    }
}
