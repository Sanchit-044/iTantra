package `in`.gov.itantra.core.pack

import `in`.gov.itantra.core.Language

/**
 * Installs or removes per-language STT/TTS files and the shared translation pack.
 * Only languages the user selected are written to disk.
 */
interface LanguagePackManager {
    fun isLanguagePackReady(language: Language): Boolean
    fun isTranslationReady(): Boolean

    suspend fun install(
        languages: Set<Language>,
        includeTranslation: Boolean = true,
        onProgress: (PackProgress) -> Unit = {},
    )

    suspend fun uninstall(language: Language)
}

data class PackProgress(
    val language: Language?,
    val fraction: Float,
    val message: String,
)
