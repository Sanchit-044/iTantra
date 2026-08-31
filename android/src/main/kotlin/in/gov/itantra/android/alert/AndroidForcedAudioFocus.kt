package `in`.gov.itantra.android.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import `in`.gov.itantra.core.alert.ForcedAudioFocus
import java.util.concurrent.atomic.AtomicInteger

/**
 * Module B6's forced audio escalation on Android.
 *
 * Acquires AUDIOFOCUS_GAIN_TRANSIENT against the alarm stream and raises alarm volume
 * to maximum for the duration of playback, then restores the operator's previous
 * volume.
 *
 * Two details that are easy to get wrong and expensive in the field:
 *
 *  1. The previous volume is restored in a `finally`. If an alert throws mid-playback
 *     and the volume is left pinned at maximum, every subsequent alarm on that handset
 *     is deafening until reboot. Restoration must not depend on the happy path.
 *
 *  2. Nested acquisitions are reference-counted. A second alert arriving while one is
 *     playing must not have its inner scope restore the volume while the outer alert is
 *     still speaking. Only the outermost scope restores.
 */
class AndroidForcedAudioFocus(
    context: Context,
) : ForcedAudioFocus {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val depth = AtomicInteger(0)

    @Volatile
    private var savedVolume: Int? = null

    @Volatile
    private var focusRequest: AudioFocusRequest? = null

    override val isHeld: Boolean get() = depth.get() > 0

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    override fun <T> withForcedAlarmAudio(block: () -> T): T {
        val outermost = depth.getAndIncrement() == 0
        if (outermost) {
            acquire()
        }
        try {
            return block()
        } finally {
            if (depth.decrementAndGet() == 0) {
                release()
            }
        }
    }

    private fun acquire() {
        requestFocus()
        try {
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            savedVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
        } catch (e: SecurityException) {
            // Some OEM builds and Do-Not-Disturb policies refuse programmatic volume
            // changes. Playing at the current volume is far better than not alerting
            // at all, so this is reported and swallowed rather than thrown.
            savedVolume = null
            onVolumeOverrideRefused?.invoke(e)
        }
    }

    private fun release() {
        try {
            savedVolume?.let {
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, it, 0)
            }
        } catch (_: SecurityException) {
            // Nothing further to do; the volume stays where the system left it.
        } finally {
            savedVolume = null
            abandonFocus()
        }
    }

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attributes)
                // The alert must be heard, not ducked under whatever else is playing.
                .setWillPauseWhenDucked(false)
                .setAcceptsDelayedFocusGain(false)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
    }

    /** Reported when the platform refuses the volume override. Wire to diagnostics. */
    var onVolumeOverrideRefused: ((SecurityException) -> Unit)? = null
}
