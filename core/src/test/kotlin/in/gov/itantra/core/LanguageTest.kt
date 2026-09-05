package `in`.gov.itantra.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguageTest {

    @Test
    fun `official set is ten languages including english`() {
        assertEquals(10, Language.entries.size)
        assertTrue(Language.ENGLISH in Language.entries)
        assertEquals(Language.HINDI, Language.DEFAULT)
    }

    @Test
    fun `wire codes are unique and hindi tamil bengali keep original values`() {
        assertEquals(Language.entries.size, Language.entries.map { it.wire }.toSet().size)
        assertEquals(0x01, Language.HINDI.wire)
        assertEquals(0x02, Language.TAMIL.wire)
        assertEquals(0x03, Language.BENGALI.wire)
    }
}
