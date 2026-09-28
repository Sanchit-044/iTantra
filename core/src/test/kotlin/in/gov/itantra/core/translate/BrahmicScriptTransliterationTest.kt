package `in`.gov.itantra.core.translate

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrahmicScriptTransliterationTest {

    @Test
    fun `isBrahmic detects all Indic languages and ignores English`() {
        assertTrue(BrahmicScriptTransliteration.isBrahmic(Language.HINDI))
        assertTrue(BrahmicScriptTransliteration.isBrahmic(Language.TAMIL))
        assertTrue(BrahmicScriptTransliteration.isBrahmic(Language.BENGALI))
        assertTrue(BrahmicScriptTransliteration.isBrahmic(Language.GUJARATI))
        assertTrue(BrahmicScriptTransliteration.isBrahmic(Language.MARATHI))
        assertTrue(BrahmicScriptTransliteration.isBrahmic(Language.KANNADA))
        assertTrue(BrahmicScriptTransliteration.isBrahmic(Language.MALAYALAM))
        assertTrue(BrahmicScriptTransliteration.isBrahmic(Language.TELUGU))
        assertTrue(BrahmicScriptTransliteration.isBrahmic(Language.ODIA))
        assertFalse(BrahmicScriptTransliteration.isBrahmic(Language.ENGLISH))
    }

    @Test
    fun `toDevanagari converts Gujarati script to Devanagari`() {
        val gujarati = "નમસ્તે"
        val devanagari = BrahmicScriptTransliteration.toDevanagari(gujarati, Language.GUJARATI)
        assertEquals("नमस्ते", devanagari)
    }

    @Test
    fun `fromDevanagari converts Devanagari to Gujarati script`() {
        val devanagari = "नमस्ते"
        val gujarati = BrahmicScriptTransliteration.fromDevanagari(devanagari, Language.GUJARATI)
        assertEquals("નમસ્તે", gujarati)
    }

    @Test
    fun `fromDevanagari converts Devanagari to Bengali script`() {
        val devanagari = "नमस्ते"
        val bengali = BrahmicScriptTransliteration.fromDevanagari(devanagari, Language.BENGALI)
        assertEquals("নমস্তে", bengali)
    }

    @Test
    fun `fromDevanagari converts Devanagari to Telugu script`() {
        val devanagari = "नमस्ते"
        val telugu = BrahmicScriptTransliteration.fromDevanagari(devanagari, Language.TELUGU)
        assertEquals("నమస్తే", telugu)
    }

    @Test
    fun `fromDevanagari converts Devanagari to Kannada script`() {
        val devanagari = "नमस्ते"
        val kannada = BrahmicScriptTransliteration.fromDevanagari(devanagari, Language.KANNADA)
        assertEquals("ನಮಸ್ತೇ", kannada)
    }

    @Test
    fun `fromDevanagari converts Devanagari to Malayalam script`() {
        val devanagari = "नमस्ते"
        val malayalam = BrahmicScriptTransliteration.fromDevanagari(devanagari, Language.MALAYALAM)
        assertEquals("നമസ്തേ", malayalam)
    }

    @Test
    fun `fromDevanagari converts Devanagari to Odia script`() {
        val devanagari = "नमस्ते"
        val odia = BrahmicScriptTransliteration.fromDevanagari(devanagari, Language.ODIA)
        assertEquals("ନମସ୍ତେ", odia)
    }

    @Test
    fun `transliterate round trip between Bengali and Gujarati`() {
        val bengali = "নমস্তে"
        val gujarati = BrahmicScriptTransliteration.transliterate(bengali, Language.BENGALI, Language.GUJARATI)
        assertEquals("નમસ્તે", gujarati)
    }

    @Test
    fun `transliterate preserves numbers punctuation and english characters`() {
        val mixedHindi = "मदद 101 SOS!"
        val transliterated = BrahmicScriptTransliteration.transliterate(mixedHindi, Language.HINDI, Language.GUJARATI)
        assertEquals("મદદ 101 SOS!", transliterated)
    }

    @Test
    fun `identity transliteration when source equals target or language is English`() {
        val text = "Emergency Alert"
        assertEquals(text, BrahmicScriptTransliteration.transliterate(text, Language.ENGLISH, Language.HINDI))
        assertEquals(text, BrahmicScriptTransliteration.transliterate(text, Language.HINDI, Language.ENGLISH))
        assertEquals("नमस्ते", BrahmicScriptTransliteration.transliterate("नमस्ते", Language.HINDI, Language.HINDI))
        assertEquals("मराठी", BrahmicScriptTransliteration.transliterate("मराठी", Language.MARATHI, Language.MARATHI))
    }
}
