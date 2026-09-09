package `in`.gov.itantra.core.queue

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QueueTtlTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `exactly thirty days is expired inclusive`() {
        assertTrue(QueueTtl.isExpired(now - QueueTtl.DURATION_MS, now))
    }

    @Test
    fun `just under thirty days is kept`() {
        assertFalse(QueueTtl.isExpired(now - QueueTtl.DURATION_MS + 1, now))
    }

    @Test
    fun `zero negative and far-future stamps are expired as corrupt`() {
        assertTrue(QueueTtl.isExpired(0, now))
        assertTrue(QueueTtl.isExpired(-1, now))
        assertTrue(QueueTtl.isExpired(now + QueueTtl.DURATION_MS, now))
    }

    @Test
    fun `a few minutes of clock skew does not expire a just-written item`() {
        assertFalse(QueueTtl.isExpired(now + 60_000, now))
    }
}
