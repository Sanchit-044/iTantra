package `in`.gov.itantra.core.discover

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

enum class SignalTrend {
    CLOSING, FURTHER, STEADY
}

/**
 * 2-Stage Signal Smoother combining median filtering and time-based exponential decay.
 * Removes multipath reflection/turn spikes and provides rock-solid real-time distance estimation.
 */
class SignalSmoother(
    private val medianWindow: Int = MEDIAN_WINDOW,
    private val smoothingSeconds: Double = SMOOTHING_SECONDS,
    private val bigStepDb: Double = BIG_STEP_DB,
    private val bigStepAlpha: Double = BIG_STEP_ALPHA,
) {
    private val window = ArrayDeque<Int>()
    private var smoothed: Double? = null
    private var lastAtMillis = 0L

    private class Trail(val atMillis: Long, val rssi: Double)
    private val trail = ArrayDeque<Trail>()

    val value: Double?
        @Synchronized get() = smoothed

    val recent: List<Int>
        @Synchronized get() = window.toList()

    @Synchronized
    fun offer(rssi: Int, atMillis: Long): Double {
        window.addLast(rssi)
        while (window.size > medianWindow) window.removeFirst()
        val median = window.sorted()[window.size / 2].toDouble()
        val previous = smoothed
        val next = if (previous == null || lastAtMillis == 0L) {
            median
        } else {
            val dt = (atMillis - lastAtMillis).coerceAtLeast(0L) / 1000.0
            var alpha = 1.0 - exp(-dt / smoothingSeconds)
            if (abs(median - previous) > bigStepDb) alpha = max(alpha, bigStepAlpha)
            previous + (median - previous) * alpha
        }
        smoothed = next
        lastAtMillis = atMillis

        trail.addLast(Trail(atMillis, next))
        while (trail.isNotEmpty() && atMillis - trail.first().atMillis > TREND_WINDOW_MILLIS + 1_000L) {
            trail.removeFirst()
        }

        return next
    }

    @Synchronized
    fun trend(nowMs: Long = System.currentTimeMillis()): SignalTrend {
        val latest = trail.lastOrNull() ?: return SignalTrend.STEADY
        val earlier = trail.firstOrNull { nowMs - it.atMillis <= TREND_WINDOW_MILLIS } ?: return SignalTrend.STEADY
        if (latest.atMillis - earlier.atMillis < TREND_WINDOW_MILLIS / 2) return SignalTrend.STEADY
        val delta = latest.rssi - earlier.rssi
        return when {
            delta >= TREND_DB -> SignalTrend.CLOSING
            delta <= -TREND_DB -> SignalTrend.FURTHER
            else -> SignalTrend.STEADY
        }
    }

    @Synchronized
    fun estimateDistanceMeters(
        referenceDbm: Double = RSSI_AT_ONE_METRE,
        exponent: Double = PATH_LOSS_EXPONENT,
    ): Float {
        val rssi = smoothed ?: return DEFAULT_DISTANCE_METERS
        val exponentTerm = (referenceDbm - rssi) / (10.0 * exponent)
        val d = 10.0.pow(exponentTerm).toFloat()
        return d.coerceIn(0.5f, 60.0f)
    }

    @Synchronized
    fun clear() {
        window.clear()
        smoothed = null
        lastAtMillis = 0L
        trail.clear()
    }

    companion object {
        const val MEDIAN_WINDOW = 5
        const val SMOOTHING_SECONDS = 0.6
        const val BIG_STEP_DB = 8.0
        const val BIG_STEP_ALPHA = 0.5

        const val RSSI_AT_ONE_METRE = -59.0
        const val PATH_LOSS_EXPONENT = 2.6
        const val DEFAULT_DISTANCE_METERS = 3.5f

        const val TREND_WINDOW_MILLIS = 3_000L
        const val TREND_DB = 2.5
    }
}
