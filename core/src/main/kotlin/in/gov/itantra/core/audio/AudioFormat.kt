package `in`.gov.itantra.core.audio

/**
 * Canonical PCM description used across STT, TTS and alert playback.
 * Everything in iTantra is 16-bit signed little-endian mono; only the rate varies
 * (16 kHz for the STT capture path, 22.05 kHz for the VITS TTS output path).
 */
data class AudioFormat(
    val sampleRate: Int,
    val channels: Int = 1,
    val bitsPerSample: Int = 16,
) {
    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channels == 1) { "iTantra is mono-only; got $channels channels" }
        require(bitsPerSample == 16) { "iTantra is 16-bit PCM only" }
    }

    val bytesPerFrame: Int get() = channels * (bitsPerSample / 8)

    fun samplesForMs(ms: Int): Int = (sampleRate.toLong() * ms / 1000).toInt()

    fun msForSamples(samples: Int): Long = samples.toLong() * 1000 / sampleRate

    companion object {
        /** IndicWav2Vec expects 16 kHz mono. */
        val STT_16K = AudioFormat(16_000)

        /** Meta MMS-TTS checkpoints are trained at 16 kHz. */
        val TTS_16K = AudioFormat(16_000)
    }
}

/** An in-memory decoded PCM buffer. */
class AudioClip(
    val pcm: ShortArray,
    val format: AudioFormat,
) {
    val durationMs: Long get() = format.msForSamples(pcm.size)

    companion object {
        fun silence(format: AudioFormat, ms: Int): AudioClip =
            AudioClip(ShortArray(format.samplesForMs(ms)), format)
    }
}

/**
 * A pull-based sink for rendered audio. The Android layer implements this over
 * AudioTrack; tests implement it as a recording buffer. Keeping playback behind
 * this interface is what lets Module B6's forced-audio-focus behaviour be asserted
 * in a plain JVM unit test.
 */
interface AudioSink : AutoCloseable {
    val format: AudioFormat

    /** Blocking write. Returns the number of samples actually consumed. */
    fun write(samples: ShortArray, offset: Int, count: Int): Int

    /** Block until previously written audio has finished rendering. */
    fun drain()

    /** Discard anything buffered and stop immediately. */
    fun flush()
}
