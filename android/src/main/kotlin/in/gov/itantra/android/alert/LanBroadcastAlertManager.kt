package `in`.gov.itantra.android.alert

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertContent
import `in`.gov.itantra.core.crypto.AesGcmSessionCrypto
import `in`.gov.itantra.core.diag.AppLog
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.PacketCodec
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Handles zero-pairing emergency alert broadcasts over a shared local Wi-Fi / LAN network.
 *
 * Uses UDP subnet broadcast (port 8989) with AES-256-GCM encryption under a shared application
 * broadcast key. Employs 3x burst transmission for delivery reliability without connection setup,
 * and sliding-window deduplication on reception.
 */
class LanBroadcastAlertManager(
    private val context: Context,
    private val port: Int = UDP_ALERT_PORT,
) {
    private val broadcastCrypto: AesGcmSessionCrypto by lazy {
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("iTantra-Emergency-Broadcast-Alert-Key-v1".toByteArray(Charsets.UTF_8))
        AesGcmSessionCrypto(hash)
    }

    private var listenerThread: Thread? = null
    private var socket: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val isListening = AtomicBoolean(false)

    // Deduplication cache: Packet signature -> timestamp
    private val recentAlerts = ConcurrentHashMap<String, Long>()

    /**
     * Start background UDP listener for incoming LAN emergency alerts.
     */
    fun startListening(onPacketReceived: (Packet) -> Unit) {
        if (isListening.getAndSet(true)) return

        acquireMulticastLock()

        listenerThread = thread(name = "iTantra-LanAlertListener") {
            try {
                val udpSocket = DatagramSocket(port).apply {
                    reuseAddress = true
                    broadcast = true
                }
                socket = udpSocket

                val buffer = ByteArray(2048)

                while (isListening.get()) {
                    val datagram = DatagramPacket(buffer, buffer.size)
                    try {
                        udpSocket.receive(datagram)
                        val len = datagram.length
                        if (len <= PacketCodec.LENGTH_PREFIX_LEN) continue

                        // Copy frame body (skipping 4-byte length prefix)
                        val body = datagram.data.copyOfRange(PacketCodec.LENGTH_PREFIX_LEN, len)
                        val packet = PacketCodec.decodeBody(body, broadcastCrypto)

                        if (packet.type == MessageType.ALERT) {
                            val alertKey = "${packet.sequence}:${packet.timestampMs}:${packet.text.hashCode()}"
                            val now = System.currentTimeMillis()

                            // Deduplicate redundant burst packets (keep window of 15 seconds)
                            purgeStaleCache(now)
                            if (recentAlerts.putIfAbsent(alertKey, now) == null) {
                                AppLog.d(TAG, "Received LAN broadcast alert: ${packet.text}")
                                onPacketReceived(packet)
                            }
                        }
                    } catch (e: Exception) {
                        if (!isListening.get()) break
                        // Ignore individual packet decoding or socket timeout errors
                    }
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "LAN Alert listener thread terminated: ${e.message}")
            } finally {
                releaseMulticastLock()
            }
        }
    }

    /**
     * Stop background UDP listener.
     */
    fun stopListening() {
        isListening.set(false)
        runCatching { socket?.close() }
        socket = null
        releaseMulticastLock()
    }

    /**
     * Broadcast an emergency alert to all devices on the local Wi-Fi network.
     * Transmits 3 rapid UDP burst packets spaced 50ms apart for maximum reliability.
     */
    fun sendBroadcastAlert(language: Language, content: AlertContent, sequence: Int) {
        val textPayload = content.toWirePayload()
        val packet = Packet.text(
            type = MessageType.ALERT,
            language = language,
            sequence = sequence,
            text = textPayload,
        )

        val frameBytes = PacketCodec.encode(packet, broadcastCrypto)

        thread(name = "iTantra-LanAlertSender") {
            try {
                val udpSocket = DatagramSocket().apply {
                    broadcast = true
                }
                val destination = InetAddress.getByName("255.255.255.255")
                val datagram = DatagramPacket(frameBytes, frameBytes.size, destination, port)

                // Send 3x burst for zero-loss delivery on unacknowledged UDP
                for (i in 1..BURST_COUNT) {
                    udpSocket.send(datagram)
                    if (i < BURST_COUNT) Thread.sleep(BURST_INTERVAL_MS)
                }
                udpSocket.close()
                AppLog.d(TAG, "Sent LAN broadcast alert burst ($BURST_COUNT packets)")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to send LAN broadcast alert: ${e.message}")
            }
        }
    }

    /**
     * Check if the device is currently connected to a Wi-Fi network.
     */
    fun isWifiConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            false
        }
    }

    private fun acquireMulticastLock() {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wm?.createMulticastLock("iTantraLanAlert")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Could not acquire MulticastLock: ${e.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
            multicastLock = null
        } catch (_: Exception) {}
    }

    private fun purgeStaleCache(now: Long) {
        recentAlerts.entries.removeIf { now - it.value > DEDUP_WINDOW_MS }
    }

    companion object {
        private const val TAG = "LanBroadcastAlertManager"
        const val UDP_ALERT_PORT = 8989
        private const val BURST_COUNT = 3
        private const val BURST_INTERVAL_MS = 50L
        private const val DEDUP_WINDOW_MS = 15_000L
    }
}
