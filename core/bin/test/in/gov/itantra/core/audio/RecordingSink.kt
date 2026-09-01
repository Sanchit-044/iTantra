package `in`.gov.itantra.core.audio

/** An [AudioSink] that records everything written to it so playback can be asserted on. */
class RecordingSink(override val format: AudioFormat = AudioFormat.TTS_22K) : AudioSink {
    val written = mutableListOf<Short>()
    var drains = 0
    var flushes = 0

    @Synchronized
    override fun write(samples: ShortArray, offset: Int, count: Int): Int {
        for (i in offset until offset + count) written += samples[i]
        return count
    }

    @Synchronized override fun drain() { drains++ }
    @Synchronized override fun flush() { flushes++ }

    @get:Synchronized
    val sampleCount: Int get() = written.size
}
