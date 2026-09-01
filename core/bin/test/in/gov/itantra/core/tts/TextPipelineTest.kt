package `in`.gov.itantra.core.tts

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IndicNumberFormatterTest {

    private val hi = IndicNumberFormatter.forLanguage(Language.HINDI)
    private val ta = IndicNumberFormatter.forLanguage(Language.TAMIL)
    private val bn = IndicNumberFormatter.forLanguage(Language.BENGALI)

    @Test
    fun `hindi small numbers`() {
        assertEquals("शून्य", hi.spell(0))
        assertEquals("एक", hi.spell(1))
        assertEquals("उन्नीस", hi.spell(19))
        assertEquals("निन्यानवे", hi.spell(99))
    }

    @Test
    fun `hindi uses the indian grouping`() {
        assertEquals("एक सौ", hi.spell(100))
        assertEquals("एक हज़ार", hi.spell(1_000))
        // 100000 is one lakh, not "one hundred thousand" -- the grouping that makes
        // this app's numbers sound native rather than translated.
        assertEquals("एक लाख", hi.spell(100_000))
        assertEquals("एक करोड़", hi.spell(10_000_000))
    }

    @Test
    fun `hindi composite number`() {
        // 12,34,567 -> बारह लाख चौंतीस हज़ार पाँच सौ सड़सठ
        assertEquals("बारह लाख चौंतीस हज़ार पाँच सौ सड़सठ", hi.spell(1_234_567))
    }

    @Test
    fun `numbers beyond a crore recurse correctly`() {
        // 1,00,00,00,000 == 100 crore.
        assertEquals("एक सौ करोड़", hi.spell(1_000_000_000L))
    }

    @Test
    fun `tamil composes tens with the combining form`() {
        assertEquals("ஒன்று", ta.spell(1))
        assertEquals("இருபது", ta.spell(20))
        // 21 uses the combining ten இருபத்தி, not the standalone இருபது.
        assertEquals("இருபத்தி ஒன்று", ta.spell(21))
        assertEquals("தொண்ணூற்றி ஒன்பது", ta.spell(99))
    }

    @Test
    fun `tamil hundreds are irregular and not composed from units`() {
        assertEquals("நூறு", ta.spell(100))
        assertEquals("இருநூறு", ta.spell(200))
        assertEquals("தொள்ளாயிரம்", ta.spell(900))
    }

    @Test
    fun `bengali small numbers and grouping`() {
        assertEquals("শূন্য", bn.spell(0))
        assertEquals("নিরানব্বই", bn.spell(99))
        assertEquals("একশো", bn.spell(100))
        assertEquals("এক লাখ", bn.spell(100_000))
        assertEquals("এক কোটি", bn.spell(10_000_000))
    }

    @Test
    fun `every language has a complete and distinct 0 to 99 table`() {
        for (lang in Language.entries) {
            val lex = NumberLexicon.forLanguage(lang)
            assertEquals(100, lex.units.size, "$lang units table is incomplete")
            assertTrue(lex.units.none { it.isBlank() }, "$lang has a blank number word")
            assertEquals(
                100, lex.units.toSet().size,
                "$lang has duplicate number words, so at least one value is wrong",
            )
        }
    }

    @Test
    fun `negative numbers are prefixed`() {
        assertTrue(hi.spell(-5).startsWith("ऋण"))
    }

    @Test
    fun `decimals are read digit by digit after the point`() {
        // "3.25" is spoken "three point two five", never "three point twenty-five".
        assertEquals("तीन दशमलव दो पाँच", hi.spellDecimal(3, "25"))
    }

    @Test
    fun `digitwise spelling for identifiers`() {
        assertEquals("नौ आठ सात", hi.spellDigitwise("987"))
    }
}

class TextNormalizerTest {

    private val hi = TextNormalizer(Language.HINDI)
    private val ta = TextNormalizer(Language.TAMIL)
    private val bn = TextNormalizer(Language.BENGALI)

    @Test
    fun `folds devanagari digits to ascii before processing`() {
        assertEquals("123", hi.foldNativeDigits("१२३"))
    }

    @Test
    fun `folds bengali and tamil digits`() {
        assertEquals("456", bn.foldNativeDigits("৪৫৬"))
        assertEquals("789", ta.foldNativeDigits("௭௮௯"))
    }

    @Test
    fun `native script digits are spoken as numbers`() {
        // A user typing in Devanagari numerals must be read, not skipped.
        assertEquals("पच्चीस", hi.normalize("२५"))
    }

    @Test
    fun `expands a slash separated date`() {
        val out = hi.normalize("15/08/2025")
        assertTrue(out.contains("अगस्त"), "month name missing from: $out")
        assertTrue(out.contains("पंद्रह"), "day missing from: $out")
        // Crucially the digits must be gone -- VITS cannot pronounce them.
        assertFalse(out.any { it.isDigit() }, "digits survived normalisation: $out")
    }

