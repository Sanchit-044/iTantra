package `in`.gov.itantra.core.diag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RollingMeanAccumulatorTest {

    @Test
    fun `empty window is unmeasured not zero`() {
        val acc = RollingMeanAccumulator(10)
        assertNull(acc.mean)
        assertEquals(0, acc.count)
    }

    @Test
    fun `uses only samples that exist when fewer than the window`() {
        val acc = RollingMeanAccumulator(10)
        acc.recordMs(10)
        acc.recordMs(20)
        assertEquals(15.0, acc.mean)
        assertEquals(2, acc.count)
    }

    @Test
    fun `drops the oldest sample after the window fills`() {
        val acc = RollingMeanAccumulator(3)
        acc.record(1.0)
        acc.record(2.0)
        acc.record(3.0)
        acc.record(4.0)
        assertEquals(3.0, acc.mean)
        assertEquals(3, acc.count)
    }

    @Test
    fun `ignores nan and infinite so they cannot flatten the mean`() {
        val acc = RollingMeanAccumulator(10)
        acc.record(10.0)
        acc.record(Double.NaN)
        acc.record(Double.POSITIVE_INFINITY)
        assertEquals(10.0, acc.mean)
        assertEquals(1, acc.count)
    }

    @Test
    fun `rejects a zero window`() {
        assertFailsWith<IllegalArgumentException> { RollingMeanAccumulator(0) }
    }
}

class RollingRealTimeFactorAccumulatorTest {

    @Test
    fun `empty is unmeasured`() {
        assertNull(RollingRealTimeFactorAccumulator().value)
    }

    @Test
    fun `is total processing over total audio not a mean of ratios`() {
        val acc = RollingRealTimeFactorAccumulator(10)
        acc.record(processingMs = 100, audioDurationMs = 1000)
        acc.record(processingMs = 900, audioDurationMs = 1000)
        assertEquals(0.5, acc.value)
    }

    @Test
    fun `ignores zero or negative audio so a bad sample cannot invent RTF`() {
        val acc = RollingRealTimeFactorAccumulator(10)
        acc.record(processingMs = 50, audioDurationMs = 0)
        acc.record(processingMs = -1, audioDurationMs = 100)
        assertNull(acc.value)
    }
}
