package `in`.gov.itantra.core.tts

import `in`.gov.itantra.core.Language

/**
 * Abbreviation, month and unit tables for the languages added after the
 * Hindi / Tamil / Bengali prototype.
 *
 * ## Why these exist
 *
 * These six languages previously shared the English lexicon via a `copyFor(language)`
 * call. That looked harmless -- the strings were at least real words -- but it produced
 * silence in practice. [TextNormalizer] substitutes these expansions into the text
 * before synthesis, and a Devanagari, Odia, Kannada, Malayalam, Telugu or Gujarati VITS
 * voice has no Latin characters in its vocabulary at all, so "National Disaster
 * Response Force" was dropped character by character and "NDRF" was simply not spoken.
 * Every month name and every measurement unit failed the same way.
 *
 * The keys stay Latin -- they match against what is written in the incoming text -- but
 * every expansion is in the target script. `LexiconScriptTest` enforces this.
 *
 * NOTE FOR REVIEW: authored without native-speaker review; validate before a public
 * demo. Plain data, so corrections need no code change. See docs/LEXICON-REVIEW.md.
 */
internal object AbbreviationLexiconExtras {

    val MARATHI = AbbreviationLexicon(
        language = Language.MARATHI,
        abbreviations = mapOf(
            "NDRF" to "राष्ट्रीय आपत्ती प्रतिसाद दल",
            "SDRF" to "राज्य आपत्ती प्रतिसाद दल",
            "NDMA" to "राष्ट्रीय आपत्ती व्यवस्थापन प्राधिकरण",
            "IMD" to "भारतीय हवामान विभाग",
            "ISRO" to "भारतीय अंतराळ संशोधन संस्था",
            "IST" to "भारतीय प्रमाण वेळ",
            "SOS" to "आणीबाणी संदेश",
            "GPS" to "जी पी एस",
            "Dr." to "डॉक्टर",
            "No." to "क्रमांक",
            "approx." to "अंदाजे",
            "etc." to "इत्यादी",
        ),
        months = listOf(
            "जानेवारी", "फेब्रुवारी", "मार्च", "एप्रिल", "मे", "जून",
            "जुलै", "ऑगस्ट", "सप्टेंबर", "ऑक्टोबर", "नोव्हेंबर", "डिसेंबर",
        ),
        units = mapOf(
            "km" to "किलोमीटर", "km/h" to "किलोमीटर प्रति तास", "kmph" to "किलोमीटर प्रति तास",
            "m" to "मीटर", "cm" to "सेंटीमीटर", "mm" to "मिलिमीटर",
            "kg" to "किलोग्रॅम", "g" to "ग्रॅम", "mg" to "मिलिग्रॅम",
            "l" to "लिटर", "ml" to "मिलिलिटर",
            "hr" to "तास", "hrs" to "तास", "min" to "मिनिट", "sec" to "सेकंद",
        ),
        hourWord = "वाजता",
        minuteWord = "मिनिटे",
        rupees = "रुपये",
        paise = "पैसे",
    )

    val ODIA = AbbreviationLexicon(
        language = Language.ODIA,
        abbreviations = mapOf(
            "NDRF" to "ଜାତୀୟ ବିପର୍ଯ୍ୟୟ ପ୍ରତିକ୍ରିୟା ବାହିନୀ",
            "SDRF" to "ରାଜ୍ୟ ବିପର୍ଯ୍ୟୟ ପ୍ରତିକ୍ରିୟା ବାହିନୀ",
            "NDMA" to "ଜାତୀୟ ବିପର୍ଯ୍ୟୟ ପରିଚାଳନା କର୍ତ୍ତୃପକ୍ଷ",
            "IMD" to "ଭାରତୀୟ ପାଣିପାଗ ବିଭାଗ",
            "ISRO" to "ଭାରତୀୟ ମହାକାଶ ଗବେଷଣା ସଂସ୍ଥା",
            "IST" to "ଭାରତୀୟ ମାନକ ସମୟ",
            "SOS" to "ଜରୁରୀ ସନ୍ଦେଶ",
            "GPS" to "ଜି ପି ଏସ",
            "Dr." to "ଡାକ୍ତର",
            "No." to "କ୍ରମାଙ୍କ",
            "approx." to "ପ୍ରାୟ",
            "etc." to "ଇତ୍ୟାଦି",
        ),
        months = listOf(
            "ଜାନୁଆରୀ", "ଫେବୃଆରୀ", "ମାର୍ଚ୍ଚ", "ଅପ୍ରେଲ", "ମେ", "ଜୁନ",
            "ଜୁଲାଇ", "ଅଗଷ୍ଟ", "ସେପ୍ଟେମ୍ବର", "ଅକ୍ଟୋବର", "ନଭେମ୍ବର", "ଡିସେମ୍ବର",
        ),
        units = mapOf(
            "km" to "କିଲୋମିଟର", "km/h" to "ଘଣ୍ଟା ପ୍ରତି କିଲୋମିଟର", "kmph" to "ଘଣ୍ଟା ପ୍ରତି କିଲୋମିଟର",
            "m" to "ମିଟର", "cm" to "ସେଣ୍ଟିମିଟର", "mm" to "ମିଲିମିଟର",
            "kg" to "କିଲୋଗ୍ରାମ", "g" to "ଗ୍ରାମ", "mg" to "ମିଲିଗ୍ରାମ",
            "l" to "ଲିଟର", "ml" to "ମିଲିଲିଟର",
            "hr" to "ଘଣ୍ଟା", "hrs" to "ଘଣ୍ଟା", "min" to "ମିନିଟ", "sec" to "ସେକେଣ୍ଡ",
        ),
        hourWord = "ଘଟିକା",
        minuteWord = "ମିନିଟ",
        rupees = "ଟଙ୍କା",
        paise = "ପଇସା",
    )