    @Test
    fun `expands an iso date`() {
        val out = bn.normalize("2025-08-15")
        assertTrue(out.contains("আগস্ট"), "expected Bengali month name in: $out")
        assertFalse(out.any { it.isDigit() })
    }

    @Test
    fun `expands a time`() {
        val out = hi.normalize("14:30")
        assertTrue(out.contains("चौदह"), "hour missing from: $out")
        assertTrue(out.contains("तीस"), "minutes missing from: $out")
    }

    @Test
    fun `expands abbreviations on whole tokens only`() {
        assertTrue(hi.normalize("NDRF").contains("राष्ट्रीय आपदा मोचन बल"))
        // A token that merely contains the letters must not be rewritten.
        assertEquals("NDRFX", hi.normalize("NDRFX"))
    }

    @Test
    fun `longer abbreviations win over shorter ones`() {
        val out = hi.normalize("SDRF")
        assertTrue(out.contains("राज्य"), "SDRF was not expanded correctly: $out")
    }

    @Test
    fun `expands currency`() {
        val out = hi.normalize("₹500")
        assertTrue(out.contains("पाँच सौ"), out)
        assertTrue(out.contains("रुपये"), out)
    }

    @Test
    fun `expands measures with their unit names`() {
        val out = hi.normalize("12 km")
        assertTrue(out.contains("बारह"), out)
        assertTrue(out.contains("किलोमीटर"), out)
    }

    @Test
    fun `long digit runs are read digit by digit`() {
        // A 10-digit phone number read as a quantity is unusable.
        val out = hi.normalize("9876543210")
        assertTrue(out.startsWith("नौ आठ सात"), "expected digitwise reading, got: $out")
    }

    @Test
    fun `no digits survive normalisation in any language`() {
        val samples = listOf("15/08/2025", "14:30", "₹1500", "42 km", "9876543210", "3.5")
        for (lang in Language.entries) {
            val n = TextNormalizer(lang)
            for (s in samples) {
                val out = n.normalize(s)
                assertFalse(
                    out.any { it.isDigit() },
                    "$lang left digits in \"$s\" -> \"$out\"; VITS cannot pronounce these",
                )
            }
        }
    }
}

class ClauseChunkerTest {

    private val chunker = ClauseChunker()

    @Test
    fun `splits on the devanagari danda`() {
        // U+0964 is the Hindi sentence terminator and is not Latin punctuation.
        val chunks = chunker.chunk("पानी बढ़ रहा है। सब लोग ऊपर जाएँ। तुरंत निकलें।")
        assertTrue(chunks.size >= 2, "danda was not treated as a boundary: $chunks")
        assertTrue(chunks.all { it.isNotBlank() })
    }

    @Test
    fun `splits on latin punctuation for tamil`() {
        val chunks = chunker.chunk("வெள்ளம் உயர்கிறது. அனைவரும் வெளியேறவும். உடனடியாக செல்லவும்.")
        assertTrue(chunks.size >= 2, "expected multiple clauses, got: $chunks")
    }

    @Test
    fun `preserves all content across chunks`() {
        val text = "पहला वाक्य। दूसरा वाक्य। तीसरा वाक्य।"
        val rejoined = chunker.chunk(text).joinToString(" ")
        // Every non-space character must survive; chunking must not drop content.
        assertEquals(
            text.filter { !it.isWhitespace() },
            rejoined.filter { !it.isWhitespace() },
            "chunking lost or reordered text",
        )
    }

    @Test
    fun `merges fragments that are too short to synthesise alone`() {
        // "हाँ।" alone would sound clipped, so it must be merged forward.
        val chunks = chunker.chunk("हाँ। नहीं। ठीक है।")
        assertTrue(chunks.all { it.length >= 8 || chunks.size == 1 }, "got clipped fragments: $chunks")
    }

    @Test
    fun `caps very long clauses`() {
        val long = "शब्द ".repeat(200)
        val chunks = chunker.chunk(long)
        assertTrue(chunks.isNotEmpty())
        assertTrue(
            chunks.all { it.length <= 200 },
            "a chunk exceeded the cap, hurting time-to-first-audio: ${chunks.map { it.length }}",
        )
    }

    @Test
    fun `empty and blank input produce no chunks`() {
        assertTrue(chunker.chunk("").isEmpty())
        assertTrue(chunker.chunk("   ").isEmpty())
    }

    @Test
    fun `a decimal point is not a clause boundary`() {
        // Guard for un-normalised text arriving over the transport.
        val chunks = ClauseChunker(minChunkChars = 1).chunk("मान 3.5 मीटर है")
        assertEquals(1, chunks.size, "split on a decimal point: $chunks")
    }

    @Test
    fun `runs of punctuation do not create empty chunks`() {
        val chunks = chunker.chunk("क्या?! सच में... हाँ।")
        assertTrue(chunks.none { it.isBlank() }, "produced a blank chunk: $chunks")
    }
}
