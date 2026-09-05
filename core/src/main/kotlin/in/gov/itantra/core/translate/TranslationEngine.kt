package `in`.gov.itantra.core.translate

import `in`.gov.itantra.core.Language

class TranslationUnavailableException(message: String) : Exception(message)

/**
 * Offline source → target translation. Same-language input is a no-op.
 */
interface TranslationEngine {
    val isAvailable: Boolean

    fun translate(text: String, source: Language, target: Language): String
}

/** Returns [text] unchanged when source == target; otherwise delegates. */
fun TranslationEngine.translateOrSame(text: String, source: Language, target: Language): String {
    if (text.isBlank() || source == target) return text
    return translate(text, source, target)
}
