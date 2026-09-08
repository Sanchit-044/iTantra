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
        override val kind = `in`.gov.itantra.core.transport.TransportKind.LOOPBACK
        override val pairingInfo = null
        override val lastRoundTripMs: Long? = null
        override var state: ConnectionState = ConnectionState.CONNECTED
        override fun setListener(listener: TransportListener?) {}
        override fun connect(timeoutMs: Long) {}
        override fun disconnect() {}
        override fun send(packet: Packet) { sentPackets.add(packet) }
        override fun requestFloor() {}
        override fun releaseFloor() {}
        override fun confirmPairing() {}
        override val stats = `in`.gov.itantra.core.transport.TransportStats()
        override fun close() {}
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
        assertTrue(fakeQueue.snapshot().isEmpty())
    }

    @Test
    fun `ignores cancelled result`() {
        val stt = FakeSttEngine()
        val useCase = StartPttTransmissionUseCase(stt, null, fakeQueue, AtomicInteger(0))
        
        useCase.execute(
            Language.HINDI, fakeTransport, sendLive = true,
            onPartialResult = {}, onFinalResult = {}, onQueued = {}, onError = {}
        )
        
        stt.listener?.onFinal(SttResult("Valid Text", Language.HINDI, null, EndpointTrigger.CANCELLED, 100, 0))
        
        assertTrue(sentPackets.isEmpty())
        assertTrue(fakeQueue.snapshot().isEmpty())
    }

    @Test
    fun `sendLive true emits packet`() {
        val stt = FakeSttEngine()
        val useCase = StartPttTransmissionUseCase(stt, null, fakeQueue, AtomicInteger(0))
        
        useCase.execute(
            Language.HINDI, fakeTransport, sendLive = true,
            onPartialResult = {}, onFinalResult = {}, onQueued = {}, onError = {}
        )
        
        stt.listener?.onFinal(SttResult("Hello", Language.HINDI, null, EndpointTrigger.SILENCE, 100, 0))
        
        assertEquals(1, sentPackets.size)
        assertEquals("Hello", sentPackets[0].text)
        assertEquals(MessageType.NORMAL, sentPackets[0].type)
        assertTrue(fakeQueue.snapshot().isEmpty())
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
        
        stt.listener?.onFinal(SttResult("Hello Offline", Language.HINDI, null, EndpointTrigger.SILENCE, 100, 0))
        
        assertTrue(sentPackets.isEmpty())
        val messages = fakeQueue.snapshot()
        assertEquals(1, messages.size)
        assertEquals("Hello Offline", messages[0].text)
        assertEquals(messages[0], queuedItem)
    }
}
