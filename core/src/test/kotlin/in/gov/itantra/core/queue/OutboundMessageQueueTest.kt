package `in`.gov.itantra.core.queue

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.PairingInfo
import `in`.gov.itantra.core.transport.Transport
import `in`.gov.itantra.core.transport.TransportKind
import `in`.gov.itantra.core.transport.TransportListener
import `in`.gov.itantra.core.transport.TransportStats
import `in`.gov.itantra.core.usecase.FlushQueuedMessagesUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutboundMessageQueueTest {

    @Test
    fun `blank and whitespace only utterances are not stored`() {
        val q = OutboundMessageQueue()
        assertNull(q.enqueue(Language.HINDI, "   "))
        assertEquals(0, q.pendingCount())
    }

    @Test
    fun `caps at ten and drops the oldest waiting item first`() {
        val q = OutboundMessageQueue(maxItems = 3)
        q.enqueue(Language.HINDI, "one")
        q.enqueue(Language.HINDI, "two")
        q.enqueue(Language.HINDI, "three")
        q.enqueue(Language.HINDI, "four")
        val texts = q.snapshot().map { it.text }
        assertEquals(listOf("two", "three", "four"), texts)
    }

    @Test
    fun `does not evict an item that is mid send`() {
        val q = OutboundMessageQueue(maxItems = 2)
        val first = q.enqueue(Language.HINDI, "keep")!!
        q.markSending(first.id)
        q.enqueue(Language.HINDI, "new")
        q.enqueue(Language.HINDI, "newer")
        assertTrue(q.snapshot().any { it.id == first.id && it.state == OutboundState.SENDING })
        assertEquals(2, q.snapshot().size)
    }

    @Test
    fun `crash during send is recovered as failed not lost`() {
        val now = 1_700_000_000_000L
        val store = MemoryQueueStore(
            outbound = listOf(
                OutboundMessage("a", Language.TAMIL, "hello", now, OutboundState.SENDING),
            )
        )
        val q = OutboundMessageQueue(store, clock = { now })
        assertEquals(OutboundState.FAILED, q.snapshot().single().state)
    }

    @Test
    fun `flush refuses to send before pairing and leaves the queue intact`() {
        val q = OutboundMessageQueue()
        q.enqueue(Language.HINDI, "hold")
        val transport = FakeTransport()
        val result = FlushQueuedMessagesUseCase(q).execute(transport, pairingConfirmed = false)
        assertEquals(0, result.sentIds.size)
        assertEquals(0, transport.sent.size)
        assertEquals(1, q.pendingCount())
    }

    @Test
    fun `drops items older than thirty days and never sends them`() {
        var now = 1_700_000_000_000L
        val q = OutboundMessageQueue(clock = { now })
        q.enqueue(Language.HINDI, "old")
        now += QueueTtl.DURATION_MS
        assertEquals(0, q.pendingCount())
        assertTrue(q.snapshot().isEmpty())

        val transport = FakeTransport()
        val result = FlushQueuedMessagesUseCase(q).execute(transport, pairingConfirmed = true)
        assertEquals(0, result.sentIds.size)
        assertEquals(0, transport.sent.size)
    }

    @Test
    fun `keeps an item that is one day shy of thirty days`() {
        var now = 1_700_000_000_000L
        val q = OutboundMessageQueue(clock = { now })
        q.enqueue(Language.HINDI, "keep")
        now += QueueTtl.DURATION_MS - 24L * 60L * 60L * 1000L
        assertEquals(1, q.pendingCount())
        assertEquals("keep", q.snapshot().single().text)
    }

    @Test
    fun `corrupt and far-future timestamps are treated as expired`() {
        val now = 1_700_000_000_000L
        val store = MemoryQueueStore(
            outbound = listOf(
                OutboundMessage("zero", Language.HINDI, "bad", 0L, OutboundState.QUEUED),
                OutboundMessage("future", Language.HINDI, "bad", now + QueueTtl.DURATION_MS, OutboundState.QUEUED),
                OutboundMessage("ok", Language.HINDI, "ok", now, OutboundState.QUEUED),
            )
        )
        val q = OutboundMessageQueue(store = store, clock = { now })
        assertEquals(listOf("ok"), q.snapshot().map { it.text })
    }

    @Test
    fun `flush sends queued packets and a transport drop fails closed`() {
        val q = OutboundMessageQueue()
        q.enqueue(Language.HINDI, "first")
        q.enqueue(Language.HINDI, "second")
        val transport = FakeTransport(failOn = 2)
        val result = FlushQueuedMessagesUseCase(q).execute(transport, pairingConfirmed = true)
        assertEquals(1, result.sentIds.size)
        assertEquals(1, result.failedIds.size)
        assertEquals(1, transport.sent.size)
        assertEquals(MessageType.QUEUED, transport.sent.single().type)
        assertEquals(1, q.failedCount())
        assertEquals(OutboundState.FAILED, q.snapshot().last().state)
        assertEquals(OutboundState.DELIVERED, q.snapshot().first().state)
    }
}

private class MemoryQueueStore(
    outbound: List<OutboundMessage> = emptyList(),
    inbox: List<InboxMessage> = emptyList(),
) : QueueStore {
    private var out = outbound
    private var inn = inbox
    override fun loadOutbound() = out
    override fun saveOutbound(items: List<OutboundMessage>) { out = items }
    override fun loadInbox() = inn
    override fun saveInbox(items: List<InboxMessage>) { inn = items }
}

private class FakeTransport(
    private val failOn: Int = Int.MAX_VALUE,
) : Transport {
    val sent = mutableListOf<Packet>()
    private var n = 0
    override val kind = TransportKind.LOOPBACK
    override var state = ConnectionState.CONNECTED
    override val pairingInfo: PairingInfo? = null
    override fun setListener(listener: TransportListener?) = Unit
    override fun connect(timeoutMs: Long) = Unit
    override fun confirmPairing() = Unit
    override fun send(packet: Packet) {
        n++
        if (n >= failOn) throw IllegalStateException("link down")
        sent += packet
    }
    override fun requestFloor() = Unit
    override fun releaseFloor() = Unit
    override fun disconnect() = Unit
    override val lastRoundTripMs: Long? = null
    override val stats = TransportStats()
    override fun close() = Unit
}
