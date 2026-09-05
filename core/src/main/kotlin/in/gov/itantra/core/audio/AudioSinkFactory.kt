package `in`.gov.itantra.core.audio

interface AudioSinkFactory {
    fun createSink(format: AudioFormat): AudioSink
}
