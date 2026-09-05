package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertContent
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.Transport
import java.util.concurrent.atomic.AtomicInteger

class SendAlertUseCase(
    private val sequence: AtomicInteger = AtomicInteger(0),
) {
    fun execute(
        transport: Transport,
        language: Language,
        content: AlertContent,
        pairingConfirmed: Boolean,
    ) {
        if (!pairingConfirmed) {
            throw IllegalStateException("pairing not confirmed")
        }
        if (transport.state != ConnectionState.CONNECTED) {
            throw IllegalStateException("not connected")
        }
        val payload = content.toWirePayload()
        if (payload.isEmpty()) throw IllegalArgumentException("alert text is empty")
        if (payload.length > MAX_CHARS) throw IllegalArgumentException("alert text is too long")

        transport.send(
            Packet.text(
                type = MessageType.ALERT,
                language = language,
                sequence = sequence.incrementAndGet(),
                text = payload,
            )
        )
    }

    companion object {
        const val MAX_CHARS = 200
    }
}
