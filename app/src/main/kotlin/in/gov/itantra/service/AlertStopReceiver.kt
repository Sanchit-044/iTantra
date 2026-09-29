package `in`.gov.itantra.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import `in`.gov.itantra.core.alert.AlertPlayer

class AlertStopReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface AlertStopReceiverEntryPoint {
        fun alertPlayer(): AlertPlayer
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_STOP_ALERT) {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    AlertStopReceiverEntryPoint::class.java
                )
                entryPoint.alertPlayer().dismissActiveAlert()
            } catch (_: Exception) {}
        }
    }

    companion object {
        const val ACTION_STOP_ALERT = "in.gov.itantra.action.STOP_ALERT"
    }
}
