package `in`.gov.itantra.core.queue

/**
 * How long store-and-forward text may live on disk.
 *
 * Thirty days is long enough for a delayed reconnect and short enough that a lost
 * handset does not keep speech forever. Age is measured from the item's own
 * timestamp (created / received), not from last open.
 */
object QueueTtl {
    const val DAYS = 30L
    const val DURATION_MS: Long = DAYS * 24L * 60L * 60L * 1000L

    /**
     * Clocks can be a few minutes off after a reboot. A stamp slightly in the
     * future is treated as "now". A stamp years ahead, zero, or negative is
     * treated as corrupt and expired.
     */
    const val CLOCK_SKEW_GRACE_MS: Long = 5L * 60L * 1000L

    fun isExpired(timestampMs: Long, nowMs: Long): Boolean {
        if (timestampMs <= 0L) return true
        if (timestampMs > nowMs + CLOCK_SKEW_GRACE_MS) return true
        if (timestampMs > nowMs) return false
        val age = nowMs - timestampMs
        return age >= DURATION_MS
    }
}
