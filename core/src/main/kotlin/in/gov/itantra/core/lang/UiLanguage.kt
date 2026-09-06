package `in`.gov.itantra.core.lang

import `in`.gov.itantra.core.Language

/**
 * App chrome language. Always includes English, plus every installed speech pack.
 * English does not require an English STT/TTS pack.
 */
object UiLanguage {

    fun allowed(installed: Set<Language>): Set<Language> =
        setOf(Language.ENGLISH) + LanguageSelection.normalizeInstalled(installed)

    fun normalize(uiLanguage: Language?, installed: Set<Language>): Language {
        val allowed = allowed(installed)
        if (uiLanguage != null && uiLanguage in allowed) return uiLanguage
        return Language.ENGLISH
    }

    fun options(installed: Set<Language>): List<Language> =
        allowed(installed).sortedBy { if (it == Language.ENGLISH) 0 else 1 + it.ordinal }
}
