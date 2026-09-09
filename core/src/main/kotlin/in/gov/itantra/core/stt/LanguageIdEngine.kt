package `in`.gov.itantra.core.stt

import `in`.gov.itantra.core.Language

/**
 * Picks which of the user's *installed* languages is being spoken.
 *
 * Never considers a language that is not in [candidates]. Returns null when unsure
 * so the caller can keep the current language (Hindi by default).
 */
interface LanguageIdEngine {
    /**
     * Audio-side detect. Implementations without a LID model return null.
     * [pcm] may be null when the caller only wants a cheap “single candidate” answer.
     */
    fun detect(candidates: Set<Language>, pcm: ShortArray? = null): Language?

    /** Refine after STT using script / text cues. */
    fun detectFromText(text: String, candidates: Set<Language>): Language?
}

/**
 * Resolves the language to use for a PTT press: the only installed language,
 * a confident LID result, or the current / Hindi fallback.
 */
fun LanguageIdEngine.resolveSpokenLanguage(
    installed: Set<Language>,
    current: Language,
    pcm: ShortArray? = null,
): Language {
    val candidates = installed.ifEmpty { setOf(Language.DEFAULT) }
    if (candidates.size == 1) return candidates.first()
    detect(candidates, pcm)?.let { if (it in candidates) return it }
    if (current in candidates) return current
    return if (Language.DEFAULT in candidates) Language.DEFAULT else candidates.first()
}
