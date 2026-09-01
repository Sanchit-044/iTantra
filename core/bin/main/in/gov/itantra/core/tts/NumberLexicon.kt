package `in`.gov.itantra.core.tts

import `in`.gov.itantra.core.Language

/**
 * Per-language spoken forms for numbers.
 *
 * All three target languages group by the Indian system (hundred / thousand / lakh /
 * crore), so [IndicNumberFormatter] can share one grouping algorithm. What differs is
 * the 0..99 vocabulary, which is irregular in Hindi and Bengali and semi-compositional
 * in Tamil, plus Tamil's irregular hundreds.
 *
 * NOTE FOR REVIEW: the Hindi table is high confidence. The Tamil and Bengali tables
 * were authored without a native-speaker review and MUST be validated by one before
 * any demo. They are isolated here, as plain data, precisely so that correcting them
 * requires no code change. See docs/LEXICON-REVIEW.md.
 */
class NumberLexicon(
    val language: Language,
    /** Spoken form of 0..99, exactly 100 entries. */
    val units: List<String>,
    /** Spoken form of 100, 200 .. 900, exactly 9 entries (index 0 == 100). */
    val hundreds: List<String>,
    val thousand: String,
    val lakh: String,
    val crore: String,
    val decimalPoint: String,
    val negative: String,
) {
    init {
        require(units.size == 100) { "$language units table must have 100 entries, had ${units.size}" }
        require(hundreds.size == 9) { "$language hundreds table must have 9 entries, had ${hundreds.size}" }
    }

    companion object {

        // ---------------------------------------------------------------- Hindi

        private val HINDI_UNITS = listOf(
            "शून्य", "एक", "दो", "तीन", "चार", "पाँच", "छह", "सात", "आठ", "नौ",
            "दस", "ग्यारह", "बारह", "तेरह", "चौदह", "पंद्रह", "सोलह", "सत्रह", "अठारह", "उन्नीस",
            "बीस", "इक्कीस", "बाईस", "तेईस", "चौबीस", "पच्चीस", "छब्बीस", "सत्ताईस", "अट्ठाईस", "उनतीस",
            "तीस", "इकतीस", "बत्तीस", "तैंतीस", "चौंतीस", "पैंतीस", "छत्तीस", "सैंतीस", "अड़तीस", "उनतालीस",
            "चालीस", "इकतालीस", "बयालीस", "तैंतालीस", "चौवालीस", "पैंतालीस", "छियालीस", "सैंतालीस", "अड़तालीस", "उनचास",
            "पचास", "इक्यावन", "बावन", "तिरेपन", "चौवन", "पचपन", "छप्पन", "सत्तावन", "अट्ठावन", "उनसठ",
            "साठ", "इकसठ", "बासठ", "तिरेसठ", "चौंसठ", "पैंसठ", "छियासठ", "सड़सठ", "अड़सठ", "उनहत्तर",
            "सत्तर", "इकहत्तर", "बहत्तर", "तिहत्तर", "चौहत्तर", "पचहत्तर", "छिहत्तर", "सतहत्तर", "अठहत्तर", "उन्यासी",
            "अस्सी", "इक्यासी", "बयासी", "तिरासी", "चौरासी", "पचासी", "छियासी", "सत्तासी", "अट्ठासी", "नवासी",
            "नब्बे", "इक्यानवे", "बानवे", "तिरानवे", "चौरानवे", "पचानवे", "छियानवे", "सत्तानवे", "अट्ठानवे", "निन्यानवे",
        )

        val HINDI = NumberLexicon(
            language = Language.HINDI,
            units = HINDI_UNITS,
            // Hindi hundreds are regular: <digit> सौ.
            hundreds = (1..9).map { "${HINDI_UNITS[it]} सौ" },
            thousand = "हज़ार",
            lakh = "लाख",
            crore = "करोड़",
            decimalPoint = "दशमलव",
            negative = "ऋण",
        )

        // -------------------------------------------------------------- Bengali

        private val BENGALI_UNITS = listOf(
            "শূন্য", "এক", "দুই", "তিন", "চার", "পাঁচ", "ছয়", "সাত", "আট", "নয়",
            "দশ", "এগারো", "বারো", "তেরো", "চৌদ্দ", "পনেরো", "ষোলো", "সতেরো", "আঠারো", "উনিশ",
            "বিশ", "একুশ", "বাইশ", "তেইশ", "চব্বিশ", "পঁচিশ", "ছাব্বিশ", "সাতাশ", "আঠাশ", "ঊনত্রিশ",
            "ত্রিশ", "একত্রিশ", "বত্রিশ", "তেত্রিশ", "চৌত্রিশ", "পঁয়ত্রিশ", "ছত্রিশ", "সাঁইত্রিশ", "আটত্রিশ", "ঊনচল্লিশ",
            "চল্লিশ", "একচল্লিশ", "বিয়াল্লিশ", "তেতাল্লিশ", "চুয়াল্লিশ", "পঁয়তাল্লিশ", "ছেচল্লিশ", "সাতচল্লিশ", "আটচল্লিশ", "ঊনপঞ্চাশ",
            "পঞ্চাশ", "একান্ন", "বাহান্ন", "তিপ্পান্ন", "চুয়ান্ন", "পঞ্চান্ন", "ছাপ্পান্ন", "সাতান্ন", "আটান্ন", "ঊনষাট",
            "ষাট", "একষট্টি", "বাষট্টি", "তেষট্টি", "চৌষট্টি", "পঁয়ষট্টি", "ছেষট্টি", "সাতষট্টি", "আটষট্টি", "ঊনসত্তর",
            "সত্তর", "একাত্তর", "বাহাত্তর", "তিয়াত্তর", "চুয়াত্তর", "পঁচাত্তর", "ছিয়াত্তর", "সাতাত্তর", "আটাত্তর", "ঊনআশি",
            "আশি", "একাশি", "বিরাশি", "তিরাশি", "চুরাশি", "পঁচাশি", "ছিয়াশি", "সাতাশি", "আটাশি", "ঊননব্বই",
            "নব্বই", "একানব্বই", "বিরানব্বই", "তিরানব্বই", "চুরানব্বই", "পঁচানব্বই", "ছিয়ানব্বই", "সাতানব্বই", "আটানব্বই", "নিরানব্বই",
        )

        val BENGALI = NumberLexicon(
            language = Language.BENGALI,
            units = BENGALI_UNITS,
            hundreds = (1..9).map { "${BENGALI_UNITS[it]}শো" },
            thousand = "হাজার",
            lakh = "লাখ",
            crore = "কোটি",
            decimalPoint = "দশমিক",
            negative = "ঋণাত্মক",
        )

        // ---------------------------------------------------------------- Tamil

        private val TAMIL_ONES = listOf(
            "சுழியம்", "ஒன்று", "இரண்டு", "மூன்று", "நான்கு",
            "ஐந்து", "ஆறு", "ஏழு", "எட்டு", "ஒன்பது",
        )

        private val TAMIL_TEENS = listOf(
            "பத்து", "பதினொன்று", "பன்னிரண்டு", "பதிமூன்று", "பதினான்கு",
            "பதினைந்து", "பதினாறு", "பதினேழு", "பதினெட்டு", "பத்தொன்பது",
        )

        /** Standalone tens: 20, 30 .. 90. */
        private val TAMIL_TENS = listOf(
            "இருபது", "முப்பது", "நாற்பது", "ஐம்பது",
            "அறுபது", "எழுபது", "எண்பது", "தொண்ணூறு",
        )

        /** Combining tens used before a unit: 21 == இருபத்தி ஒன்று. */
        private val TAMIL_TENS_COMBINING = listOf(
            "இருபத்தி", "முப்பத்தி", "நாற்பத்தி", "ஐம்பத்தி",
            "அறுபத்தி", "எழுபத்தி", "எண்பத்தி", "தொண்ணூற்றி",
        )

        // Tamil composes 21..99 regularly from a combining ten plus a unit, unlike the
        // fully irregular Hindi and Bengali tables, so it is generated rather than typed.
        private val TAMIL_UNITS: List<String> = buildList(100) {
            addAll(TAMIL_ONES)
            addAll(TAMIL_TEENS)
            for (ten in 2..9) {
                for (unit in 0..9) {
                    add(
                        if (unit == 0) TAMIL_TENS[ten - 2]
                        else "${TAMIL_TENS_COMBINING[ten - 2]} ${TAMIL_ONES[unit]}"
                    )
                }
            }
        }

        val TAMIL = NumberLexicon(
            language = Language.TAMIL,
            units = TAMIL_UNITS,
            // Tamil hundreds are irregular and cannot be composed from the units table.
            hundreds = listOf(
                "நூறு", "இருநூறு", "முந்நூறு", "நானூறு", "ஐந்நூறு",
                "அறுநூறு", "எழுநூறு", "எண்ணூறு", "தொள்ளாயிரம்",
            ),
            thousand = "ஆயிரம்",
            lakh = "லட்சம்",
            crore = "கோடி",
            decimalPoint = "புள்ளி",
            negative = "கழித்தல்",
        )

        fun forLanguage(language: Language): NumberLexicon = when (language) {
            Language.HINDI -> HINDI
            Language.TAMIL -> TAMIL
            Language.BENGALI -> BENGALI
        }
    }
}
