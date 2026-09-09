package `in`.gov.itantra.core.chat

import `in`.gov.itantra.core.Language

/**
 * The canned one-tap replies on the Talk screen.
 *
 * These exist in [Language] form rather than as literals in the UI for two reasons.
 * The text is tagged with the sender's current language on the wire, so an English
 * literal sent by a Hindi operator would arrive labelled Hindi and be fed to a Hindi
 * TTS voice. And because every phrase is enumerated here, the offline phrase table can
 * guarantee a translation for all of them — see
 * `in.gov.itantra.core.translate.DictionaryTranslationEngine`.
 *
 * [NEED_HELP] deliberately reuses the wording of the NEED_HELP concept in that table
 * so the two sources agree on a single string per language.
 */
enum class QuickChat {
    YES,
    NO,
    OKAY,
    NEED_HELP,
    WAIT,
    ;

    fun phrase(language: Language): String = when (this) {
        YES -> when (language) {
            Language.HINDI -> "हाँ"
            Language.TAMIL -> "ஆம்"
            Language.BENGALI -> "হ্যাঁ"
            Language.GUJARATI -> "હા"
            Language.MARATHI -> "होय"
            Language.KANNADA -> "ಹೌದು"
            Language.MALAYALAM -> "അതെ"
            Language.TELUGU -> "అవును"
            Language.ODIA -> "ହଁ"
            Language.ENGLISH -> "Yes"
        }
        NO -> when (language) {
            Language.HINDI -> "नहीं"
            Language.TAMIL -> "இல்லை"
            Language.BENGALI -> "না"
            Language.GUJARATI -> "ના"
            Language.MARATHI -> "नाही"
            Language.KANNADA -> "ಇಲ್ಲ"
            Language.MALAYALAM -> "ഇല്ല"
            Language.TELUGU -> "కాదు"
            Language.ODIA -> "ନା"
            Language.ENGLISH -> "No"
        }
        OKAY -> when (language) {
            Language.HINDI -> "ठीक है"
            Language.TAMIL -> "சரி"
            Language.BENGALI -> "ঠিক আছে"
            Language.GUJARATI -> "બરાબર"
            Language.MARATHI -> "ठीक आहे"
            Language.KANNADA -> "ಸರಿ"
            Language.MALAYALAM -> "ശരി"
            Language.TELUGU -> "సరే"
            Language.ODIA -> "ଠିକ ଅଛି"
            Language.ENGLISH -> "Okay"
        }
        NEED_HELP -> when (language) {
            Language.HINDI -> "मुझे मदद चाहिए"
            Language.TAMIL -> "எனக்கு உதவி வேண்டும்"
            Language.BENGALI -> "আমার সাহায্য দরকার"
            Language.GUJARATI -> "મને મદદ જોઈએ છે"
            Language.MARATHI -> "मला मदतीची गरज आहे"
            Language.KANNADA -> "ನನಗೆ ಸಹಾಯ ಬೇಕು"
            Language.MALAYALAM -> "എനിക്ക് സഹായം വേണം"
            Language.TELUGU -> "నాకు సహాయం కావాలి"
            Language.ODIA -> "ମୋତେ ସାହାଯ୍ୟ ଦରକାର"
            Language.ENGLISH -> "I need help"
        }
        WAIT -> when (language) {
            Language.HINDI -> "रुकें"
            Language.TAMIL -> "காத்திருங்கள்"
            Language.BENGALI -> "অপেক্ষা করুন"
            Language.GUJARATI -> "રાહ જુઓ"
            Language.MARATHI -> "थांबा"
            Language.KANNADA -> "ಕಾಯಿರಿ"
            Language.MALAYALAM -> "കാത്തിരിക്കൂ"
            Language.TELUGU -> "వేచి ఉండండి"
            Language.ODIA -> "ଅପେକ୍ଷା କରନ୍ତୁ"
            Language.ENGLISH -> "Wait"
        }
    }
}
