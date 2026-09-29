package `in`.gov.itantra.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.gov.itantra.core.diag.AppLog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VibratorHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            AppLog.w("VibratorHelper", "Failed to access Vibrator system service: ${e.message}")
            null
        }
    }

    /**
     * Heavy repeating vibration pattern triggered on receiving an emergency SOS alert.
     */
    fun startAlertVibration() {
        vibrator?.let { v ->
            try {
                if (!v.hasVibrator()) return
                v.cancel()
                val pattern = longArrayOf(0, 500, 200, 500, 200, 800)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, 0)
                }
            } catch (e: Exception) {
                AppLog.w("VibratorHelper", "Failed to trigger alert vibration: ${e.message}")
            }
        }
    }

    /**
     * Distinct pulse vibration pattern triggered while actively broadcasting an emergency SOS alert.
     */
    fun startBroadcastingVibration() {
        vibrator?.let { v ->
            try {
                if (!v.hasVibrator()) return
                v.cancel()
                val pattern = longArrayOf(0, 300, 400, 300, 700)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    v.vibrate(pattern, 0)
                }
            } catch (e: Exception) {
                AppLog.w("VibratorHelper", "Failed to trigger broadcast vibration: ${e.message}")
            }
        }
    }

    /**
     * Stops any ongoing alert or broadcast vibration.
     */
    fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            AppLog.w("VibratorHelper", "Failed to stop vibration: ${e.message}")
        }
    }
}
