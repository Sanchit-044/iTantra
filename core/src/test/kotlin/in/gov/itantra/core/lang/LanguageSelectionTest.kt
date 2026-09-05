package `in`.gov.itantra.core.lang

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals

class LanguageSelectionTest {

    @Test
    fun `empty selection falls back to hindi`() {
        assertEquals(setOf(Language.HINDI), LanguageSelection.normalizeInstalled(emptySet()))
    }

    @Test
    fun `current stays when it is still installed`() {
        val installed = setOf(Language.HINDI, Language.TAMIL)
        assertEquals(
            Language.TAMIL,
            LanguageSelection.normalizeCurrent(Language.TAMIL, installed),
        )
    }

    @Test
    fun `current falls back to hindi when it is no longer installed`() {
        assertEquals(
            Language.HINDI,
            LanguageSelection.normalizeCurrent(Language.TAMIL, setOf(Language.HINDI, Language.ENGLISH)),
        )
    }

    @Test
    fun `current falls back to the only remaining language`() {
        assertEquals(
            Language.TAMIL,
            LanguageSelection.normalizeCurrent(Language.HINDI, setOf(Language.TAMIL)),
        )
    }
}
