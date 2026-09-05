package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.stt.SttListener
import `in`.gov.itantra.core.stt.SttResult
import `in`.gov.itantra.core.stt.SttException
import `in`.gov.itantra.core.stt.EndpointTrigger
import `in`.gov.itantra.core.diag.DiagnosticsSink
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.Transport
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.Language
import java.util.concurrent.atomic.AtomicInteger

/**
 * UseCase for handling Push-to-Talk activation.
 * It starts the STT engine and pipes recognized sentences to the Transport layer.
 */
class StartPttTransmissionUseCase(
    private val sttEngine: SttEngine,
    private val diagnostics: DiagnosticsSink? = null,
) {
    private val sequenceCounter = AtomicInteger(0)

    fun execute(
        language: Language,
        transport: Transport,
        onPartialResult: (String) -> Unit,
        onFinalResult: (String) -> Unit = {},
    ) {
        // Ensure the correct language model is loaded
        if (sttEngine.activeLanguage != language) {
            sttEngine.loadModel(language)
        }

        // Start listening
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
                // Show the final transmitted text on the sending phone's UI
                onPartialResult(result.text)
                onFinalResult(result.text)
                // Emit as a complete sentence packet over transport
                val packet = Packet.text(
                    type = MessageType.NORMAL,
                    language = language,
                    sequence = sequenceCounter.incrementAndGet(),
                    text = result.text
                )
                transport.send(packet)
            }

            override fun onError(error: SttException) {
                // Handle error
            }
        })
    }
}
