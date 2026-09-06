package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.queue.OutboundMessage
import `in`.gov.itantra.core.queue.OutboundMessageQueue
import `in`.gov.itantra.core.stt.EndpointTrigger
import `in`.gov.itantra.core.stt.FakeSttEngine
import `in`.gov.itantra.core.stt.SttResult
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.Transport
import `in`.gov.itantra.core.transport.TransportListener
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StartPttTransmissionUseCaseTest {

    private val sentPackets = mutableListOf<Packet>()
    
    private val fakeTransport = object : Transport {
        override val kind = TransportKind.LOOPBACK
        override val pairingInfo = null
        override val lastRoundTripMs = null
        override var state: ConnectionState = ConnectionState.CONNECTED
        override fun setListener(listener: TransportListener?) {}
        override fun connect(timeoutMs: Long) {}
        override fun disconnect() {}
        override fun send(packet: Packet) { sentPackets.add(packet) }
        override fun requestFloor() {}
        override fun releaseFloor() {}
        override fun confirmPairing() {}
    }

    private val fakeQueue = OutboundMessageQueue()

    @Test
    fun `ignores empty text`() {
        val stt = FakeSttEngine()
        val useCase = StartPttTransmissionUseCase(stt, null, fakeQueue, AtomicInteger(0))
        
        useCase.execute(
            Language.HINDI, fakeTransport, sendLive = true,
            onPartialResult = {}, onFinalResult = {}, onQueued = {}, onError = {}
        )
        
        stt.listener?.onFinal(SttResult("   ", Language.HINDI, null, EndpointTrigger.SILENCE, 100, 0))
        
        assertTrue(sentPackets.isEmpty())
        assertTrue(fakeQueue.messages.isEmpty())
    }

    @Test
    fun `ignores cancelled result`() {
        val stt = FakeSttEngine()
        val useCase = StartPttTransmissionUseCase(stt, null, fakeQueue, AtomicInteger(0))
        
        useCase.execute(
            Language.HINDI, fakeTransport, sendLive = true,
            onPartialResult = {}, onFinalResult = {}, onQueued = {}, onError = {}
        )
        
        stt.listener?.onFinal(SttResult("Valid Text", Language.HINDI, EndpointTrigger.CANCELLED, 100, 100))
        
        assertTrue(sentPackets.isEmpty())
        assertTrue(fakeQueue.messages.isEmpty())
    }

    @Test
    fun `sendLive true emits packet`() {
        val stt = FakeSttEngine()
        val useCase = StartPttTransmissionUseCase(stt, null, fakeQueue, AtomicInteger(0))
        
        useCase.execute(
            Language.HINDI, fakeTransport, sendLive = true,
            onPartialResult = {}, onFinalResult = {}, onQueued = {}, onError = {}
        )
        
        stt.listener?.onFinal(SttResult("Test", Language.HINDI, null, EndpointTrigger.SILENCE, 100, 0))
        
        assertEquals(1, sentPackets.size)
        assertEquals("Hello", sentPackets[0].text)
        assertEquals(MessageType.NORMAL, sentPackets[0].type)
        assertTrue(fakeQueue.messages.isEmpty())
    }

    @Test
    fun `sendLive false queues packet`() {
        val stt = FakeSttEngine()
        val useCase = StartPttTransmissionUseCase(stt, null, fakeQueue, AtomicInteger(0))
        var queuedItem: OutboundMessage? = null
        
        useCase.execute(
            Language.HINDI, fakeTransport, sendLive = false,
            onPartialResult = {}, onFinalResult = {}, onQueued = { queuedItem = it }, onError = {}
        )
        
        stt.listener?.onFinal(SttResult("Hello Offline", Language.HINDI, EndpointTrigger.SILENCE, 100, 100))
        
        assertTrue(sentPackets.isEmpty())
        assertEquals(1, fakeQueue.messages.size)
        assertEquals("Hello Offline", fakeQueue.messages[0].text)
        assertEquals(fakeQueue.messages[0], queuedItem)
    }
}
