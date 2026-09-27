package `in`.gov.itantra.core.transport

import `in`.gov.itantra.core.diag.AppLog
import java.util.concurrent.atomic.AtomicInteger

/**
 * Half-duplex PTT floor. The microphone must not open until [FloorState.HOLDING].
 *
 * Simultaneous requests are resolved by [winsTies] (the session host). Timeout
 * fails closed: no grant means no talk. Alerts are not gated here.
 */
enum class FloorState {
    IDLE,
    REQUESTING,
    HOLDING,
    PEER_HOLDING,
}

interface FloorListener {
    fun onGranted() {}
    fun onDenied(reason: String) {}
    fun onPeerHolding() {}
    fun onIdle() {}
}

/**
 * [send] carries a correlation token alongside the message type. Every FLOOR_REQUEST
 * we emit gets a fresh, locally-unique token; a peer answering it with FLOOR_GRANT or
 * FLOOR_DENY echoes that same token back. Without this, [onRemote] could only tell
 * "am I currently REQUESTING", and a grant/deny delayed by retransmission (see
 * [ChannelArbiter]) could arrive after the user aborted and re-pressed PTT, landing on
 * a *new* request that also happens to be REQUESTING and getting misapplied to it.
 */
class FloorController(
    private val send: (MessageType, String) -> Unit,
    private val scheduler: Scheduler,
    private val winsTies: Boolean,
    private val grantTimeoutMs: Long = DEFAULT_GRANT_TIMEOUT_MS,
    /**
     * HOLDING and PEER_HOLDING otherwise have no timeout at all. If the packet that
     * would end them is ever lost -- discarded for failing authentication, evicted
     * from a full [ChannelArbiter] queue, or simply never sent because the holder
     * disconnected uncleanly -- the peer is stuck believing the channel is busy
     * forever, denying every future request, with no recovery short of a full
     * disconnect/reconnect. This bounds that: a lease that is never renewed expires.
     */
    private val maxHoldMs: Long = DEFAULT_MAX_HOLD_MS,
) {
    private val lock = Any()
    private var state: FloorState = FloorState.IDLE
    private var timeout: Cancellable? = null

    /** Token of the FLOOR_REQUEST we are currently awaiting an answer for, if any. */
    private var pendingToken: String? = null
    private val tokenSeq = AtomicInteger(0)

    var listener: FloorListener? = null

    val current: FloorState get() = synchronized(lock) { state }
    val hasFloor: Boolean get() = synchronized(lock) { state == FloorState.HOLDING }
    val peerHolds: Boolean get() = synchronized(lock) { state == FloorState.PEER_HOLDING }

    fun requestLocal() {
        AppLog.d("FloorController", "requestLocal() invoked")
        val action = synchronized(lock) {
            when (state) {
                FloorState.IDLE -> {
                    val token = nextToken()
                    pendingToken = token
                    enterLocked(FloorState.REQUESTING)
                    scheduleTimeoutLocked()
                    ({ send(MessageType.FLOOR_REQUEST, token) })
                }
                FloorState.REQUESTING -> null
                FloorState.HOLDING -> ({ listener?.onGranted() })
                FloorState.PEER_HOLDING -> ({ listener?.onDenied(BUSY) })
            }
        }
        action?.invoke()
    }

    fun releaseLocal() {
        AppLog.d("FloorController", "releaseLocal() invoked")
        val shouldNotifyIdle = synchronized(lock) {
            if (state != FloorState.HOLDING && state != FloorState.REQUESTING) {
                return
            }
            // Drop the pending token so a grant/deny for the aborted request that
            // arrives late (it was already in flight) cannot be mistaken for the
            // answer to whatever request comes next.
            pendingToken = null
            enterLocked(FloorState.IDLE)
            true
        }
        send(MessageType.FLOOR_RELEASE, "")
        if (shouldNotifyIdle) listener?.onIdle()
    }

    fun onRemote(type: MessageType, token: String) {
        if (!type.isFloorControl) return
        AppLog.d("FloorController", "onRemote() received floor control packet: $type")
        val action = synchronized(lock) {
            when (type) {
                MessageType.FLOOR_REQUEST -> onRemoteRequestLocked(token)
                MessageType.FLOOR_GRANT -> onRemoteGrantLocked(token)
                MessageType.FLOOR_DENY -> onRemoteDenyLocked(token)
                MessageType.FLOOR_RELEASE -> onRemoteReleaseLocked()
                else -> null
            }
        }
        action?.invoke()
    }

    fun reset() {
        AppLog.d("FloorController", "reset() invoked")
        synchronized(lock) {
            pendingToken = null
            enterLocked(FloorState.IDLE)
        }
        listener?.onIdle()
    }

    private fun onRemoteRequestLocked(token: String): (() -> Unit)? = when (state) {
        FloorState.IDLE -> {
            enterLocked(FloorState.PEER_HOLDING)
            scheduleHoldTimeoutLocked()
            ({
                send(MessageType.FLOOR_GRANT, token)
                listener?.onPeerHolding()
            })
        }
        FloorState.REQUESTING -> if (winsTies) {
            enterLocked(FloorState.HOLDING)
            scheduleHoldTimeoutLocked()
            ({
                send(MessageType.FLOOR_DENY, token)
                listener?.onGranted()
            })
        } else {
            enterLocked(FloorState.PEER_HOLDING)
            scheduleHoldTimeoutLocked()
            ({
                send(MessageType.FLOOR_GRANT, token)
                listener?.onDenied(BUSY)
            })
        }
        FloorState.HOLDING, FloorState.PEER_HOLDING -> {
            ({ send(MessageType.FLOOR_DENY, token) })
        }
    }

    private fun onRemoteGrantLocked(token: String): (() -> Unit)? {
        if (state != FloorState.REQUESTING || token != pendingToken) return null
        enterLocked(FloorState.HOLDING)
        scheduleHoldTimeoutLocked()
        return { listener?.onGranted() }
    }

    private fun onRemoteDenyLocked(token: String): (() -> Unit)? {
        if (state != FloorState.REQUESTING || token != pendingToken) return null
        enterLocked(FloorState.IDLE)
        return { listener?.onDenied(BUSY) }
    }

    private fun onRemoteReleaseLocked(): (() -> Unit)? {
        if (state != FloorState.PEER_HOLDING) return null
        enterLocked(FloorState.IDLE)
        return { listener?.onIdle() }
    }

    private fun enterLocked(next: FloorState) {
        timeout?.cancel()
        timeout = null
        state = next
    }

    private fun scheduleTimeoutLocked() {
        timeout?.cancel()
        timeout = scheduler.schedule(grantTimeoutMs) {
            val timedOut = synchronized(lock) {
                if (state != FloorState.REQUESTING) return@schedule
                pendingToken = null
                enterLocked(FloorState.IDLE)
                true
            }
            if (timedOut) listener?.onDenied(TIMEOUT)
        }
    }

    /**
     * Bounds how long HOLDING or PEER_HOLDING can persist without being ended by a
     * real RELEASE. Cancelled and re-armed by every [enterLocked] the same as the
     * grant timeout, so a clean release/re-grant never fires it.
     */
    private fun scheduleHoldTimeoutLocked() {
        val expiredIn = state
        timeout?.cancel()
        timeout = scheduler.schedule(maxHoldMs) {
            val expired = synchronized(lock) {
                if (state != expiredIn) return@schedule
                enterLocked(FloorState.IDLE)
                true
            }
            if (!expired) return@schedule
            when (expiredIn) {
                FloorState.HOLDING -> {
                    // We held it too long without releasing -- force it back so the
                    // peer isn't blocked forever, and tell our own UI to stop talking.
                    send(MessageType.FLOOR_RELEASE, "")
                    listener?.onDenied(HOLD_TIMEOUT)
                }
                FloorState.PEER_HOLDING -> {
                    // The peer never sent RELEASE (lost packet, or it disconnected
                    // uncleanly). Assume it's gone rather than staying stuck "busy".
                    listener?.onIdle()
                }
                else -> Unit
            }
        }
    }

    private fun nextToken(): String = "${System.nanoTime()}-${tokenSeq.incrementAndGet()}"

    companion object {
        const val DEFAULT_GRANT_TIMEOUT_MS = 5_000L
        const val DEFAULT_MAX_HOLD_MS = 45_000L
        const val BUSY = "channel busy"
        const val TIMEOUT = "no floor grant"
        const val HOLD_TIMEOUT = "max talk time exceeded"
    }
}
