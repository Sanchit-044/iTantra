package `in`.gov.itantra.core.tts

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Guards the whole lexicon layer against the failure that made numbers and
 * abbreviations inaudible in six of the ten languages.
 *
 * The bug was not a typo. Both lexicons had been filled with strings in the wrong
 * script -- generated ASCII placeholders like `or-n-five` in the number tables, and
 * borrowed English expansions in the abbreviation tables. Nothing failed loudly:
 * [in.gov.itantra.android.tts.VitsTokenizer] can only emit symbols that exist in the
 * loaded voice's vocabulary, and an Odia voice has no Latin letters, so every one of
 * those characters was dropped on the floor and the number or phrase was simply absent
 * from the audio.
 *
 * These tests are mechanical rather than linguistic. They cannot tell you that
 * "ପଚାଶ" is the right word for fifty -- only a native speaker can, and
 * docs/LEXICON-REVIEW.md tracks that -- but they can guarantee that whatever word is
 * there is written in a script the voice can actually pronounce.
 */
class LexiconScriptTest {

    /** Unicode block each language's lexicon strings must stay inside. */
    private val blocks: Map<Language, IntRange> = mapOf(
        Language.HINDI to 0x0900..0x097F,
        Language.MARATHI to 0x0900..0x097F,
        Language.BENGALI to 0x0980..0x09FF,
        Language.GUJARATI to 0x0A80..0x0AFF,
        Language.ODIA to 0x0B00..0x0B7F,
        Language.TAMIL to 0x0B80..0x0BFF,
        Language.TELUGU to 0x0C00..0x0C7F,
        Language.KANNADA to 0x0C80..0x0CFF,
        Language.MALAYALAM to 0x0D00..0x0D7F,
        Language.ENGLISH to 0x0000..0x007F,
    )

    /**
     * Characters allowed in any language: space, the zero-width joiners that Indic
     * shaping needs, and the shared danda.
     */
    private val universallyAllowed = setOf(' ', '‌', '‍', '।', '॥')

    @Test
    fun `every number lexicon is written in its own script`() {
        for (language in Language.entries) {
            val lex = NumberLexicon.forLanguage(language)
            assertEquals(language, lex.language, "lexicon returned for the wrong language")
            val strings = lex.units + lex.hundreds +
                listOf(lex.thousand, lex.lakh, lex.crore, lex.decimalPoint, lex.negative)
            assertInScript(language, strings, "NumberLexicon")
        }
    }

    @Test
    fun `every abbreviation expansion is written in its own script`() {
        for (language in Language.entries) {
            val lex = AbbreviationLexicon.forLanguage(language)
            assertEquals(language, lex.language, "lexicon returned for the wrong language")
            // Keys stay Latin on purpose -- they match the written source text. Only
            // what is handed to the synthesiser has to be in the target script.
            val strings = lex.abbreviations.values + lex.months + lex.units.values +
                listOf(lex.hourWord, lex.minuteWord, lex.rupees, lex.paise)
            assertInScript(language, strings, "AbbreviationLexicon")
        }
    }

    @Test
    fun `no lexicon string is blank or padded`() {
        for (language in Language.entries) {
            val nlex = NumberLexicon.forLanguage(language)
            val alex = AbbreviationLexicon.forLanguage(language)
            val strings = nlex.units + nlex.hundreds + alex.months + alex.units.values +
                alex.abbreviations.values
            for (s in strings) {
                assertTrue(s.isNotBlank(), "$language has a blank lexicon entry")
                assertEquals(s.trim(), s, "$language entry '$s' has surrounding whitespace")
            }
        }
    }

    @Test
    fun `number tables have no duplicate entries below one hundred`() {
        // A duplicate means two different numbers are spoken identically, which is a
        // copy-paste slip rather than a legitimate linguistic collision.
        for (language in Language.entries) {
            val units = NumberLexicon.forLanguage(language).units
            val duplicates = units.groupBy { it }.filterValues { it.size > 1 }.keys
            assertTrue(
                duplicates.isEmpty(),
                "$language repeats these spoken numbers: $duplicates",
            )
        }
    }

    @Test
    fun `spelling a realistic message produces only pronounceable characters`() {
        // Exercises the whole normaliser, not just the tables: a date, a time, an
        // amount, a measurement, an abbreviation and a bare number in one string.
        val message = "NDRF 15/08/2025 14:30 Rs. 2500 12 km"
        for (language in Language.entries) {
            val spoken = TextNormalizer(language).normalize(message)
            assertInScript(language, listOf(spoken), "normalised output")
        }
    }

    private fun assertInScript(language: Language, strings: List<String>, what: String) {
        val block = blocks.getValue(language)
        for (s in strings) {
            for (ch in s) {
                if (ch in universallyAllowed) continue
                // Digits and punctuation carry no script and are handled upstream.
                if (!ch.isLetter() && !ch.isMark()) continue
                if (ch.code !in block) {
                    fail(
                        "$what for $language contains '$ch' (U+%04X) from outside the %s block, ".format(
                            ch.code,
                            language.englishName,
                        ) + "in the entry \"$s\". A voice for this language cannot pronounce it " +
                            "and the character will be dropped during synthesis.",
                    )
                }
            }
        }
    }

    /** Combining marks are not letters but are just as script-specific. */
    private fun Char.isMark(): Boolean = when (Character.getType(this).toByte()) {
        Character.NON_SPACING_MARK,
        Character.COMBINING_SPACING_MARK,
        Character.ENCLOSING_MARK,
        -> true
        else -> false
    }
}
