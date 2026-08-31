package `in`.gov.itantra.core.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Minimal RIFF/WAVE reader and writer for 16-bit PCM.
 *
 * Lives in :core rather than in the Android module purely so it can be unit-tested.
 * It is used by the bundled alert templates (Module B6) and by the evaluation corpus
 * loader (Module B2), and a bug here would be near-impossible to diagnose from the
 * symptom: a burst of noise where an evacuation instruction should be.
 */
object WavCodec {

    private const val PCM_FORMAT = 1

    class MalformedWavException(message: String) : Exception(message)

    /**
     * Parses a 16-bit PCM WAV.
     *
     * Chunks are walked rather than assumed to sit at fixed offsets. Plenty of encoders
     * insert LIST, fact or cue chunks between "fmt " and "data", and the common
     * shortcut of reading samples from byte 44 turns those files into noise.
     */
    fun decode(bytes: ByteArray, name: String = "<wav>"): AudioClip {
        if (bytes.size < 44) throw MalformedWavException("$name is too small to be a WAV")
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        if (tag(buf, 0) != "RIFF") throw MalformedWavException("$name is not a RIFF file")
        if (tag(buf, 8) != "WAVE") throw MalformedWavException("$name is not a WAVE file")

        var pos = 12
        var sampleRate = -1
        var channels = -1
        var bitsPerSample = -1
        var dataOffset = -1
        var dataLength = -1

        while (pos + 8 <= bytes.size) {
            val chunkId = tag(buf, pos)
            val chunkSize = buf.getInt(pos + 4)
            if (chunkSize < 0) break
            val body = pos + 8

            when (chunkId) {
                "fmt " -> {
                    if (body + 16 > bytes.size) throw MalformedWavException("$name has a truncated fmt chunk")
                    val audioFormat = buf.getShort(body).toInt()
                    if (audioFormat != PCM_FORMAT) {
                        throw MalformedWavException("$name is not uncompressed PCM (format $audioFormat)")
                    }
                    channels = buf.getShort(body + 2).toInt()
                    sampleRate = buf.getInt(body + 4)
                    bitsPerSample = buf.getShort(body + 14).toInt()
                }

                "data" -> {
                    dataOffset = body
                    dataLength = minOf(chunkSize, bytes.size - body)
                }
            }
            // Chunks are word-aligned; an odd size is followed by a pad byte.
            // Computed in Long: a corrupt or overlong declared size would overflow Int
            // and wrap to a negative offset, which then indexes outside the array.
            val next = body.toLong() + chunkSize.toLong() + (chunkSize and 1).toLong()
            if (next <= pos || next > bytes.size) break
            pos = next.toInt()
        }

        if (dataOffset < 0 || dataLength <= 0) throw MalformedWavException("$name has no data chunk")
        if (bitsPerSample != 16) {
            throw MalformedWavException("$name is $bitsPerSample-bit; only 16-bit PCM is supported")
        }
        if (channels != 1) {
            throw MalformedWavException("$name has $channels channels; only mono is supported")
        }

        val sampleCount = dataLength / 2
        val pcm = ShortArray(sampleCount)
        val data = ByteBuffer.wrap(bytes, dataOffset, dataLength).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) pcm[i] = data.short

        return AudioClip(pcm, AudioFormat(sampleRate))
    }

    /** Serialises a clip as a canonical 44-byte-header mono 16-bit WAV. */
    fun encode(clip: AudioClip): ByteArray {
        val dataBytes = clip.pcm.size * 2
        val buf = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        val rate = clip.format.sampleRate

        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt(36 + dataBytes)
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))

        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(PCM_FORMAT.toShort())
        buf.putShort(1)                       // mono
        buf.putInt(rate)
        buf.putInt(rate * 2)                  // byte rate
        buf.putShort(2)                       // block align
        buf.putShort(16)                      // bits per sample

        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataBytes)
        for (s in clip.pcm) buf.putShort(s)

        return buf.array()
    }

    private fun tag(buf: ByteBuffer, at: Int): String =
        String(
            byteArrayOf(buf.get(at), buf.get(at + 1), buf.get(at + 2), buf.get(at + 3)),
            Charsets.US_ASCII,
        )
}
