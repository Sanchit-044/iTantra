package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.queue.OutboundMessage
import `in`.gov.itantra.core.queue.OutboundMessageQueue
import `in`.gov.itantra.core.stt.EndpointTrigger
import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.stt.SttException
import `in`.gov.itantra.core.stt.SttListener
import `in`.gov.itantra.core.stt.SttResult
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
    fun execute(
        language: Language,
        transport: Transport?,
        sendLive: Boolean = true,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit = {},
        onQueued: (OutboundMessage) -> Unit = {},
        onError: (String) -> Unit = {},
    ) {
        if (sttEngine.activeLanguage != language) {
            sttEngine.loadModel(language)
        }

        sttEngine.start(object : SttListener {
            override fun onPartial(text: String) {
                onPartialResult(text)
            }

            override fun onFinal(result: SttResult) {
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
                if (cancelled || result.text.isBlank()) return
                val text = result.text.trim()
                
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
                        return
                    } catch (_: Exception) {
                        // Fall through and queue so the utterance is not lost.
                    }
                }

                val queued = outboundQueue.enqueue(language, text)
                if (queued != null) onQueued(queued)
            }

            override fun onError(error: SttException) {
                onError(error.message ?: "Speech recognition failed")
            }
        })
    }
}
