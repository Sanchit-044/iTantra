package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.stt.SttListener
import `in`.gov.itantra.core.stt.SttResult
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.Transport
import `in`.gov.itantra.core.Language

/**
 * UseCase for handling Push-to-Talk activation.
 * It starts the STT engine and pipes recognized sentences to the Transport layer.
 */
class StartPttTransmissionUseCase(
    private val sttEngine: SttEngine,
    private val transport: Transport
) {
    fun execute(language: Language, onPartialResult: (String) -> Unit) {
        // Start listening
        sttEngine.start(language, object : SttListener {
            override fun onPartial(text: String) {
                onPartialResult(text)
            }

            override fun onSentence(text: String) {
                // Emit as a complete sentence packet over transport
                // Message type 0x01 = normal
                val packet = Packet(
                    language = language,
                    messageType = 0x01.toByte(),
                    payload = text.toByteArray(Charsets.UTF_8)
                )
                transport.send(packet)
            }

            override fun onError(error: Throwable) {
                // Handle error
            }
        })
    }
}
