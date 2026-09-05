package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioSinkFactory
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.translate.TranslationEngine
import `in`.gov.itantra.core.translate.translateOrSame
import `in`.gov.itantra.core.tts.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ReceivePttTransmissionUseCase(
    private val ttsEngine: TtsEngine,
    private val audioSinkFactory: AudioSinkFactory,
    private val translationEngine: TranslationEngine,
) {
    private val playLock = Mutex()

    /**
     * Live walkie-talkie path only. [MessageType.QUEUED] is ignored here so a
     * deferred message can never auto-play. Alerts are played by AlertPlayer at alarm volume, not as ordinary PTT speech.
     */
    suspend fun execute(packet: Packet, currentLanguage: Language) {
        if (packet.type != MessageType.NORMAL || packet.text.isBlank()) return
        
        withContext(Dispatchers.Default) {
            val playbackText = translationEngine.translateOrSame(
                packet.text,
                packet.language,
                currentLanguage,
            )
            playText(playbackText, currentLanguage)
        }
    }

    /** Operator-initiated playback for an inbox item. */
    suspend fun playText(text: String, language: Language) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) return
        playLock.withLock {
            withContext(Dispatchers.Default) {
                if (ttsEngine.activeLanguage != language) {
                    ttsEngine.loadVoice(language)
                }
                val audioClip = ttsEngine.synthesize(cleaned, language)
                if (audioClip.pcm.isEmpty()) return@withContext

                val sink = audioSinkFactory.createSink(audioClip.format)
                try {
                    var offset = 0
                    while (offset < audioClip.pcm.size) {
                        val remaining = audioClip.pcm.size - offset
                        val written = sink.write(audioClip.pcm, offset, remaining)
                        if (written <= 0) break
                        offset += written
                    }
                    sink.drain()
                } finally {
                    sink.close()
                }
            }
        }
    }
}
