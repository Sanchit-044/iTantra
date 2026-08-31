package `in`.gov.itantra.core.tts

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.core.audio.AudioFormat

enum class TtsState { IDLE, VOICE_LOADED, SYNTHESISING, ERROR }

class TtsException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Module B3 -- the text-to-speech engine contract.
 *
 * [synthesize] is deliberately a single blocking call returning a complete clip. That
 * is the honest shape of a VITS model: one non-autoregressive forward pass produces
 * the whole waveform, and no amount of API design turns it into a stream. Incremental
 * playback is built one layer up, in [ChunkedSpeaker], by splitting the *text*.
 *
 * As with [in.gov.itantra.core.stt.SttEngine], exactly one voice is resident at a
 * time to respect the 2 GB device budget.
 */
interface TtsEngine : AutoCloseable {

    val state: TtsState

    val activeLanguage: Language?

    /** Sample rate and layout of clips returned by [synthesize]. */
    val outputFormat: AudioFormat

    /** Load (or switch to) the voice for [language], unloading any previous voice first. */
    fun loadVoice(language: Language)

    /** Release the resident voice and its native memory. Idempotent. */
    fun unloadVoice()

    /**
     * Synthesise [text] in one pass.
     *
     * Implementations are expected to run [TextNormalizer] internally so that callers
     * cannot accidentally feed raw digits to the model; [synthesizeNormalised] is the
     * escape hatch for callers that have already normalised.
     */
    fun synthesize(text: String, language: Language): AudioClip

    /** Synthesise text that has already been through [TextNormalizer]. */
    fun synthesizeNormalised(text: String, language: Language): AudioClip
}
