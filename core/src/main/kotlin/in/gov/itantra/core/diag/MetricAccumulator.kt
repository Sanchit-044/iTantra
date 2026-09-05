package `in`.gov.itantra.core.diag

import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe running statistics for a latency-style metric.
 *
 * Reports null rather than 0.0 until at least one sample has been recorded, so a
 * never-exercised path is visibly unmeasured instead of appearing to be instant.
 */
class MetricAccumulator(val name: String) {
    private val count = AtomicLong(0)
    private val totalMicros = AtomicLong(0)
    private val maxMicros = AtomicLong(0)
    private val minMicros = AtomicLong(Long.MAX_VALUE)

    val samples: Long get() = count.get()

    fun record(millis: Double) {
        val micros = (millis * 1000).toLong()
        count.incrementAndGet()
        totalMicros.addAndGet(micros)
        maxMicros.updateAndGet { if (micros > it) micros else it }
        minMicros.updateAndGet { if (micros < it) micros else it }
    }

    fun recordMs(millis: Long) = record(millis.toDouble())

    val meanMs: Double? get() {
        val n = count.get()
        return if (n == 0L) null else totalMicros.get() / 1000.0 / n
    }

    val maxMs: Double? get() = if (count.get() == 0L) null else maxMicros.get() / 1000.0

    val minMs: Double? get() = if (count.get() == 0L) null else minMicros.get() / 1000.0

    fun reset() {
        count.set(0); totalMicros.set(0); maxMicros.set(0); minMicros.set(Long.MAX_VALUE)
    }
}

/**
 * Running mean of a dimensionless ratio, used for real-time factor.
 *
 * RTF is tracked as total-processing-time over total-audio-duration rather than as a
 * mean of per-utterance ratios. Those differ, and the ratio-of-totals is the one that
 * answers the question that matters: can this device keep up with continuous speech?
 * A mean of ratios lets a handful of tiny, fast utterances mask consistent lateness on
 * long ones.
 */
class RealTimeFactorAccumulator {
    private val processingMicros = AtomicLong(0)
    private val audioMicros = AtomicLong(0)

    fun record(processingMs: Long, audioDurationMs: Long) {
        processingMicros.addAndGet(processingMs * 1000)
        audioMicros.addAndGet(audioDurationMs * 1000)
    }

    /** Null until any audio has been processed. Below 1.0 means faster than real time. */
    val value: Double? get() {
        val audio = audioMicros.get()
        return if (audio == 0L) null else processingMicros.get().toDouble() / audio
    }

    fun reset() {
        processingMicros.set(0); audioMicros.set(0)
    }
}

/** Simple monotonic counter. */
class Counter {
    private val v = AtomicLong(0)
    val value: Long get() = v.get()
    fun increment(by: Long = 1) = v.addAndGet(by)
    fun reset() = v.set(0)
}

/**
 * Mean of the last [window] samples. Null until the first sample, and uses only the
 * samples that exist when fewer than [window] have arrived -- it does not pad with
 * zeros, which would invent a better number than the device has earned.
 */
class RollingMeanAccumulator(val window: Int = 10) {
    private val lock = Any()
    private val samples = ArrayDeque<Double>()

    init {
        require(window > 0) { "window must be positive" }
    }

    fun record(value: Double) {
        if (value.isNaN() || value.isInfinite()) return
        synchronized(lock) {
            samples.addLast(value)
            while (samples.size > window) samples.removeFirst()
        }
    }

    fun recordMs(millis: Long) = record(millis.toDouble())

    val mean: Double? get() = synchronized(lock) {
        if (samples.isEmpty()) null else samples.sum() / samples.size
    }

    val count: Int get() = synchronized(lock) { samples.size }

    fun reset() = synchronized(lock) { samples.clear() }
}

/**
 * Rolling real-time factor over the last [window] utterances, as total processing
 * time over total audio duration -- the same definition as [RealTimeFactorAccumulator].
 */
class RollingRealTimeFactorAccumulator(val window: Int = 10) {
    private val lock = Any()
    private val samples = ArrayDeque<Pair<Long, Long>>()

    init {
        require(window > 0) { "window must be positive" }
    }

    fun record(processingMs: Long, audioDurationMs: Long) {
        if (processingMs < 0 || audioDurationMs <= 0) return
        synchronized(lock) {
            samples.addLast(processingMs to audioDurationMs)
            while (samples.size > window) samples.removeFirst()
        }
    }

    val value: Double? get() = synchronized(lock) {
        val audio = samples.sumOf { it.second }
        if (audio <= 0L) null else samples.sumOf { it.first }.toDouble() / audio
    }

    fun reset() = synchronized(lock) { samples.clear() }
}
