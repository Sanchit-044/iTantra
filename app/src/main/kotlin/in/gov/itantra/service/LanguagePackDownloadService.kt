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
import `in`.gov.itantra.core.pack.LanguagePackInstallCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Shows a running language-pack download as a system-tray progress notification, so the operator
 * can leave the Language screen (or the app entirely) while it finishes instead of relying on an
 * in-app banner visible only while the app is open. The Language screen keeps its own inline
 * progress card for whoever stays on it -- this is for everyone else. Started from
 * [in.gov.itantra.ui.LanguageSelectionViewModel.confirm] right after
 * [LanguagePackInstallCoordinator.install] kicks off; stops itself once that reaches a terminal
 * (done or failed) state.
 */
@AndroidEntryPoint
class LanguagePackDownloadService : Service() {

    @Inject
    lateinit var installCoordinator: LanguagePackInstallCoordinator

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWith(progressNotification(fraction = 0f, message = "Starting download…"))
        scope.launch {
            installCoordinator.state.collectLatest { install ->
                when {
                    install.busy -> {
                        val fraction = (install.progress?.fraction ?: 0f).coerceIn(0f, 1f)
                        val message = install.progress?.message ?: "Downloading language packs…"
                        notify(progressNotification(fraction, message))
                    }
                    install.error != null -> {
                        notify(finishedNotification("Language pack download failed", install.error))
                        finish()
                    }
                    else -> {
                        notify(finishedNotification("Language packs downloaded", null))
                        finish()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Detaches from foreground but leaves the finished notification up for the operator to see/dismiss. */
    private fun finish() {
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun startForegroundWith(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(notification: Notification) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun progressNotification(fraction: Float, message: String): Notification {
        val percent = (fraction * 100).roundToInt()
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading language packs")
            .setContentText("$message ($percent%)")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, false)
            .build()
    }

    private fun finishedNotification(title: String, detail: String?): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(detail)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Language Pack Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows progress while language packs download in the background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "itantra_download_channel"
        private const val NOTIFICATION_ID = 3003

        fun start(context: Context) {
            val intent = Intent(context, LanguagePackDownloadService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
