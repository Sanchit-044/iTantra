package `in`.gov.itantra.android.audio

import `in`.gov.itantra.core.audio.AudioFormat
import `in`.gov.itantra.core.audio.AudioSink
import `in`.gov.itantra.core.audio.AudioSinkFactory

class AndroidAudioSinkFactory : AudioSinkFactory {
    override fun createSink(format: AudioFormat): AudioSink {
        return AudioTrackSink(format)
    }
}
