package `in`.gov.itantra.android.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * System tray notice for a queued inbound message. Never starts TTS.
 * In-app inbox is the source of truth; this is only for when the operator is not looking.
 */
class QueuedMessageNotifier(private val context: Context) {

    fun notifyUnread(unreadCount: Int, preview: String) {
        if (unreadCount <= 0) {
            cancel()
            return
        }
        ensureChannel()
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: return
        launch.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        val pending = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val clipped = preview.trim().replace('\n', ' ').take(80)
        val text = if (unreadCount == 1) {
            clipped.ifBlank { "Queued message — tap to open, then Play" }
        } else {
            "$unreadCount queued messages — tap to open, then Play"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("iTantra queued message")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied -- in-app banner still works.
        }
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Queued messages",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Store-and-forward speech waiting to be played. Does not auto-play."
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "itantra.queued"
        const val NOTIFICATION_ID = 26173
    }
}
