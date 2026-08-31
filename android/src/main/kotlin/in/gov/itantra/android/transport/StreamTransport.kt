package `in`.gov.itantra.android.transport

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.crypto.AesGcmSessionCrypto
import `in`.gov.itantra.core.crypto.AuthenticationFailedException
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import `in`.gov.itantra.core.crypto.PairingCode
import `in`.gov.itantra.core.crypto.SessionKeyDerivation
import `in`.gov.itantra.core.transport.Cancellable
import `in`.gov.itantra.core.transport.ChannelArbiter
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.FrameReader
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.PacketCodec
import `in`.gov.itantra.core.transport.PairingInfo
import `in`.gov.itantra.core.transport.Scheduler
import `in`.gov.itantra.core.transport.Transport
import `in`.gov.itantra.core.transport.TransportException
import `in`.gov.itantra.core.transport.TransportListener
import `in`.gov.itantra.core.transport.TransportStats
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Everything both transports share: the handshake, framing, encryption, half-duplex
 * arbitration and heartbeats.
 *
 * Wi-Fi Direct and Bluetooth RFCOMM differ only in how a byte stream is obtained, so
 * that is the single abstract method. Keeping the wire format and the security
 * handshake in one place means the two radios cannot drift apart -- which matters,
 * because a subtle difference between them is exactly the kind of bug that only shows
 * up in the field when one handset falls back to Bluetooth.
 */
