package `in`.gov.itantra.core.chat

import `in`.gov.itantra.core.Language

/**
 * Short conversational phrases for the Talk screen's quick-chat chips.
 *
 * Unlike most UI chrome ([in.gov.itantra.core.lang.UiStrings], whose catalog covers
 * only English/Hindi/Tamil/Bengali and falls back to English for the other six
 * supported languages), these need every language: the text is both what the
 * operator sees and what actually gets sent, tagged with their active speaking
 * language and spoken by the receiver's TTS. An English fallback here silently
 * sends the literal English word tagged as e.g. Marathi -- not just a wrong menu
 * label. Same reasoning and pattern as [in.gov.itantra.core.alert.AlertTemplate].
 */
enum class QuickChat {
    YES, NO, OKAY, NEED_HELP, WAIT;

    fun phrase(language: Language): String = when (this) {
        YES -> when (language) {
            Language.HINDI -> "हाँ"
            Language.TAMIL -> "ஆம்"
            Language.BENGALI -> "হ্যাঁ"
            Language.GUJARATI -> "હા"
            Language.MARATHI -> "हो"
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
            Language.ODIA -> "ଠିକ୍ ଅଛି"
            Language.ENGLISH -> "Okay"
        }
        NEED_HELP -> when (language) {
            Language.HINDI -> "मदद चाहिए"
            Language.TAMIL -> "உதவி வேண்டும்"
            Language.BENGALI -> "সাহায্য দরকার"
            Language.GUJARATI -> "મદદ જોઈએ છે"
            Language.MARATHI -> "मदत हवी आहे"
            Language.KANNADA -> "ಸಹಾಯ ಬೇಕಾಗಿದೆ"
            Language.MALAYALAM -> "സഹായം വേണം"
            Language.TELUGU -> "సహాయం కావాలి"
            Language.ODIA -> "ସାହାଯ୍ୟ ଦରକାର"
            Language.ENGLISH -> "Need help"
        }
        WAIT -> when (language) {
            Language.HINDI -> "रुकिए"
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
