package `in`.gov.itantra.core.transport

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

class FloorController(
    private val send: (MessageType) -> Unit,
    private val scheduler: Scheduler,
    private val winsTies: Boolean,
    private val grantTimeoutMs: Long = DEFAULT_GRANT_TIMEOUT_MS,
) {
    private val lock = Any()
    private var state: FloorState = FloorState.IDLE
    private var timeout: Cancellable? = null
    var listener: FloorListener? = null

    val current: FloorState get() = synchronized(lock) { state }
    val hasFloor: Boolean get() = synchronized(lock) { state == FloorState.HOLDING }
    val peerHolds: Boolean get() = synchronized(lock) { state == FloorState.PEER_HOLDING }

    fun requestLocal() {
        val action = synchronized(lock) {
            when (state) {
                FloorState.IDLE -> {
                    enterLocked(FloorState.REQUESTING)
                    scheduleTimeoutLocked()
                    { send(MessageType.FLOOR_REQUEST) }
                }
                FloorState.REQUESTING -> null
                FloorState.HOLDING -> ({ listener?.onGranted() })
                FloorState.PEER_HOLDING -> ({ listener?.onDenied(BUSY) })
            }
        }
        action?.invoke()
    }

    fun releaseLocal() {
        val shouldNotifyIdle = synchronized(lock) {
            if (state != FloorState.HOLDING && state != FloorState.REQUESTING) {
                return
            }
            enterLocked(FloorState.IDLE)
            true
        }
        send(MessageType.FLOOR_RELEASE)
        if (shouldNotifyIdle) listener?.onIdle()
    }

    fun onRemote(type: MessageType) {
        if (!type.isFloorControl) return
        val action = synchronized(lock) {
            when (type) {
                MessageType.FLOOR_REQUEST -> onRemoteRequestLocked()
                MessageType.FLOOR_GRANT -> onRemoteGrantLocked()
                MessageType.FLOOR_DENY -> onRemoteDenyLocked()
                MessageType.FLOOR_RELEASE -> onRemoteReleaseLocked()
                else -> null
            }
        }
        action?.invoke()
    }

    fun reset() {
        synchronized(lock) { enterLocked(FloorState.IDLE) }
        listener?.onIdle()
    }

    private fun onRemoteRequestLocked(): (() -> Unit)? = when (state) {
        FloorState.IDLE -> {
            enterLocked(FloorState.PEER_HOLDING)
            {
                send(MessageType.FLOOR_GRANT)
                listener?.onPeerHolding()
            }
        }
        FloorState.REQUESTING -> if (winsTies) {
            enterLocked(FloorState.HOLDING)
            {
                send(MessageType.FLOOR_DENY)
                listener?.onGranted()
            }
        } else {
            enterLocked(FloorState.PEER_HOLDING)
            {
                send(MessageType.FLOOR_GRANT)
                listener?.onDenied(BUSY)
            }
        }
        FloorState.HOLDING, FloorState.PEER_HOLDING -> {
            { send(MessageType.FLOOR_DENY) }
        }
    }

    private fun onRemoteGrantLocked(): (() -> Unit)? {
        if (state != FloorState.REQUESTING) return null
        enterLocked(FloorState.HOLDING)
        return { listener?.onGranted() }
    }

    private fun onRemoteDenyLocked(): (() -> Unit)? {
        if (state != FloorState.REQUESTING) return null
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
                enterLocked(FloorState.IDLE)
                true
            }
            if (timedOut) listener?.onDenied(TIMEOUT)
        }
    }

    companion object {
        const val DEFAULT_GRANT_TIMEOUT_MS = 1_500L
        const val BUSY = "channel busy"
        const val TIMEOUT = "no floor grant"
    }
}
