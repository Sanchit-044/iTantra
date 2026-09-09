package `in`.gov.itantra.core.translate

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BpeTokenizerTest {

    private fun miniTokenizer(): BpeTokenizer {
        val vocab = mapOf(
            "▁" to 4,
            "h" to 5,
            "e" to 6,
            "l" to 7,
            "o" to 8,
            "w" to 9,
            "r" to 10,
            "d" to 11,
            "▁h" to 12,
            "he" to 13,
            "ll" to 14,
            "lo" to 15,
            "▁he" to 16,
            "llo" to 17,
            "▁w" to 18,
            "or" to 19,
            "ld" to 20,
            "orld" to 21,
        )
        val merges = listOf(
            "▁ h",       // rank 0: ▁ + h → ▁h
            "h e",       // rank 1: h + e → he
            "l l",       // rank 2: l + l → ll
            "l o",       // rank 3: l + o → lo
            "▁h e",      // rank 4: ▁h + e → ▁he
            "ll o",      // rank 5: ll + o → llo
            "▁ w",       // rank 6: ▁ + w → ▁w
            "o r",       // rank 7: o + r → or
            "l d",       // rank 8: l + d → ld
            "or ld",     // rank 9: or + ld → orld
        )
        val special = mapOf("<s>" to 0, "<pad>" to 1, "</s>" to 2, "<unk>" to 3)
        val langTags = mapOf("hin_Deva" to 100, "ben_Beng" to 101)

        return BpeTokenizer.build(vocab, merges, special, langTags)
    }

    @Test
    fun `encode empty string returns empty array`() {
        val tok = miniTokenizer()
        assertTrue(tok.encode("").isEmpty())
        assertTrue(tok.encode("   ").isEmpty())
    }

    @Test
    fun `encode single known word`() {
        val tok = miniTokenizer()
        val ids = tok.encode("hello")
        assertTrue(ids.isNotEmpty(), "Should produce non-empty IDs")
    }

    @Test
    fun `decode reverses encode for known tokens`() {
        val tok = miniTokenizer()
        val text = "hello"
        val ids = tok.encode(text)
        val decoded = tok.decode(ids)
        assertEquals(text, decoded)
    }

    @Test
    fun `decode skips BOS EOS PAD`() {
        val tok = miniTokenizer()
        val ids = intArrayOf(tok.bosId, 12, 6, tok.eosId, tok.padId)
        val decoded = tok.decode(ids)
        assertFalse(decoded.contains("<s>"))
        assertFalse(decoded.contains("</s>"))
    }

    @Test
    fun `unknown characters map to UNK`() {
        val tok = miniTokenizer()
        val ids = tok.encode("xyz")
        assertTrue(ids.any { it == tok.unkId }, "Unknown chars should map to unkId")
    }

    @Test
    fun `langTagId returns correct ID`() {
        val tok = miniTokenizer()
        assertEquals(100, tok.langTagId("hin_Deva"))
        assertEquals(101, tok.langTagId("ben_Beng"))
        assertNull(tok.langTagId("nonexistent"))
    }

    @Test
    fun `floresToCode maps all Languages`() {
        for (lang in Language.entries) {
            val code = BpeTokenizer.floresToCode(lang)
            assertNotNull(code, "$lang should have a FLORES code")
            assertTrue(code.contains("_"), "FLORES code should contain underscore")
        }
    }

    @Test
    fun `floresToCode hindi is hin_Deva`() {
        assertEquals("hin_Deva", BpeTokenizer.floresToCode(Language.HINDI))
    }

    @Test
    fun `floresToCode bengali is ben_Beng`() {
        assertEquals("ben_Beng", BpeTokenizer.floresToCode(Language.BENGALI))
    }

    @Test
    fun `floresToCode english is eng_Latn`() {
        assertEquals("eng_Latn", BpeTokenizer.floresToCode(Language.ENGLISH))
    }

    @Test
    fun `vocabSize includes all token types`() {
        val tok = miniTokenizer()
        assertTrue(tok.vocabSize > 0)
    }

    @Test
    fun `multi-word string produces multiple word-boundary markers`() {
        val tok = miniTokenizer()
        val ids = tok.encode("hello world")
        assertTrue(ids.size > 2, "Multi-word should produce multiple IDs")
    }
}
