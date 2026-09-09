package `in`.gov.itantra.core.transport

import `in`.gov.itantra.core.crypto.PairingCode

enum class TransportKind { WIFI_DIRECT, BLUETOOTH_RFCOMM, LAN, LOOPBACK }

enum class ConnectionState { DISCONNECTED, DISCOVERING, HANDSHAKING, CONNECTED, FAILED }

class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Information a future pairing screen needs. Produced during connection setup and
 * exposed here; rendering and confirming it is out of scope for this pass.
 */
data class PairingInfo(
    /** The 6-digit short authentication string. See [PairingCode]. */
    val code: String,
    val peerName: String,
    val peerAddress: String,
    /** True when the local private key lives in hardware-isolated Keystore. */
    val hardwareBacked: Boolean,
)

interface TransportListener {
    fun onStateChanged(state: ConnectionState) {}

    /**
     * The handshake produced a pairing code. A future UI shows this and asks both
     * operators to confirm it matches. Until [Transport.confirmPairing] is called the
     * session is established but unconfirmed.
     */
    fun onPairingCodeAvailable(info: PairingInfo) {}

    fun onReceive(packet: Packet) {}

    /**
     * The half-duplex channel became busy or idle. A future UI shows "channel busy"
     * from this. See [ChannelArbiter].
     */
    fun onChannelBusyChanged(busy: Boolean) {}

    /** Local PTT may start STT. */
    fun onFloorGranted() {}

    /** Local PTT must not start STT. [reason] is for the status line, not a disconnect. */
    fun onFloorDenied(reason: String) {}

    /** A packet could not be delivered after the configured retries. */
    fun onSendFailed(packet: Packet, reason: String) {}

    /** A frame arrived but failed authentication or parsing. Counted by Module B7. */
    fun onPacketDiscarded(reason: String) {}

    fun onError(error: TransportException) {}
}

/**
 * Module B5 -- the common transport contract.
 *
 * Both WiFiDirectTransport and BluetoothTransport implement this and share the same
 * [PacketCodec] wire format, so the layers above never learn which radio is in use.
 *
 * All implementations are half-duplex in practice: Wi-Fi Direct on a single channel
 * and RFCOMM over a shared baseband both degrade badly if a send collides with a
 * receive. [ChannelArbiter] enforces that at this layer rather than leaving it to
 * callers.
 */
interface Transport : AutoCloseable {

    val kind: TransportKind

    val state: ConnectionState

    /** Non-null once the handshake has completed. */
    val pairingInfo: PairingInfo?

    fun setListener(listener: TransportListener?)

    /**
     * Discover a peer, establish the link, and perform the ECDH handshake.
     * Blocking; call off the main thread. On success the state becomes
     * [ConnectionState.CONNECTED] and [pairingInfo] is populated.
     */
    fun connect(timeoutMs: Long = DEFAULT_CONNECT_TIMEOUT_MS)

    /**
     * Records that the operator confirmed the pairing code matches the peer's.
     * Until this is called, [send] of anything other than a handshake message is
     * rejected, so an unconfirmed (possibly man-in-the-middled) session cannot carry
     * real traffic.
     */
    fun confirmPairing()

    /**
     * Queue [packet] for transmission. Returns immediately; delivery is asynchronous
     * because the channel may be busy receiving. Failure is reported through
     * [TransportListener.onSendFailed].
     */
    fun send(packet: Packet)

    /**
     * Ask for the talk token. STT must start only after [TransportListener.onFloorGranted].
     */
    fun requestFloor()

    /** Give the talk token back. Safe if we never held it. */
    fun releaseFloor()

    fun disconnect()

    /** Round-trip time from the most recent heartbeat, or null if none completed. */
    val lastRoundTripMs: Long?

    /** Counters for Module B7. */
    val stats: TransportStats

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 30_000L
    }
}

/** Live transport counters consumed by the diagnostics service. */
data class TransportStats(
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    /** Frames dropped: failed authentication, malformed, or unknown fields. */
    val packetsDiscarded: Long = 0,
    /** Sends abandoned after exhausting busy-channel retries. */
    val sendFailures: Long = 0,
    val bytesSent: Long = 0,
    val bytesReceived: Long = 0,
)
