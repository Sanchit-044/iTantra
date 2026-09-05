package `in`.gov.itantra.core.alert

import `in`.gov.itantra.core.Language

/**
 * The five fixed alert templates that ship pre-rendered as WAV, one file per template
 * per language (15 files total).
 *
 * OPEN QUESTION -- these five are a placeholder. The brief specifies "the 5 fixed
 * templates" without naming them, and the actual set is an operational decision for
 * whoever owns the field protocol, not an engineering one. The enum is wired
 * end-to-end so swapping the members costs only a rename plus new audio assets.
 */
enum class AlertTemplate(val assetKey: String) {
    EVACUATE_IMMEDIATELY("evacuate"),
    MOVE_TO_HIGHER_GROUND("higher_ground"),
    MEDICAL_EMERGENCY("medical"),
    HOLD_POSITION("hold_position"),
    ALL_CLEAR("all_clear"),
    ;

    /** Asset path convention: alerts/<lang>/<key>.wav */
    fun assetPath(language: Language): String = "alerts/${language.code}/$assetKey.wav"
}

/**
 * What an alert should say.
 *
 * Both variants are handled by the same playback path in [AlertPlayer]; see the note
 * there on why that is enforced structurally rather than by convention.
 */
sealed interface AlertContent {
    /** One of the five bundled templates; played from a pre-rendered WAV. */
    data class Template(val template: AlertTemplate) : AlertContent

    /** Free text; routed through the Module B3 TTS engine. */
    data class Custom(val text: String) : AlertContent
}

data class IncomingAlert(
    val content: AlertContent,
    val language: Language,
    /** Sequence number of the packet that carried this alert, for de-duplication. */
    val sequence: Int,
    val receivedAtMs: Long,
)

/**
 * Acquires and releases forced alarm-stream audio focus.
 *
 * The Android implementation requests AUDIOFOCUS_GAIN_TRANSIENT on the alarm stream,
 * raises alarm volume to maximum, and restores the previous volume afterwards. It is
 * an interface so that Module B6's central guarantee -- that *every* alert path forces
 * focus -- can be asserted in a plain JVM test with a recording fake, rather than
 * hoped for on a device.
 */
interface ForcedAudioFocus {

    /**
     * Runs [block] while holding forced alarm focus at maximum volume.
     *
     * Implementations must restore the previous volume and abandon focus even if
     * [block] throws; an alert that crashes mid-playback must not leave the handset
     * pinned at maximum alarm volume permanently.
     */
    fun <T> withForcedAlarmAudio(block: () -> T): T

    /** True while focus is currently held. */
    val isHeld: Boolean
}

/** Observability for alert playback. */
interface AlertListener {
    fun onAlertStarted(alert: IncomingAlert) {}
    fun onAlertCompleted(alert: IncomingAlert, durationMs: Long) {}

    /** A packet arrived mid-alert and was held rather than played. */
    fun onDeferredDuringAlert(sequence: Int) {}

    /** A second alert arrived while one was playing. */
    fun onAlertQueued(alert: IncomingAlert, queueDepth: Int) {}

    fun onAlertFailed(alert: IncomingAlert, reason: String) {}
}
