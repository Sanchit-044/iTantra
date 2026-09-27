package `in`.gov.itantra.core.tts

import `in`.gov.itantra.core.Language

/**
 * Number tables for the languages added after the Hindi / Tamil / Bengali prototype.
 *
 * ## Why these are not placeholders any more
 *
 * These tables previously held generated ASCII tokens -- `mr-n-five`, `or-t-three`,
 * `hajar` -- on the theory that a unique placeholder was safer than a blank slot. It is
 * not. [TextNormalizer] rewrites every digit into one of these strings before the text
 * reaches the synthesiser, and [in.gov.itantra.android.tts.VitsTokenizer] can only emit
 * characters that exist in that voice's vocabulary. A Devanagari or Odia voice has no
 * Latin letters at all, so `or-n-five` was reduced to its two hyphens and the number
 * simply vanished from the utterance. Any sentence containing a digit, a time, a date
 * or an amount came out truncated or silent.
 *
 * Every string here is therefore written in the language's own script. The
 * `LexiconScriptTest` in this module enforces that mechanically, so a Latin placeholder
 * can never be reintroduced unnoticed.
 *
 * NOTE FOR REVIEW: as with the Tamil and Bengali tables in [NumberLexicon], these were
 * authored without native-speaker review and must be validated before any public demo.
 * They are plain data and isolated here precisely so a correction needs no code change.
 * See docs/LEXICON-REVIEW.md.
 */
internal object NumberLexiconExtras {

    // -------------------------------------------------------------------- English

