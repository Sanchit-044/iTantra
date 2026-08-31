package `in`.gov.itantra.core.eval

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.core.audio.AudioFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SttEvaluationHarnessTest {

    /** Returns a canned hypothesis per item id, so scoring is deterministic. */
    private class StubTranscriber(
        override val supportedLanguages: Set<Language>,
        private val hypotheses: Map<String, String> = emptyMap(),
        private val defaultHypothesis: (Language) -> String = { "" },
    ) : OfflineTranscriber {
        var calls = 0
            private set

        override fun transcribe(clip: AudioClip, language: Language): String {
            calls++
            return hypotheses[clip.hashCode().toString()] ?: defaultHypothesis(language)
        }
    }

    private fun clip(ms: Int = 1000) =
        AudioClip(ShortArray(AudioFormat.STT_16K.samplesForMs(ms)), AudioFormat.STT_16K)

    private fun item(id: String, lang: Language, ref: String) =
        CorpusItem(id, lang, clip(), ref)

    // ------------------------------------------------------------- coverage gap

    /**
     * The finding that actually matters for this project: a backend with no Tamil or
     * Bengali model must report those languages as uncovered, never as a perfect score.
     * Filtering an empty result set would otherwise produce WER 0.0 and look like a pass.
     */
    @Test
    fun `a language with no model is reported as a blocker not as zero error`() {
        val harness = SttEvaluationHarness(
            StubTranscriber(supportedLanguages = setOf(Language.HINDI)) { "पानी बढ़ रहा है" }
        )
        val report = harness.evaluate(
            listOf(
                item("h1", Language.HINDI, "पानी बढ़ रहा है"),
                item("t1", Language.TAMIL, "வெள்ளம் உயர்கிறது"),
                item("b1", Language.BENGALI, "বন্যার জল বাড়ছে"),
            )
        )

        assertEquals(setOf(Language.HINDI), report.evaluatedLanguages)
        assertEquals(SkipReason.NO_MODEL_AVAILABLE, report.skipped[Language.TAMIL])
        assertEquals(SkipReason.NO_MODEL_AVAILABLE, report.skipped[Language.BENGALI])

        val findings = report.findings()
        assertEquals(2, findings.count { it.startsWith("BLOCKER") })
        assertTrue(findings.any { it.contains("தமிழ்") }, "Tamil was not named in the findings")
        assertTrue(findings.any { it.contains("বাংলা") }, "Bengali was not named in the findings")
    }

    @Test
    fun `an unsupported language is never transcribed`() {
        val stub = StubTranscriber(supportedLanguages = setOf(Language.HINDI)) { "x" }
        SttEvaluationHarness(stub).evaluate(
            listOf(
                item("h1", Language.HINDI, "a"),
                item("t1", Language.TAMIL, "b"),
            )
        )
        assertEquals(1, stub.calls, "the harness invoked a backend that has no model")
    }

    // ------------------------------------------------------------- regressions

    @Test
    fun `a materially worse language is flagged against the hindi baseline`() {
        val harness = SttEvaluationHarness(
            StubTranscriber(supportedLanguages = Language.entries.toSet()) { lang ->
                // Hindi perfect; Tamil entirely wrong.
                if (lang == Language.HINDI) "एक दो तीन चार" else "क ख ग घ"
            }
        )
        val report = harness.evaluate(
            listOf(
                item("h1", Language.HINDI, "एक दो तीन चार"),
                item("t1", Language.TAMIL, "ஒன்று இரண்டு மூன்று நான்கு"),
            )
        )

        assertEquals(0.0, report.byLanguage[Language.HINDI]!!.corpusWer)
        assertEquals(1.0, report.byLanguage[Language.TAMIL]!!.corpusWer)
        assertTrue(
            report.findings().any { it.startsWith("REGRESSION") && it.contains("தமிழ்") },
            "a total Tamil failure was not flagged: ${report.findings()}",
        )
    }

    @Test
    fun `comparable languages produce no regression finding`() {
        val harness = SttEvaluationHarness(
            StubTranscriber(supportedLanguages = Language.entries.toSet()) { "एक दो तीन गलत" }
        )
        // Every language gets the same one-word-wrong hypothesis against the same
        // reference, so all three score identically and nothing should be escalated.
        val report = harness.evaluate(
            Language.entries.map { item("${it.code}1", it, "एक दो तीन चार") }
        )
        assertTrue(
            report.findings().none { it.startsWith("REGRESSION") },
            "flagged a regression where all languages scored the same: ${report.findings()}",
        )
    }

    @Test
    fun `missing samples are reported separately from missing models`() {
        val harness = SttEvaluationHarness(
            StubTranscriber(supportedLanguages = Language.entries.toSet()) { "x" }
        )
        val report = harness.evaluate(listOf(item("h1", Language.HINDI, "x")))

        assertEquals(SkipReason.NO_SAMPLES, report.skipped[Language.TAMIL])
        assertTrue(report.findings().any { it.startsWith("INCOMPLETE") })
    }

    @Test
    fun `a clean run across all three languages yields no findings`() {
        val harness = SttEvaluationHarness(
            StubTranscriber(supportedLanguages = Language.entries.toSet()) { "एक दो तीन चार" }
        )
        val report = harness.evaluate(
            Language.entries.map { item("${it.code}1", it, "एक दो तीन चार") }
        )
        assertTrue(report.findings().isEmpty(), "unexpected findings: ${report.findings()}")
    }

    @Test
    fun `the formatted report names every language including skipped ones`() {
        val harness = SttEvaluationHarness(
            StubTranscriber(supportedLanguages = setOf(Language.HINDI)) { "x" }
        )
        val text = harness.evaluate(listOf(item("h1", Language.HINDI, "x"))).format()

        for (lang in Language.entries) {
            assertTrue(text.contains(lang.code), "${lang.code} missing from the report:\n$text")
        }
        assertTrue(text.contains("SKIPPED"))
        assertTrue(text.contains("BLOCKER"))
    }
}
