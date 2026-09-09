package `in`.gov.itantra.core.stt

import `in`.gov.itantra.core.Language
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    // ------------------------------------------------------- Hindi versus Marathi

    private val both = setOf(Language.HINDI, Language.MARATHI)

    @Test
    fun `marathi is detected from its function words`() {
        // "I need help" -- मला and आहे are Marathi; neither is a Hindi word.
        assertEquals(
            Language.MARATHI,
            lid.detectFromText("मला मदतीची गरज आहे", both),
        )
    }

    @Test
    fun `hindi is detected from its function words`() {
        assertEquals(
            Language.HINDI,
            lid.detectFromText("मुझे मदद की जरूरत है", both),
        )
    }

    @Test
    fun `marathi is detected from letters hindi does not use`() {
        // ळ carries the signal; there is no Hindi function word to offset it.
        assertEquals(
            Language.MARATHI,
            lid.detectFromText("पाऊस सुरू झाला आहे", both),
        )
    }

    @Test
    fun `ambiguous devanagari does not guess`() {
        // Written entirely in letters and words common to both languages. The previous
        // implementation answered Hindi here and silently rewrote the user's setting.
        assertNull(lid.detectFromText("पाणी", both))
    }

    @Test
    fun `marathi is returned when hindi is not installed`() {
        assertEquals(
            Language.MARATHI,
            lid.detectFromText("पाणी वाढत आहे", setOf(Language.MARATHI, Language.TAMIL)),
        )
    }

    @Test
    fun `hindi is returned when marathi is not installed`() {
        assertEquals(
            Language.HINDI,
            lid.detectFromText("पानी बढ़ रहा है", setOf(Language.HINDI, Language.TAMIL)),
        )
    }

    @Test
    fun `marathi text does not switch a marathi user to hindi`() {
        // The regression this whole change exists to prevent: a Marathi speaker with
        // Hindi also installed must never be moved off Marathi by their own speech.
        val marathiUtterances = listOf(
            "मला मदत हवी आहे",
            "पाणी वाढत आहे",
            "सर्वजण सुरक्षित आहेत",
            "इथे डॉक्टरांची गरज आहे",
            "रस्ता बंद आहे",
        )
        for (text in marathiUtterances) {
            val refined = lid.detectFromText(text, both)
            assertTrue(
                refined != Language.HINDI,
                "Marathi utterance \"$text\" was misread as Hindi",
            )
        }
    }

    // ------------------------------------------------------------- general safety

    @Test
    fun `too little text is not enough to switch language`() {
        assertNull(lid.detectFromText("अ", both))
        assertNull(lid.detectFromText("7", setOf(Language.HINDI, Language.ENGLISH)))
        assertNull(lid.detectFromText("!!", setOf(Language.HINDI, Language.ENGLISH)))
    }

    @Test
    fun `digits and punctuation alone never pick a language`() {
        assertNull(lid.detectFromText("15/08/2025 14:30", setOf(Language.HINDI, Language.ENGLISH)))
    }

    @Test
    fun `heavily mixed script is treated as unsure`() {
        // One Latin loanword inside a Tamil sentence must not flip the handset to English.
        assertEquals(
            Language.TAMIL,
            lid.detectFromText("தண்ணீர் உயர்கிறது ok", setOf(Language.TAMIL, Language.ENGLISH)),
        )
        // Genuinely half-and-half text is not a reliable signal either way.
        assertNull(
            lid.detectFromText("help தண்", setOf(Language.TAMIL, Language.ENGLISH)),
        )
    }

    @Test
    fun `empty candidate set is handled`() {
        assertNull(lid.detectFromText("पाणी वाढत आहे", emptySet()))
        assertNull(lid.detect(emptySet(), pcm = null))
    }

    @Test
    fun `native digits do not count as script evidence`() {
        // Devanagari digits are typed for any language and say nothing about which.
        assertNull(lid.detectFromText("१२३४५", both))
    }

    @Test
    fun `each indic script resolves to its own language`() {
        val all = Language.entries.toSet()
        assertEquals(Language.BENGALI, lid.detectFromText("পানি বাড়ছে", all))
        assertEquals(Language.GUJARATI, lid.detectFromText("પાણી વધી રહ્યું છે", all))
        assertEquals(Language.ODIA, lid.detectFromText("ପାଣି ବଢୁଛି", all))
        assertEquals(Language.TAMIL, lid.detectFromText("தண்ணீர் உயர்கிறது", all))
        assertEquals(Language.TELUGU, lid.detectFromText("నీరు పెరుగుతోంది", all))
        assertEquals(Language.KANNADA, lid.detectFromText("ನೀರು ಏರುತ್ತಿದೆ", all))
        assertEquals(Language.MALAYALAM, lid.detectFromText("വെള്ളം ഉയരുന്നു", all))
        assertEquals(Language.ENGLISH, lid.detectFromText("the water is rising", all))
    }
}
