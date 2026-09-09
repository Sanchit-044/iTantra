package `in`.gov.itantra.core.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloorControllerTest {

    private val sent = mutableListOf<MessageType>()
    private val events = mutableListOf<String>()
    private lateinit var scheduler: FakeScheduler

    private fun controller(winsTies: Boolean): FloorController {
        scheduler = FakeScheduler()
        sent.clear()
        events.clear()
        return FloorController(
            send = { sent += it },
            scheduler = scheduler,
            winsTies = winsTies,
        ).apply {
            listener = object : FloorListener {
                override fun onGranted() { events += "granted" }
                override fun onDenied(reason: String) { events += "denied:$reason" }
                override fun onPeerHolding() { events += "peer" }
                override fun onIdle() { events += "idle" }
            }
        }
    }

    @Test
    fun `idle request is sent and stt must wait for grant`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        assertEquals(listOf(MessageType.FLOOR_REQUEST), sent)
        assertEquals(FloorState.REQUESTING, f.current)
        assertFalse(f.hasFloor)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `grant opens the floor`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        f.onRemote(MessageType.FLOOR_GRANT)
        assertTrue(f.hasFloor)
        assertEquals(listOf("granted"), events)
    }

    @Test
    fun `deny does not open the mic`() {
        val f = controller(winsTies = false)
        f.requestLocal()
        f.onRemote(MessageType.FLOOR_DENY)
        assertEquals(FloorState.IDLE, f.current)
        assertFalse(f.hasFloor)
        assertEquals(listOf("denied:${FloorController.BUSY}"), events)
    }

    @Test
    fun `peer request while idle is granted and marks channel busy`() {
        val f = controller(winsTies = true)
        f.onRemote(MessageType.FLOOR_REQUEST)
        assertEquals(listOf(MessageType.FLOOR_GRANT), sent)
        assertTrue(f.peerHolds)
        assertEquals(listOf("peer"), events)
    }

    @Test
    fun `local request while peer holds is denied without a new request`() {
        val f = controller(winsTies = true)
        f.onRemote(MessageType.FLOOR_REQUEST)
        sent.clear()
        f.requestLocal()
        assertTrue(sent.isEmpty())
        assertTrue(f.peerHolds)
        assertTrue(events.last().startsWith("denied"))
    }

    @Test
    fun `simultaneous request host wins and guest is denied`() {
        val host = controller(winsTies = true)
        val guest = controller(winsTies = false)
        host.requestLocal()
        guest.requestLocal()
        host.onRemote(MessageType.FLOOR_REQUEST)
        guest.onRemote(MessageType.FLOOR_REQUEST)

        assertTrue(host.hasFloor)
        assertTrue(guest.peerHolds)
        assertFalse(guest.hasFloor)
        assertTrue(sent.contains(MessageType.FLOOR_DENY))
        assertTrue(sent.contains(MessageType.FLOOR_GRANT))
    }

    @Test
    fun `release frees the peer`() {
        val f = controller(winsTies = true)
        f.onRemote(MessageType.FLOOR_REQUEST)
        f.onRemote(MessageType.FLOOR_RELEASE)
        assertEquals(FloorState.IDLE, f.current)
        assertTrue(events.contains("idle"))
    }

    @Test
    fun `local release while holding announces free`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        f.onRemote(MessageType.FLOOR_GRANT)
        sent.clear()
        f.releaseLocal()
        assertEquals(listOf(MessageType.FLOOR_RELEASE), sent)
        assertEquals(FloorState.IDLE, f.current)
    }

    @Test
    fun `cancel while requesting still sends release`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        f.releaseLocal()
        assertEquals(FloorState.IDLE, f.current)
        assertTrue(sent.contains(MessageType.FLOOR_RELEASE))
        assertFalse(f.hasFloor)
    }

    @Test
    fun `timeout fails closed`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        assertEquals(listOf(FloorController.DEFAULT_GRANT_TIMEOUT_MS), scheduler.requestedDelays())
        scheduler.runPending()
        assertEquals(FloorState.IDLE, f.current)
        assertFalse(f.hasFloor)
        assertEquals("denied:${FloorController.TIMEOUT}", events.last())
    }

    @Test
    fun `stale grant after cancel is ignored`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        f.releaseLocal()
        events.clear()
        f.onRemote(MessageType.FLOOR_GRANT)
        assertFalse(f.hasFloor)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `second request while holding is already granted`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        f.onRemote(MessageType.FLOOR_GRANT)
        events.clear()
        f.requestLocal()
        assertEquals(listOf("granted"), events)
        assertTrue(f.hasFloor)
    }

    @Test
    fun `holding denies a late peer request`() {
        val f = controller(winsTies = false)
        f.requestLocal()
        f.onRemote(MessageType.FLOOR_GRANT)
        sent.clear()
        f.onRemote(MessageType.FLOOR_REQUEST)
        assertEquals(listOf(MessageType.FLOOR_DENY), sent)
        assertTrue(f.hasFloor)
    }

    @Test
    fun `reset clears floor without sending`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        f.onRemote(MessageType.FLOOR_GRANT)
        sent.clear()
        f.reset()
        assertEquals(FloorState.IDLE, f.current)
        assertTrue(sent.isEmpty())
    }
}
