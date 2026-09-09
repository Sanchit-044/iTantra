package `in`.gov.itantra.core.translate

import `in`.gov.itantra.core.Language

/**
 * Small offline phrase table so Hindi ↔ Tamil (and a few other pairs) can be
 * demonstrated without the IndicTrans2 ONNX pack. Unknown sentences throw
 * [TranslationUnavailableException] rather than feeding the wrong TTS voice.
 */
class DictionaryTranslationEngine(
    private val phrases: Map<PhraseKey, String> = DEFAULT_PHRASES,
) : TranslationEngine {

    data class PhraseKey(val source: Language, val target: Language, val text: String)

    override val isAvailable: Boolean = true

    override fun translate(text: String, source: Language, target: Language): String {
        if (source == target) return text
        val key = PhraseKey(source, target, normalize(text))
        return phrases[key]
            ?: throw TranslationUnavailableException(
                "No offline translation for ${source.englishName} → ${target.englishName}. " +
                    "Download the translation pack to hear this in ${target.endonym}."
            )
    }

    companion object {
        private val PUNCTUATION_RE = Regex("[।.,!?\"'\\(\\)\\-:;]")
        private val WHITESPACE_RE = Regex("\\s+")

        fun normalize(text: String): String =
            text.lowercase()
                .replace(PUNCTUATION_RE, "")
                .replace(WHITESPACE_RE, " ")
                .trim()

        private val CONCEPTS: List<Map<Language, String>> = listOf(
            // Concept 1: NEED_HELP
            mapOf(
                Language.HINDI to "मुझे मदद चाहिए",
                Language.TAMIL to "எனக்கு உதவி வேண்டும்",
                Language.BENGALI to "আমার সাহায্য দরকার",
                Language.GUJARATI to "મને મદદ જોઈએ છે",
                Language.MARATHI to "मला मदतीची गरज आहे",
                Language.KANNADA to "ನನಗೆ ಸಹಾಯ ಬೇಕು",
                Language.MALAYALAM to "എനിക്ക് സഹായം വേണം",
                Language.TELUGU to "నాకు సహాయం కావాలి",
                Language.ODIA to "ମୋତେ ସାହାଯ୍ୟ ଦରକାର",
                Language.ENGLISH to "I need help",
            ),
            // Concept 2: WATER_RISING
            mapOf(
                Language.HINDI to "पानी बढ़ रहा है",
                Language.TAMIL to "தண்ணீர் உயர்கிறது",
                Language.BENGALI to "জল বাড়ছে",
                Language.GUJARATI to "પાણી વધી રહ્યું છે",
                Language.MARATHI to "पाणी वाढत आहे",
                Language.KANNADA to "ನೀರು ಏರುತ್ತಿದೆ",
                Language.MALAYALAM to "വെള്ളം പൊങ്ങുന്നു",
                Language.TELUGU to "నీరు పెరుగుతోంది",
                Language.ODIA to "ପାଣି ବଢୁଛି",
                Language.ENGLISH to "The water is rising",
            ),
            // Concept 3: EVERYONE_SAFE
            mapOf(
                Language.HINDI to "सब लोग सुरक्षित हैं",
                Language.TAMIL to "அனைவரும் பாதுகாப்பாக உள்ளனர்",
                Language.BENGALI to "সবাই নিরাপদ",
                Language.GUJARATI to "બધા સલામત છે",
                Language.MARATHI to "सर्वजण सुरक्षित आहेत",
                Language.KANNADA to "ಎಲ್ಲರೂ ಸುರಕ್ಷಿತವಾಗಿದ್ದಾರೆ",
                Language.MALAYALAM to "എല്ലാവരും സുരക്ഷിതരാണ്",
                Language.TELUGU to "అందరూ సురક્ષితంగా ఉన్నారు",
                Language.ODIA to "ସମସ୍ତେ ସୁରକ୍ଷିତ ଅଛନ୍ତି",
                Language.ENGLISH to "Everyone is safe",
            ),
            // Concept 4: DOCTOR_NEEDED
            mapOf(
                Language.HINDI to "डॉक्टर की जरूरत है",
                Language.TAMIL to "மருத்துவர் தேவை",
                Language.BENGALI to "ডাক্তার দরকার",
                Language.GUJARATI to "ડોક્ટરની જરૂર છે",
                Language.MARATHI to "डॉक्टरांची गरज आहे",
                Language.KANNADA to "ವೈದ್ಯರ ಅಗತ್ಯವಿದೆ",
                Language.MALAYALAM to "ഡോക്ടറെ വേണം",
                Language.TELUGU to "డాక్టర్ అవసరం",
                Language.ODIA to "ଡାକ୍ତର ଆବଶ୍ୟକ",
                Language.ENGLISH to "Doctor needed",
            ),
            // Concept 5: EVACUATE_IMMEDIATELY
            mapOf(
                Language.HINDI to "तुरंत बाहर निकलें",
                Language.TAMIL to "உடனடியாக வெளியேறவும்",
                Language.BENGALI to "অবিলম্বে খালি করুন",
                Language.GUJARATI to "તરત જ બહાર નીકળો",
                Language.MARATHI to "त्वरित बाहेर पडा",
                Language.KANNADA to "ತಕ್ಷಣವೇ ತೆರವುಗೊಳಿಸಿ",
                Language.MALAYALAM to "ഉടൻ ഒഴിയുക",
                Language.TELUGU to "వెంటనే ఖాళీ చేయండి",
                Language.ODIA to "ତୁରନ୍ତ ଖାଲି କରନ୍ତୁ",
                Language.ENGLISH to "Evacuate immediately",
            ),
            // Concept 6: SEND_FOOD_WATER
            mapOf(
                Language.HINDI to "खाना और पानी भेजें",
                Language.TAMIL to "உணவு மற்றும் தண்ணீர் அனுப்புங்கள்",
                Language.BENGALI to "খাবার এবং জল পাঠান",
                Language.GUJARATI to "ખોરાક અને પાણી મોકલો",
                Language.MARATHI to "अन्न आणि पाणी पाठवा",
                Language.KANNADA to "ಆಹಾರ ಮತ್ತು ನೀರನ್ನು ಕಳುಹಿಸಿ",
                Language.MALAYALAM to "ഭക്ഷണവും വെള്ളവും അയക്കുക",
                Language.TELUGU to "ఆహారం మరియు నీరు పంపండి",
                Language.ODIA to "ଖାଦ୍ୟ ଏବଂ ପାଣି ପଠାନ୍ତୁ",
                Language.ENGLISH to "Send food and water",
            ),
            // Concept 7: FIRE_OUTBREAK
            mapOf(
                Language.HINDI to "आग लग गई है",
                Language.TAMIL to "தீ விபத்து ஏற்பட்டது",
                Language.BENGALI to "আগুন লেগেছে",
                Language.GUJARATI to "આગ લાગી છે",
                Language.MARATHI to "आग लागली आहे",
                Language.KANNADA to "ಬೆಂಕಿ ಬಿದ್ದಿದೆ",
                Language.MALAYALAM to "തീപിടിത്തമുണ്ടായി",
                Language.TELUGU to "నిప్పు అంటుకుంది",
                Language.ODIA to "ନିଆଁ ଲାଗିଛି",
                Language.ENGLISH to "Fire outbreak",
            ),
            // Concept 8: ROAD_BLOCKED
            mapOf(
                Language.HINDI to "रास्ता बंद है",
                Language.TAMIL to "பாதை அடைக்கப்பட்டுள்ளது",
                Language.BENGALI to "রাস্তা বন্ধ",
                Language.GUJARATI to "રસ્તો બંધ છે",
                Language.MARATHI to "रस्ता बंद आहे",
                Language.KANNADA to "ರಸ್ತೆ ಮುಚ್ಚಲಾಗಿದೆ",
                Language.MALAYALAM to "വഴി അടച്ചിരിക്കുന്നു",
                Language.TELUGU to "దారి మూసివేయబడింది",
                Language.ODIA to "ରାସ୍ତା ବନ୍ଦ ଅଛି",
                Language.ENGLISH to "Road is blocked",
            ),
        )

        val DEFAULT_PHRASES: Map<PhraseKey, String> = buildMap {
            for (concept in CONCEPTS) {
                for ((srcLang, srcText) in concept) {
                    for ((tgtLang, tgtText) in concept) {
                        if (srcLang != tgtLang) {
                            this[PhraseKey(srcLang, tgtLang, normalize(srcText))] = tgtText
                        }
                    }
                }
            }
        }
    }
}
