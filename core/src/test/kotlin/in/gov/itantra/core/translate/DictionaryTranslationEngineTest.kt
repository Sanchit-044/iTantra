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
    fun `known hindi phrase becomes tamil`() {
        assertEquals(
            "எனக்கு உதவி வேண்டும்",
            engine.translate("मुझे मदद चाहिए", Language.HINDI, Language.TAMIL),
        )
    }

    @Test
    fun `unknown sentence does not feed the wrong tts voice`() {
        assertFailsWith<TranslationUnavailableException> {
            engine.translate("यह एक अज्ञात वाक्य है", Language.HINDI, Language.TAMIL)
        }
    }
}
