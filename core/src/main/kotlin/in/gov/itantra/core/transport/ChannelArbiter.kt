package `in`.gov.itantra.core.transport

import java.util.ArrayDeque

/** Injected time source, so busy-channel behaviour can be tested without sleeping. */
fun interface Clock {
    fun nowMs(): Long

    companion object {
        val SYSTEM = Clock { System.currentTimeMillis() }
    }
}

/** A cancellable future task. */
interface Cancellable {
    fun cancel()
}

/**
 * Injected delayed executor. Production uses a single-threaded scheduler; tests use a
 * fake that runs tasks on demand, which is what makes the retry logic deterministic.
 */
interface Scheduler {
    fun schedule(delayMs: Long, task: () -> Unit): Cancellable
}

/**
 * Half-duplex channel arbitration for Module B5.
 *
 * Wi-Fi Direct and RFCOMM are both effectively half-duplex here: transmitting while
 * the peer is mid-transmission corrupts or drops one of the two. The rule this
 * implements is the specified one -- if a send is attempted while a receive is in
 * progress, hold it for one second and retry -- plus the state callback a future UI
 * needs to display "channel busy".
 *
 * One design choice worth stating rather than burying: ALERT packets are placed at the
 * head of the pending queue rather than behind ordinary traffic. In a disaster-response
 * app, a queued alert arriving after three ordinary messages is a safety problem, and
 * FIFO would produce exactly that. Ordering within each priority class stays FIFO.
 *
 * Floor-control packets (FLOOR_REQUEST/GRANT/DENY/RELEASE) share that head-of-queue
 * priority. They are small, latency-sensitive handshake messages that gate whether the
 * microphone may open at all -- if one sits FIFO behind a queued voice message it can
 * easily miss [FloorController]'s grant-timeout window even though the channel was
 * only briefly busy, producing spurious "no floor grant" failures under real traffic.
 */
class ChannelArbiter(
    private val sender: Sender,
    private val scheduler: Scheduler,
    private val clock: Clock = Clock.SYSTEM,
    /** Specified hold time before retrying a send blocked by an in-flight receive. */
    private val retryDelayMs: Long = DEFAULT_RETRY_DELAY_MS,
    /** Attempts before a packet is abandoned and reported. */
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    /** Bound on queued packets, so a wedged channel cannot exhaust memory. */
    private val maxQueueDepth: Int = DEFAULT_MAX_QUEUE_DEPTH,
) {
    /** Performs the actual radio write. Returns false if the write could not proceed. */
    fun interface Sender {
        fun deliver(packet: Packet): Boolean
    }

    interface Listener {
        fun onBusyChanged(busy: Boolean) {}
        fun onSendFailed(packet: Packet, reason: String) {}
        fun onSendDeferred(packet: Packet, attempt: Int) {}
    }

    private class Pending(val packet: Packet, var attempts: Int = 0)

    private val lock = Any()
    private val queue = ArrayDeque<Pending>()

    private var receiving = false
    private var busySignalled = false
    private var retryTask: Cancellable? = null

    var listener: Listener? = null

    /** True while the channel is unavailable for sending. */
    val isBusy: Boolean get() = synchronized(lock) { receiving }

    val queueDepth: Int get() = synchronized(lock) { queue.size }

    /** Called by the transport when it begins reading an inbound frame. */
    fun onReceiveStarted() {
        synchronized(lock) {
            receiving = true
            signalBusyLocked()
        }
    }

    /**
     * Called by the transport when the inbound frame is complete. Anything queued
     * while busy is flushed immediately rather than waiting out the retry delay --
     * the delay exists to avoid colliding, and the collision window has just closed.
     */
    fun onReceiveFinished() {
        synchronized(lock) {
            receiving = false
            signalBusyLocked()
        }
        pump()
    }

    /** Submit a packet for delivery. */
    fun submit(packet: Packet) {
        synchronized(lock) {
            if (queue.size >= maxQueueDepth) {
                // Drop the oldest NORMAL packet rather than the newest: stale position
                // reports are worth less than current ones. Never drop a priority packet
                // (alert or floor control). Priority packets sit at the head, so the
                // first non-priority packet is the oldest ordinary one.
                val victim = queue.firstOrNull { !it.packet.isPriority }
                if (victim == null) {
                    listener?.onSendFailed(packet, "send queue full (${queue.size}) and all queued packets are priority")
                    return
                }
                queue.remove(victim)
                listener?.onSendFailed(victim.packet, "evicted: send queue full")
            }
            val pending = Pending(packet)
            if (packet.isPriority) queue.addFirst(pending) else queue.addLast(pending)
        }
        pump()
    }

    /**
     * Attempt to drain the queue. Safe to call from any thread and re-entrantly; work
     * is only performed by whichever caller finds the channel idle.
     */
    fun pump() {
        while (true) {
            val pending: Pending = synchronized(lock) {
                if (receiving) {
                    scheduleRetryLocked()
                    return
                }
                queue.peekFirst() ?: return
            }

            val delivered = try {
                sender.deliver(pending.packet)
            } catch (e: Exception) {
                false
            }

            synchronized(lock) {
                if (delivered) {
                    queue.remove(pending)
                } else {
                    pending.attempts++
                    if (pending.attempts >= maxAttempts) {
                        queue.remove(pending)
                        listener?.onSendFailed(
                            pending.packet,
                            "delivery failed after ${pending.attempts} attempts",
                        )
                    } else {
                        listener?.onSendDeferred(pending.packet, pending.attempts)
                        scheduleRetryLocked()
                        return
                    }
                }
            }
        }
    }

    /** Cancels pending retries and clears the queue. Called on disconnect. */
    fun reset() {
        synchronized(lock) {
            retryTask?.cancel()
            retryTask = null
            queue.clear()
            receiving = false
            signalBusyLocked()
        }
    }

    private fun scheduleRetryLocked() {
        if (retryTask != null) return
        if (queue.isEmpty()) return
        retryTask = scheduler.schedule(retryDelayMs) {
            synchronized(lock) { retryTask = null }
            pump()
        }
    }

    private fun signalBusyLocked() {
        if (receiving != busySignalled) {
            busySignalled = receiving
            listener?.onBusyChanged(receiving)
        }
    }

    companion object {
        /** The specified hold-and-retry interval. */
        const val DEFAULT_RETRY_DELAY_MS = 1_000L
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_MAX_QUEUE_DEPTH = 32
    }
}
