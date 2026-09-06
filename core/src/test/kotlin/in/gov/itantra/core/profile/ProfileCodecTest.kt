package `in`.gov.itantra.core.profile

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileCodecTest {

    @Test
    fun `round trips name and thumbnail`() {
        val thumb = ByteArray(32) { it.toByte() }
        val encoded = ProfileCodec.encode("  Ravi  ", thumb)
        val decoded = ProfileCodec.decode(encoded)!!
        assertEquals("Ravi", decoded.name)
        assertTrue(decoded.photoPresent)
        assertContentEquals(thumb, decoded.thumbnailJpeg)
    }

    @Test
    fun `name only when thumbnail omitted`() {
        val decoded = ProfileCodec.decode(ProfileCodec.encode("Anita", null))!!
        assertEquals("Anita", decoded.name)
        assertFalsePhoto(decoded)
    }

    @Test
    fun `oversized thumbnail is dropped on encode`() {
        val huge = ByteArray(ProfileRules.MAX_THUMB_BYTES + 1)
        val decoded = ProfileCodec.decode(ProfileCodec.encode("Ravi", huge))!!
        assertEquals("Ravi", decoded.name)
        assertFalsePhoto(decoded)
    }

    @Test
    fun `blank name does not encode into a usable profile`() {
        assertNull(ProfileCodec.decode(ProfileCodec.encode("   ", null)))
    }

    @Test
    fun `garbage payload is ignored`() {
        assertNull(ProfileCodec.decode(ByteArray(0)))
        assertNull(ProfileCodec.decode(byteArrayOf(99, 0, 0)))
    }

    private fun assertFalsePhoto(profile: OperatorProfile) {
        assertEquals(false, profile.photoPresent)
        assertEquals(null, profile.thumbnailJpeg)
    }
}
