package `in`.gov.itantra.android.alert

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Handler
import android.os.Looper
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertCodec
import `in`.gov.itantra.core.alert.AlertContent
import `in`.gov.itantra.core.alert.AlertPlayer
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.alert.IncomingAlert
import `in`.gov.itantra.core.diag.AppLog
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

import java.util.UUID

class WifiAlertScanner(
    private val context: Context,
    private val alertPlayer: AlertPlayer
) {
    private val handler = Handler(Looper.getMainLooper())
    private var isScanning = false
    @Volatile
    private var fastScanMode = false

    private var p2pManager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null

    private val recentAlerts = mutableSetOf<String>()
    private val recentAckTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    init {
        val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (manager != null) {
            p2pManager = manager
            p2pChannel = manager.initialize(context, Looper.getMainLooper(), null)
        }
    }

    private val isDiscovering = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Enable fast scanning (5s interval) to quickly discover ACK services
     * when we have an active inbound or outbound alert.
     */
    fun setFastScanMode(enabled: Boolean) {
        fastScanMode = enabled
        if (isScanning) {
            handler.removeCallbacks(keepAliveRunnable)
            if (enabled) {
                handler.post { triggerServiceDiscovery() }
            }
            val interval = if (enabled) 5_000L else 10_000L
            handler.postDelayed(keepAliveRunnable, interval)
        }
    }

    private fun triggerServiceDiscovery() {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return
        if (!isScanning) return
        if (!isDiscovering.compareAndSet(false, true)) return

        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                manager.discoverServices(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        AppLog.d("WifiAlertScanner", "Wi-Fi Direct DNS-SD service discovery active")
                        isDiscovering.set(false)
                    }
                    override fun onFailure(reason: Int) {
                        AppLog.w("WifiAlertScanner", "discoverServices failed: $reason")
                        isDiscovering.set(false)
                    }
                })
            }
            override fun onFailure(reason: Int) {
                manager.discoverServices(channel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        AppLog.d("WifiAlertScanner", "Wi-Fi Direct DNS-SD service discovery active")
                        isDiscovering.set(false)
                    }
                    override fun onFailure(r: Int) {
                        isDiscovering.set(false)
                    }
                })
            }
        })
    }

    private val keepAliveRunnable = object : Runnable {
        @SuppressLint("MissingPermission")
        override fun run() {
            if (!isScanning) return
            triggerServiceDiscovery()
            val interval = if (fastScanMode) 5_000L else 10_000L
            handler.postDelayed(this, interval)
        }
    }

    private val _alerts = kotlinx.coroutines.flow.MutableSharedFlow<`in`.gov.itantra.core.transport.Packet>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val alerts = _alerts.asSharedFlow()

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (isScanning) return
        val manager = p2pManager ?: run {
            AppLog.e("WifiAlertScanner", "Wi-Fi P2P Manager is null, cannot scan")
            return
        }
        val channel = p2pChannel ?: run {
            AppLog.e("WifiAlertScanner", "Wi-Fi P2P Channel is null, cannot scan")
            return
        }

        isScanning = true

        manager.setDnsSdResponseListeners(channel,
            { instanceName, registrationType, srcDevice ->
                AppLog.d("WifiAlertScanner", "DNS-SD Service Available: $instanceName, $registrationType from ${srcDevice.deviceAddress}")
                if (instanceName.startsWith("iTantra_Alt_")) {
                    try {
                        val parts = instanceName.removePrefix("iTantra_Alt_").split("_")
                        val seq = parts.getOrNull(0)?.toIntOrNull()
                        val langWire = parts.getOrNull(1)?.toByteOrNull()
                        val typeTag = parts.getOrNull(2)
                        if (seq != null && langWire != null && typeTag != null) {
                            if (WifiAlertBroadcaster.isOriginated(seq)) return@setDnsSdResponseListeners

                            val language = Language.fromWire(langWire) ?: Language.DEFAULT
                            val content: AlertContent
                            val senderName: String
                            when (typeTag) {
                                "T" -> {
                                    val tplOrdinal = parts.getOrNull(3)?.toIntOrNull() ?: 0
                                    val template = AlertTemplate.entries.getOrNull(tplOrdinal) ?: AlertTemplate.EMERGENCY_ASSISTANCE
                                    val name = parts.getOrNull(4)?.takeIf { it.isNotBlank() } ?: "Wi-Fi Peer"
                                    content = AlertContent.Template(template)
                                    senderName = name
                                }
                                "C" -> {
                                    val customSnippet = parts.getOrNull(3)?.takeIf { it.isNotBlank() } ?: "Emergency Alert"
                                    val name = parts.getOrNull(4)?.takeIf { it.isNotBlank() } ?: "Wi-Fi Peer"
                                    content = AlertContent.Custom(customSnippet)
                                    senderName = name
                                }
                                else -> {
                                    content = AlertContent.Template(AlertTemplate.EMERGENCY_ASSISTANCE)
                                    senderName = "Wi-Fi Peer"
                                }
                            }

                            val wirePayload = content.toWirePayload()
                            val dedupKey = "${srcDevice.deviceAddress}:$seq:$wirePayload"
                            if (recentAlerts.add(dedupKey)) {
                                AppLog.d("WifiAlertScanner", "Fast Wi-Fi Direct Alert emitted from service response for seq $seq, lang $language, content=$content")
                                val textPayload = "$senderName\u001F$wirePayload"
                                val packet = `in`.gov.itantra.core.transport.Packet.text(
                                    type = `in`.gov.itantra.core.transport.MessageType.ALERT,
                                    language = language,
                                    sequence = seq,
                                    text = textPayload,
                                    flags = 3,
                                )
                                _alerts.tryEmit(packet)
                            }
                        }
                    } catch (e: Exception) {
                        AppLog.w("WifiAlertScanner", "Failed to parse fast Wi-Fi alert from instanceName: $instanceName", e)
                    }
                } else if (instanceName.startsWith("iTantra_Ack_")) {
                    val parts = instanceName.removePrefix("iTantra_Ack_").split("_", limit = 4)
                    val sequence = parts.getOrNull(0)?.toIntOrNull()
                    val payloadHash = parts.getOrNull(1)?.toIntOrNull()
                    val (statusTag, parsedName) = when (parts.size) {
                        4 -> parts[2] to (parts[3].replace("-", " ").takeIf { it.isNotBlank() } ?: "Responder")
                        3 -> {
                            if (parts[2].length == 3 && parts[2] in listOf("RCV", "TRK", "STP")) {
                                parts[2] to "Responder"
                            } else {
                                "RCV" to (parts[2].replace("-", " ").takeIf { it.isNotBlank() } ?: "Responder")
                            }
                        }
                        else -> "RCV" to "Responder"
                    }

                    if (sequence != null && payloadHash != null) {
                        val isTracking = statusTag == "TRK"
                        val isStopped = statusTag == "STP"
                        val statusLabel = when {
                            isStopped -> "• RECEIVED & STOPPED"
                            isTracking -> "• TRACKING LIVE"
                            else -> "• RECEIVED"
                        }
                        val dedupKey = "fast_ack:${srcDevice.deviceAddress}:$sequence:$payloadHash:$statusTag"
                        val now = System.currentTimeMillis()
                        val lastSeen = recentAckTimes[dedupKey]
                        if (lastSeen == null || now - lastSeen > 1_500L) {
                            recentAckTimes[dedupKey] = now
                            AppLog.d("WifiAlertScanner", "Fast Wi-Fi Direct ACK emitted from service response for seq $sequence, hash $payloadHash, status=$statusTag, name=$parsedName")
                            val fastAckPacket = `in`.gov.itantra.core.transport.Packet.text(
                                type = `in`.gov.itantra.core.transport.MessageType.ACK,
                                language = Language.ENGLISH,
                                sequence = sequence,
                                text = "$payloadHash:$parsedName:Direct RF Mesh $statusLabel",
                            )
                            _alerts.tryEmit(fastAckPacket)
                        }
                    }
                }
            },
            { fullDomainName, record, srcDevice ->
                AppLog.d("WifiAlertScanner", "Discovered DNS-SD TXT: $fullDomainName from ${srcDevice.deviceAddress}")

                val isAck = record["type"] == "ack" || fullDomainName.contains("Ack", ignoreCase = true) || record.containsKey("hash")
                if (isAck) {
                    val sequence = record["seq"]?.toIntOrNull() ?: 0
                    val payloadHash = record["hash"]?.toIntOrNull() ?: 0
                    val receiverName = record["name"]?.takeIf { it.isNotBlank() } ?: "Responder"
                    val rawLoc = record["loc"] ?: ""
                    val status = record["status"] ?: ""

                    val isStopped = rawLoc.contains("STOPPED", ignoreCase = true) || status == "STP"
                    val isTracking = !isStopped && (rawLoc.contains("TRACKING", ignoreCase = true) || receiverName.contains("Tracking", ignoreCase = true) || status == "TRK")
                    val statusTag = when {
                        isStopped -> "STP"
                        isTracking -> "TRK"
                        else -> "RCV"
                    }
                    val statusLabel = when {
                        isStopped -> "• RECEIVED & STOPPED"
                        isTracking -> "• TRACKING LIVE"
                        else -> "• RECEIVED"
                    }
                    val locLabel = if (rawLoc.isNotBlank()) rawLoc else "Direct RF Mesh $statusLabel"

                    val dedupKey = "txt_ack:${srcDevice.deviceAddress}:$sequence:$payloadHash:$receiverName:$statusTag"
                    val windowMs = 1_500L
                    val now = System.currentTimeMillis()
                    val lastSeen = recentAckTimes[dedupKey]
                    if (lastSeen != null && now - lastSeen < windowMs) {
                        return@setDnsSdResponseListeners
                    }
                    recentAckTimes[dedupKey] = now

                    AppLog.d("WifiAlertScanner", "Discovered Wi-Fi Direct ACK for seq $sequence, hash $payloadHash from $receiverName ($locLabel)")
                    val ackPacket = `in`.gov.itantra.core.transport.Packet.text(
                        type = `in`.gov.itantra.core.transport.MessageType.ACK,
                        language = Language.ENGLISH,
                        sequence = sequence,
                        text = "$payloadHash:$receiverName:$locLabel",
                    )
                    _alerts.tryEmit(ackPacket)
                    return@setDnsSdResponseListeners
                }

                val decoded = AlertCodec.decodeWifiPayload(record)
                if (decoded != null) {
                    val language = decoded.language
                    val sequence = decoded.sequence
                    val content = decoded.content
                    val senderName = decoded.senderName ?: "Wi-Fi Peer"
                    val wirePayload = content.toWirePayload()

                    if (WifiAlertBroadcaster.isOriginated(sequence)) {
                        return@setDnsSdResponseListeners // Ignore self-originated Wi-Fi Direct alert broadcast
                    }

                    val dedupKey = "${srcDevice.deviceAddress}:$sequence:$wirePayload"
                    if (!recentAlerts.add(dedupKey)) {
                        return@setDnsSdResponseListeners
                    }
                    
                    if (recentAlerts.size > 100) {
                        val iterator = recentAlerts.iterator()
                        for (i in 0 until 50) {
                            if (iterator.hasNext()) iterator.next()
                            iterator.remove()
                        }
                    }

                    AppLog.d("WifiAlertScanner", "Received connectionless Wi-Fi alert (seq $sequence): $content")
                    
                    val textPayload = if (decoded.senderLoc != null) {
                        "$senderName\u001F$wirePayload\u001F${decoded.senderLoc}"
                    } else {
                        "$senderName\u001F$wirePayload"
                    }
                    val packet = `in`.gov.itantra.core.transport.Packet.text(
                        type = `in`.gov.itantra.core.transport.MessageType.ALERT,
                        language = language,
                        sequence = sequence,
                        text = textPayload,
                        flags = decoded.ttl,
                    )
                    _alerts.tryEmit(packet)
                }
            }
        )

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        manager.addServiceRequest(channel, serviceRequest, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                AppLog.d("WifiAlertScanner", "Started Wi-Fi Direct background scanning for alerts")
                triggerServiceDiscovery()
                handler.postDelayed(keepAliveRunnable, if (fastScanMode) 2_000L else 5_000L)
            }

            override fun onFailure(reason: Int) {
                AppLog.e("WifiAlertScanner", "Failed to add service request for Wi-Fi alerts: $reason")
                isScanning = false
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (!isScanning) return
        isScanning = false
        handler.removeCallbacks(keepAliveRunnable)
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return
        
        serviceRequest?.let { req ->
            manager.removeServiceRequest(channel, req, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {}
                override fun onFailure(reason: Int) {}
            })
        }
        serviceRequest = null
        manager.clearServiceRequests(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {}
        })
        AppLog.d("WifiAlertScanner", "Stopped Wi-Fi Direct background scanning")
    }
}
