package `in`.gov.itantra.core.eval

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WerTest {

    @Test
    fun `identical text scores zero`() {
        val r = Wer.score("पानी बढ़ रहा है", "पानी बढ़ रहा है")
        assertEquals(0, r.errors)
        assertEquals(0.0, r.wer)
        assertEquals(4, r.hits)
    }

    @Test
    fun `counts a substitution`() {
        val r = Wer.score("पानी बढ़ रहा है", "पानी घट रहा है")
        assertEquals(1, r.substitutions)
        assertEquals(0, r.deletions)
        assertEquals(0, r.insertions)
        assertEquals(0.25, r.wer)
    }

    @Test
    fun `counts a deletion`() {
        val r = Wer.score("एक दो तीन चार", "एक दो चार")
        assertEquals(1, r.deletions)
        assertEquals(0.25, r.wer)
    }

    @Test
    fun `counts an insertion`() {
        val r = Wer.score("एक दो तीन", "एक दो अतिरिक्त तीन")
        assertEquals(1, r.insertions)
        assertEquals(1.0 / 3, r.wer, 1e-9)
    }

    @Test
    fun `wer can exceed one when the hypothesis rambles`() {
        val r = Wer.score("एक", "एक दो तीन चार पाँच")
        assertTrue(r.wer > 1.0, "expected WER above 1.0, got ${r.wer}")
    }

    @Test
    fun `punctuation and case are ignored`() {
        assertEquals(0.0, Wer.score("पानी बढ़ रहा है।", "पानी बढ़ रहा है").wer)
    }

    @Test
    fun `native digits are folded before scoring`() {
        // The reference may use Devanagari numerals while the engine emits ASCII.
        assertEquals(0.0, Wer.score("२५ मीटर", "25 मीटर").wer)
    }

    @Test
    fun `combining marks are not stripped`() {
        // बढ़ vs बढ differ by a nukta; that is a real error and must be counted.
        assertTrue(Wer.score("बढ़", "बढ").errors > 0, "diacritic difference was silently forgiven")
    }

    @Test
    fun `empty reference with output counts as insertions`() {
        val r = Wer.score("", "कुछ शब्द")
        assertEquals(2, r.insertions)
        assertEquals(0, r.referenceWords)
    }

    @Test
    fun `character error rate handles agglutinative comparison`() {
        // One wrong suffix damages a whole Tamil word (WER 1.0) but few characters.
        val wer = Wer.score("வெளியேறவும்", "வெளியேறவும").wer
        val cer = Wer.characterErrorRate("வெளியேறவும்", "வெளியேறவும")
        assertEquals(1.0, wer)
        assertTrue(cer < 0.2, "CER should be far below WER for a one-character slip, got $cer")
    }

    @Test
    fun `corpus wer weights by reference length not by utterance count`() {
        // A short perfect utterance must not cancel out a long broken one.
        val long = UtteranceScore(
            id = "long", language = Language.HINDI,
            reference = "एक दो तीन चार पाँच छह सात आठ नौ दस",
            hypothesis = "क ख ग घ ङ च छ ज झ ञ",
            result = Wer.score("एक दो तीन चार पाँच छह सात आठ नौ दस", "क ख ग घ ङ च छ ज झ ञ"),
            characterErrorRate = 1.0, audioDurationMs = 5000, processingMs = 1000,
        )
        val short = UtteranceScore(
            id = "short", language = Language.HINDI,
            reference = "हाँ", hypothesis = "हाँ",
            result = Wer.score("हाँ", "हाँ"),
            characterErrorRate = 0.0, audioDurationMs = 500, processingMs = 100,
        )
        val report = LanguageReport(Language.HINDI, listOf(long, short))

        // 10 errors over 11 reference words, not the 0.5 a per-utterance mean would give.
        assertEquals(10.0 / 11, report.corpusWer, 1e-9)
    }

    @Test
    fun `real time factor is processing over audio duration`() {
        val u = UtteranceScore(
            id = "u", language = Language.HINDI, reference = "a", hypothesis = "a",
            result = Wer.score("a", "a"), characterErrorRate = 0.0,
            audioDurationMs = 2000, processingMs = 500,
        )
        assertEquals(0.25, u.realTimeFactor)
    }
}