abstract class StreamTransport(
    private val keyAgreement: KeyAgreementProvider,
    private val scheduler: Scheduler = DefaultScheduler(),
) : Transport {

    /** The byte stream plus who is on the other end. */
    protected class Link(
        val input: InputStream,
        val output: OutputStream,
        val peerName: String,
        val peerAddress: String,
        val closer: () -> Unit,
    )

    /** Obtain a connected byte stream. Blocking; may throw. */
    protected abstract fun openLink(timeoutMs: Long): Link

    @Volatile
    override var state: ConnectionState = ConnectionState.DISCONNECTED
        protected set

    @Volatile
    override var pairingInfo: PairingInfo? = null
        private set

    @Volatile
    private var listener: TransportListener? = null

    private var link: Link? = null
    private var crypto: AesGcmSessionCrypto? = null
    private var readerThread: Thread? = null
    private val running = AtomicBoolean(false)

    private val sequence = AtomicInteger(0)
    private val pairingConfirmed = AtomicBoolean(false)

    private val sent = AtomicLong(0)
    private val received = AtomicLong(0)
    private val discarded = AtomicLong(0)
    private val sendFailures = AtomicLong(0)
    private val bytesOut = AtomicLong(0)
    private val bytesIn = AtomicLong(0)

    @Volatile
    override var lastRoundTripMs: Long? = null
        private set

    private val heartbeatSentAt = java.util.concurrent.ConcurrentHashMap<Int, Long>()

    private val arbiter = ChannelArbiter(
        sender = { packet -> writePacket(packet) },
        scheduler = scheduler,
    ).apply {
        listener = object : ChannelArbiter.Listener {
            override fun onBusyChanged(busy: Boolean) {
                this@StreamTransport.listener?.onChannelBusyChanged(busy)
            }

            override fun onSendFailed(packet: Packet, reason: String) {
                sendFailures.incrementAndGet()
                this@StreamTransport.listener?.onSendFailed(packet, reason)
            }
        }
    }

    override val stats: TransportStats
        get() = TransportStats(
            packetsSent = sent.get(),
            packetsReceived = received.get(),
            packetsDiscarded = discarded.get(),
            sendFailures = sendFailures.get(),
            bytesSent = bytesOut.get(),
            bytesReceived = bytesIn.get(),
        )

    val isPairingConfirmed: Boolean get() = pairingConfirmed.get()

    val isChannelBusy: Boolean get() = arbiter.isBusy

    override fun setListener(listener: TransportListener?) {
        this.listener = listener
    }

    override fun connect(timeoutMs: Long) {
        if (state == ConnectionState.CONNECTED) return
        setState(ConnectionState.DISCOVERING)
        try {
            val l = openLink(timeoutMs)
            link = l
            setState(ConnectionState.HANDSHAKING)
            handshake(l)
            running.set(true)
            readerThread = Thread({ readLoop(l) }, "itantra-transport-rx").apply { start() }
            setState(ConnectionState.CONNECTED)
        } catch (e: Exception) {
            setState(ConnectionState.FAILED)
            disconnect()
            throw TransportException("connection failed on ${kind.name}", e)
        }
    }

    /**
     * Ephemeral ECDH, then HKDF to an AES-256 session key, then the 6-digit SAS.
     *
     * The exchange is symmetric -- both sides write their hello then read the peer's --
     * so neither transport needs a notion of client and server beyond whatever the
     * radio itself imposes.
     */
    private fun handshake(l: Link) {
        keyAgreement.generateEphemeralKeyPair().use { local ->
            writeHello(l.output, local.publicKeyEncoded)
            val peerPublicKey = readHello(l.input)

            val shared = local.computeSharedSecret(peerPublicKey)
            val transcript = SessionKeyDerivation.transcript(local.publicKeyEncoded, peerPublicKey)
            val sessionKey = SessionKeyDerivation.deriveKey(shared, transcript)

            crypto = AesGcmSessionCrypto(sessionKey)

            val info = PairingInfo(
                code = PairingCode.derive(shared, local.publicKeyEncoded, peerPublicKey),
                peerName = l.peerName,
                peerAddress = l.peerAddress,
                hardwareBacked = keyAgreement.isHardwareBacked,
            )
            pairingInfo = info
            listener?.onPairingCodeAvailable(info)
        }
    }

    private fun writeHello(out: OutputStream, publicKey: ByteArray) {
        val body = ByteBuffer.allocate(MAGIC.size + 1 + 4 + publicKey.size).apply {
            put(MAGIC)
            put(PacketCodec.VERSION)
            putInt(publicKey.size)
            put(publicKey)
        }.array()
        out.write(ByteBuffer.allocate(4).putInt(body.size).array())
        out.write(body)
        out.flush()
    }

    private fun readHello(input: InputStream): ByteArray {
        val din = DataInputStream(input)
        val bodyLen = din.readInt()
        if (bodyLen !in 1..MAX_HELLO_LEN) {
            throw TransportException("implausible handshake length $bodyLen")
        }
        val body = ByteArray(bodyLen)
        din.readFully(body)

        val buf = ByteBuffer.wrap(body)
        val magic = ByteArray(MAGIC.size).also { buf.get(it) }
        if (!magic.contentEquals(MAGIC)) {
            throw TransportException("peer is not an iTantra device")
        }
        val version = buf.get()
        if (version != PacketCodec.VERSION) {
            throw TransportException("peer speaks protocol version $version, we speak ${PacketCodec.VERSION}")
        }
        val keyLen = buf.int
        if (keyLen !in 1..MAX_KEY_LEN) {
            throw TransportException("implausible public key length $keyLen")
        }
        return ByteArray(keyLen).also { buf.get(it) }
    }

    override fun confirmPairing() {
        check(pairingInfo != null) { "no pairing to confirm" }
        pairingConfirmed.set(true)
    }

    override fun send(packet: Packet) {
        check(state == ConnectionState.CONNECTED) { "not connected" }
        // An unconfirmed session may be man-in-the-middled. Only heartbeats, which
        // carry no content, are allowed through before the operators confirm the code.
        if (!pairingConfirmed.get() && packet.type != MessageType.HEARTBEAT) {
            listener?.onSendFailed(packet, "pairing not confirmed; refusing to send content")
            return
        }
        arbiter.submit(packet)
    }

    /** Send a heartbeat and time the ACK. Populates [lastRoundTripMs]. */
    fun sendHeartbeat(language: Language = Language.HINDI) {
        val seq = sequence.incrementAndGet()
        heartbeatSentAt[seq] = System.currentTimeMillis()
        arbiter.submit(
            Packet.text(MessageType.HEARTBEAT, language, seq, "", flags = Packet.FLAG_REQUIRES_ACK)
        )
    }

    private fun writePacket(packet: Packet): Boolean {
        val l = link ?: return false
        val c = crypto ?: return false
        return try {
            val frame = PacketCodec.encode(packet, c)
            synchronized(l.output) {
                l.output.write(frame)
                l.output.flush()
            }
            sent.incrementAndGet()
            bytesOut.addAndGet(frame.size.toLong())
            true
        } catch (e: Exception) {
            listener?.onError(TransportException("write failed", e))
            false
        }
    }

    private fun readLoop(l: Link) {
        val reader = FrameReader()
        val buffer = ByteArray(READ_BUFFER_BYTES)
        val c = crypto ?: return

        while (running.get()) {
            val n = try {
                l.input.read(buffer)
            } catch (e: Exception) {
                if (running.get()) listener?.onError(TransportException("read failed", e))
                break
            }
            if (n < 0) break

            bytesIn.addAndGet(n.toLong())

            // Half-duplex: mark the channel busy for the span of frame reassembly so a
            // concurrent send defers rather than colliding on the air.
            arbiter.onReceiveStarted()
            try {
                val bodies = try {
                    reader.offer(buffer, 0, n)
                } catch (e: PacketCodec.MalformedPacketException) {
                    // Framing is unrecoverable once desynchronised; drop the session
                    // rather than reinterpreting arbitrary bytes as packets.
                    discarded.incrementAndGet()
                    listener?.onPacketDiscarded("framing lost: ${e.message}")
                    break
                }

                for (body in bodies) {
                    val packet = try {
                        PacketCodec.decodeBody(body, c)
                    } catch (e: AuthenticationFailedException) {
                        discarded.incrementAndGet()
                        listener?.onPacketDiscarded("authentication failed")
                        continue
                    } catch (e: PacketCodec.MalformedPacketException) {
                        discarded.incrementAndGet()
                        listener?.onPacketDiscarded("malformed: ${e.message}")
                        continue
                    }
                    received.incrementAndGet()
                    dispatch(packet)
                }
            } finally {
                arbiter.onReceiveFinished()
            }
        }

        running.set(false)
        if (state == ConnectionState.CONNECTED) setState(ConnectionState.DISCONNECTED)
    }

    private fun dispatch(packet: Packet) {
        when (packet.type) {
            MessageType.HEARTBEAT -> {
                // Reply with an ACK carrying the heartbeat's sequence.
                arbiter.submit(
                    Packet.text(
                        MessageType.ACK,
                        packet.language,
                        sequence.incrementAndGet(),
                        packet.sequence.toString(),
                    )
                )
            }

            MessageType.ACK -> {
                val acked = packet.text.toIntOrNull()
                val sentAt = acked?.let { heartbeatSentAt.remove(it) }
                if (sentAt != null) {
                    lastRoundTripMs = System.currentTimeMillis() - sentAt
                }
            }

            MessageType.NORMAL, MessageType.ALERT -> listener?.onReceive(packet)
        }
    }

    override fun disconnect() {
        running.set(false)
        arbiter.reset()
        readerThread?.interrupt()
        readerThread = null
        runCatching { link?.closer?.invoke() }
        link = null
        crypto = null
        pairingConfirmed.set(false)
        pairingInfo = null
        setState(ConnectionState.DISCONNECTED)
    }

    override fun close() {
        disconnect()
        (scheduler as? DefaultScheduler)?.shutdown()
    }

    protected fun setState(next: ConnectionState) {
        if (state != next) {
            state = next
            listener?.onStateChanged(next)
        }
    }

    protected companion object {
        val MAGIC = "ITAN".toByteArray(Charsets.US_ASCII)
        const val MAX_HELLO_LEN = 4096
        const val MAX_KEY_LEN = 2048
        const val READ_BUFFER_BYTES = 4096
    }
}

/** Scheduler backed by a single daemon thread. */
class DefaultScheduler : Scheduler {
    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "itantra-scheduler").apply { isDaemon = true }
        }

    override fun schedule(delayMs: Long, task: () -> Unit): Cancellable {
        val future = executor.schedule(task, delayMs, TimeUnit.MILLISECONDS)
        return object : Cancellable {
            override fun cancel() { future.cancel(false) }
        }
    }

    fun shutdown() = executor.shutdownNow().let { }
}
