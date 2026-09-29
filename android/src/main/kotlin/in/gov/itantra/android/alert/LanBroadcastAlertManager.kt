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
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _alerts = kotlinx.coroutines.flow.MutableSharedFlow<Packet>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val alerts = _alerts.asSharedFlow()

    fun markOriginated(sequence: Int) = Companion.markOriginated(sequence)
    fun isOriginated(sequence: Int): Boolean = Companion.isOriginated(sequence)
    fun clearOriginated(sequence: Int) = Companion.clearOriginated(sequence)
    fun setLocalDeviceName(name: String) = Companion.setLocalDeviceName(name)

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<(Packet) -> Unit>()

    /**
     * Start background UDP listener for incoming LAN emergency packets.
     */
    fun startListening(onPacketReceived: ((Packet) -> Unit)? = null) {
        if (onPacketReceived != null && !listeners.contains(onPacketReceived)) {
            listeners.add(onPacketReceived)
        }
        startListeningInternal()
    }

    /**
     * Start background UDP listener with dedicated callbacks for alerts and delivery ACKs.
     */
    fun startListening(
        onAlertReceived: (Packet) -> Unit,
        onAckReceived: (sequence: Int, payloadHash: Int, receiverName: String, locationInfo: String?) -> Unit,
    ) {
        val compositeHandler: (Packet) -> Unit = { packet ->
            if (packet.type == MessageType.ALERT) {
                onAlertReceived(packet)
            } else if (packet.type == MessageType.ACK) {
                val parts = packet.text.split(":", limit = 3)
                val hash = parts.getOrNull(0)?.toIntOrNull() ?: 0
                val name = parts.getOrNull(1)?.ifBlank { "Responder" } ?: "Responder"
                val loc = parts.getOrNull(2)
                onAckReceived(packet.sequence, hash, name, loc)
            }
        }
        listeners.add(compositeHandler)
        startListeningInternal()
    }

    private fun startListeningInternal() {
        if (isListening.getAndSet(true)) return

        acquireMulticastLock()

        listenerThread = thread(name = "iTantra-LanAlertListener") {
            try {
                val udpSocket = DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(java.net.InetSocketAddress(port))
                }
                socket = udpSocket

                val buffer = ByteArray(2048)

                while (isListening.get()) {
                    val datagram = DatagramPacket(buffer, buffer.size)
                    try {
                        udpSocket.receive(datagram)

                        val senderAddr = datagram.address
                        if (senderAddr != null && (senderAddr.isLoopbackAddress || isLocalAddress(senderAddr))) {
                            // Ignore UDP packets looped back to our own socket from our own interfaces
                            continue
                        }

                        val len = datagram.length
                        if (len <= PacketCodec.LENGTH_PREFIX_LEN) continue

                        // Copy frame body (skipping 4-byte length prefix)
                        val body = datagram.data.copyOfRange(PacketCodec.LENGTH_PREFIX_LEN, len)
                        val packet = PacketCodec.decodeBody(body, broadcastCrypto)

                        if (packet.type == MessageType.ALERT || packet.type == MessageType.ACK) {
                            if (packet.type == MessageType.ALERT) {
                                if (senderAddr != null) {
                                    alertSenderIps[packet.sequence] = senderAddr
                                }
                                if (isOriginated(packet.sequence)) {
                                    // Drop self-originated alert broadcast by sequence
                                    continue
                                }
                            }

                            val alertKey = "${packet.type.name}:${packet.sequence}:${packet.timestampMs}:${packet.text.hashCode()}"
                            val now = System.currentTimeMillis()

                            // Deduplicate redundant burst packets (keep window of 15 seconds)
                            purgeStaleCache(now)
                            if (recentAlerts.putIfAbsent(alertKey, now) == null) {
                                AppLog.d(TAG, "Received LAN broadcast packet (${packet.type}): ${packet.text}")
                                _alerts.tryEmit(packet)
                                for (listener in listeners) {
                                    runCatching { listener(packet) }
                                }
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
     * Broadcast an emergency alert to all devices on the local Wi-Fi / hotspot / LAN network.
     * Transmits rapid UDP burst packets across all active broadcast destinations for maximum reliability.
     */
    fun sendBroadcastAlert(
        language: Language,
        content: AlertContent,
        sequence: Int,
        senderName: String,
        ttl: Int = 3,
        senderLoc: String? = null,
    ) {
        markOriginated(sequence)
        if (senderName.isNotBlank()) {
            setLocalDeviceName(senderName)
        }
        val locPart = if (!senderLoc.isNullOrBlank()) "\u001F$senderLoc" else ""
        val textPayload = "$senderName\u001F${content.toWirePayload()}$locPart"
        val packet = Packet.text(
            type = MessageType.ALERT,
            language = language,
            sequence = sequence,
            text = textPayload,
            flags = ttl,
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
                val destinations = getBroadcastDestinations()

                // Send burst to every active broadcast destination
                for (i in 1..BURST_COUNT) {
                    for (dest in destinations) {
                        try {
                            val datagram = DatagramPacket(frameBytes, frameBytes.size, dest, port)
                            udpSocket.send(datagram)
                        } catch (_: Exception) {}
                    }
                    if (i < BURST_COUNT) Thread.sleep(BURST_INTERVAL_MS)
                }
                udpSocket.close()
                AppLog.d(TAG, "Sent LAN broadcast alert burst ($BURST_COUNT packets to ${destinations.size} destinations)")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to send LAN broadcast alert: ${e.message}")
            }
        }
    }

    /**
     * Broadcast a delivery receipt (ACK) back to the network so the original sender
     * can update its UI.
     */
    fun sendBroadcastAck(sequence: Int, payloadHash: Int, receiverName: String, locationInfo: String? = null) {
        val safeName = receiverName.replace(":", " ").trim()
        val safeLoc = locationInfo?.replace(":", " ")?.trim()
        val locPart = if (!safeLoc.isNullOrBlank()) ":$safeLoc" else ""
        val packet = Packet.text(
            type = MessageType.ACK,
            language = Language.ENGLISH, // Doesn't matter for ACK
            sequence = sequence,
            text = "$payloadHash:$safeName$locPart",
        )
        val frameBytes = PacketCodec.encode(packet, broadcastCrypto)

        thread(name = "iTantra-LanAckSender") {
            try {
                // Short random jitter prevents UDP collisions when multiple devices ACK simultaneously
                Thread.sleep((30..200).random().toLong())
                
                val udpSocket = DatagramSocket().apply { broadcast = true }
                val destinations = getBroadcastDestinations().toMutableSet()
                alertSenderIps[sequence]?.let { destinations.add(it) }
                
                // Send burst across all destinations
                for (i in 1..4) {
                    for (dest in destinations) {
                        try {
                            val datagram = DatagramPacket(frameBytes, frameBytes.size, dest, port)
                            udpSocket.send(datagram)
                        } catch (_: Exception) {}
                    }
                    if (i < 4) Thread.sleep(BURST_INTERVAL_MS)
                }
                
                udpSocket.close()
                AppLog.d(TAG, "Sent LAN broadcast ACK burst for sequence $sequence to ${destinations.size} destinations")
            } catch (e: Exception) {
                AppLog.w(TAG, "Failed to send LAN broadcast ACK: ${e.message}")
            }
        }
    }

    private fun getBroadcastDestinations(): Set<InetAddress> {
        val destinations = mutableSetOf<InetAddress>()
        try {
            destinations.add(InetAddress.getByName("255.255.255.255"))
            // Common default AP/Hotspot and Wi-Fi Direct broadcast addresses
            listOf(
                "192.168.43.255", // Standard Android Hotspot broadcast
                "192.168.49.255", // Standard Android Wi-Fi Direct P2P broadcast
                "192.168.43.1",   // Hotspot host
                "192.168.49.1",   // Wi-Fi Direct Group Owner host
                "192.168.1.255",
                "192.168.0.255",
                "10.0.0.255"
            ).forEach {
                runCatching { destinations.add(InetAddress.getByName(it)) }
            }

            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return destinations
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.interfaceAddresses) {
                    val broadcast = addr.broadcast
                    if (broadcast != null) {
                        destinations.add(broadcast)
                    }
                    val ip = addr.address
                    if (ip is java.net.Inet4Address) {
                        // Calculate broadcast if prefix length is available
                        val prefix = addr.networkPrefixLength.toInt()
                        if (prefix in 1..31) {
                            val mask = -1 shl (32 - prefix)
                            val ipBytes = ip.address
                            val ipInt = ((ipBytes[0].toInt() and 0xFF) shl 24) or
                                    ((ipBytes[1].toInt() and 0xFF) shl 16) or
                                    ((ipBytes[2].toInt() and 0xFF) shl 8) or
                                    (ipBytes[3].toInt() and 0xFF)
                            val broadcastInt = ipInt or mask.inv()
                            val bCastBytes = byteArrayOf(
                                ((broadcastInt ushr 24) and 0xFF).toByte(),
                                ((broadcastInt ushr 16) and 0xFF).toByte(),
                                ((broadcastInt ushr 8) and 0xFF).toByte(),
                                (broadcastInt and 0xFF).toByte()
                            )
                            runCatching { destinations.add(InetAddress.getByAddress(bCastBytes)) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLog.w(TAG, "Error resolving broadcast destinations: ${e.message}")
        }
        return destinations
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

    private fun isLocalAddress(addr: InetAddress): Boolean {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return false
            for (iface in interfaces) {
                for (ifaceAddr in iface.inetAddresses) {
                    if (ifaceAddr == addr) return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    private fun purgeStaleCache(now: Long) {
        recentAlerts.entries.removeIf { now - it.value > DEDUP_WINDOW_MS }
    }

    companion object {
        private const val TAG = "LanBroadcastAlertManager"
        const val UDP_ALERT_PORT = 8989
        private const val BURST_COUNT = 5
        private const val BURST_INTERVAL_MS = 25L
        private const val DEDUP_WINDOW_MS = 15_000L

        private val originatedSequences = ConcurrentHashMap<Int, Long>()
        private val localDeviceNames = ConcurrentHashMap.newKeySet<String>()
        private val alertSenderIps = ConcurrentHashMap<Int, InetAddress>()

        fun markOriginated(sequence: Int) {
            originatedSequences[sequence] = System.currentTimeMillis()
        }

        fun isOriginated(sequence: Int): Boolean {
            val ts = originatedSequences[sequence] ?: return false
            if (System.currentTimeMillis() - ts > 300_000L) {
                originatedSequences.remove(sequence)
                return false
            }
            return true
        }

        fun clearOriginated(sequence: Int) {
            originatedSequences.remove(sequence)
        }

        fun setLocalDeviceName(name: String) {
            if (name.isNotBlank()) {
                localDeviceNames.add(name.trim().lowercase())
            }
        }

        fun isLocalDeviceName(name: String): Boolean {
            return localDeviceNames.contains(name.trim().lowercase())
        }
    }
}
