package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.queue.OutboundMessage
import `in`.gov.itantra.core.queue.OutboundMessageQueue
import `in`.gov.itantra.core.stt.EndpointTrigger
import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.stt.SttException
import `in`.gov.itantra.core.stt.SttListener
import `in`.gov.itantra.core.stt.SttResult
import `in`.gov.itantra.core.diag.AppLog
import `in`.gov.itantra.core.diag.DiagnosticsSink
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.Transport
import java.util.concurrent.atomic.AtomicInteger

/**
 * Push-to-talk. When [sendLive] is true the final text goes on the air immediately.
 * Otherwise it is stored and flushed after pairing -- the other phone will not hear
 * it until they tap Play.
 */
class StartPttTransmissionUseCase(
    private val sttEngine: SttEngine,
    private val diagnostics: DiagnosticsSink? = null,
    private val outboundQueue: OutboundMessageQueue,
    private val sequence: AtomicInteger = AtomicInteger(0),
) {
    fun preload(language: Language) {
        if (sttEngine.activeLanguage != language) {
            AppLog.d("StartPttUseCase", "Preloading STT model for language: $language")
            sttEngine.loadModel(language)
        }
    }

    fun execute(
        language: Language,
        transport: Transport?,
        sendLive: Boolean = true,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit = {},
        onSentLive: (String, Long) -> Unit = { _, _ -> },
        onQueued: (OutboundMessage) -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        if (sttEngine.activeLanguage != language) {
            AppLog.d("StartPttUseCase", "Loading STT model for language: $language")
            sttEngine.loadModel(language)
        }
        
        AppLog.d("StartPttUseCase", "Starting STT engine, sendLive=$sendLive")

        sttEngine.start(object : SttListener {
            override fun onPartial(text: String) {
                onPartialResult(text)
            }

            override fun onFinal(result: SttResult) {
                // Everything below must release the floor on the way out -- including
                // a blank/cancelled result, which is not rare: it is exactly what a
                // language with weaker STT model coverage (garbled or silent
                // recognition, low-confidence endpointing) produces more often than
                // others. Skipping releaseFloor() on that path left the sender HOLDING
                // and the receiver PEER_HOLDING forever, which is why "channel stuck
                // busy" showed up as language-selective rather than universal.
                try {
                    val cancelled = result.trigger == EndpointTrigger.CANCELLED
                    runCatching {
                        diagnostics?.onSttFinal(
                            text = result.text,
                            language = result.language,
                            finalisationMs = result.finalisationLatencyMs,
                            audioMs = result.utteranceDurationMs,
                            cancelled = cancelled,
                        )
                    }
                    if (cancelled || result.text.isBlank()) {
                        AppLog.d("StartPttUseCase", "Ignoring empty or cancelled STT result (cancelled=$cancelled)")
                        return
                    }
                    val text = result.text.trim()
                    AppLog.d("StartPttUseCase", "Final STT text ready: $text")

                    onPartialResult(text)
                    onFinalResult(text)

                    if (sendLive && transport != null) {
                        val packet = Packet.text(
                            type = MessageType.NORMAL,
                            language = language,
                            sequence = sequence.incrementAndGet(),
                            text = text,
                        )
                        try {
                            transport.send(packet)
                            AppLog.d("StartPttUseCase", "Sent packet live: sq=${packet.sequence}")
                            onSentLive(text, packet.timestampMs)
                            return
                        } catch (e: Exception) {
                            AppLog.w("StartPttUseCase", "Failed to send live packet: ${e.message}, queuing it instead")
                            // Fall through and queue so the utterance is not lost.
                        }
                    }

                    AppLog.d("StartPttUseCase", "Queuing offline packet")
                    val queued = outboundQueue.enqueue(language, text)
                    if (queued != null) onQueued(queued)
                } finally {
                    AppLog.d("StartPttUseCase", "Releasing floor after STT final")
                    transport?.releaseFloor()
                }
            }

            override fun onError(error: SttException) {
                try {
                    AppLog.e("StartPttUseCase", "STT Error: ${error.message}", error)
                    onError(error.message ?: "Speech recognition failed")
                } finally {
                    AppLog.d("StartPttUseCase", "Releasing floor after STT error")
                    transport?.releaseFloor()
                }
            }
        })
    }
}
