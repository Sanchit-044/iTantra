package `in`.gov.itantra.android.alert

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.os.Handler
import android.os.Looper
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertCodec
import `in`.gov.itantra.core.alert.AlertContent
import `in`.gov.itantra.core.diag.AppLog

class WifiAlertBroadcaster(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var isAdvertising = false

    private var p2pManager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var currentServiceInfo: WifiP2pDnsSdServiceInfo? = null
    private var currentAckServiceInfo: WifiP2pDnsSdServiceInfo? = null
    private var currentAckSeq: Int? = null
    private var currentAckHash: Int? = null
    private var currentAckName: String? = null
    private var currentAckLoc: String? = null
    private var stopBroadcastRunnable: Runnable? = null
    private var stopAckRunnable: Runnable? = null

    init {
        val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (manager != null) {
            p2pManager = manager
            p2pChannel = manager.initialize(context, Looper.getMainLooper(), null)
        }
    }

    fun markOriginated(sequence: Int) = Companion.markOriginated(sequence)
    fun isOriginated(sequence: Int): Boolean = Companion.isOriginated(sequence)
    fun clearOriginated(sequence: Int) = Companion.clearOriginated(sequence)

    @SuppressLint("MissingPermission")
    fun broadcastAlert(
        language: Language,
        content: AlertContent,
        sequence: Long,
        senderName: String? = null,
        durationMs: Long = 300_000L, // 5 minutes default
        senderLoc: String? = null,
    ) {
        AppLog.d("WifiAlertBroadcaster", "broadcastAlert called for sequence $sequence")
        val manager = p2pManager ?: run {
            AppLog.e("WifiAlertBroadcaster", "Wi-Fi P2P Manager is null, cannot broadcast")
            return
        }
        val channel = p2pChannel ?: run {
            AppLog.e("WifiAlertBroadcaster", "Wi-Fi P2P Channel is null, cannot broadcast")
            return
        }

        originatedSequences[sequence.toInt()] = System.currentTimeMillis()

        val contentTag = when (content) {
            is AlertContent.Template -> "T_${content.template.ordinal}"
            is AlertContent.Custom -> "C_${content.text.take(10).filter { it.isLetterOrDigit() }.ifBlank { "ALERT" }}"
        }
        val safeSenderName = (senderName ?: "Peer").take(10).filter { it.isLetterOrDigit() }.ifBlank { "Peer" }
        val alertInstanceName = "iTantra_Alt_${sequence}_${language.wire}_${contentTag}_$safeSenderName"

        val record = AlertCodec.encodeWifiPayload(language, content, sequence, senderName, senderLoc = senderLoc)
        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            alertInstanceName,
            SERVICE_TYPE,
            record
        )

        val registerAlertService = {
            manager.addLocalService(channel, serviceInfo, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    AppLog.d("WifiAlertBroadcaster", "Started Wi-Fi Direct DNS-SD broadcasting for alert")
                    isAdvertising = true
                    currentServiceInfo = serviceInfo

                    // Trigger peer discovery immediately and pulse again to broadcast probe requests on social channels
                    manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            AppLog.d("WifiAlertBroadcaster", "Instant peer discovery triggered for alert service")
                        }
                        override fun onFailure(reason: Int) {}
                    })
                    handler.postDelayed({
                        manager.discoverPeers(channel, null)
                    }, 2500L)

                    val runnable = Runnable { stopBroadcasting() }
                    stopBroadcastRunnable = runnable
                    handler.postDelayed(runnable, durationMs)
                }

                override fun onFailure(reason: Int) {
                    AppLog.e("WifiAlertBroadcaster", "Failed to add local service for Wi-Fi alert: $reason")
                }
            })
        }

        val oldInfo = currentServiceInfo
        if (oldInfo != null) {
            stopBroadcastRunnable?.let { handler.removeCallbacks(it) }
            stopBroadcastRunnable = null
            manager.removeLocalService(channel, oldInfo, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    currentServiceInfo = null
                    isAdvertising = false
                    registerAlertService()
                }
                override fun onFailure(reason: Int) {
                    currentServiceInfo = null
                    isAdvertising = false
                    registerAlertService()
                }
            })
        } else {
            registerAlertService()
        }
    }

    @SuppressLint("MissingPermission")
    private val ackToken = Any()

    @SuppressLint("MissingPermission")
    fun broadcastAck(
        sequence: Int,
        payloadHash: Int,
        receiverName: String,
        locationLabel: String,
        durationMs: Long = 30_000L,
    ) {
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return

        val statusTag = when {
            locationLabel.contains("STOPPED", ignoreCase = true) -> "STP"
            locationLabel.contains("TRACKING", ignoreCase = true) || receiverName.contains("Tracking", ignoreCase = true) -> "TRK"
            else -> "RCV"
        }
        val cleanName = receiverName.replace(" (Tracking)", "").trim().ifBlank { "Responder" }
        val safeName = cleanName.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(16).ifBlank { "Responder" }
        val ackInstanceName = "iTantra_Ack_${sequence}_${payloadHash}_${statusTag}_${safeName}"

        // If we are already advertising this exact ACK with same status, just refresh the timeout
        if (currentAckServiceInfo != null && currentAckSeq == sequence && currentAckHash == payloadHash && currentAckName == cleanName && currentAckLoc == locationLabel) {
            handler.removeCallbacksAndMessages(ackToken)
            handler.postDelayed({ stopBroadcastingAck(currentAckServiceInfo) }, ackToken, durationMs)
            return
        }

        val record = mapOf(
            "type" to "ack",
            "seq" to sequence.toString(),
            "hash" to payloadHash.toString(),
            "name" to cleanName,
            "status" to statusTag,
            "loc" to locationLabel,
        )

        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            ackInstanceName,
            SERVICE_TYPE,
            record,
        )

        handler.removeCallbacksAndMessages(ackToken)

        val registerNewService = {
            manager.addLocalService(channel, serviceInfo, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    AppLog.d("WifiAlertBroadcaster", "Started Wi-Fi Direct DNS-SD broadcasting for ACK: $ackInstanceName (seq=$sequence, hash=$payloadHash)")
                    currentAckServiceInfo = serviceInfo
                    currentAckSeq = sequence
                    currentAckHash = payloadHash
                    currentAckName = cleanName
                    currentAckLoc = locationLabel

                    manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            AppLog.d("WifiAlertBroadcaster", "Instant peer discovery triggered for ACK service")
                        }
                        override fun onFailure(reason: Int) {}
                    })
                    handler.postDelayed({
                        manager.discoverPeers(channel, null)
                    }, 2000L)

                    handler.postDelayed({
                        stopBroadcastingAck(serviceInfo)
                    }, ackToken, durationMs)
                }

                override fun onFailure(reason: Int) {
                    AppLog.e("WifiAlertBroadcaster", "Failed to add local service for Wi-Fi ACK: $reason")
                }
            })
        }

        val oldInfo = currentAckServiceInfo
        if (oldInfo != null) {
            manager.removeLocalService(channel, oldInfo, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    currentAckServiceInfo = null
                    registerNewService()
                }
                override fun onFailure(reason: Int) {
                    currentAckServiceInfo = null
                    registerNewService()
                }
            })
        } else {
            registerNewService()
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBroadcastingAck(expectedInfo: WifiP2pDnsSdServiceInfo? = null) {
        handler.removeCallbacksAndMessages(ackToken)
        val info = currentAckServiceInfo ?: return
        if (expectedInfo != null && info != expectedInfo) {
            // A newer ACK service is already active, don't stop it!
            return
        }
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return

        manager.removeLocalService(channel, info, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                AppLog.d("WifiAlertBroadcaster", "Stopped Wi-Fi Direct ACK broadcasting")
            }
            override fun onFailure(reason: Int) {}
        })
        currentAckServiceInfo = null
        currentAckSeq = null
        currentAckHash = null
        currentAckName = null
        currentAckLoc = null
    }

    @SuppressLint("MissingPermission")
    fun stopBroadcasting() {
        stopBroadcastRunnable?.let { handler.removeCallbacks(it) }
        stopBroadcastRunnable = null
        if (!isAdvertising) return
        val manager = p2pManager ?: return
        val channel = p2pChannel ?: return
        val info = currentServiceInfo ?: return

        manager.removeLocalService(channel, info, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                AppLog.d("WifiAlertBroadcaster", "Stopped Wi-Fi Direct broadcasting")
            }
            override fun onFailure(reason: Int) {
                AppLog.w("WifiAlertBroadcaster", "Failed to remove local service: $reason")
            }
        })

        currentServiceInfo = null
        isAdvertising = false
    }

    companion object {
        val originatedSequences = java.util.concurrent.ConcurrentHashMap<Int, Long>()
        const val INSTANCE_PREFIX = "iTantra_Alert"
        const val SERVICE_TYPE = "_itantra._tcp"

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
    }
    
    private val instanceName = INSTANCE_PREFIX + "_" + java.util.UUID.randomUUID().toString().take(6)
}
