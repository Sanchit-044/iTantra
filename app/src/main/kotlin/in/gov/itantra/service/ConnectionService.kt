package `in`.gov.itantra.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import `in`.gov.itantra.android.alert.BleAlertScanner
import `in`.gov.itantra.android.alert.WifiAlertScanner
import `in`.gov.itantra.core.diag.AppLog
import javax.inject.Inject

@AndroidEntryPoint
class ConnectionService : Service() {

    @Inject
    lateinit var bleAlertScanner: BleAlertScanner

    @Inject
    lateinit var wifiAlertScanner: WifiAlertScanner

    @Inject
    lateinit var alertNotificationManager: AlertNotificationManager

    /**
     * Held only while [MainViewModel]'s transport reports CONNECTED (see
     * [ACTION_TRANSPORT_CONNECTED]/[ACTION_TRANSPORT_DISCONNECTED]), not for the whole
     * app lifetime -- a live PTT session's socket and reader thread otherwise stall
     * once the CPU sleeps or the Wi-Fi radio drops into power-save with the screen off,
     * which is what made the connection look "unstable" only in the background.
     */
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        alertNotificationManager.startObserving()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // If the service is killed by the system, recreate it.

        bleAlertScanner.startScanning()
        wifiAlertScanner.startScanning()

        when (intent?.action) {
            ACTION_TRANSPORT_CONNECTED -> acquireLocks()
            ACTION_TRANSPORT_DISCONNECTED -> releaseLocks()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        bleAlertScanner.stopScanning()
        wifiAlertScanner.stopScanning()
        releaseLocks()
    }

    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "iTantra:connection")?.apply {
                setReferenceCounted(false)
                // Safety net against a leaked lock if a disconnect signal is ever
                // missed -- not a real per-session limit.
                acquire(MAX_LOCK_DURATION_MS)
            }
        }
        if (wifiLock?.isHeld != true) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            @Suppress("DEPRECATION")
            wifiLock = wm?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "iTantra:connection")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        AppLog.d("ConnectionService", "Acquired wake/Wi-Fi locks for live connection")
    }

    private fun releaseLocks() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
        wakeLock = null
        wifiLock = null
        AppLog.d("ConnectionService", "Released wake/Wi-Fi locks")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("iTantra")
            .setContentText("Listening for background alerts")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Connection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Wi-Fi Direct connection alive in the background"
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "itantra_connection_channel"
        private const val NOTIFICATION_ID = 1001

        /** 6 hours: far longer than any real PTT session, purely a leak backstop. */
        private const val MAX_LOCK_DURATION_MS = 6 * 60 * 60 * 1000L

        const val ACTION_TRANSPORT_CONNECTED = "in.gov.itantra.action.TRANSPORT_CONNECTED"
        const val ACTION_TRANSPORT_DISCONNECTED = "in.gov.itantra.action.TRANSPORT_DISCONNECTED"

        private fun dispatch(context: Context, action: String? = null) {
            val intent = Intent(context, ConnectionService::class.java).apply { action?.let { this.action = it } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun start(context: Context) = dispatch(context)

        /** Call when the live transport reaches CONNECTED: protects the socket from Doze/screen-off. */
        fun notifyTransportConnected(context: Context) = dispatch(context, ACTION_TRANSPORT_CONNECTED)

        /** Call when the live transport leaves CONNECTED (DISCONNECTED/FAILED): releases the locks. */
        fun notifyTransportDisconnected(context: Context) = dispatch(context, ACTION_TRANSPORT_DISCONNECTED)

        fun stop(context: Context) {
            val intent = Intent(context, ConnectionService::class.java)
            context.stopService(intent)
        }
    }
}
