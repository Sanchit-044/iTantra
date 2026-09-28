package `in`.gov.itantra.core.discover

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SignalSmootherTest {

    @Test
    fun testInitialReadingSetsSmoothed() {
        val smoother = SignalSmoother()
        val smoothed = smoother.offer(-60, 1000L)
        assertEquals(-60.0, smoothed, 0.001)
        val dist = smoother.estimateDistanceMeters()
        assertTrue(dist > 0.9f && dist < 1.3f)
    }

    @Test
    fun testMedianFilterRejectsSpikes() {
        val smoother = SignalSmoother()
        smoother.offer(-60, 1000L)
        smoother.offer(-60, 1100L)
        // Inject single anomalous reflection spike
        smoother.offer(-30, 1200L)
        smoother.offer(-60, 1300L)
        smoother.offer(-60, 1400L)

        val value = smoother.value ?: 0.0
        assertTrue(value < -55.0, "Expected smoothed value around -60, got $value")
    }

    @Test
    fun testTrendDetection() {
        val smoother = SignalSmoother()
        smoother.offer(-75, 1000L)
        smoother.offer(-70, 2000L)
        smoother.offer(-62, 3000L)
        assertEquals(SignalTrend.CLOSING, smoother.trend(3000L))
    }
}
