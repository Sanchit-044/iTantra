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
        sequenceNum: Int? = null,
        senderName: String? = null,
        senderLoc: String? = null,
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

        val seq = sequenceNum ?: sequence.incrementAndGet()
        val textToSend = if (!senderName.isNullOrBlank()) {
            val locPart = if (!senderLoc.isNullOrBlank()) "\u001F$senderLoc" else ""
            "$senderName\u001F$payload$locPart"
        } else {
            payload
        }

        transport.send(
            Packet.text(
                type = MessageType.ALERT,
                language = language,
                sequence = seq,
                text = textToSend,
            )
        )
    }

    companion object {
        const val MAX_CHARS = 200
    }
}
