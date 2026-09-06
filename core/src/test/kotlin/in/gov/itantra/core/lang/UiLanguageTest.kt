package `in`.gov.itantra.core.lang

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UiLanguageTest {

    @Test
    fun `installed only plus english`() {
        val allowed = UiLanguage.allowed(setOf(Language.TAMIL))
        assertEquals(setOf(Language.ENGLISH, Language.TAMIL), allowed)
    }

    @Test
    fun `empty installed still allows english and hindi default pack`() {
        val allowed = UiLanguage.allowed(emptySet())
        assertEquals(setOf(Language.ENGLISH, Language.HINDI), allowed)
    }

    @Test
    fun `ui language snaps to english when uninstalled`() {
        assertEquals(
            Language.ENGLISH,
            UiLanguage.normalize(Language.TAMIL, setOf(Language.HINDI)),
        )
    }

    @Test
    fun `ui language kept when still allowed`() {
        assertEquals(
            Language.TAMIL,
            UiLanguage.normalize(Language.TAMIL, setOf(Language.TAMIL)),
        )
    }

    @Test
    fun `null becomes english`() {
        assertEquals(Language.ENGLISH, UiLanguage.normalize(null, setOf(Language.HINDI)))
    }

    @Test
    fun `english stays allowed without an english speech pack`() {
        assertTrue(Language.ENGLISH in UiLanguage.allowed(setOf(Language.HINDI)))
        assertEquals(Language.ENGLISH, UiLanguage.normalize(Language.ENGLISH, setOf(Language.HINDI)))
    }

    @Test
    fun `options always list english first`() {
        val options = UiLanguage.options(setOf(Language.TAMIL, Language.BENGALI))
        assertEquals(Language.ENGLISH, options.first())
        assertEquals(setOf(Language.ENGLISH, Language.TAMIL, Language.BENGALI), options.toSet())
    }
}
