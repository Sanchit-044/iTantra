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

    init {
        val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        if (manager != null) {
            p2pManager = manager
            p2pChannel = manager.initialize(context, Looper.getMainLooper(), null)
        }
    }

    @SuppressLint("MissingPermission")
    fun broadcastAlert(language: Language, content: AlertContent, sequence: Long) {
        AppLog.d("WifiAlertBroadcaster", "broadcastAlert called for sequence $sequence")
        val manager = p2pManager ?: run {
            AppLog.e("WifiAlertBroadcaster", "Wi-Fi P2P Manager is null, cannot broadcast")
            return
        }
        val channel = p2pChannel ?: run {
            AppLog.e("WifiAlertBroadcaster", "Wi-Fi P2P Channel is null, cannot broadcast")
            return
        }

        stopBroadcasting()

        AppLog.d("WifiAlertBroadcaster", "Attempting to broadcast alert sequence $sequence over Wi-Fi Direct")
        val record = AlertCodec.encodeWifiPayload(language, content, sequence)

        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance(
            instanceName,
            SERVICE_TYPE,
            record
        )
        
        manager.addLocalService(channel, serviceInfo, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                AppLog.d("WifiAlertBroadcaster", "Started Wi-Fi Direct DNS-SD broadcasting for alert")
                isAdvertising = true
                currentServiceInfo = serviceInfo

                // Broadcast for 30 seconds
                handler.postDelayed({
                    stopBroadcasting()
                }, 30_000)
            }

            override fun onFailure(reason: Int) {
                AppLog.e("WifiAlertBroadcaster", "Failed to add local service for Wi-Fi alert: $reason")
            }
        })
    }

    @SuppressLint("MissingPermission")
    fun stopBroadcasting() {
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
        const val INSTANCE_PREFIX = "iTantra_Alert"
        const val SERVICE_TYPE = "_itantra._tcp"
    }
    
    private val instanceName = INSTANCE_PREFIX + "_" + java.util.UUID.randomUUID().toString().take(6)
}
