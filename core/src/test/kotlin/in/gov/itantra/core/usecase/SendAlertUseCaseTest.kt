package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertContent
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.PairingInfo
import `in`.gov.itantra.core.transport.Transport
import `in`.gov.itantra.core.transport.TransportKind
import `in`.gov.itantra.core.transport.TransportListener
import `in`.gov.itantra.core.transport.TransportStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SendAlertUseCaseTest {

    @Test
    fun `refuses to send before pairing`() {
        val transport = FakeAlertTransport()
        assertFailsWith<IllegalStateException> {
            SendAlertUseCase().execute(
                transport,
                Language.HINDI,
                AlertContent.Template(AlertTemplate.ALL_CLEAR),
                pairingConfirmed = false,
            )
        }
        assertTrue(transport.sent.isEmpty())
    }

    @Test
    fun `sends type 0x02 with a stable template key`() {
        val transport = FakeAlertTransport()
        SendAlertUseCase().execute(
            transport,
            Language.TAMIL,
            AlertContent.Template(AlertTemplate.EVACUATE_IMMEDIATELY),
            pairingConfirmed = true,
        )
        val packet = transport.sent.single()
        assertEquals(MessageType.ALERT, packet.type)
        assertEquals(0x02.toByte(), packet.type.wire)
        assertEquals(Language.TAMIL, packet.language)
        assertEquals("tpl:evacuate", packet.text)
        assertEquals(
            AlertContent.Template(AlertTemplate.EVACUATE_IMMEDIATELY),
            AlertTemplate.fromWirePayload(packet.text),
        )
    }

    @Test
    fun `blank custom text is rejected`() {
        val transport = FakeAlertTransport()
        assertFailsWith<IllegalArgumentException> {
            SendAlertUseCase().execute(
                transport,
                Language.HINDI,
                AlertContent.Custom("   "),
                pairingConfirmed = true,
            )
        }
    }
}

private class FakeAlertTransport : Transport {
    val sent = mutableListOf<Packet>()
    override val kind = TransportKind.LOOPBACK
    override var state = ConnectionState.CONNECTED
    override fun requestFloor() {}
    override val pairingInfo: PairingInfo? = null
    override fun setListener(listener: TransportListener?) = Unit
    override fun connect(timeoutMs: Long) = Unit
    override fun confirmPairing() = Unit
    override fun send(packet: Packet) { sent += packet }
    override fun requestFloor() = Unit
    override fun releaseFloor() = Unit
    override fun disconnect() = Unit
    override val lastRoundTripMs: Long? = null
    override val stats = TransportStats()
    override fun close() = Unit
}
