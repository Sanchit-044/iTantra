package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.audio.AudioSinkFactory
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.tts.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReceivePttTransmissionUseCase(
    private val ttsEngine: TtsEngine,
    private val audioSinkFactory: AudioSinkFactory
) {
    suspend fun execute(packet: Packet) {
        if (packet.type != MessageType.NORMAL || packet.text.isBlank()) return

        withContext(Dispatchers.Default) {
            // Ensure the voice for the incoming language is loaded
            if (ttsEngine.activeLanguage != packet.language) {
                ttsEngine.loadVoice(packet.language)
            }

            // Synthesize the text into an AudioClip
            val audioClip = ttsEngine.synthesize(packet.text, packet.language)
            
            if (audioClip.pcm.isEmpty()) return@withContext

            // Create a sink for the output format (e.g. 22.05 kHz) and play it
            val sink = audioSinkFactory.createSink(audioClip.format)
            try {
                // Write the PCM data to the sink
                var offset = 0
                while (offset < audioClip.pcm.size) {
                    val remaining = audioClip.pcm.size - offset
                    // Write in chunks to not block indefinitely if sink buffer is small
                    val written = sink.write(audioClip.pcm, offset, remaining)
                    if (written <= 0) break
                    offset += written
                }
                
                // Block until playback finishes
                sink.drain()
            } finally {
                sink.close()
            }
        }
    }
}
