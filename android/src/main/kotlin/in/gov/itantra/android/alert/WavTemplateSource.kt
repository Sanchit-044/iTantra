package `in`.gov.itantra.android.alert

import android.content.Context
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.alert.TemplateAudioSource
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.core.audio.WavCodec

/**
 * Loads the pre-rendered alert WAVs bundled in the APK (one per template per language,
 * 15 files).
 *
 * These exist so the five fixed alerts play instantly and identically every time, with
 * no model load and no synthesis latency on the path that matters most. An alert must
 * not wait several hundred milliseconds for a TTS voice to page in.
 *
 * A small LRU cache keeps recently used clips in memory. It is bounded because these
 * are uncompressed PCM and an unbounded cache of 15 clips would be a needless resident
 * cost on a 2 GB device.
 */
class WavTemplateSource(
    private val context: Context,
    private val maxCachedClips: Int = 3,
) : TemplateAudioSource {

    private val cache = object : LinkedHashMap<String, AudioClip>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, AudioClip>?): Boolean =
            size > maxCachedClips
    }

    override fun load(template: AlertTemplate, language: Language): AudioClip {
        val path = template.assetPath(language)
        synchronized(cache) { cache[path] }?.let { return it }

        val clip = try {
            val bytes = context.assets.open(path).use { it.readBytes() }
            WavCodec.decode(bytes, path)
        } catch (_: Exception) {
            // Generate loud, synthetic tactical emergency siren alarm if pre-rendered WAV is absent
            generateEmergencySirenClip(template)
        }

        synchronized(cache) { cache[path] = clip }
        return clip
    }

    /** Pre-loads the clips for one language, e.g. just after a language switch. */
    fun preload(language: Language) {
        AlertTemplate.entries.take(maxCachedClips).forEach { template ->
            try {
                load(template, language)
            } catch (_: Exception) {}
        }
    }

    companion object {
        private const val SAMPLE_RATE = 16_000

        /**
         * Generates a loud, punchy 1.6-second tactical two-tone warble emergency siren
         * (alternating 960 Hz / 720 Hz pulses with 10ms smooth cosine fade).
         */
        fun generateEmergencySirenClip(template: AlertTemplate): AudioClip {
            val totalDurationMs = 1600
            val pulseDurationMs = 200
            val totalSamples = SAMPLE_RATE * totalDurationMs / 1000
            val pulseSamples = SAMPLE_RATE * pulseDurationMs / 1000
            val pcm = ShortArray(totalSamples)

            val baseHz = when (template) {
                AlertTemplate.EMERGENCY_ASSISTANCE -> 960f
                AlertTemplate.MEDICAL_HELP -> 880f
                AlertTemplate.EVACUATE_IMMEDIATELY -> 1040f
                AlertTemplate.STAY_IN_POSITION -> 720f
                AlertTemplate.ALL_CLEAR -> 600f
            }
            val secondHz = baseHz * 0.75f

            var offset = 0
            var highTone = true
            while (offset < totalSamples) {
                val len = minOf(pulseSamples, totalSamples - offset)
                val hz = if (highTone) baseHz else secondHz
                writeSineTone(pcm, offset, len, hz, amplitude = 0.85, fadeMs = 12)
                offset += len
                highTone = !highTone
            }

            return AudioClip(pcm, `in`.gov.itantra.core.audio.AudioFormat(SAMPLE_RATE))
        }

        private fun writeSineTone(
            out: ShortArray,
            offset: Int,
            length: Int,
            hz: Float,
            amplitude: Double,
            fadeMs: Int,
        ) {
            val fadeSamples = (SAMPLE_RATE * fadeMs / 1000).coerceAtLeast(1)
            for (i in 0 until length) {
                var s = kotlin.math.sin(2.0 * Math.PI * hz * i / SAMPLE_RATE) * amplitude
                if (i < fadeSamples) {
                    s *= i.toDouble() / fadeSamples
                } else if (length - 1 - i < fadeSamples) {
                    s *= (length - 1 - i).toDouble() / fadeSamples
                }
                out[offset + i] = (s * Short.MAX_VALUE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }
}
