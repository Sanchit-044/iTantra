package `in`.gov.itantra.core.relay

import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import kotlin.random.Random

/**
 * Multi-hop Relay Flooding Engine for long-distance communication.
 *
 * Implements three critical safeguards:
 * 1. TTL decrement (drops when TTL reaches 0).
 * 2. Sliding seen-set deduplication (512 entries) to prevent broadcast storm loops.
 * 3. 0-50 ms randomized jitter delay to prevent RF collisions when multiple intermediate relays forward simultaneously.
 */
class RelayEngine(
    private val localNodeId: String,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val random: Random = Random.Default,
) {
    sealed interface Decision {
        data class Forward(val packet: Packet, val delayMillis: Long) : Decision
        data class Drop(val reason: Reason) : Decision
    }

    enum class Reason {
        NOT_RELAYABLE,
        TTL_EXHAUSTED,
        ALREADY_SEEN,
        OWN_PACKET,
    }

    private val seen = LinkedHashSet<Long>()

    val seenCount: Int get() = seen.size

    fun consider(
        packet: Packet,
        senderNodeId: String? = null,
        ttl: Int = DEFAULT_TTL,
    ): Decision {
        if (!isRelayable(packet.type)) {
            return Decision.Drop(Reason.NOT_RELAYABLE)
        }

        if (senderNodeId != null && senderNodeId == localNodeId) {
            return Decision.Drop(Reason.OWN_PACKET)
        }

        val key = computeKey(packet)

        if (!remember(key)) {
            return Decision.Drop(Reason.ALREADY_SEEN)
        }

        if (ttl <= 1) {
            return Decision.Drop(Reason.TTL_EXHAUSTED)
        }

        val forwardedPacket = packet.copy()
        val delay = random.nextLong(MAX_JITTER_MILLIS + 1)

        return Decision.Forward(forwardedPacket, delay)
    }

    fun markOriginated(packet: Packet) {
        remember(computeKey(packet))
    }

    private fun remember(key: Long): Boolean {
        if (!seen.add(key)) return false
        if (seen.size > capacity) {
            val eldest = seen.first()
            seen.remove(eldest)
        }
        return true
    }

    fun clear() {
        seen.clear()
    }

    private fun isRelayable(type: MessageType): Boolean = when (type) {
        MessageType.ALERT -> true
        MessageType.NORMAL -> true
        MessageType.QUEUED -> true
        MessageType.HEARTBEAT -> false
        MessageType.ACK -> false
        MessageType.FLOOR_REQUEST -> false
        MessageType.FLOOR_GRANT -> false
        MessageType.FLOOR_DENY -> false
        MessageType.FLOOR_RELEASE -> false
        MessageType.PROFILE -> false
    }

    private fun computeKey(packet: Packet): Long {
        val payloadHash = packet.payload.contentHashCode().toLong() and 0xFFFFFFFFL
        val seq = packet.sequence.toLong() and 0xFFFFL
        val type = packet.type.wire.toLong() and 0xFFL
        val lang = packet.language.wire.toLong() and 0xFFL
        return (type shl 56) or (lang shl 48) or (seq shl 32) or payloadHash
    }

    companion object {
        const val DEFAULT_CAPACITY = 512
        const val MAX_JITTER_MILLIS = 50L
        const val DEFAULT_TTL = 3
    }
}
