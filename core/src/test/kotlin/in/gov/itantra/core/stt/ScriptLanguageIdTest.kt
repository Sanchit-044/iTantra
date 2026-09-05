package `in`.gov.itantra.core.stt

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScriptLanguageIdTest {

    private val lid = ScriptLanguageId()

    @Test
    fun `single candidate is returned without looking at text`() {
        assertEquals(
            Language.TAMIL,
            lid.detectFromText("", setOf(Language.TAMIL)),
        )
    }

    @Test
    fun `tamil script is detected only among installed languages`() {
        assertEquals(
            Language.TAMIL,
            lid.detectFromText("தண்ணீர் உயர்கிறது", setOf(Language.HINDI, Language.TAMIL)),
        )
    }

    @Test
    fun `latin text maps to english when english is installed`() {
        assertEquals(
            Language.ENGLISH,
            lid.detectFromText("I need help", setOf(Language.HINDI, Language.ENGLISH)),
        )
    }

    @Test
    fun `a language that is not installed is never returned`() {
        assertNull(
            lid.detectFromText("தண்ணீர்", setOf(Language.HINDI, Language.ENGLISH)),
        )
    }

    @Test
    fun `audio detect is unsure when several candidates exist`() {
        assertNull(lid.detect(setOf(Language.HINDI, Language.TAMIL), pcm = null))
    }

    @Test
    fun `resolve keeps current when lid is unsure`() {
        val spoken = lid.resolveSpokenLanguage(
            installed = setOf(Language.HINDI, Language.TAMIL),
            current = Language.TAMIL,
        )
        assertEquals(Language.TAMIL, spoken)
    }
}
