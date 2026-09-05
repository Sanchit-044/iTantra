package `in`.gov.itantra.core.diag

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.eval.Wer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WerTestSessionTest {

    @Test
    fun `disabled session never reports a fabricated perfect wer`() {
        val session = WerTestSession()
        assertNull(session.peekPrompt(Language.HINDI))
        assertNull(session.recordHypothesis(Language.HINDI, "नमस्ते मेरा नाम भारत है"))
        assertNull(session.wer)
        assertEquals(0, session.pairCount)
    }

    @Test
    fun `blank and whitespace hypotheses do not consume the prompt`() {
        val session = WerTestSession()
        session.setEnabled(true)
        val prompt = session.peekPrompt(Language.HINDI)
        assertEquals("नमस्ते मेरा नाम भारत है", prompt)
        assertNull(session.recordHypothesis(Language.HINDI, "   "))
        assertEquals(prompt, session.peekPrompt(Language.HINDI))
        assertEquals(0, session.pairCount)
    }

    @Test
    fun `perfect match against the prompt is zero wer`() {
        val session = WerTestSession()
        session.setEnabled(true)
        val prompt = session.peekPrompt(Language.HINDI)!!
        val wer = session.recordHypothesis(Language.HINDI, prompt)
        assertEquals(0.0, wer)
        assertEquals(1, session.pairCount)
    }

    @Test
    fun `corpus wer weights by reference words across the last ten pairs`() {
        val session = WerTestSession(maxPairs = 10)
        session.setEnabled(true)
        repeat(10) {
            val prompt = session.peekPrompt(Language.HINDI)!!
            session.recordHypothesis(Language.HINDI, prompt)
        }
        assertEquals(0.0, session.wer)
        assertEquals(10, session.pairCount)

        val eleventh = session.peekPrompt(Language.HINDI)!!
        session.recordHypothesis(Language.HINDI, "zzzz")
        assertEquals(10, session.pairCount)
        val expected = Wer.score(eleventh, "zzzz")
        assertTrue(session.wer!! > 0.0)
        assertTrue(expected.wer > 0.0)
    }

    @Test
    fun `turning test mode off clears pairs so a later screen cannot quote stale wer`() {
        val session = WerTestSession()
        session.setEnabled(true)
        session.recordHypothesis(Language.HINDI, session.peekPrompt(Language.HINDI)!!)
        session.setEnabled(false)
        assertNull(session.wer)
        assertEquals(0, session.pairCount)
        assertNull(session.peekPrompt(Language.HINDI))
    }
}
