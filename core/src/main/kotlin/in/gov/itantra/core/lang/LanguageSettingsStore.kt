package `in`.gov.itantra.core.lang

import `in`.gov.itantra.core.Language
import kotlinx.coroutines.flow.Flow

interface LanguageSettingsStore {
    val snapshot: LanguageSettings
    val settings: Flow<LanguageSettings>

    suspend fun completeSetup(installed: Set<Language>, current: Language)

    suspend fun updateSelection(installed: Set<Language>, current: Language)

    suspend fun setCurrentLanguage(language: Language)
}
