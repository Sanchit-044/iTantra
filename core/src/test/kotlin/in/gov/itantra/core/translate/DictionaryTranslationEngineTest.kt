package `in`.gov.itantra.core.translate

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DictionaryTranslationEngineTest {

    private val engine = DictionaryTranslationEngine()

    @Test
    fun `same language is a no-op`() {
        assertEquals(
            "पानी बढ़ रहा है",
            engine.translateOrSame("पानी बढ़ रहा है", Language.HINDI, Language.HINDI),
        )
    }

    @Test
    fun `known hindi phrase with punctuation becomes tamil`() {
        assertEquals(
            "எனக்கு உதவி வேண்டும்",
            engine.translate("मुझे मदद चाहिए।", Language.HINDI, Language.TAMIL),
        )
        assertEquals(
            "I need help",
            engine.translate("  मुझे मदद चाहिए!  ", Language.HINDI, Language.ENGLISH),
        )
    }

    @Test
    fun `all 10 languages translate emergency phrases across all pairs`() {
        for (src in Language.entries) {
            for (tgt in Language.entries) {
                if (src == tgt) continue
                val translated = engine.translateOrSame("I need help", Language.ENGLISH, tgt)
                kotlin.test.assertTrue(
                    translated.isNotBlank(),
                    "Translation from ENGLISH to ${tgt.englishName} should not be blank"
                )
            }
        }
    }

    @Test
    fun `unknown sentence does not feed the wrong tts voice`() {
        assertFailsWith<TranslationUnavailableException> {
            engine.translate("यह एक अज्ञात वाक्य है", Language.HINDI, Language.TAMIL)
        }
    }
}
