package `in`.gov.itantra.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import `in`.gov.itantra.android.alert.BleAlertScanner
import `in`.gov.itantra.android.alert.WifiAlertScanner
import javax.inject.Inject

@AndroidEntryPoint
class ConnectionService : Service() {

    @Inject
    lateinit var bleAlertScanner: BleAlertScanner

    @Inject
    lateinit var wifiAlertScanner: WifiAlertScanner

    @Inject
    lateinit var alertNotificationManager: AlertNotificationManager

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
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        bleAlertScanner.stopScanning()
        wifiAlertScanner.stopScanning()
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

        fun start(context: Context) {
            val intent = Intent(context, ConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ConnectionService::class.java)
            context.stopService(intent)
        }
    }
}
