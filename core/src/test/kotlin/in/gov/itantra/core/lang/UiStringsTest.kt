package `in`.gov.itantra.core.lang

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiStringsTest {

    @Test
    fun `every language has a non-blank value for every key`() {
        for (language in Language.entries) {
            val strings = UiStrings.forLanguage(language)
            for ((key, value) in strings.allValues()) {
                assertTrue(value.isNotBlank(), "$language $key")
            }
        }
    }

    @Test
    fun `pairing and queue templates substitute placeholders`() {
        val s = UiStrings.forLanguage(Language.ENGLISH)
        assertTrue(s.pairingBody("123456").contains("123456"))
        assertEquals("2 queued messages — tap to open, then Play", s.queuedBody(2, "ignored"))
        assertTrue(s.queuedBody(1, "hello").contains("hello"))
        assertTrue(s.waitingToSend(3, 1).contains("3"))
    }

    @Test
    fun `gujarati without a table still returns english not blanks`() {
        val s = UiStrings.forLanguage(Language.GUJARATI)
        assertEquals(UiStrings.forLanguage(Language.ENGLISH).talk, s.talk)
        assertFalse(s.connect.isBlank())
    }

    @Test
    fun `error and empty queue bodies never go blank`() {
        val s = UiStrings.forLanguage(Language.HINDI)
        assertTrue(s.genericError(null).isNotBlank())
        assertTrue(s.genericError("boom").contains("boom"))
        assertEquals("", s.queuedBody(0, "ignored"))
        assertEquals(s.queuedOneFallback, s.queuedBody(1, "   "))
    }
}