    val GUJARATI = AbbreviationLexicon(
        language = Language.GUJARATI,
        abbreviations = mapOf(
            "NDRF" to "રાષ્ટ્રીય આપત્તિ પ્રતિભાવ દળ",
            "SDRF" to "રાજ્ય આપત્તિ પ્રતિભાવ દળ",
            "NDMA" to "રાષ્ટ્રીય આપત્તિ વ્યવસ્થાપન સત્તામંડળ",
            "IMD" to "ભારતીય હવામાન વિભાગ",
            "ISRO" to "ભારતીય અંતરિક્ષ સંશોધન સંસ્થા",
            "IST" to "ભારતીય પ્રમાણ સમય",
            "SOS" to "કટોકટી સંદેશ",
            "GPS" to "જી પી એસ",
            "Dr." to "ડૉક્ટર",
            "No." to "નંબર",
            "approx." to "આશરે",
            "etc." to "વગેરે",
        ),
        months = listOf(
            "જાન્યુઆરી", "ફેબ્રુઆરી", "માર્ચ", "એપ્રિલ", "મે", "જૂન",
            "જુલાઈ", "ઓગસ્ટ", "સપ્ટેમ્બર", "ઓક્ટોબર", "નવેમ્બર", "ડિસેમ્બર",
        ),
        units = mapOf(
            "km" to "કિલોમીટર", "km/h" to "કિલોમીટર પ્રતિ કલાક", "kmph" to "કિલોમીટર પ્રતિ કલાક",
            "m" to "મીટર", "cm" to "સેન્ટીમીટર", "mm" to "મિલીમીટર",
            "kg" to "કિલોગ્રામ", "g" to "ગ્રામ", "mg" to "મિલીગ્રામ",
            "l" to "લિટર", "ml" to "મિલીલિટર",
            "hr" to "કલાક", "hrs" to "કલાક", "min" to "મિનિટ", "sec" to "સેકન્ડ",
        ),
        hourWord = "વાગ્યે",
        minuteWord = "મિનિટ",
        rupees = "રૂપિયા",
        paise = "પૈસા",
    )

    val KANNADA = AbbreviationLexicon(
        language = Language.KANNADA,
        abbreviations = mapOf(
            "NDRF" to "ರಾಷ್ಟ್ರೀಯ ವಿಪತ್ತು ಸ್ಪಂದನ ಪಡೆ",
            "SDRF" to "ರಾಜ್ಯ ವಿಪತ್ತು ಸ್ಪಂದನ ಪಡೆ",
            "NDMA" to "ರಾಷ್ಟ್ರೀಯ ವಿಪತ್ತು ನಿರ್ವಹಣಾ ಪ್ರಾಧಿಕಾರ",
            "IMD" to "ಭಾರತೀಯ ಹವಾಮಾನ ಇಲಾಖೆ",
            "ISRO" to "ಭಾರತೀಯ ಬಾಹ್ಯಾಕಾಶ ಸಂಶೋಧನಾ ಸಂಸ್ಥೆ",
            "IST" to "ಭಾರತೀಯ ಪ್ರಮಾಣ ಸಮಯ",
            "SOS" to "ತುರ್ತು ಸಂದೇಶ",
            "GPS" to "ಜಿ ಪಿ ಎಸ್",
            "Dr." to "ವೈದ್ಯರು",
            "No." to "ಸಂಖ್ಯೆ",
            "approx." to "ಸುಮಾರು",
            "etc." to "ಇತ್ಯಾದಿ",
        ),
        months = listOf(
            "ಜನವರಿ", "ಫೆಬ್ರವರಿ", "ಮಾರ್ಚ್", "ಏಪ್ರಿಲ್", "ಮೇ", "ಜೂನ್",
            "ಜುಲೈ", "ಆಗಸ್ಟ್", "ಸೆಪ್ಟೆಂಬರ್", "ಅಕ್ಟೋಬರ್", "ನವೆಂಬರ್", "ಡಿಸೆಂಬರ್",
        ),
        units = mapOf(
            "km" to "ಕಿಲೋಮೀಟರ್", "km/h" to "ಗಂಟೆಗೆ ಕಿಲೋಮೀಟರ್", "kmph" to "ಗಂಟೆಗೆ ಕಿಲೋಮೀಟರ್",
            "m" to "ಮೀಟರ್", "cm" to "ಸೆಂಟಿಮೀಟರ್", "mm" to "ಮಿಲಿಮೀಟರ್",
            "kg" to "ಕಿಲೋಗ್ರಾಂ", "g" to "ಗ್ರಾಂ", "mg" to "ಮಿಲಿಗ್ರಾಂ",
            "l" to "ಲೀಟರ್", "ml" to "ಮಿಲಿಲೀಟರ್",
            "hr" to "ಗಂಟೆ", "hrs" to "ಗಂಟೆ", "min" to "ನಿಮಿಷ", "sec" to "ಸೆಕೆಂಡ್",
        ),
        hourWord = "ಗಂಟೆ",
        minuteWord = "ನಿಮಿಷ",
        rupees = "ರೂಪಾಯಿ",
        paise = "ಪೈಸೆ",
    )