    private val EN_ONES = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    )
    private val EN_TEENS = listOf(
        "ten", "eleven", "twelve", "thirteen", "fourteen",
        "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val EN_TENS = listOf(
        "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
    )

    val ENGLISH = NumberLexicon(
        language = Language.ENGLISH,
        units = NumberLexicon.composeUnits(EN_ONES, EN_TEENS, EN_TENS, EN_TENS),
        hundreds = (1..9).map { "${EN_ONES[it]} hundred" },
        thousand = "thousand",
        lakh = "lakh",
        crore = "crore",
        decimalPoint = "point",
        negative = "minus",
    )

    // -------------------------------------------------------------------- Marathi

    // Marathi 21..99 is irregular in the same way Hindi is -- the forms cannot be
    // composed from a ten plus a unit -- so the table is written out in full.
    private val MR_UNITS = listOf(
        "शून्य", "एक", "दोन", "तीन", "चार", "पाच", "सहा", "सात", "आठ", "नऊ",
        "दहा", "अकरा", "बारा", "तेरा", "चौदा", "पंधरा", "सोळा", "सतरा", "अठरा", "एकोणीस",
        "वीस", "एकवीस", "बावीस", "तेवीस", "चोवीस", "पंचवीस", "सव्वीस", "सत्तावीस", "अठ्ठावीस", "एकोणतीस",
        "तीस", "एकतीस", "बत्तीस", "तेहतीस", "चौतीस", "पस्तीस", "छत्तीस", "सदतीस", "अडतीस", "एकोणचाळीस",
        "चाळीस", "एक्केचाळीस", "बेचाळीस", "त्रेचाळीस", "चव्वेचाळीस", "पंचेचाळीस", "सेहेचाळीस", "सत्तेचाळीस", "अठ्ठेचाळीस", "एकोणपन्नास",
        "पन्नास", "एक्कावन्न", "बावन्न", "त्रेपन्न", "चौपन्न", "पंचावन्न", "छप्पन्न", "सत्तावन्न", "अठ्ठावन्न", "एकोणसाठ",
        "साठ", "एकसष्ट", "बासष्ट", "त्रेसष्ट", "चौसष्ट", "पासष्ट", "सहासष्ट", "सदुसष्ट", "अडुसष्ट", "एकोणसत्तर",
        "सत्तर", "एकाहत्तर", "बाहत्तर", "त्र्याहत्तर", "चौऱ्याहत्तर", "पंच्याहत्तर", "शहात्तर", "सत्त्याहत्तर", "अठ्ठ्याहत्तर", "एकोणऐंशी",
        "ऐंशी", "एक्याऐंशी", "ब्याऐंशी", "त्र्याऐंशी", "चौऱ्याऐंशी", "पंच्याऐंशी", "शहाऐंशी", "सत्त्याऐंशी", "अठ्ठ्याऐंशी", "एकोणनव्वद",
        "नव्वद", "एक्याण्णव", "ब्याण्णव", "त्र्याण्णव", "चौऱ्याण्णव", "पंच्याण्णव", "शहाण्णव", "सत्त्याण्णव", "अठ्ठ्याण्णव", "नव्व्याण्णव",
    )

    val MARATHI = NumberLexicon(
        language = Language.MARATHI,
        units = MR_UNITS,
        hundreds = listOf(
            "शंभर", "दोनशे", "तीनशे", "चारशे", "पाचशे", "सहाशे", "सातशे", "आठशे", "नऊशे",
        ),
        thousand = "हजार",
        lakh = "लाख",
        crore = "कोटी",
        decimalPoint = "दशांश",
        negative = "उणे",
    )

    // ----------------------------------------------------------------------- Odia

    private val OR_UNITS = listOf(
        "ଶୂନ୍ୟ", "ଏକ", "ଦୁଇ", "ତିନି", "ଚାରି", "ପାଞ୍ଚ", "ଛଅ", "ସାତ", "ଆଠ", "ନଅ",
        "ଦଶ", "ଏଗାର", "ବାର", "ତେର", "ଚଉଦ", "ପନ୍ଦର", "ଷୋହଳ", "ସତର", "ଅଠର", "ଊଣେଇଶ",
        "କୋଡ଼ିଏ", "ଏକୋଇଶ", "ବାଇଶ", "ତେଇଶ", "ଚବିଶ", "ପଚିଶ", "ଛବିଶ", "ସତାଇଶ", "ଅଠାଇଶ", "ଅଣତିରିଶ",
        "ତିରିଶ", "ଏକତିରିଶ", "ବତିଶ", "ତେତିଶ", "ଚଉତିରିଶ", "ପଇଁତିରିଶ", "ଛତିଶ", "ସଇଁତିରିଶ", "ଅଠତିରିଶ", "ଅଣଚାଳିଶ",
        "ଚାଳିଶ", "ଏକଚାଳିଶ", "ବୟାଳିଶ", "ତେୟାଳିଶ", "ଚଉରାଳିଶ", "ପଞ୍ଚଚାଳିଶ", "ଛୟାଳିଶ", "ସତଚାଳିଶ", "ଅଠଚାଳିଶ", "ଅଣଚାଶ",
        "ପଚାଶ", "ଏକାବନ", "ବାଉନ", "ତେପନ", "ଚଉବନ", "ପଞ୍ଚାବନ", "ଛପନ", "ସତାବନ", "ଅଠାବନ", "ଅଣଷଠି",
        "ଷାଠିଏ", "ଏକଷଠି", "ବାଷଠି", "ତେଷଠି", "ଚଉଷଠି", "ପଞ୍ଚଷଠି", "ଛଅଷଠି", "ସତଷଠି", "ଅଠଷଠି", "ଅଣସ୍ତରୀ",
        "ସତୁରୀ", "ଏକସ୍ତରୀ", "ବାସ୍ତରୀ", "ତେସ୍ତରୀ", "ଚଉସ୍ତରୀ", "ପଞ୍ଚସ୍ତରୀ", "ଛଅସ୍ତରୀ", "ସତସ୍ତରୀ", "ଅଠସ୍ତରୀ", "ଅଣାଅଶୀ",
        "ଅଶୀ", "ଏକାଅଶୀ", "ବୟାଅଶୀ", "ତେୟାଅଶୀ", "ଚଉରାଅଶୀ", "ପଞ୍ଚାଅଶୀ", "ଛୟାଅଶୀ", "ସତାଅଶୀ", "ଅଠାଅଶୀ", "ଅଣାନବେ",
        "ନବେ", "ଏକାନବେ", "ବୟାନବେ", "ତେୟାନବେ", "ଚଉରାନବେ", "ପଞ୍ଚାନବେ", "ଛୟାନବେ", "ସତାନବେ", "ଅଠାନବେ", "ଅଣାଶହେ",
    )

    val ODIA = NumberLexicon(
        language = Language.ODIA,
        units = OR_UNITS,
        hundreds = listOf(
            "ଶହେ", "ଦୁଇଶହ", "ତିନିଶହ", "ଚାରିଶହ", "ପାଞ୍ଚଶହ", "ଛଅଶହ", "ସାତଶହ", "ଆଠଶହ", "ନଅଶହ",
        ),
        thousand = "ହଜାର",
        lakh = "ଲକ୍ଷ",
        crore = "କୋଟି",
        decimalPoint = "ଦଶମିକ",
        negative = "ଋଣ",
    )

    // ------------------------------------------------------------------- Gujarati

    private val GU_UNITS = listOf(
        "શૂન્ય", "એક", "બે", "ત્રણ", "ચાર", "પાંચ", "છ", "સાત", "આઠ", "નવ",
        "દસ", "અગિયાર", "બાર", "તેર", "ચૌદ", "પંદર", "સોળ", "સત્તર", "અઢાર", "ઓગણીસ",
        "વીસ", "એકવીસ", "બાવીસ", "તેવીસ", "ચોવીસ", "પચ્ચીસ", "છવ્વીસ", "સત્તાવીસ", "અઠ્ઠાવીસ", "ઓગણત્રીસ",
        "ત્રીસ", "એકત્રીસ", "બત્રીસ", "તેત્રીસ", "ચોત્રીસ", "પાંત્રીસ", "છત્રીસ", "સાડત્રીસ", "આડત્રીસ", "ઓગણચાલીસ",
        "ચાલીસ", "એકતાલીસ", "બેતાલીસ", "તેતાલીસ", "ચુમ્માલીસ", "પિસ્તાલીસ", "છેતાલીસ", "સુડતાલીસ", "અડતાલીસ", "ઓગણપચાસ",
        "પચાસ", "એકાવન", "બાવન", "ત્રેપન", "ચોપન", "પંચાવન", "છપ્પન", "સત્તાવન", "અઠ્ઠાવન", "ઓગણસાઠ",
        "સાઠ", "એકસઠ", "બાસઠ", "ત્રેસઠ", "ચોસઠ", "પાંસઠ", "છાસઠ", "સડસઠ", "અડસઠ", "ઓગણસિત્તેર",
        "સિત્તેર", "એકોતેર", "બોતેર", "ત્રોતેર", "ચુમોતેર", "પંચોતેર", "છોતેર", "સિત્યોતેર", "ઇઠ્યોતેર", "ઓગણાએંસી",
        "એંસી", "એક્યાસી", "બ્યાસી", "ત્યાસી", "ચોર્યાસી", "પંચ્યાસી", "છ્યાસી", "સિત્યાસી", "ઇઠ્યાસી", "નેવ્યાસી",
        "નેવું", "એકાણું", "બાણું", "ત્રાણું", "ચોરાણું", "પંચાણું", "છન્નું", "સત્તાણું", "અઠ્ઠાણું", "નવ્વાણું",
    )

    val GUJARATI = NumberLexicon(
        language = Language.GUJARATI,
        units = GU_UNITS,
        hundreds = listOf(
            "સો", "બસો", "ત્રણસો", "ચારસો", "પાંચસો", "છસો", "સાતસો", "આઠસો", "નવસો",
        ),
        thousand = "હજાર",
        lakh = "લાખ",
        crore = "કરોડ",
        decimalPoint = "દશાંશ",
        negative = "ઋણ",
    )

    // -------------------------------------------------------------------- Kannada

    // Kannada composes 21..99 regularly from a combining ten plus a unit.
    private val KN_ONES = listOf(
        "ಸೊನ್ನೆ", "ಒಂದು", "ಎರಡು", "ಮೂರು", "ನಾಲ್ಕು", "ಐದು", "ಆರು", "ಏಳು", "ಎಂಟು", "ಒಂಬತ್ತು",
    )
    private val KN_TEENS = listOf(
        "ಹತ್ತು", "ಹನ್ನೊಂದು", "ಹನ್ನೆರಡು", "ಹದಿಮೂರು", "ಹದಿನಾಲ್ಕು",
        "ಹದಿನೈದು", "ಹದಿನಾರು", "ಹದಿನೇಳು", "ಹದಿನೆಂಟು", "ಹತ್ತೊಂಬತ್ತು",
    )
    private val KN_TENS = listOf(
        "ಇಪ್ಪತ್ತು", "ಮೂವತ್ತು", "ನಲವತ್ತು", "ಐವತ್ತು", "ಅರವತ್ತು", "ಎಪ್ಪತ್ತು", "ಎಂಬತ್ತು", "ತೊಂಬತ್ತು",
    )
    private val KN_TENS_COMBINING = listOf(
        "ಇಪ್ಪತ್ತ", "ಮೂವತ್ತ", "ನಲವತ್ತ", "ಐವತ್ತ", "ಅರವತ್ತ", "ಎಪ್ಪತ್ತ", "ಎಂಬತ್ತ", "ತೊಂಬತ್ತ",
    )

    val KANNADA = NumberLexicon(
        language = Language.KANNADA,
        units = NumberLexicon.composeUnits(KN_ONES, KN_TEENS, KN_TENS, KN_TENS_COMBINING),
        hundreds = listOf(
            "ನೂರು", "ಇನ್ನೂರು", "ಮುನ್ನೂರು", "ನಾನ್ನೂರು", "ಐನೂರು",
            "ಆರುನೂರು", "ಏಳುನೂರು", "ಎಂಟುನೂರು", "ಒಂಬೈನೂರು",
        ),
        thousand = "ಸಾವಿರ",
        lakh = "ಲಕ್ಷ",
        crore = "ಕೋಟಿ",
        decimalPoint = "ದಶಮಾಂಶ",
        negative = "ಋಣ",
    )

    // ------------------------------------------------------------------ Malayalam

    private val ML_ONES = listOf(
        "പൂജ്യം", "ഒന്ന്", "രണ്ട്", "മൂന്ന്", "നാല്", "അഞ്ച്", "ആറ്", "ഏഴ്", "എട്ട്", "ഒമ്പത്",
    )
    private val ML_TEENS = listOf(
        "പത്ത്", "പതിനൊന്ന്", "പന്ത്രണ്ട്", "പതിമൂന്ന്", "പതിനാല്",
        "പതിനഞ്ച്", "പതിനാറ്", "പതിനേഴ്", "പതിനെട്ട്", "പത്തൊമ്പത്",
    )
    private val ML_TENS = listOf(
        "ഇരുപത്", "മുപ്പത്", "നാല്പത്", "അമ്പത്", "അറുപത്", "എഴുപത്", "എൺപത്", "തൊണ്ണൂറ്",
    )
    private val ML_TENS_COMBINING = listOf(
        "ഇരുപത്തി", "മുപ്പത്തി", "നാല്പത്തി", "അമ്പത്തി",
        "അറുപത്തി", "എഴുപത്തി", "എൺപത്തി", "തൊണ്ണൂറ്റി",
    )

    val MALAYALAM = NumberLexicon(
        language = Language.MALAYALAM,
        units = NumberLexicon.composeUnits(ML_ONES, ML_TEENS, ML_TENS, ML_TENS_COMBINING),
        hundreds = listOf(
            "നൂറ്", "ഇരുനൂറ്", "മുന്നൂറ്", "നാനൂറ്", "അഞ്ഞൂറ്",
            "അറുനൂറ്", "എഴുനൂറ്", "എണ്ണൂറ്", "തൊള്ളായിരം",
        ),
        thousand = "ആയിരം",
        lakh = "ലക്ഷം",
        crore = "കോടി",
        decimalPoint = "ദശാംശം",
        negative = "ന്യൂനം",
    )

    // --------------------------------------------------------------------- Telugu

    private val TE_ONES = listOf(
        "సున్నా", "ఒకటి", "రెండు", "మూడు", "నాలుగు", "ఐదు", "ఆరు", "ఏడు", "ఎనిమిది", "తొమ్మిది",
    )
    private val TE_TEENS = listOf(
        "పది", "పదకొండు", "పన్నెండు", "పదమూడు", "పద్నాలుగు",
        "పదిహేను", "పదహారు", "పదిహేడు", "పద్దెనిమిది", "పంతొమ్మిది",
    )
    private val TE_TENS = listOf(
        "ఇరవై", "ముప్పై", "నలభై", "యాభై", "అరవై", "డెబ్బై", "ఎనభై", "తొంభై",
    )

    val TELUGU = NumberLexicon(
        language = Language.TELUGU,
        // Telugu writes 21 as "ఇరవై ఒకటి" -- the standalone ten is also the combining
        // form, so the same table serves both slots.
        units = NumberLexicon.composeUnits(TE_ONES, TE_TEENS, TE_TENS, TE_TENS),
        hundreds = listOf(
            "వంద", "రెండు వందలు", "మూడు వందలు", "నాలుగు వందలు", "ఐదు వందలు",
            "ఆరు వందలు", "ఏడు వందలు", "ఎనిమిది వందలు", "తొమ్మిది వందలు",
        ),
        thousand = "వెయ్యి",
        lakh = "లక్ష",
        crore = "కోటి",
        decimalPoint = "దశాంశం",
        negative = "రుణ",
    )
}
