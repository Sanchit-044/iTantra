package `in`.gov.itantra.core.stt

import `in`.gov.itantra.core.Language

/**
 * Which inference stack a model runs on.
 *
 * There is exactly one. Vosk was evaluated and dropped: it publishes no Tamil and no
 * Bengali acoustic model, so it could serve only one of the three languages in scope.
 * The enum is retained rather than removed because it keeps the model catalogue and the
 * engine interface backend-neutral, which is what made that swap cheap in the first
 * place. See docs/STT-BACKEND.md.
 */
enum class SttBackend {
    /** IndicWav2Vec CTC exported to ONNX and quantised to INT8. Covers all three. */
    ONNX_CTC,
}

/**
 * Description of a bundled model asset.
 *
 * [approxSizeBytes] is the *expected* on-disk size used for build-time budget checks
 * and is explicitly marked as an estimate. The measured figure comes from
 * [in.gov.itantra.core.diag.DiagnosticsService] at runtime; nothing here should be
 * reported as a measurement.
 */
data class ModelDescriptor(
    val language: Language,
    val backend: SttBackend,
    /** Path within the APK assets. Models are bundled at install time, never fetched. */
    val assetPath: String,
    val approxSizeBytes: Long,
    /**
     * Rough resident-memory multiplier over on-disk size: an ONNX INT8 graph is largely
     * resident, plus ONNX Runtime arena overhead.
     */
    val residentMultiplier: Double,
) {
    val estimatedResidentBytes: Long get() = (approxSizeBytes * residentMultiplier).toLong()
}

/**
 * The bundled-model catalogue and the 2 GB budget check.
 *
 * All sizes here are ESTIMATES pending the actual model files. Nothing in this class is
 * a measurement; the measured figures come from Module B7 at runtime.
 */
object ModelRegistry {

    /**
     * IndicWav2Vec CTC models exported to ONNX and quantised to INT8. Sizes are
     * estimated from a ~95 M parameter wav2vec2-base encoder at INT8 plus vocabulary
     * and a small n-gram rescoring LM.
     */
    val ONNX_CTC: Map<Language, ModelDescriptor> = Language.entries.associateWith { lang ->
        ModelDescriptor(
            language = lang,
            backend = SttBackend.ONNX_CTC,
            assetPath = "models/stt/onnx/indicwav2vec-${lang.code}-int8.onnx",
            approxSizeBytes = 95L * 1024 * 1024,
            residentMultiplier = 1.4,
        )
    }

    /** Languages this backend can actually serve. */
    fun supportedLanguages(backend: SttBackend): Set<Language> = when (backend) {
        SttBackend.ONNX_CTC -> ONNX_CTC.keys
    }

    fun descriptor(backend: SttBackend, language: Language): ModelDescriptor? =
        when (backend) {
            SttBackend.ONNX_CTC -> ONNX_CTC[language]
        }

    /** Languages in scope that a backend cannot serve at all. */
    fun missingLanguages(backend: SttBackend): Set<Language> =
        Language.entries.toSet() - supportedLanguages(backend)
}

/**
 * Static budget arithmetic for the 2 GB / no-GPU target device.
 *
 * This computes *estimates* from declared model sizes so that an obviously
 * over-budget configuration fails at build time rather than on a judge's handset. It
 * is not a substitute for the measured runtime figure from Module B7, and deliberately
 * says so in its output.
 */
object MemoryBudget {

    /**
     * Practical ceiling for one app on a 2 GB device. Android itself, the system UI
     * and the platform's own services consume most of the first gigabyte, and the
     * low-memory killer starts culling background apps well before the nominal limit.
     * ~350 MB resident is a realistic budget for staying alive during a long session.
     */
    const val APP_RESIDENT_BUDGET_BYTES = 350L * 1024 * 1024

    /** APK ceiling. Models are bundled at install time, so this is where they land. */
    const val APK_BUDGET_BYTES = 500L * 1024 * 1024

    data class Assessment(
        val sttResidentBytes: Long,
        val ttsResidentBytes: Long,
        val overheadBytes: Long,
        val budgetBytes: Long,
    ) {
        val totalBytes: Long get() = sttResidentBytes + ttsResidentBytes + overheadBytes
        val withinBudget: Boolean get() = totalBytes <= budgetBytes
        val headroomBytes: Long get() = budgetBytes - totalBytes

        fun describe(): String = buildString {
            append("ESTIMATE (not a measurement): ")
            append("STT ${mb(sttResidentBytes)} + TTS ${mb(ttsResidentBytes)} + ")
            append("runtime ${mb(overheadBytes)} = ${mb(totalBytes)} ")
            append("against a ${mb(budgetBytes)} budget -- ")
            append(if (withinBudget) "OK, ${mb(headroomBytes)} headroom" else "OVER by ${mb(-headroomBytes)}")
        }

        private fun mb(b: Long): String = "%.0f MB".format(b / 1024.0 / 1024.0)
    }

    /**
     * Assesses one resident STT model plus one resident TTS voice -- the only
     * configuration this app permits, which is precisely why the budget works.
     */
    fun assess(
        sttModel: ModelDescriptor?,
        ttsModelBytes: Long,
        /** ONNX Runtime arenas, JVM heap, audio buffers, app code. */
        overheadBytes: Long = DEFAULT_OVERHEAD_BYTES,
        budgetBytes: Long = APP_RESIDENT_BUDGET_BYTES,
    ): Assessment = Assessment(
        sttResidentBytes = sttModel?.estimatedResidentBytes ?: 0L,
        ttsResidentBytes = ttsModelBytes,
        overheadBytes = overheadBytes,
        budgetBytes = budgetBytes,
    )

    /** ONNX Runtime arenas plus JVM heap plus audio buffering, estimated. */
    const val DEFAULT_OVERHEAD_BYTES = 90L * 1024 * 1024

    /** A VITS INT8 voice, estimated. */
    const val VITS_INT8_ESTIMATE_BYTES = 40L * 1024 * 1024
}
