package `in`.gov.itantra.core.lang

import `in`.gov.itantra.core.Language

/**
 * Pure rules for the one-language-per-phone model (speak and listen are the same).
 * Hindi is the default whenever the user leaves the set empty or the current
 * language is no longer installed.
 */
object LanguageSelection {

    fun normalizeInstalled(selected: Set<Language>): Set<Language> {
        if (selected.isEmpty()) return setOf(Language.DEFAULT)
        return selected
    }

    fun normalizeCurrent(current: Language?, installed: Set<Language>): Language {
        val set = normalizeInstalled(installed)
        if (current != null && current in set) return current
        return if (Language.DEFAULT in set) Language.DEFAULT else set.first()
    }
}

data class LanguageSettings(
    val setupDone: Boolean = false,
    val installed: Set<Language> = setOf(Language.DEFAULT),
    val current: Language = Language.DEFAULT,
) {
    val normalized: LanguageSettings
        get() {
            val installed = LanguageSelection.normalizeInstalled(installed)
            return copy(
                installed = installed,
                current = LanguageSelection.normalizeCurrent(current, installed),
            )
        }
}
