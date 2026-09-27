package `in`.gov.itantra.core.translate

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.diag.AppLog

/**
 * Tries each delegate in order until one succeeds.
 *
 * Typical chain: [DictionaryTranslationEngine] (instant, offline, limited phrases)
 * → [TranslationEngine] backed by an ONNX seq2seq model (slower, needs model files,
 * handles arbitrary text).
 *
 * If **every** engine in the chain throws [TranslationUnavailableException], the
 * exception from the **last** engine is propagated so the caller gets the most
 * informative message (the ONNX engine can report "model not installed" while the
 * dictionary just says "phrase not found").
 */
class ChainedTranslationEngine(
    private val delegates: List<TranslationEngine>,
) : TranslationEngine {

    init {
        require(delegates.isNotEmpty()) { "ChainedTranslationEngine needs at least one delegate" }
    }

    override val isAvailable: Boolean
        get() = delegates.any { it.isAvailable }

    override fun translate(text: String, source: Language, target: Language): String {
        if (source == target) return text

        var lastException: Exception? = null

        for (engine in delegates) {
            if (!engine.isAvailable) continue
            try {
                val result = engine.translate(text, source, target)
                AppLog.d(TAG, "${engine.javaClass.simpleName} translated ${source.code}→${target.code}")
                return result
            } catch (e: TranslationUnavailableException) {
                AppLog.d(TAG, "${engine.javaClass.simpleName} unavailable: ${e.message}")
                lastException = e
            } catch (e: Exception) {
                AppLog.e(TAG, "${engine.javaClass.simpleName} crashed: ${e.message}", e)
                lastException = e
            }
        }

        throw when (lastException) {
            is TranslationUnavailableException -> lastException
            null -> TranslationUnavailableException(
                "No translation engine available for ${source.englishName} → ${target.englishName}"
            )
            else -> TranslationUnavailableException(
                "Translation failed for ${source.englishName} → ${target.englishName}: ${lastException.message}"
            )
        }
    }

    private companion object {
        const val TAG = "ChainedTranslation"
    }
}
