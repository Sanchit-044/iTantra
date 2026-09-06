package `in`.gov.itantra.core.theme

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {

    @Test
    fun `missing or garbage prefs become system`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("   "))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("sepia"))
    }

    @Test
    fun `stored names are case insensitive`() {
        assertEquals(ThemeMode.DARK, ThemeMode.fromStored("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStored("Light"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStored("SYSTEM"))
    }

    @Test
    fun `system follows the phone`() {
        assertTrue(ThemeMode.SYSTEM.resolveDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.resolveDark(systemDark = false))
    }

    @Test
    fun `forced light stays light when the phone is dark`() {
        assertFalse(ThemeMode.LIGHT.resolveDark(systemDark = true))
        assertFalse(ThemeMode.LIGHT.resolveDark(systemDark = false))
    }

    @Test
    fun `forced dark stays dark when the phone is light`() {
        assertTrue(ThemeMode.DARK.resolveDark(systemDark = false))
        assertTrue(ThemeMode.DARK.resolveDark(systemDark = true))
    }
}
