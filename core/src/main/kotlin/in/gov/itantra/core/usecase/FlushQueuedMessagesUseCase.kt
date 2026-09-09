package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.queue.OutboundMessageQueue
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.Transport
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Drains the outbound queue after pairing is confirmed.
 *
 * Fail-closed: if the link drops mid-flush the current item is Failed and the rest
 * stay queued. A second call while one flush is running is a no-op.
 */
class FlushQueuedMessagesUseCase(
    private val queue: OutboundMessageQueue,
    private val sequence: AtomicInteger = AtomicInteger(0),
) {
    private val flushing = AtomicBoolean(false)

    data class Result(val sentIds: List<String>, val failedIds: List<String>)

    fun execute(transport: Transport, pairingConfirmed: Boolean, receiverName: String? = null): Result {
        if (!pairingConfirmed) return Result(emptyList(), emptyList())
        if (transport.state != ConnectionState.CONNECTED) return Result(emptyList(), emptyList())
        if (!flushing.compareAndSet(false, true)) return Result(emptyList(), emptyList())

        val sent = mutableListOf<String>()
        val failed = mutableListOf<String>()
        try {
            for (item in queue.itemsToFlush()) {
                if (transport.state != ConnectionState.CONNECTED) {
                    queue.markFailed(item.id)
                    failed.add(item.id)
                    break
                }
                if (!queue.markSending(item.id)) continue
                val packet = Packet.text(
                    type = if (item.isAlert) MessageType.ALERT else MessageType.QUEUED,
                    language = item.language,
                    sequence = sequence.incrementAndGet(),
                    text = item.text,
                    timestampMs = item.createdAtMs,
                )
                try {
                    transport.send(packet)
                    queue.markSent(item.id, receiverName)
                    sent.add(item.id)
                } catch (_: Exception) {
                    queue.markFailed(item.id)
                    failed.add(item.id)
                    break
                }
            }
        } finally {
            flushing.set(false)
        }
        return Result(sent, failed)
    }
}
