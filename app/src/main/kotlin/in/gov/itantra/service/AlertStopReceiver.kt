package `in`.gov.itantra.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import `in`.gov.itantra.core.alert.AlertPlayer
import javax.inject.Inject

@AndroidEntryPoint
class AlertStopReceiver : BroadcastReceiver() {

    @Inject
    lateinit var alertPlayer: AlertPlayer

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_STOP_ALERT) {
            alertPlayer.dismissActiveAlert()
        }
    }

    companion object {
        const val ACTION_STOP_ALERT = "in.gov.itantra.action.STOP_ALERT"
    }
}
