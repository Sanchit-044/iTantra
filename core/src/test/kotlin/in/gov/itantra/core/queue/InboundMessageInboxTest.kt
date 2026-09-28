package `in`.gov.itantra.core.queue

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InboundMessageInboxTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `offer creates inbound message with correct metadata and alert flags`() {
        val inbox = InboundMessageInbox(clock = { now })
        val msg = inbox.offer(
            id = "alert_101",
            language = Language.HINDI,
            text = "आपातकालीन स्थिति - तुरंत मदद चाहिए",
            receivedAtMs = now,
            senderName = "Commander Vikram",
            isAlert = true,
            locationLabel = "Sector 4 Beacon",
        )

        assertNotNull(msg)
        assertEquals("alert_101", msg.id)
        assertEquals(Language.HINDI, msg.language)
        assertEquals("आपातकालीन स्थिति - तुरंत मदद चाहिए", msg.text)
        assertTrue(msg.isAlert)
        assertEquals("Sector 4 Beacon", msg.locationLabel)
        assertEquals("Commander Vikram", msg.senderName)
        assertFalse(msg.unread)
    }

    @Test
    fun `blank text and duplicate ids are ignored so replay cannot inflate the inbox`() {
        val inbox = InboundMessageInbox(clock = { now })
        assertNull(inbox.offer("1", Language.HINDI, "  ", receivedAtMs = now))
        val first = inbox.offer("1", Language.HINDI, "पानी", receivedAtMs = now)
        assertEquals("पानी", first?.text)
        assertNull(inbox.offer("1", Language.HINDI, "पानी", receivedAtMs = now))
        assertEquals(1, inbox.unreadCount())
    }

    @Test
    fun `snapshot returns messages in reverse chronological order`() {
        val inbox = InboundMessageInbox(clock = { now })
        inbox.offer("m1", Language.HINDI, "पहला संदेश", now - 3000L)
        inbox.offer("m2", Language.BENGALI, "दूसरा संदेश", now - 2000L)
        inbox.offer("m3", Language.GUJARATI, "तीसरा संदेश", now - 1000L)

        val snapshot = inbox.snapshot()
        assertEquals(3, snapshot.size)
        assertEquals("m3", snapshot[0].id)
        assertEquals("m2", snapshot[1].id)
        assertEquals("m1", snapshot[2].id)
    }

    @Test
    fun `oldest items fall off when the inbox is full`() {
        val inbox = InboundMessageInbox(maxItems = 2, clock = { now })
        inbox.offer("a", Language.HINDI, "a", now - 3000L)
        inbox.offer("b", Language.HINDI, "b", now - 2000L)
        inbox.offer("c", Language.HINDI, "c", now - 1000L)
        assertEquals(listOf("c", "b"), inbox.snapshot().map { it.text })
    }

    @Test
    fun `inbox drops rows older than thirty days and rejects already-expired arrivals`() {
        var currentTime = 1_700_000_000_000L
        val inbox = InboundMessageInbox(clock = { currentTime })
        inbox.offer("old", Language.HINDI, "old", receivedAtMs = currentTime)
        currentTime += QueueTtl.DURATION_MS
        assertEquals(0, inbox.unreadCount())
        assertNull(inbox.offer("late", Language.HINDI, "late", receivedAtMs = currentTime - QueueTtl.DURATION_MS))
        val fresh = inbox.offer("new", Language.HINDI, "new", receivedAtMs = currentTime)
        assertEquals("new", fresh?.text)
    }

    @Test
    fun `markRead marks target message as read and updates unreadCount`() {
        val inbox = InboundMessageInbox(clock = { now })
        inbox.offer("m1", Language.HINDI, "पहला", now - 2000L)
        inbox.offer("m2", Language.HINDI, "दूसरा", now - 1000L)

        assertEquals(2, inbox.unreadCount())
        inbox.markRead("m1")
        assertEquals(1, inbox.unreadCount())

        val m1 = inbox.find("m1")
        assertNotNull(m1)
        assertFalse(m1.unread)

        val m2 = inbox.find("m2")
        assertNotNull(m2)
        assertTrue(m2.unread)
    }

    @Test
    fun `discard removes message from inbox`() {
        val inbox = InboundMessageInbox(clock = { now })
        inbox.offer("m1", Language.HINDI, "संदेश", now)
        assertEquals(1, inbox.snapshot().size)

        inbox.discard("m1")
        assertEquals(0, inbox.snapshot().size)
        assertNull(inbox.find("m1"))
    }
}
