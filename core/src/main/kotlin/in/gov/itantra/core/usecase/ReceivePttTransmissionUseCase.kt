package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioSinkFactory
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.translate.TranslationEngine
import `in`.gov.itantra.core.diag.AppLog
import `in`.gov.itantra.core.translate.TranslationUnavailableException
import `in`.gov.itantra.core.translate.translateOrSame
import `in`.gov.itantra.core.tts.ChunkedSpeaker
import `in`.gov.itantra.core.tts.TtsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ReceivePttTransmissionUseCase(
    private val ttsEngine: TtsEngine,
    private val audioSinkFactory: AudioSinkFactory,
    private val translationEngine: TranslationEngine,
) {
    private val playLock = Mutex()

    fun preload(language: Language) {
        if (ttsEngine.activeLanguage != language) {
            AppLog.d("ReceivePttUseCase", "Preloading TTS voice for language: $language")
            ttsEngine.loadVoice(language)
        }
    }

    /**
     * Live walkie-talkie path only. [MessageType.QUEUED] is ignored here so a
     * deferred message can never auto-play. Alerts are played by AlertPlayer at alarm volume, not as ordinary PTT speech.
     */
    suspend fun execute(packet: Packet, currentLanguage: Language) {
        if (packet.type != MessageType.NORMAL || packet.text.isBlank()) {
            AppLog.d("ReceivePttUseCase", "Ignoring packet type=${packet.type} or empty text")
            return
        }
        
        AppLog.d("ReceivePttUseCase", "Executing translation for live packet: sq=${packet.sequence} from=${packet.language} to=$currentLanguage")
        withContext(Dispatchers.IO) {
            val playbackText = try {
                withTimeoutOrNull(TRANSLATION_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        translationEngine.translateOrSame(
                            packet.text,
                            packet.language,
                            currentLanguage,
                        )
                    }
                } ?: run {
                    AppLog.w(
                        "ReceivePttUseCase",
                        "Translation timed out after ${TRANSLATION_TIMEOUT_MS}ms — " +
                            "falling back to original text in ${packet.language.code}",
                    )
                    packet.text
                }
            } catch (e: TranslationUnavailableException) {
                AppLog.w("ReceivePttUseCase", "Translation unavailable: ${e.message} — playing original")
                packet.text
            }
            val playbackLanguage = if (playbackText == packet.text) packet.language else currentLanguage
            playText(playbackText, playbackLanguage)
        }
    }

    /**
     * Operator-initiated playback for an inbox item.
     *
     * Runs on [Dispatchers.IO] because [ChunkedSpeaker.speak] is fully blocking
     * (synthesis + AudioTrack drain). [Dispatchers.Default] has a bounded thread
     * pool sized to the CPU count; occupying one of those threads for the entire
     * synthesis + playback duration starves other coroutines under load.
     *
     * A [TTS_TIMEOUT_MS] hard deadline is applied so a stuck ONNX call (e.g., the
     * model is loaded but inference never returns on a pathological device) cannot
     * block the coroutine indefinitely.
     */
    suspend fun playText(text: String, language: Language) {
        val cleaned = text.trim()
        if (cleaned.isEmpty()) {
            AppLog.d("ReceivePttUseCase", "Ignoring empty playText request")
            return
        }
        AppLog.d("ReceivePttUseCase", "playText: language=$language text=$cleaned")
        playLock.withLock {
            withContext(Dispatchers.IO) {
                if (ttsEngine.activeLanguage != language) {
                    AppLog.d("ReceivePttUseCase", "Loading TTS voice for language: $language")
                    ttsEngine.loadVoice(language)
                }
                AppLog.d("ReceivePttUseCase", "Speaking text chunked...")
                val sink = audioSinkFactory.createSink(ttsEngine.outputFormat)
                try {
                    val speaker = ChunkedSpeaker(ttsEngine)
                    val handle = speaker.speak(cleaned, language, sink)
                    val completed = handle.await(TTS_TIMEOUT_MS)
                    if (!completed) {
                        AppLog.d(
                            "ReceivePttUseCase",
                            "TTS timed out after ${TTS_TIMEOUT_MS} ms — cancelling utterance",
                        )
                        handle.cancel()
                    }
                } finally {
                    sink.close()
                }
            }
        }
    }

    private companion object {
        /**
         * Maximum time to wait for a TTS utterance to complete before giving up.
         *
         * VITS on a slow CPU can take 5-15 seconds for a short Hindi sentence;
         * 30 seconds allows for a longer sentence on a loaded low-end device while
         * still preventing the coroutine from blocking forever if ONNX hangs.
         */
        const val TTS_TIMEOUT_MS = 30_000L

        /**
         * Maximum time to wait for translation before falling back to the original text.
         *
         * IndicTrans2 on a mid-range phone takes 2-4 seconds per sentence. 15 seconds
         * allows for a very slow device or a long sentence while preventing the user
         * from hearing nothing if the model is stuck. On timeout the original
         * (untranslated) text is played in the sender's language — imperfect but better
         * than silence.
         */
        const val TRANSLATION_TIMEOUT_MS = 15_000L
    }
}
