package `in`.gov.itantra.core.relay

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RelayEngineTest {

    @Test
    fun `forwards new alert packet with valid TTL`() {
        val relay = RelayEngine(localNodeId = "node_1")
        val packet = Packet.text(MessageType.ALERT, Language.HINDI, 1, "test alert")

        val decision = relay.consider(packet, senderNodeId = "node_2", ttl = 3)

        assertTrue(decision is RelayEngine.Decision.Forward)
    }

    @Test
    fun `drops duplicate packet within seen window`() {
        val relay = RelayEngine(localNodeId = "node_1")
        val packet = Packet.text(MessageType.ALERT, Language.HINDI, 1, "test alert")

        val first = relay.consider(packet, senderNodeId = "node_2", ttl = 3)
        val second = relay.consider(packet, senderNodeId = "node_3", ttl = 2)

        assertTrue(first is RelayEngine.Decision.Forward)
        assertTrue(second is RelayEngine.Decision.Drop)
        assertEquals(RelayEngine.Reason.ALREADY_SEEN, (second as RelayEngine.Decision.Drop).reason)
    }

    @Test
    fun `drops packet when TTL exhausted`() {
        val relay = RelayEngine(localNodeId = "node_1")
        val packet = Packet.text(MessageType.ALERT, Language.HINDI, 1, "test alert")

        val decision = relay.consider(packet, senderNodeId = "node_2", ttl = 1)

        assertTrue(decision is RelayEngine.Decision.Drop)
        assertEquals(RelayEngine.Reason.TTL_EXHAUSTED, (decision as RelayEngine.Decision.Drop).reason)
    }

    @Test
    fun `drops own packet`() {
        val relay = RelayEngine(localNodeId = "node_1")
        val packet = Packet.text(MessageType.ALERT, Language.HINDI, 1, "test alert")

        val decision = relay.consider(packet, senderNodeId = "node_1", ttl = 3)

        assertTrue(decision is RelayEngine.Decision.Drop)
        assertEquals(RelayEngine.Reason.OWN_PACKET, (decision as RelayEngine.Decision.Drop).reason)
    }
}
