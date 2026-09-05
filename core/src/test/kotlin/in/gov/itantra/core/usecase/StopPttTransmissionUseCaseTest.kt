package `in`.gov.itantra.core.usecase

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.stt.FakeSttEngine
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.Transport
import `in`.gov.itantra.core.transport.TransportListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StopPttTransmissionUseCaseTest {

    @Test
    fun `execute stops stt and releases floor`() {
        val stt = FakeSttEngine()
        val useCase = StopPttTransmissionUseCase(stt)
        
        stt.start(object : `in`.gov.itantra.core.stt.SttListener {
            override fun onPartial(text: String) {}
            override fun onFinal(result: `in`.gov.itantra.core.stt.SttResult) {}
            override fun onError(error: `in`.gov.itantra.core.stt.SttException) {}
        })
        assertTrue(stt.isListening)
        
        var floorReleased = false
        val fakeTransport = object : Transport {
            override val state: ConnectionState = ConnectionState.CONNECTED
            override fun setListener(listener: TransportListener?) {}
            override fun connect() {}
            override fun disconnect() {}
            override fun send(packet: Packet) {}
            override fun requestFloor() {}
            override fun releaseFloor() { floorReleased = true }
            override fun confirmPairing() {}
        }
        
        useCase.execute(fakeTransport)
        
        assertFalse(stt.isListening)
        assertTrue(floorReleased)
    }
}
