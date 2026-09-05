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

        val bytes = try {
            context.assets.open(path).use { it.readBytes() }
        } catch (e: Exception) {
            // AlertPlayer catches this and speaks the template phrase through TTS.
            throw IllegalStateException("bundled alert asset missing: $path", e)
        }

        val clip = WavCodec.decode(bytes, path)
        synchronized(cache) { cache[path] = clip }
        return clip
    }

    /** Pre-loads the clips for one language, e.g. just after a language switch. */
    fun preload(language: Language) {
        AlertTemplate.entries.take(maxCachedClips).forEach { template ->
            try {
                load(template, language)
            } catch (_: Exception) {
                // Missing WAV is fine: playback falls back to TTS.
            }
        }
    }
}
