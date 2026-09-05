package `in`.gov.itantra.core.queue

import `in`.gov.itantra.core.Language
import java.util.UUID

enum class OutboundState { QUEUED, SENDING, FAILED }

data class OutboundMessage(
    val id: String,
    val language: Language,
    val text: String,
    val createdAtMs: Long,
    val state: OutboundState,
)

interface QueueStore {
    fun loadOutbound(): List<OutboundMessage>
    fun saveOutbound(items: List<OutboundMessage>)
    fun loadInbox(): List<InboxMessage>
    fun saveInbox(items: List<InboxMessage>)
}

/**
 * Local store-and-forward buffer for speech captured while the radio is down.
 *
 * Text only -- a WAV would blow the 2 GB budget and is not what the wire sends.
 * Caps at [maxItems] and [maxChars] so a wedged operator cannot grow this without bound.
 * Items older than [QueueTtl] are deleted. Blank and cancelled utterances are rejected.
 * Oldest Queued/Failed items fall off first; an item in [OutboundState.SENDING] is kept
 * so a flush in flight is not silently dropped -- unless it is already past TTL on load.
 */
class OutboundMessageQueue(
    private val store: QueueStore? = null,
    private val maxItems: Int = DEFAULT_MAX_ITEMS,
    private val maxChars: Int = DEFAULT_MAX_CHARS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val lock = Any()
    private val items = ArrayDeque<OutboundMessage>()

    init {
        val loaded = store?.loadOutbound().orEmpty()
        synchronized(lock) {
            for (item in loaded) {
                val recovered = if (item.state == OutboundState.SENDING) {
                    item.copy(state = OutboundState.FAILED)
                } else {
                    item
                }
                items.addLast(recovered)
            }
            expireLocked()
            trimLocked()
            persistLocked()
        }
    }

    fun enqueue(language: Language, text: String): OutboundMessage? {
        val cleaned = sanitize(text) ?: return null
        val now = clock()
        val message = OutboundMessage(
            id = UUID.randomUUID().toString(),
            language = language,
            text = cleaned,
            createdAtMs = now,
            state = OutboundState.QUEUED,
        )
        synchronized(lock) {
            expireLocked()
            items.addLast(message)
            trimLocked()
            persistLocked()
        }
        return message
    }

    /** Drop expired rows and persist. Safe to call from the UI on every open. */
    fun purgeExpired(): Int = synchronized(lock) {
        val removed = expireLocked()
        if (removed > 0) persistLocked()
        removed
    }

    fun snapshot(): List<OutboundMessage> = synchronized(lock) {
        expireLocked()
        items.toList()
    }

    fun pendingCount(): Int = synchronized(lock) {
        expireLocked()
        items.count { it.state != OutboundState.SENDING }
    }

    fun failedCount(): Int = synchronized(lock) {
        expireLocked()
        items.count { it.state == OutboundState.FAILED }
    }

    /** Queued and Failed, oldest first. Sending items are left alone. */
    fun itemsToFlush(): List<OutboundMessage> = synchronized(lock) {
        if (expireLocked() > 0) persistLocked()
        items.filter { it.state == OutboundState.QUEUED || it.state == OutboundState.FAILED }
    }

    /** @return false if the item is gone or past TTL -- caller must not send it. */
    fun markSending(id: String): Boolean = update(id, OutboundState.SENDING)

    fun markFailed(id: String): Boolean = update(id, OutboundState.FAILED)

    fun markSent(id: String) {
        synchronized(lock) {
            items.removeAll { it.id == id }
            persistLocked()
        }
    }

    fun discard(id: String) {
        synchronized(lock) {
            items.removeAll { it.id == id }
            persistLocked()
        }
    }

    private fun update(id: String, state: OutboundState): Boolean {
        synchronized(lock) {
            expireLocked()
            val index = items.indexOfFirst { it.id == id }
            if (index < 0) return false
            val current = items[index]
            if (expired(current.createdAtMs)) {
                items.removeAt(index)
                persistLocked()
                return false
            }
            items[index] = current.copy(state = state)
            persistLocked()
            return true
        }
    }

    private fun expireLocked(): Int {
        val now = clock()
        val before = items.size
        items.removeAll { item ->
            if (item.state == OutboundState.SENDING) false else expired(item.createdAtMs, now)
        }
        return before - items.size
    }

    private fun expired(timestampMs: Long, nowMs: Long = clock()): Boolean =
        QueueTtl.isExpired(timestampMs, nowMs)

    private fun trimLocked() {
        while (items.size > maxItems) {
            val victim = items.indexOfFirst { it.state != OutboundState.SENDING }
            if (victim < 0) break
            items.removeAt(victim)
        }
    }

    private fun persistLocked() {
        store?.saveOutbound(items.toList())
    }

    private fun sanitize(text: String): String? {
        val trimmed = text.trim().replace(WHITESPACE, " ")
        if (trimmed.isEmpty()) return null
        return if (trimmed.length <= maxChars) trimmed else trimmed.take(maxChars)
    }

    companion object {
        const val DEFAULT_MAX_ITEMS = 10
        const val DEFAULT_MAX_CHARS = 2_000
        private val WHITESPACE = Regex("\\s+")
    }
}
