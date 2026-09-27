package `in`.gov.itantra.core.alert

import `in`.gov.itantra.core.transport.MessageType

/**
 * Tracks progressive delivery and multi-peer acknowledgements for emergency alerts.
 * Enables live progress displays like "Delivered to 3 of 5 units" with automated retransmission.
 */
class AlertDeliveryTracker(
    private val retryIntervalMillis: Long = RETRY_INTERVAL_MILLIS,
    private val maxAttempts: Int = MAX_ATTEMPTS,
) {
    data class Progress(
        val seq: Int,
        val attempts: Int,
        val ackedBy: Set<String>,
        val peerCount: Int,
        val givenUp: Boolean = false,
    ) {
        val isDelivered: Boolean get() = ackedBy.isNotEmpty()

        fun display(): String = if (peerCount > 0) {
            "${ackedBy.size} of $peerCount units"
        } else {
            "${ackedBy.size} units"
        }

        val isComplete: Boolean get() = peerCount > 0 && ackedBy.size >= peerCount
    }

    private val inFlight = LinkedHashMap<Int, Progress>()
    private val nextRetryAt = HashMap<Int, Long>()

    val trackedCount: Int get() = inFlight.size

    fun trackAlert(seq: Int, nowMillis: Long, peerCount: Int = 0) {
        inFlight[seq] = Progress(seq, attempts = 1, ackedBy = emptySet(), peerCount = peerCount)
        nextRetryAt[seq] = nowMillis + retryIntervalMillis
        evictFinished()
    }

    fun onAck(seq: Int, peerName: String, nowMillis: Long): Progress? {
        val current = inFlight[seq] ?: return null
        val updated = current.copy(ackedBy = current.ackedBy + peerName)
        inFlight[seq] = updated
        if (updated.isDelivered) {
            nextRetryAt.remove(seq)
        }
        return updated
    }

    fun dueForRetry(nowMillis: Long): List<Int> {
        val due = nextRetryAt.filterValues { it <= nowMillis }.keys.toList()
        val out = ArrayList<Int>(due.size)

        for (seq in due) {
            val progress = inFlight[seq] ?: continue
            if (progress.attempts >= maxAttempts) {
                inFlight[seq] = progress.copy(givenUp = true)
                nextRetryAt.remove(seq)
                continue
            }
            inFlight[seq] = progress.copy(attempts = progress.attempts + 1)
            nextRetryAt[seq] = nowMillis + retryIntervalMillis
            out.add(seq)
        }
        return out
    }

    fun progressOf(seq: Int): Progress? = inFlight[seq]

    fun forget(seq: Int) {
        inFlight.remove(seq)
        nextRetryAt.remove(seq)
    }

    fun clear() {
        inFlight.clear()
        nextRetryAt.clear()
    }

    private fun evictFinished() {
        if (inFlight.size <= MAX_TRACKED) return
        val eldestFinished = inFlight.entries.firstOrNull { (_, progress) ->
            progress.isDelivered || progress.givenUp
        } ?: return
        forget(eldestFinished.key)
    }

    companion object {
        const val RETRY_INTERVAL_MILLIS = 1000L
        const val MAX_ATTEMPTS = 3
        const val MAX_TRACKED = 64
    }
}
