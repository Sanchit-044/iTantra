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
import kotlinx.coroutines.launch

import `in`.gov.itantra.data.history.HistoryDao
import `in`.gov.itantra.data.history.HistoryMessage
import `in`.gov.itantra.data.history.MessageDirection
import `in`.gov.itantra.data.history.MessageStatus
import java.util.UUID

class WifiAlertScanner(
    private val context: Context,
    private val alertPlayer: AlertPlayer,
    private val historyDao: HistoryDao
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

            manager.discoverServices(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {}
                override fun onFailure(reason: Int) {}
            })

            handler.postDelayed(this, 10_000)
        }
    }

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
                // Handled in TxtRecordListener
            },
            { fullDomainName, record, srcDevice ->
                AppLog.d("WifiAlertScanner", "Discovered DNS-SD service: $fullDomainName from ${srcDevice.deviceAddress}")
                if (fullDomainName.contains(WifiAlertBroadcaster.INSTANCE_PREFIX)) {
                    val decoded = AlertCodec.decodeWifiPayload(record)
                    if (decoded == null) {
                        AppLog.w("WifiAlertScanner", "Failed to decode Wi-Fi alert payload from TXT record")
                        return@setDnsSdResponseListeners
                    }
                    
                    val language = decoded.language
                    val sequence = decoded.sequence
                    val content = decoded.content

                    val dedupKey = "${srcDevice.deviceAddress}:$sequence"
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
                            senderName = decoded.senderName
                        )
                        
                    AppLog.d("WifiAlertScanner", "Received connectionless Wi-Fi alert (seq $sequence): $content")
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        alertPlayer.play(alert)
                        historyDao.insertMessage(
                            HistoryMessage(
                                id = UUID.randomUUID().toString(),
                                text = content.toWirePayload(),
                                language = language,
                                timestampMs = alert.receivedAtMs,
                                direction = MessageDirection.INBOUND,
                                status = MessageStatus.RECEIVED,
                                peerName = decoded.senderName,
                                isAlert = true
                            )
                        )
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
