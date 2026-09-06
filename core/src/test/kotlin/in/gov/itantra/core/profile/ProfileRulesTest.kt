package `in`.gov.itantra.core.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileRulesTest {

    @Test
    fun `blank name is incomplete even with a photo`() {
        assertFalse(ProfileRules.isComplete("   ", photoPresent = true))
        assertFalse(ProfileRules.isComplete("", photoPresent = true))
    }

    @Test
    fun `name without photo is incomplete`() {
        assertFalse(ProfileRules.isComplete("Ravi", photoPresent = false))
    }

    @Test
    fun `trimmed name and photo is complete`() {
        assertTrue(ProfileRules.isComplete("  Ravi  Kumar  ", photoPresent = true))
    }

    @Test
    fun `name is trimmed collapsed and capped`() {
        assertEquals("Ravi Kumar", ProfileRules.normalizeName("  Ravi   Kumar  "))
        val long = "a".repeat(ProfileRules.MAX_NAME_CHARS + 10)
        assertEquals(ProfileRules.MAX_NAME_CHARS, ProfileRules.normalizeName(long).length)
    }

    @Test
    fun `display name falls back when empty`() {
        assertEquals(OperatorProfile.FALLBACK_NAME, OperatorProfile(name = "  ").displayName)
        assertEquals("Ravi", OperatorProfile(name = "Ravi").displayName)
    }

    @Test
    fun `complete only after recorded save with name and photo`() {
        assertFalse(
            OperatorProfile(name = "Ravi", photoPresent = true, recorded = false).isComplete,
        )
        assertTrue(
            OperatorProfile(name = "Ravi", photoPresent = true, recorded = true).isComplete,
        )
        assertFalse(
            OperatorProfile(name = "Ravi", photoPresent = false, recorded = true).isComplete,
        )
    }
}
