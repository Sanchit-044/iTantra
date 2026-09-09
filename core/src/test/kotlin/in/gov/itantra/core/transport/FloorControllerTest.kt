package `in`.gov.itantra.core.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloorControllerTest {

    private val sent = mutableListOf<Pair<MessageType, String>>()
    private val events = mutableListOf<String>()
    private lateinit var scheduler: FakeScheduler

    private val sentTypes: List<MessageType> get() = sent.map { it.first }

    private fun controller(winsTies: Boolean): FloorController {
        scheduler = FakeScheduler()
        sent.clear()
        events.clear()
        return FloorController(
            send = { type, token -> sent += type to token },
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

    /** The token this controller most recently sent for the given message type. */
    private fun tokenFor(type: MessageType): String = sent.last { it.first == type }.second

    @Test
    fun `idle request is sent and stt must wait for grant`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        assertEquals(listOf(MessageType.FLOOR_REQUEST), sentTypes)
        assertEquals(FloorState.REQUESTING, f.current)
        assertFalse(f.hasFloor)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `grant opens the floor`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        f.onRemote(MessageType.FLOOR_GRANT, tokenFor(MessageType.FLOOR_REQUEST))
        assertTrue(f.hasFloor)
        assertEquals(listOf("granted"), events)
    }

    @Test
    fun `deny does not open the mic`() {
        val f = controller(winsTies = false)
        f.requestLocal()
        f.onRemote(MessageType.FLOOR_DENY, tokenFor(MessageType.FLOOR_REQUEST))
        assertEquals(FloorState.IDLE, f.current)
        assertFalse(f.hasFloor)
        assertEquals(listOf("denied:${FloorController.BUSY}"), events)
    }

    @Test
    fun `peer request while idle is granted and marks channel busy`() {
        val f = controller(winsTies = true)
        f.onRemote(MessageType.FLOOR_REQUEST, "peer-token")
        assertEquals(listOf(MessageType.FLOOR_GRANT), sentTypes)
        assertEquals("peer-token", tokenFor(MessageType.FLOOR_GRANT))
        assertTrue(f.peerHolds)
        assertEquals(listOf("peer"), events)
    }

    @Test
    fun `local request while peer holds is denied without a new request`() {
        val f = controller(winsTies = true)
        f.onRemote(MessageType.FLOOR_REQUEST, "peer-token")
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
        val hostToken = tokenFor(MessageType.FLOOR_REQUEST)
        guest.requestLocal()
        val guestToken = tokenFor(MessageType.FLOOR_REQUEST)

        host.onRemote(MessageType.FLOOR_REQUEST, guestToken)
        guest.onRemote(MessageType.FLOOR_REQUEST, hostToken)

        assertTrue(host.hasFloor)
        assertTrue(guest.peerHolds)
        assertFalse(guest.hasFloor)
        assertTrue(sentTypes.contains(MessageType.FLOOR_DENY))
        assertTrue(sentTypes.contains(MessageType.FLOOR_GRANT))
    }

    @Test
    fun `release frees the peer`() {
        val f = controller(winsTies = true)
        f.onRemote(MessageType.FLOOR_REQUEST, "peer-token")
        f.onRemote(MessageType.FLOOR_RELEASE, "")
        assertEquals(FloorState.IDLE, f.current)
        assertTrue(events.contains("idle"))
    }

    @Test
    fun `local release while holding announces free`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        f.onRemote(MessageType.FLOOR_GRANT, tokenFor(MessageType.FLOOR_REQUEST))
        sent.clear()
        f.releaseLocal()
        assertEquals(listOf(MessageType.FLOOR_RELEASE), sentTypes)
        assertEquals(FloorState.IDLE, f.current)
    }

    @Test
    fun `cancel while requesting still sends release`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        f.releaseLocal()
        assertEquals(FloorState.IDLE, f.current)
        assertTrue(sentTypes.contains(MessageType.FLOOR_RELEASE))
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
        val token = tokenFor(MessageType.FLOOR_REQUEST)
        f.releaseLocal()
        events.clear()
        f.onRemote(MessageType.FLOOR_GRANT, token)
        assertFalse(f.hasFloor)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `stale grant for an abandoned request is ignored even after a new request starts`() {
        // Regression test: a grant delayed by retransmission (see ChannelArbiter) must
        // not be misapplied to a fresh request just because the state machine happens
        // to be REQUESTING again -- see FloorController's class doc for why a bare
        // state check is not enough correlation.
        val f = controller(winsTies = true)
        f.requestLocal()
        val firstToken = tokenFor(MessageType.FLOOR_REQUEST)
        f.releaseLocal()

        f.requestLocal()
        events.clear()

        // The late answer to the abandoned first request arrives while we are
        // REQUESTING again for a second, distinct attempt.
        f.onRemote(MessageType.FLOOR_GRANT, firstToken)

        assertFalse(f.hasFloor)
        assertEquals(FloorState.REQUESTING, f.current)
        assertTrue(events.isEmpty())

        // The real answer to the second request still works.
        val secondToken = tokenFor(MessageType.FLOOR_REQUEST)
        f.onRemote(MessageType.FLOOR_GRANT, secondToken)
        assertTrue(f.hasFloor)
        assertEquals(listOf("granted"), events)
    }

    @Test
    fun `second request while holding is already granted`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        f.onRemote(MessageType.FLOOR_GRANT, tokenFor(MessageType.FLOOR_REQUEST))
        events.clear()
        f.requestLocal()
        assertEquals(listOf("granted"), events)
        assertTrue(f.hasFloor)
    }

    @Test
    fun `holding denies a late peer request`() {
        val f = controller(winsTies = false)
        f.requestLocal()
        f.onRemote(MessageType.FLOOR_GRANT, tokenFor(MessageType.FLOOR_REQUEST))
        sent.clear()
        f.onRemote(MessageType.FLOOR_REQUEST, "peer-token")
        assertEquals(listOf(MessageType.FLOOR_DENY), sentTypes)
        assertTrue(f.hasFloor)
    }

    @Test
    fun `reset clears floor without sending`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        f.onRemote(MessageType.FLOOR_GRANT, tokenFor(MessageType.FLOOR_REQUEST))
        sent.clear()
        f.reset()
        assertEquals(FloorState.IDLE, f.current)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `peer holding self-heals if the peer never sends release`() {
        // Regression test: without a hold lease, a lost RELEASE (auth failure,
        // queue eviction, unclean disconnect) leaves this device believing the
        // channel is busy forever, denying every future request.
        val f = controller(winsTies = true)
        f.onRemote(MessageType.FLOOR_REQUEST, "peer-token")
        assertTrue(f.peerHolds)
        events.clear()

        scheduler.runPending()

        assertEquals(FloorState.IDLE, f.current)
        assertFalse(f.peerHolds)
        assertEquals(listOf("idle"), events)
    }

    @Test
    fun `holding too long force-releases and notifies locally`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        f.onRemote(MessageType.FLOOR_GRANT, tokenFor(MessageType.FLOOR_REQUEST))
        assertTrue(f.hasFloor)
        sent.clear()
        events.clear()

        scheduler.runPending()

        assertEquals(FloorState.IDLE, f.current)
        assertFalse(f.hasFloor)
        assertEquals(listOf(MessageType.FLOOR_RELEASE), sentTypes)
        assertEquals(listOf("denied:${FloorController.HOLD_TIMEOUT}"), events)
    }

    @Test
    fun `a real release before the hold timeout cancels it`() {
        val f = controller(winsTies = true)
        f.requestLocal()
        f.onRemote(MessageType.FLOOR_GRANT, tokenFor(MessageType.FLOOR_REQUEST))
        f.releaseLocal()
        events.clear()
        sent.clear()

        scheduler.runPending()

        assertTrue(events.isEmpty())
        assertTrue(sent.isEmpty())
    }
}
