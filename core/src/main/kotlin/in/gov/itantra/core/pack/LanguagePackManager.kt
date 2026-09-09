package `in`.gov.itantra.core.pack

import `in`.gov.itantra.core.Language

/**
 * How much of a language pack is actually on disk.
 *
 * The distinction matters because installing a pack writes a `.ready` marker even
 * when no ONNX weights were available to copy — that is what lets the setup screen
 * record the user's selection before the models are provisioned. Without a separate
 * state the UI would report such a language as installed, and the first PTT press
 * would fail with a decoder error instead of an explainable message.
 */
enum class PackState {
    /** Weights are present. STT and TTS can run for this language. */
    READY,

    /** Selection recorded, weights missing. Speech will not work until they land. */
    PLACEHOLDER,

    /** Not selected, or uninstalled. */
    MISSING,
}

/**
 * Installs or removes per-language STT/TTS files and the shared translation pack.
 * Only languages the user selected are written to disk.
 */
interface LanguagePackManager {
    /** Distinguishes real weights from a recorded selection. See [PackState]. */
    fun packState(language: Language): PackState

    /** True only when speech can actually run — a [PackState.PLACEHOLDER] is not ready. */
    fun isLanguagePackReady(language: Language): Boolean =
        packState(language) == PackState.READY

    fun translationState(): PackState

    fun isTranslationReady(): Boolean = translationState() == PackState.READY

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
