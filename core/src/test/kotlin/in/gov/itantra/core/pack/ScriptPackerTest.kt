package `in`.gov.itantra.core.pack

import `in`.gov.itantra.core.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ScriptPackerTest {

    @Test
    fun `packs and unpacks Hindi Devanagari text losslessly`() {
        val original = "नमस्ते दुनिया और सहायता चाहिए"
        val packed = ScriptPacker.pack(original, Language.HINDI)
        val unpacked = ScriptPacker.unpack(packed, Language.HINDI)

        assertEquals(original, unpacked)
        assertTrue(packed.size < original.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `packs and unpacks Tamil text losslessly`() {
        val original = "வணக்கம் உலகம் உதவி தேவை"
        val packed = ScriptPacker.pack(original, Language.TAMIL)
        val unpacked = ScriptPacker.unpack(packed, Language.TAMIL)

        assertEquals(original, unpacked)
        assertTrue(packed.size < original.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `preserves ASCII characters and digits`() {
        val original = "Alert 102 SOS"
        val packed = ScriptPacker.pack(original, Language.HINDI)
        val unpacked = ScriptPacker.unpack(packed, Language.HINDI)

        assertEquals(original, unpacked)
    }

    @Test
    fun `handles mixed Indic and Latin script`() {
        val original = "Unit 4: आपातकालीन स्थिति!"
        val packed = ScriptPacker.pack(original, Language.HINDI)
        val unpacked = ScriptPacker.unpack(packed, Language.HINDI)

        assertEquals(original, unpacked)
    }
}
