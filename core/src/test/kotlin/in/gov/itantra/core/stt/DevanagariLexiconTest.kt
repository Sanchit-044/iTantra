package `in`.gov.itantra.core.stt

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.chat.QuickChat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DevanagariLexiconTest {

    private val lid = ScriptLanguageId()
    private val devanagari = setOf(Language.HINDI, Language.MARATHI)

    @Test
    fun `marathi copula wins over hindi`() {
        assertEquals(Language.MARATHI, DevanagariLexicon.disambiguate("पाणी वाढत आहे"))
        assertEquals(Language.MARATHI, DevanagariLexicon.disambiguate("मला मदतीची गरज आहे"))
    }

    @Test
    fun `hindi copula wins over marathi`() {
        assertEquals(Language.HINDI, DevanagariLexicon.disambiguate("पानी बढ़ रहा है"))
        assertEquals(Language.HINDI, DevanagariLexicon.disambiguate("मुझे मदद चाहिए"))
    }

    @Test
    fun `the letter LLA alone is enough for marathi`() {
        // No function words at all, just ळ — the letter Hindi does not use.
        assertEquals(Language.MARATHI, DevanagariLexicon.disambiguate("जवळ"))
    }

    @Test
    fun `text with no evidence either way is undecided`() {
        assertNull(DevanagariLexicon.disambiguate("डॉक्टर"))
        assertNull(DevanagariLexicon.disambiguate(""))
    }

    @Test
    fun `marathi is reachable when hindi is also installed`() {
        // The regression this guards: script scoring ties Hindi and Marathi on every
        // Devanagari character, and the old tie-break returned Hindi unconditionally.
        assertEquals(
            Language.MARATHI,
            lid.detectFromText("सर्वजण सुरक्षित आहेत", devanagari),
        )
    }

    @Test
    fun `hindi still wins when the text carries no marathi evidence`() {
        assertEquals(
            Language.HINDI,
            lid.detectFromText("डॉक्टर", devanagari),
        )
    }

    @Test
    fun `every marathi alert template is detected as marathi`() {
        for (template in AlertTemplate.entries) {
            assertEquals(
                Language.MARATHI,
                lid.detectFromText(template.phrase(Language.MARATHI), devanagari),
                "${template.name} in Marathi should not be detected as Hindi",
            )
        }
    }

    @Test
    fun `every hindi alert template is detected as hindi`() {
        for (template in AlertTemplate.entries) {
            assertEquals(
                Language.HINDI,
                lid.detectFromText(template.phrase(Language.HINDI), devanagari),
                "${template.name} in Hindi should not be detected as Marathi",
            )
        }
    }

    @Test
    fun `quick chats are attributed to the language they were written in`() {
        for (chat in QuickChat.entries) {
            assertEquals(
                Language.MARATHI,
                lid.detectFromText(chat.phrase(Language.MARATHI), devanagari),
                "${chat.name} in Marathi should not be detected as Hindi",
            )
            assertEquals(
                Language.HINDI,
                lid.detectFromText(chat.phrase(Language.HINDI), devanagari),
                "${chat.name} in Hindi should not be detected as Marathi",
            )
        }
    }

    @Test
    fun `marathi is never returned when it is not installed`() {
        assertEquals(
            Language.HINDI,
            lid.detectFromText("पाणी वाढत आहे", setOf(Language.HINDI, Language.TAMIL)),
        )
    }

    @Test
    fun `other scripts are unaffected by the tie-break`() {
        assertEquals(
            Language.TAMIL,
            lid.detectFromText("தண்ணீர் உயர்கிறது", setOf(Language.HINDI, Language.MARATHI, Language.TAMIL)),
        )
    }
}
