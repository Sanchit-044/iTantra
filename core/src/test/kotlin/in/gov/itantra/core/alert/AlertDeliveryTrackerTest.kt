package `in`.gov.itantra.core.alert

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AlertDeliveryTrackerTest {

    @Test
    fun `tracks multi peer acknowledgements progressively`() {
        val tracker = AlertDeliveryTracker()
        val now = 1000L

        tracker.trackAlert(seq = 42, nowMillis = now, peerCount = 3)
        var progress = tracker.progressOf(42)

        assertNotNull(progress)
        assertFalse(progress!!.isDelivered)
        assertEquals("0 of 3 units", progress.display())

        progress = tracker.onAck(seq = 42, peerName = "Alpha", nowMillis = now + 100)
        assertNotNull(progress)
        assertTrue(progress!!.isDelivered)
        assertEquals("1 of 3 units", progress.display())

        progress = tracker.onAck(seq = 42, peerName = "Bravo", nowMillis = now + 200)
        assertNotNull(progress)
        assertEquals("2 of 3 units", progress!!.display())
        assertFalse(progress!!.isComplete)

        progress = tracker.onAck(seq = 42, peerName = "Charlie", nowMillis = now + 300)
        assertNotNull(progress)
        assertEquals("3 of 3 units", progress!!.display())
        assertTrue(progress!!.isComplete)
    }

    @Test
    fun `identifies alerts due for retry`() {
        val tracker = AlertDeliveryTracker(retryIntervalMillis = 500L, maxAttempts = 3)
        val now = 1000L

        tracker.trackAlert(seq = 10, nowMillis = now, peerCount = 2)

        val dueBefore = tracker.dueForRetry(now + 200)
        assertTrue(dueBefore.isEmpty())

        val dueAfter = tracker.dueForRetry(now + 600)
        assertEquals(listOf(10), dueAfter)
    }
}
