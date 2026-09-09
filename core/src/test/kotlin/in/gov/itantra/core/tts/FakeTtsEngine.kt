package `in`.gov.itantra.core.tts

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.core.audio.AudioFormat

/**
 * A TTS engine that produces silence proportional to text length.
 * [synthesisDelayMs] simulates a slow model so the synthesis/playback pipeline can be
 * observed overlapping.
 */
class FakeTtsEngine(
    private val samplesPerChar: Int = 100,
    private val synthesisDelayMs: Long = 0,
    private val onSynthesise: ((String) -> Unit)? = null,
) : TtsEngine {
    override var state: TtsState = TtsState.VOICE_LOADED
    override var activeLanguage: Language? = null
    override val outputFormat: AudioFormat = AudioFormat.TTS_16K

    val synthesisedChunks = mutableListOf<String>()

    override fun loadVoice(language: Language) { activeLanguage = language }
    override fun unloadVoice() { activeLanguage = null }

    override fun synthesize(text: String, language: Language): AudioClip =
        synthesizeNormalised(text, language)

    override fun synthesizeNormalised(text: String, language: Language): AudioClip {
        if (synthesisDelayMs > 0) Thread.sleep(synthesisDelayMs)
        synchronized(synthesisedChunks) { synthesisedChunks += text }
        // Invoked on COMPLETION, not on entry. Counting starts instead of completions
        // would make a fully serial pipeline look overlapped.
        onSynthesise?.invoke(text)
        return AudioClip(ShortArray(text.length * samplesPerChar) { 1 }, outputFormat)
    }

    override fun close() { unloadVoice() }
}
