package `in`.gov.itantra.core.alert

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.core.audio.AudioFormat
import `in`.gov.itantra.core.audio.AudioSink
import `in`.gov.itantra.core.tts.ChunkedSpeaker
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Supplies the pre-rendered WAV for a bundled template. */
interface TemplateAudioSource {
    /** @throws IllegalStateException if the asset for this template/language is missing. */
    fun load(template: AlertTemplate, language: Language): AudioClip
}

/**
 * Module B6 -- alert playback.
 *
 * The one invariant this class exists to guarantee: **every** alert, whether it comes
 * from a pre-rendered template WAV or from custom text routed through the TTS engine,
 * is played inside forced alarm-stream audio focus at maximum volume.
 *
 * That is enforced structurally, not by convention. [renderAlert] is the only method
 * that touches a sink, it is private, and the sole call site is inside
 * [ForcedAudioFocus.withForcedAlarmAudio]. There is no code path from [play] to audio
 * output that does not pass through that lambda, so the custom-text path cannot
 * silently skip the escalation the way it would if each branch requested focus for
 * itself. `AlertPlayerTest` asserts this for both branches against a recording fake,
 * rather than assuming the custom path inherits the behaviour.
 *
 * Non-interruptibility is the second guarantee. While an alert is playing, ordinary
 * message playback is refused through [tryPlayNormal]; a normal packet arriving
 * mid-alert is deferred, never mixed in and never allowed to cut the alert short. A
 * second *alert* does not interrupt either -- it queues and plays immediately after.
 */
class AlertPlayer(
    private val focus: ForcedAudioFocus,
    private val templates: TemplateAudioSource,
    private val speaker: ChunkedSpeaker,
    /** Produces a sink for the given format. Android supplies an AudioTrack-backed one. */
    private val sinkProvider: (AudioFormat) -> AudioSink,
    private val listener: AlertListener? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val alertActive = AtomicBoolean(false)
    private val dismissed = AtomicBoolean(false)
    private val lock = Any()
    private val pending = ArrayDeque<IncomingAlert>()
    private var activeSpeechHandle: ChunkedSpeaker.SpeechHandle? = null

    private val _activeAlertState = MutableStateFlow<IncomingAlert?>(null)
    val activeAlertState: StateFlow<IncomingAlert?> = _activeAlertState.asStateFlow()

    /** True while an alert is playing. A future UI can show a banner from this. */
    val isAlertActive: Boolean get() = alertActive.get()

    val queuedAlerts: Int get() = synchronized(lock) { pending.size }

    fun dismissActiveAlert() {
        dismissed.set(true)
        synchronized(lock) {
            activeSpeechHandle?.cancel()
            pending.clear()
        }
        _activeAlertState.value = null
    }

    /**
     * Play [alert], or queue it if one is already playing. Blocks the calling thread
     * for the duration of playback, so callers should dispatch this on a dedicated
     * alert thread.
     */
    fun play(alert: IncomingAlert) {
        synchronized(lock) {
            if (alertActive.get()) {
                val currentAlert = _activeAlertState.value
                val isDuplicate = currentAlert != null &&
                    currentAlert.sequence == alert.sequence &&
                    currentAlert.content.toWirePayload().hashCode() == alert.content.toWirePayload().hashCode()
                
                if (isDuplicate) {
                    if (currentAlert.senderName == null && alert.senderName != null) {
                        _activeAlertState.value = currentAlert.copy(senderName = alert.senderName)
                    }
                    return
                }

                pending.clear()
                pending.addLast(alert)
                dismissed.set(true)
                activeSpeechHandle?.cancel()
                listener?.onAlertQueued(alert, pending.size)
                return
            }
            alertActive.set(true)
            dismissed.set(false)
        }

        var current: IncomingAlert? = alert
        try {
            while (current != null) {
                _activeAlertState.value = current
                playNow(current)
                // Draining the queue and clearing the flag must happen under the same
                // lock as the enqueue check above.
                current = synchronized(lock) {
                    val next = pending.pollFirst()
                    if (next == null) {
                        alertActive.set(false)
                        _activeAlertState.value = null
                    } else {
                        dismissed.set(false) // Reset for the replacement alert
                    }
                    next
                }
            }
        } catch (t: Throwable) {
            synchronized(lock) { alertActive.set(false) }
            _activeAlertState.value = null
            throw t
        }
    }

    private fun playNow(alert: IncomingAlert) {
        val startedAt = clock()
        listener?.onAlertStarted(alert)
        try {
            // The single audio-focus call site. Both content variants are rendered
            // inside this lambda; see the class comment.
            focus.withForcedAlarmAudio {
                renderAlert(alert)
            }
            listener?.onAlertCompleted(alert, clock() - startedAt)
        } catch (e: Exception) {
            listener?.onAlertFailed(alert, e.message ?: e::class.java.simpleName)
        }
    }

    /**
     * The only code in this class that produces audio. Private, and called from
     * exactly one place -- inside the forced-focus scope.
     */
    private fun renderAlert(alert: IncomingAlert) {
        val startedAt = clock()
        val timeoutMs = 2 * 60 * 1000L // 2 minutes

        when (val content = alert.content) {
            is AlertContent.Template -> {
                val clip = try {
                    templates.load(content.template, alert.language)
                } catch (_: Exception) {
                    null
                }
                if (clip != null) {
                    val sink = sinkProvider(clip.format)
                    try {
                        while (!dismissed.get() && (clock() - startedAt < timeoutMs)) {
                            writeFully(sink, clip)
                            if (dismissed.get()) break
                            Thread.sleep(1000)
                        }
                        sink.drain()
                    } finally {
                        sink.close()
                    }
                } else {
                    while (!dismissed.get() && (clock() - startedAt < timeoutMs)) {
                        speakCustom(content.template.phrase(alert.language), alert.language)
                        if (dismissed.get()) break
                        Thread.sleep(1000)
                    }
                }
            }

            is AlertContent.Custom -> {
                while (!dismissed.get() && (clock() - startedAt < timeoutMs)) {
                    speakCustom(content.text, alert.language)
                    if (dismissed.get()) break
                    Thread.sleep(1000)
                }
            }
        }
    }

    private fun speakCustom(text: String, language: Language) {
        val sink = sinkProvider(AudioFormat.TTS_16K)
        try {
            val handle = speaker.speak(text, language, sink)
            synchronized(lock) {
                if (dismissed.get()) {
                    handle.cancel()
                } else {
                    activeSpeechHandle = handle
                }
            }
            handle.await()
        } catch (_: Exception) {
            // Ignore cancellation or chunker failures during loop
        } finally {
            synchronized(lock) {
                if (activeSpeechHandle != null) {
                    activeSpeechHandle = null
                }
            }
            sink.close()
        }
    }

    /**
     * Attempt ordinary, interruptible playback. Returns false without running [block]
     * when an alert holds the channel, which is what makes alerts non-interruptible by
     * normal traffic.
     */
    fun tryPlayNormal(sequence: Int, block: () -> Unit): Boolean {
        if (alertActive.get()) {
            listener?.onDeferredDuringAlert(sequence)
            return false
        }
        block()
        return true
    }

    private fun writeFully(sink: AudioSink, clip: AudioClip) {
        var off = 0
        while (off < clip.pcm.size && !dismissed.get()) {
            val chunk = minOf(clip.pcm.size - off, 4096)
            val n = sink.write(clip.pcm, off, chunk)
            if (n <= 0) return
            off += n
        }
    }
}
