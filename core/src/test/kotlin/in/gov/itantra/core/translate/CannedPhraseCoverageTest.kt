package `in`.gov.itantra.core.translate

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.chat.QuickChat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Every phrase the app can send with one tap must survive a cross-language receive.
 *
 * Free speech can legitimately fall outside the offline phrase table — that is what
 * [TranslationUnavailableException] is for. A canned phrase cannot: the operator did
 * not type it, the app did, so failing to translate it is the app's own fault and
 * shows up as an error on the receiver for no reason the operator can act on.
 */
class CannedPhraseCoverageTest {

    private val engine = DictionaryTranslationEngine()

    /** Every language pair, both directions, excluding same-language no-ops. */
    private fun eachPair(block: (source: Language, target: Language) -> Unit) {
        for (source in Language.entries) {
            for (target in Language.entries) {
                if (source != target) block(source, target)
            }
        }
    }

    @Test
    fun `every alert template translates across all 90 language pairs`() {
        for (template in AlertTemplate.entries) {
            eachPair { source, target ->
                val sent = template.phrase(source)
                val received = try {
                    engine.translate(sent, source, target)
                } catch (e: TranslationUnavailableException) {
                    fail("${template.name}: $source -> $target has no entry for \"$sent\"")
                }
                assertTrue(
                    received.isNotBlank(),
                    "${template.name}: $source -> $target translated to a blank string",
                )
                assertEquals(
                    template.phrase(target),
                    received,
                    "${template.name}: $source -> $target should land on the target-language phrase",
                )
            }
        }
    }

    @Test
    fun `every quick chat translates across all 90 language pairs`() {
        for (chat in QuickChat.entries) {
            eachPair { source, target ->
                val sent = chat.phrase(source)
                val received = try {
                    engine.translate(sent, source, target)
                } catch (e: TranslationUnavailableException) {
                    fail("${chat.name}: $source -> $target has no entry for \"$sent\"")
                }
                assertEquals(
                    chat.phrase(target),
                    received,
                    "${chat.name}: $source -> $target should land on the target-language phrase",
                )
            }
        }
    }

    @Test
    fun `canned phrases survive punctuation and casing the way free text does`() {
        val hindi = AlertTemplate.ALL_CLEAR.phrase(Language.HINDI)
        assertEquals(
            AlertTemplate.ALL_CLEAR.phrase(Language.TAMIL),
            engine.translate("  $hindi।  ", Language.HINDI, Language.TAMIL),
        )
    }

    @Test
    fun `free text is still rejected rather than mistranslated`() {
        // The guarantee above is about canned phrases only. Arbitrary speech must
        // keep failing loudly instead of reaching the wrong TTS voice.
        kotlin.test.assertFailsWith<TranslationUnavailableException> {
            engine.translate("यह एक अज्ञात वाक्य है", Language.HINDI, Language.TAMIL)
        }
    }

    @Test
    fun `no canned phrase mixes scripts from two languages`() {
        // Guards the class of bug where a Gujarati cluster was pasted into the Telugu
        // string for "Everyone is safe": it renders as tofu and skews script-based
        // language ID toward the wrong language.
        val phrases = buildList {
            for (language in Language.entries) {
                AlertTemplate.entries.forEach { add(language to it.phrase(language)) }
                QuickChat.entries.forEach { add(language to it.phrase(language)) }
                DictionaryTranslationEngine.DEFAULT_PHRASES
                    .filterKeys { it.target == language }
                    .values
                    .forEach { add(language to it) }
            }
        }
        for ((language, text) in phrases) {
            val block = INDIC_BLOCKS[language] ?: continue
            for (ch in text) {
                val c = ch.code
                // Only judge characters that live in an Indic block at all; ASCII,
                // spaces, the danda and dashes are shared punctuation.
                if (c < FIRST_INDIC || c > LAST_INDIC) continue
                assertTrue(
                    c in block,
                    "${language.englishName} phrase \"$text\" contains U+%04X, ".format(c) +
                        "which is outside its script block",
                )
            }
        }
    }

    private companion object {
        const val FIRST_INDIC = 0x0900
        const val LAST_INDIC = 0x0D7F

        /** Hindi and Marathi share Devanagari; English has no Indic block. */
        val INDIC_BLOCKS: Map<Language, IntRange> = mapOf(
            Language.HINDI to 0x0900..0x097F,
            Language.MARATHI to 0x0900..0x097F,
            Language.BENGALI to 0x0980..0x09FF,
            Language.GUJARATI to 0x0A80..0x0AFF,
            Language.ODIA to 0x0B00..0x0B7F,
            Language.TAMIL to 0x0B80..0x0BFF,
            Language.TELUGU to 0x0C00..0x0C7F,
            Language.KANNADA to 0x0C80..0x0CFF,
            Language.MALAYALAM to 0x0D00..0x0D7F,
        )
    }
}
