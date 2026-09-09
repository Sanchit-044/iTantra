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

                        if (packet.type == MessageType.ALERT || packet.type == MessageType.ACK) {
                            val alertKey = "${packet.type.name}:${packet.sequence}:${packet.timestampMs}:${packet.text.hashCode()}"
                            val now = System.currentTimeMillis()

                            // Deduplicate redundant burst packets (keep window of 15 seconds)
                            purgeStaleCache(now)
                            if (recentAlerts.putIfAbsent(alertKey, now) == null) {
                                AppLog.d(TAG, "Received LAN broadcast packet (${packet.type}): ${packet.text}")
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
    fun sendBroadcastAlert(language: Language, content: AlertContent, sequence: Int, senderName: String) {
        val textPayload = "$senderName\u001F${content.toWirePayload()}"
        val packet = Packet.text(
            type = MessageType.ALERT,
            language = language,
            sequence = sequence,
            text = textPayload,
        )

        // Add to deduplication cache before sending to prevent receiving our own UDP broadcast
        val alertKey = "${packet.type.name}:${packet.sequence}:${packet.timestampMs}:${packet.text.hashCode()}"
        recentAlerts[alertKey] = System.currentTimeMillis()

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
     * Broadcast a delivery receipt (ACK) back to the network so the original sender
     * can update its UI.
     */
    fun sendBroadcastAck(sequence: Int, payloadHash: Int, receiverName: String) {
        val packet = Packet.text(
            type = MessageType.ACK,
            language = Language.ENGLISH, // Doesn't matter for ACK
            sequence = sequence,
            text = "$payloadHash:$receiverName",
        )
        val frameBytes = PacketCodec.encode(packet, broadcastCrypto)

        thread(name = "iTantra-LanAckSender") {
            try {
                // Large random jitter prevents UDP collisions when multiple devices ACK simultaneously
                Thread.sleep((100..1500).random().toLong())
                
                val udpSocket = DatagramSocket().apply { broadcast = true }
                val destination = InetAddress.getByName("255.255.255.255")
                val datagram = DatagramPacket(frameBytes, frameBytes.size, destination, port)
                
                // Send 5x burst for maximum reliability on unacknowledged UDP ACKs
                for (i in 1..5) {
                    udpSocket.send(datagram)
                    if (i < 5) Thread.sleep(BURST_INTERVAL_MS)
                }
                
                udpSocket.close()
                AppLog.d(TAG, "Sent LAN broadcast ACK burst (5 packets) for sequence $sequence")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to send LAN broadcast ACK: ${e.message}")
            }
        }
    }

    /**
     * Check if the device is currently connected to a Wi-Fi network, hotspot, or has Wi-Fi enabled.
     */
    fun isWifiConnected(): Boolean {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null && wm.isWifiEnabled) {
                return true
            }

            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (cm != null) {
                val activeNetwork = cm.activeNetwork
                if (activeNetwork != null) {
                    val caps = cm.getNetworkCapabilities(activeNetwork)
                    if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        return true
                    }
                }
                // Check all available networks (handles offline Wi-Fi APs without internet)
                for (network in cm.allNetworks) {
                    val caps = cm.getNetworkCapabilities(network) ?: continue
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        return true
                    }
                }
            }

            // Fallback: Check active network interfaces for active WLAN / AP / SoftAP IPs
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return false
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                if (name.contains("wlan") || name.contains("ap") || name.contains("p2p") || name.contains("swlan") || name.contains("eth")) {
                    val hasIp = iface.inetAddresses.asSequence().any { !it.isLoopbackAddress && it is java.net.Inet4Address }
                    if (hasIp) return true
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "isWifiConnected check error: ${e.message}")
        }
        return false
    }

    /**
     * Register OS network callback to receive instant events when Wi-Fi connects or disconnects.
     */
    fun observeWifiState(onChanged: (Boolean) -> Unit) {
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
            val request = android.net.NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    onChanged(true)
                }
                override fun onLost(network: android.net.Network) {
                    onChanged(isWifiConnected())
                }
            })
        } catch (e: Exception) {
            AppLog.w(TAG, "Failed to register network callback: ${e.message}")
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