    val MALAYALAM = AbbreviationLexicon(
        language = Language.MALAYALAM,
        abbreviations = mapOf(
            "NDRF" to "ദേശീയ ദുരന്ത നിവാരണ സേന",
            "SDRF" to "സംസ്ഥാന ദുരന്ത നിവാരണ സേന",
            "NDMA" to "ദേശീയ ദുരന്ത നിവാരണ അതോറിറ്റി",
            "IMD" to "ഇന്ത്യൻ കാലാവസ്ഥാ വകുപ്പ്",
            "ISRO" to "ഇന്ത്യൻ ബഹിരാകാശ ഗവേഷണ സംഘടന",
            "IST" to "ഇന്ത്യൻ സ്റ്റാൻഡേർഡ് സമയം",
            "SOS" to "അടിയന്തര സന്ദേശം",
            "GPS" to "ജി പി എസ്",
            "Dr." to "ഡോക്ടർ",
            "No." to "നമ്പർ",
            "approx." to "ഏകദേശം",
            "etc." to "തുടങ്ങിയവ",
        ),
        months = listOf(
            "ജനുവരി", "ഫെബ്രുവരി", "മാർച്ച്", "ഏപ്രിൽ", "മേയ്", "ജൂൺ",
            "ജൂലൈ", "ഓഗസ്റ്റ്", "സെപ്റ്റംബർ", "ഒക്ടോബർ", "നവംബർ", "ഡിസംബർ",
        ),
        units = mapOf(
            "km" to "കിലോമീറ്റർ", "km/h" to "മണിക്കൂറിൽ കിലോമീറ്റർ", "kmph" to "മണിക്കൂറിൽ കിലോമീറ്റർ",
            "m" to "മീറ്റർ", "cm" to "സെന്റിമീറ്റർ", "mm" to "മില്ലിമീറ്റർ",
            "kg" to "കിലോഗ്രാം", "g" to "ഗ്രാം", "mg" to "മില്ലിഗ്രാം",
            "l" to "ലിറ്റർ", "ml" to "മില്ലിലിറ്റർ",
            "hr" to "മണിക്കൂർ", "hrs" to "മണിക്കൂർ", "min" to "മിനിറ്റ്", "sec" to "സെക്കൻഡ്",
        ),
        hourWord = "മണി",
        minuteWord = "മിനിറ്റ്",
        rupees = "രൂപ",
        paise = "പൈസ",
    )

    val TELUGU = AbbreviationLexicon(
        language = Language.TELUGU,
        abbreviations = mapOf(
            "NDRF" to "జాతీయ విపత్తు స్పందన దళం",
            "SDRF" to "రాష్ట్ర విపత్తు స్పందన దళం",
            "NDMA" to "జాతీయ విపత్తు నిర్వహణ సంస్థ",
            "IMD" to "భారత వాతావరణ శాఖ",
            "ISRO" to "భారత అంతరిక్ష పరిశోధన సంస్థ",
            "IST" to "భారత ప్రామాణిక సమయం",
            "SOS" to "అత్యవసర సందేశం",
            "GPS" to "జి పి ఎస్",
            "Dr." to "డాక్టర్",
            "No." to "సంఖ్య",
            "approx." to "సుమారు",
            "etc." to "మొదలైనవి",
        ),
        months = listOf(
            "జనవరి", "ఫిబ్రవరి", "మార్చి", "ఏప్రిల్", "మే", "జూన్",
            "జూలై", "ఆగస్టు", "సెప్టెంబర్", "అక్టోబర్", "నవంబర్", "డిసెంబర్",
        ),
        units = mapOf(
            "km" to "కిలోమీటర్", "km/h" to "గంటకు కిలోమీటర్", "kmph" to "గంటకు కిలోమీటర్",
            "m" to "మీటర్", "cm" to "సెంటీమీటర్", "mm" to "మిల్లీమీటర్",
            "kg" to "కిలోగ్రాము", "g" to "గ్రాము", "mg" to "మిల్లీగ్రాము",
            "l" to "లీటరు", "ml" to "మిల్లీలీటరు",
            "hr" to "గంట", "hrs" to "గంటలు", "min" to "నిమిషం", "sec" to "సెకను",
        ),
        hourWord = "గంటలకు",
        minuteWord = "నిమిషాలు",
        rupees = "రూపాయలు",
        paise = "పైసలు",
    )
}
