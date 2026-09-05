package `in`.gov.itantra.core.tts

import `in`.gov.itantra.core.Language

/**
 * Per-language spoken expansions for abbreviations, month names and measurement units.
 *
 * The abbreviation set is scoped to the operational vocabulary this app actually
 * carries -- disaster response, rescue coordination, position reporting -- rather than
 * being a general-purpose dictionary. Entries are matched case-insensitively on whole
 * tokens only.
 *
 * NOTE FOR REVIEW: as with [NumberLexicon], the Tamil and Bengali strings need
 * native-speaker validation. They are data, not code, so corrections are cheap.
 */
class AbbreviationLexicon(
    val language: Language,
    val abbreviations: Map<String, String>,
    /** Month names, January first, exactly 12 entries. */
    val months: List<String>,
    /** Spoken names for measurement unit tokens, keyed by the written form. */
    val units: Map<String, String>,
    val hourWord: String,
    val minuteWord: String,
    val rupees: String,
    val paise: String,
) {
    init {
        require(months.size == 12) { "$language needs 12 month names, had ${months.size}" }
    }

    /** Longest-first so a longer abbreviation is never shadowed by a shorter prefix. */
    val sortedAbbreviations: List<Pair<String, String>> =
        abbreviations.entries
            .sortedByDescending { it.key.length }
            .map { it.key to it.value }

    companion object {

        val HINDI = AbbreviationLexicon(
            language = Language.HINDI,
            abbreviations = mapOf(
                "NDRF" to "राष्ट्रीय आपदा मोचन बल",
                "SDRF" to "राज्य आपदा मोचन बल",
                "NDMA" to "राष्ट्रीय आपदा प्रबंधन प्राधिकरण",
                "IMD" to "भारत मौसम विज्ञान विभाग",
                "ISRO" to "भारतीय अंतरिक्ष अनुसंधान संगठन",
                "IST" to "भारतीय मानक समय",
                "SOS" to "आपातकालीन संदेश",
                "GPS" to "जी पी एस",
                "Dr." to "डॉक्टर",
                "No." to "नंबर",
                "approx." to "लगभग",
                "etc." to "इत्यादि",
            ),
            months = listOf(
                "जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून",
                "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर",
            ),
            units = mapOf(
                "km" to "किलोमीटर", "km/h" to "किलोमीटर प्रति घंटा", "kmph" to "किलोमीटर प्रति घंटा",
                "m" to "मीटर", "cm" to "सेंटीमीटर", "mm" to "मिलीमीटर",
                "kg" to "किलोग्राम", "g" to "ग्राम", "mg" to "मिलीग्राम",
                "l" to "लीटर", "ml" to "मिलीलीटर",
                "hr" to "घंटा", "hrs" to "घंटे", "min" to "मिनट", "sec" to "सेकंड",
            ),
            hourWord = "बजे",
            minuteWord = "मिनट",
            rupees = "रुपये",
            paise = "पैसे",
        )

        val TAMIL = AbbreviationLexicon(
            language = Language.TAMIL,
            abbreviations = mapOf(
                "NDRF" to "தேசிய பேரிடர் மீட்புப் படை",
                "SDRF" to "மாநில பேரிடர் மீட்புப் படை",
                "NDMA" to "தேசிய பேரிடர் மேலாண்மை ஆணையம்",
                "IMD" to "இந்திய வானிலை ஆய்வு மையம்",
                "ISRO" to "இந்திய விண்வெளி ஆராய்ச்சி நிறுவனம்",
                "IST" to "இந்திய நிலையான நேரம்",
                "SOS" to "அவசர செய்தி",
                "GPS" to "ஜி பி எஸ்",
                "Dr." to "மருத்துவர்",
                "No." to "எண்",
                "approx." to "தோராயமாக",
                "etc." to "முதலியன",
            ),
            months = listOf(
                "ஜனவரி", "பிப்ரவரி", "மார்ச்", "ஏப்ரல்", "மே", "ஜூன்",
                "ஜூலை", "ஆகஸ்ட்", "செப்டம்பர்", "அக்டோபர்", "நவம்பர்", "டிசம்பர்",
            ),
            units = mapOf(
                "km" to "கிலோமீட்டர்", "km/h" to "மணிக்கு கிலோமீட்டர்", "kmph" to "மணிக்கு கிலோமீட்டர்",
                "m" to "மீட்டர்", "cm" to "சென்டிமீட்டர்", "mm" to "மில்லிமீட்டர்",
                "kg" to "கிலோகிராம்", "g" to "கிராம்", "mg" to "மில்லிகிராம்",
                "l" to "லிட்டர்", "ml" to "மில்லிலிட்டர்",
                "hr" to "மணி", "hrs" to "மணி", "min" to "நிமிடம்", "sec" to "வினாடி",
            ),
            hourWord = "மணி",
            minuteWord = "நிமிடம்",
            rupees = "ரூபாய்",
            paise = "பைசா",
        )

        val BENGALI = AbbreviationLexicon(
            language = Language.BENGALI,
            abbreviations = mapOf(
                "NDRF" to "জাতীয় বিপর্যয় মোকাবিলা বাহিনী",
                "SDRF" to "রাজ্য বিপর্যয় মোকাবিলা বাহিনী",
                "NDMA" to "জাতীয় বিপর্যয় ব্যবস্থাপনা কর্তৃপক্ষ",
                "IMD" to "ভারতীয় আবহাওয়া দপ্তর",
                "ISRO" to "ভারতীয় মহাকাশ গবেষণা সংস্থা",
                "IST" to "ভারতীয় প্রমাণ সময়",
                "SOS" to "জরুরি বার্তা",
                "GPS" to "জি পি এস",
                "Dr." to "ডাক্তার",
                "No." to "নম্বর",
                "approx." to "আনুমানিক",
                "etc." to "ইত্যাদি",
            ),
            months = listOf(
                "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
                "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর",
            ),
            units = mapOf(
                "km" to "কিলোমিটার", "km/h" to "কিলোমিটার প্রতি ঘণ্টা", "kmph" to "কিলোমিটার প্রতি ঘণ্টা",
                "m" to "মিটার", "cm" to "সেন্টিমিটার", "mm" to "মিলিমিটার",
                "kg" to "কিলোগ্রাম", "g" to "গ্রাম", "mg" to "মিলিগ্রাম",
                "l" to "লিটার", "ml" to "মিলিলিটার",
                "hr" to "ঘণ্টা", "hrs" to "ঘণ্টা", "min" to "মিনিট", "sec" to "সেকেন্ড",
            ),
            hourWord = "টা",
            minuteWord = "মিনিট",
            rupees = "টাকা",
            paise = "পয়সা",
        )

        val ENGLISH = AbbreviationLexicon(
            language = Language.ENGLISH,
            abbreviations = mapOf(
                "NDRF" to "National Disaster Response Force",
                "SDRF" to "State Disaster Response Force",
                "NDMA" to "National Disaster Management Authority",
                "IMD" to "India Meteorological Department",
                "ISRO" to "Indian Space Research Organisation",
                "IST" to "Indian Standard Time",
                "SOS" to "emergency message",
                "GPS" to "G P S",
                "Dr." to "Doctor",
                "No." to "number",
                "approx." to "approximately",
                "etc." to "etcetera",
            ),
            months = listOf(
                "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December",
            ),
            units = mapOf(
                "km" to "kilometers", "km/h" to "kilometers per hour", "kmph" to "kilometers per hour",
                "m" to "meters", "cm" to "centimeters", "mm" to "millimeters",
                "kg" to "kilograms", "g" to "grams", "mg" to "milligrams",
                "l" to "liters", "ml" to "milliliters",
                "hr" to "hour", "hrs" to "hours", "min" to "minutes", "sec" to "seconds",
            ),
            hourWord = "hours",
            minuteWord = "minutes",
            rupees = "rupees",
            paise = "paise",
        )

        fun forLanguage(language: Language): AbbreviationLexicon = when (language) {
            Language.HINDI -> HINDI
            Language.TAMIL -> TAMIL
            Language.BENGALI -> BENGALI
            Language.ENGLISH -> ENGLISH
            Language.GUJARATI,
            Language.MARATHI,
            Language.KANNADA,
            Language.MALAYALAM,
            Language.TELUGU,
            Language.ODIA -> ENGLISH.copyFor(language)
        }

        private fun AbbreviationLexicon.copyFor(language: Language): AbbreviationLexicon =
            AbbreviationLexicon(
                language = language,
                abbreviations = abbreviations,
                months = months,
                units = units,
                hourWord = hourWord,
                minuteWord = minuteWord,
                rupees = rupees,
                paise = paise,
            )
    }
}
