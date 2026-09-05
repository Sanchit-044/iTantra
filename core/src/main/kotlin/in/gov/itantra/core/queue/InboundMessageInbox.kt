package `in`.gov.itantra.core.queue

import `in`.gov.itantra.core.Language

data class InboxMessage(
    val id: String,
    val language: Language,
    val text: String,
    val receivedAtMs: Long,
    val unread: Boolean = true,
)

/**
 * Received store-and-forward messages. Never plays them -- that is the caller's job
 * after the operator taps Play.
 */
class InboundMessageInbox(
    private val store: QueueStore? = null,
    private val maxItems: Int = DEFAULT_MAX_ITEMS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()
    private val items = ArrayDeque<InboxMessage>()

    init {
        synchronized(lock) {
            items.addAll(store?.loadInbox().orEmpty())
            expireLocked()
            trimLocked()
            persistLocked()
        }
    }

    /**
     * @return the stored item, or null when [text] is blank or [id] was already received
     * (replay / double dispatch).
     */
    fun offer(
        id: String,
        language: Language,
        text: String,
        receivedAtMs: Long = System.currentTimeMillis(),
    ): InboxMessage? {
        val cleaned = text.trim()
        if (cleaned.isEmpty() || id.isBlank()) return null
        val now = clock()
        if (QueueTtl.isExpired(receivedAtMs, now)) return null
        synchronized(lock) {
            expireLocked()
            if (items.any { it.id == id }) return null
            val message = InboxMessage(id, language, cleaned, receivedAtMs, unread = true)
            items.addLast(message)
            trimLocked()
            persistLocked()
            return message
        }
    }

    fun purgeExpired(): Int = synchronized(lock) {
        val removed = expireLocked()
        if (removed > 0) persistLocked()
        removed
    }

    fun snapshot(): List<InboxMessage> = synchronized(lock) {
        expireLocked()
        items.toList().asReversed()
    }

    fun unreadCount(): Int = synchronized(lock) {
        expireLocked()
        items.count { it.unread }
    }

    fun find(id: String): InboxMessage? = synchronized(lock) {
        if (expireLocked() > 0) persistLocked()
        items.firstOrNull { it.id == id }
    }

    fun markRead(id: String) {
        synchronized(lock) {
            expireLocked()
            val index = items.indexOfFirst { it.id == id }
            if (index < 0) return
            items[index] = items[index].copy(unread = false)
            persistLocked()
        }
    }

    fun discard(id: String) {
        synchronized(lock) {
            items.removeAll { it.id == id }
            persistLocked()
        }
    }

    private fun expireLocked(): Int {
        val now = clock()
        val before = items.size
        items.removeAll { QueueTtl.isExpired(it.receivedAtMs, now) }
        return before - items.size
    }

    private fun trimLocked() {
        while (items.size > maxItems) items.removeFirst()
    }

    private fun persistLocked() {
        store?.saveInbox(items.toList())
    }

    companion object {
        const val DEFAULT_MAX_ITEMS = 10
    }
}
