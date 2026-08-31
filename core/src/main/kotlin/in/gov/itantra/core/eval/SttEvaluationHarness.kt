package `in`.gov.itantra.core.eval

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip

/**
 * What the harness drives. Implemented on-device by the real STT engine; implemented
 * by a stub in tests so the scoring and reporting logic is verifiable off-device.
 */
interface OfflineTranscriber {
    /** Languages this backend can actually serve. */
    val supportedLanguages: Set<Language>

    /** Decode one clip end to end. Must not touch the network. */
    fun transcribe(clip: AudioClip, language: Language): String
}

/** One labelled evaluation sample. */
data class CorpusItem(
    val id: String,
    val language: Language,
    val audio: AudioClip,
    val reference: String,
)

/**
 * Why a language produced no score.
 */
enum class SkipReason {
    /** The backend has no model for this language at all. */
    NO_MODEL_AVAILABLE,

    /** The corpus contained no samples for this language. */
    NO_SAMPLES,
}

/**
 * Module B2 -- the headless STT evaluation harness.
 *
 * Produces per-language WER and CER plus an explicit verdict on whether the non-Hindi
 * languages are materially worse. The brief calls for reporting the numbers rather than
 * proceeding silently, so this class refuses to reduce a run to a single pass/fail; a
 * language that could not be evaluated is reported as skipped, never as zero error.
 */
class SttEvaluationHarness(
    private val transcriber: OfflineTranscriber,
    /**
     * How much worse than Hindi a language may be before it is flagged. 1.3 means
     * "more than 30% relatively worse". Chosen as a review trigger, not a hard limit.
     */
    private val regressionRatio: Double = 1.3,
) {

    data class Report(
        val byLanguage: Map<Language, LanguageReport>,
        val skipped: Map<Language, SkipReason>,
        /** Carried from the harness so [format] and [findings] agree on the threshold. */
        val regressionRatio: Double = 1.3,
    ) {
        val evaluatedLanguages: Set<Language> get() = byLanguage.keys

        /**
         * The comparison the brief asks for. Returns a line per language that is
         * materially worse than Hindi, plus a line per language that could not be
         * evaluated at all. Empty means nothing needs escalating.
         */
        fun findings(regressionRatio: Double = this.regressionRatio): List<String> {
            val out = mutableListOf<String>()

            for ((lang, reason) in skipped) {
                out += when (reason) {
                    SkipReason.NO_MODEL_AVAILABLE ->
                        "BLOCKER: ${lang.endonym} (${lang.code}) has no model on this backend. " +
                            "It was not evaluated -- this is not a WER of zero, it is no coverage at all."
                    SkipReason.NO_SAMPLES ->
                        "INCOMPLETE: no evaluation samples were supplied for ${lang.endonym} (${lang.code})."
                }
            }

            val hindi = byLanguage[Language.HINDI]
            if (hindi == null) {
                if (out.isNotEmpty() || byLanguage.isNotEmpty()) {
                    out += "INCOMPLETE: Hindi was not evaluated, so there is no baseline to compare against."
                }
                return out
            }

            for ((lang, report) in byLanguage) {
                if (lang == Language.HINDI) continue
                val ratio = if (hindi.corpusWer == 0.0) Double.POSITIVE_INFINITY
                else report.corpusWer / hindi.corpusWer
                if (report.corpusWer > hindi.corpusWer && ratio >= regressionRatio) {
                    out += "REGRESSION: ${lang.endonym} WER %.1f%% vs Hindi %.1f%% (%.2fx worse). "
                        .format(report.corpusWer * 100, hindi.corpusWer * 100, ratio) +
                        "Do not build further on this backend until the gap is understood."
                }
            }
            return out
        }

        /** Human-readable summary for a headless run. */
        fun format(): String = buildString {
            appendLine("=== iTantra Module B2: STT evaluation ===")
            if (byLanguage.isEmpty()) appendLine("No language was evaluated.")
            for (lang in Language.entries) {
                val r = byLanguage[lang]
                if (r == null) {
                    val why = skipped[lang]
                    appendLine("%-8s SKIPPED (%s)".format(lang.code, why?.name ?: "unknown"))
                    continue
                }
                appendLine(
                    "%-8s WER %6.2f%%  CER %6.2f%%  RTF %5.2f  utterances %3d  audio %6.1fs".format(
                        lang.code,
                        r.corpusWer * 100,
                        r.corpusCer * 100,
                        r.meanRealTimeFactor,
                        r.utterances.size,
                        r.totalAudioMs / 1000.0,
                    )
                )
            }
            val f = findings()
            if (f.isEmpty()) {
                appendLine("No findings requiring escalation.")
            } else {
                appendLine()
                appendLine("--- Findings ---")
                f.forEach { appendLine("* $it") }
            }
        }
    }

    fun evaluate(corpus: List<CorpusItem>): Report {
        val byLanguage = mutableMapOf<Language, LanguageReport>()
        val skipped = mutableMapOf<Language, SkipReason>()

        for (language in Language.entries) {
            if (language !in transcriber.supportedLanguages) {
                skipped[language] = SkipReason.NO_MODEL_AVAILABLE
                continue
            }
            val items = corpus.filter { it.language == language }
            if (items.isEmpty()) {
                skipped[language] = SkipReason.NO_SAMPLES
                continue
            }
            byLanguage[language] = LanguageReport(language, items.map { score(it) })
        }
        return Report(byLanguage, skipped, regressionRatio)
    }

    private fun score(item: CorpusItem): UtteranceScore {
        val startedAt = System.nanoTime()
        val hypothesis = transcriber.transcribe(item.audio, item.language)
        val processingMs = (System.nanoTime() - startedAt) / 1_000_000

        return UtteranceScore(
            id = item.id,
            language = item.language,
            reference = item.reference,
            hypothesis = hypothesis,
            result = Wer.score(item.reference, hypothesis),
            characterErrorRate = Wer.characterErrorRate(item.reference, hypothesis),
            audioDurationMs = item.audio.durationMs,
            processingMs = processingMs,
        )
    }
}
