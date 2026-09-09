package `in`.gov.itantra.core.alert

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.core.audio.AudioFormat
import java.util.concurrent.atomic.AtomicInteger

/**
 * Records how many times forced alarm focus was acquired, and whether it was held at
 * the moment audio was written.
 *
 * That second property is what makes the Module B6 test meaningful: acquiring focus and
 * then playing after releasing it would still satisfy a naive "was focus requested"
 * assertion while leaving the handset at whatever volume it happened to be on.
 */
class FakeForcedAudioFocus : ForcedAudioFocus {
    val acquisitions = AtomicInteger(0)
    private var held = false
    var maxNestingObserved = 0
        private set
    private var depth = 0

    override val isHeld: Boolean get() = synchronized(this) { held }

    override fun <T> withForcedAlarmAudio(block: () -> T): T {
        synchronized(this) {
            acquisitions.incrementAndGet()
            held = true
            depth++
            if (depth > maxNestingObserved) maxNestingObserved = depth
        }
        try {
            return block()
        } finally {
            synchronized(this) {
                depth--
                if (depth == 0) held = false
            }
        }
    }
}

/** Produces a fixed-length tone per template so clips are distinguishable. */
class FakeTemplateAudioSource(
    private val samplesPerClip: Int = 1000,
) : TemplateAudioSource {
    val loads = mutableListOf<Pair<AlertTemplate, Language>>()

    override fun load(template: AlertTemplate, language: Language): AudioClip {
        loads += template to language
        val pcm = ShortArray(samplesPerClip) { (template.ordinal + 1).toShort() }
        return AudioClip(pcm, AudioFormat.TTS_16K)
    }
}
