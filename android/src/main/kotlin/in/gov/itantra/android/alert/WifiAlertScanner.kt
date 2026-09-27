package `in`.gov.itantra.android.alert

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
import android.os.Handler
import android.os.Looper
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertCodec
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

    private var p2pManager: WifiP2pManager? = null
    private var p2pChannel: WifiP2pManager.Channel? = null
    private var serviceRequest: WifiP2pDnsSdServiceRequest? = null

    private val recentAlerts = mutableSetOf<String>()

    init {
        val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (manager != null) {
            p2pManager = manager
            p2pChannel = manager.initialize(context, Looper.getMainLooper(), null)
        }
    }

    private val scanRunnable = object : Runnable {
        @SuppressLint("MissingPermission")
        override fun run() {
            if (!isScanning) return
            val manager = p2pManager ?: return
            val channel = p2pChannel ?: return

            // Android Wi-Fi Direct requires discoverPeers to activate radio scan before discoverServices works
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    manager.discoverServices(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            AppLog.d("WifiAlertScanner", "Wi-Fi Direct DNS-SD service discovery running")
                        }
                        override fun onFailure(reason: Int) {
                            AppLog.w("WifiAlertScanner", "Wi-Fi Direct service discovery failed: $reason")
                        }
                    })
                }
                override fun onFailure(reason: Int) {
                    // Direct service discovery fallback
                    manager.discoverServices(channel, null)
                }
            })

            handler.postDelayed(this, 10_000)
        }
    }

    private val _alerts = kotlinx.coroutines.flow.MutableSharedFlow<`in`.gov.itantra.core.transport.Packet>(extraBufferCapacity = 10)
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
            },
            { fullDomainName, record, srcDevice ->
                AppLog.d("WifiAlertScanner", "Discovered DNS-SD TXT: $fullDomainName from ${srcDevice.deviceAddress}")
                val decoded = AlertCodec.decodeWifiPayload(record)
                if (decoded != null) {
                    val language = decoded.language
                    val sequence = decoded.sequence
                    val content = decoded.content
                    val senderName = decoded.senderName ?: "Wi-Fi Peer"
                    val wirePayload = content.toWirePayload()

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

                    val alert = IncomingAlert(
                        content = content,
                        language = language,
                        sequence = sequence,
                        receivedAtMs = System.currentTimeMillis(),
                        senderName = senderName
                    )
                    
                    AppLog.d("WifiAlertScanner", "Received connectionless Wi-Fi alert (seq $sequence): $content")
                    
                    val textPayload = "$senderName\u001F$wirePayload"
                    val packet = `in`.gov.itantra.core.transport.Packet.text(
                        type = `in`.gov.itantra.core.transport.MessageType.ALERT,
                        language = language,
                        sequence = sequence,
                        text = textPayload,
                        flags = decoded.ttl,
                    )
                    _alerts.tryEmit(packet)

                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        alertPlayer.play(alert)
                    }
                }
            }
        )

        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
        manager.addServiceRequest(channel, serviceRequest, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                AppLog.d("WifiAlertScanner", "Started Wi-Fi Direct background scanning for alerts")
                handler.post(scanRunnable)
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
        handler.removeCallbacks(scanRunnable)
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
