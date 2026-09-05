package `in`.gov.itantra.core.transport

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChannelArbiterTest {

    private val delivered = mutableListOf<Packet>()
    private val busyEvents = mutableListOf<Boolean>()
    private val failures = mutableListOf<Pair<Packet, String>>()
    private lateinit var scheduler: FakeScheduler

    private var deliverySucceeds = true

    private fun arbiter(maxAttempts: Int = 3, maxQueueDepth: Int = 32): ChannelArbiter {
        scheduler = FakeScheduler()
        val a = ChannelArbiter(
            sender = { p -> if (deliverySucceeds) { delivered += p; true } else false },
            scheduler = scheduler,
            maxAttempts = maxAttempts,
            maxQueueDepth = maxQueueDepth,
        )
        a.listener = object : ChannelArbiter.Listener {
            override fun onBusyChanged(busy: Boolean) { busyEvents += busy }
            override fun onSendFailed(packet: Packet, reason: String) { failures += packet to reason }
        }
        return a
    }

    private fun packet(seq: Int, type: MessageType = MessageType.NORMAL) =
        Packet.text(type, Language.HINDI, seq, "msg$seq", timestampMs = 0L)

    @Test
    fun `sends immediately when the channel is idle`() {
        val a = arbiter()
        a.submit(packet(1))
        assertEquals(listOf(1), delivered.map { it.sequence })
    }

    @Test
    fun `a send during a receive is queued, not sent`() {
        val a = arbiter()
        a.onReceiveStarted()
        a.submit(packet(1))

        assertTrue(delivered.isEmpty(), "sent while the half-duplex channel was receiving")
        assertEquals(1, a.queueDepth)
        assertTrue(a.isBusy)
    }

    @Test
    fun `the retry is scheduled for the specified one second`() {
        val a = arbiter()
        a.onReceiveStarted()
        a.submit(packet(1))

        assertEquals(
            listOf(ChannelArbiter.DEFAULT_RETRY_DELAY_MS),
            scheduler.requestedDelays(),
        )
        assertEquals(1000L, ChannelArbiter.DEFAULT_RETRY_DELAY_MS)
    }

    @Test
    fun `the queued send goes out when the retry fires after the channel clears`() {
        val a = arbiter()
        a.onReceiveStarted()
        a.submit(packet(1))
        assertTrue(delivered.isEmpty())

        // Channel still busy when the timer fires: still nothing sent.
        scheduler.runPending()
        assertTrue(delivered.isEmpty(), "sent while still receiving")

        a.onReceiveFinished()
        assertEquals(listOf(1), delivered.map { it.sequence })
    }

    @Test
    fun `busy state is reported to the listener in both directions`() {
        val a = arbiter()
        a.onReceiveStarted()
        a.onReceiveFinished()
        assertEquals(listOf(true, false), busyEvents, "channel-busy callback did not toggle")
    }

    @Test
    fun `busy state is not re-reported for repeated receive starts`() {
        val a = arbiter()
        a.onReceiveStarted()
        a.onReceiveStarted()
        a.onReceiveFinished()
        assertEquals(listOf(true, false), busyEvents, "duplicate busy callbacks would flicker a UI")
    }

    @Test
    fun `alerts jump ahead of queued normal traffic`() {
        val a = arbiter()
        a.onReceiveStarted()
        a.submit(packet(1))
        a.submit(packet(2))
        a.submit(packet(3, MessageType.ALERT))
        a.onReceiveFinished()

        assertEquals(
            listOf(3, 1, 2), delivered.map { it.sequence },
            "an alert was delivered behind ordinary traffic",
        )
    }

    @Test
    fun `a send that keeps failing is abandoned after the configured attempts`() {
        val a = arbiter(maxAttempts = 3)
        deliverySucceeds = false
        try {
            a.submit(packet(1))
            // One attempt made on submit; drive the two scheduled retries.
            scheduler.runPending()
            scheduler.runPending()

            assertEquals(1, failures.size, "packet was not abandoned after 3 attempts")
            assertTrue(failures.single().second.contains("3 attempts"))
            assertEquals(0, a.queueDepth)
        } finally {
            deliverySucceeds = true
        }
    }

    @Test
    fun `queue overflow evicts the oldest normal packet and never an alert`() {
        val a = arbiter(maxQueueDepth = 3)
        a.onReceiveStarted()
        a.submit(packet(1))
        a.submit(packet(2, MessageType.ALERT))
        a.submit(packet(3))
        // Full: submitting a fourth must evict the oldest NORMAL (seq 1), not the alert.
        a.submit(packet(4))

        assertEquals(1, failures.size)
        assertEquals(1, failures.single().first.sequence, "evicted the wrong packet")

        a.onReceiveFinished()
        val sent = delivered.map { it.sequence }
        assertTrue(2 in sent, "the alert was evicted from a full queue")
        assertFalse(1 in sent, "the evicted packet was still delivered")
    }

    @Test
    fun `reset clears the queue and the busy state`() {
        val a = arbiter()
        a.onReceiveStarted()
        a.submit(packet(1))
        a.reset()

        assertEquals(0, a.queueDepth)
        assertFalse(a.isBusy)
    }
}
