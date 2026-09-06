package `in`.gov.itantra.core.alert

import `in`.gov.itantra.core.Language

/**
 * The five F-07 alert templates. Each ships as a pre-rendered WAV per language
 * (`alerts/<lang>/<assetKey>.wav`, 15 files).
 */
enum class AlertTemplate(val assetKey: String) {
    EMERGENCY_ASSISTANCE("emergency"),
    ALL_CLEAR("all_clear"),
    EVACUATE_IMMEDIATELY("evacuate"),
    STAY_IN_POSITION("stay_position"),
    MEDICAL_HELP("medical"),
    ;

    fun assetPath(language: Language): String = "alerts/${language.code}/$assetKey.wav"

    fun phrase(language: Language): String = when (this) {
        EMERGENCY_ASSISTANCE -> when (language) {
            Language.HINDI -> "आपातकाल — सहायता चाहिए"
            Language.TAMIL -> "அவசரம் — உதவி தேவை"
            Language.BENGALI -> "জরুরি — সাহায্য চাই"
            Language.GUJARATI -> "કટોકટી — મદદ જોઈએ છે"
            Language.MARATHI -> "आणीबाणी — मदत हवी आहे"
            Language.KANNADA -> "ತುರ್ತು ಪರಿಸ್ಥಿತಿ — ಸಹಾಯ ಬೇಕಾಗಿದೆ"
            Language.MALAYALAM -> "അടിയന്തിരാവസ്ഥ — സഹായം വേണം"
            Language.TELUGU -> "అత్యవసర పరిస్థితి — సహాయం కావాలి"
            Language.ODIA -> "ଜରୁରୀକାଳୀନ — ସାହାଯ୍ୟ ଦରକାର"
            Language.ENGLISH -> "Emergency — Assistance needed"
        }
        ALL_CLEAR -> when (language) {
            Language.HINDI -> "सब ठीक है"
            Language.TAMIL -> "அனைத்தும் பாதுகாப்பு"
            Language.BENGALI -> "সব ঠিক আছে"
            Language.GUJARATI -> "બધું સુરક્ષિત છે"
            Language.MARATHI -> "सर्व काही सुरक्षित आहे"
            Language.KANNADA -> "ಎಲ್ಲವೂ ಸುರಕ್ಷಿತವಾಗಿದೆ"
            Language.MALAYALAM -> "എല്ലാം സുരക്ഷിതമാണ്"
            Language.TELUGU -> "అంతా సురక్షితం"
            Language.ODIA -> "ସବୁ ସୁରକ୍ଷିତ ଅଛି"
            Language.ENGLISH -> "All clear"
        }
        EVACUATE_IMMEDIATELY -> when (language) {
            Language.HINDI -> "तुरंत निकलें"
            Language.TAMIL -> "உடனடியாக வெளியேறுங்கள்"
            Language.BENGALI -> "অবিলম্বে সরিয়ে যান"
            Language.GUJARATI -> "તરત જ બહાર નીકળો"
            Language.MARATHI -> "तातडीने बाहेर पडा"
            Language.KANNADA -> "ತಕ್ಷಣವೇ ತೆರವುಗೊಳಿಸಿ"
            Language.MALAYALAM -> "ഉടൻ പുറത്തുകടക്കുക"
            Language.TELUGU -> "వెంటనే ఖాళీ చేయండి"
            Language.ODIA -> "ତୁରନ୍ତ ଖାଲି କରନ୍ତୁ"
            Language.ENGLISH -> "Evacuate immediately"
        }
        STAY_IN_POSITION -> when (language) {
            Language.HINDI -> "अपनी जगह पर रहें"
            Language.TAMIL -> "இருக்கும் இடத்தில் இருங்கள்"
            Language.BENGALI -> "অবস্থানে থাকুন"
            Language.GUJARATI -> "તમારી જગ્યા પર રહો"
            Language.MARATHI -> "आपल्या जागी राहा"
            Language.KANNADA -> "ನಿಮ್ಮ ಸ್ಥಾನದಲ್ಲೇ ಇರಿ"
            Language.MALAYALAM -> "നിങ്ങളുടെ സ്ഥാനത്ത് തുടരുക"
            Language.TELUGU -> "మీ స్థానంలోనే ఉండండి"
            Language.ODIA -> "ନିଜ ସ୍ଥାନରେ ରୁହନ୍ତୁ"
            Language.ENGLISH -> "Stay in position"
        }
        MEDICAL_HELP -> when (language) {
            Language.HINDI -> "चिकित्सा सहायता चाहिए"
            Language.TAMIL -> "மருத்துவ உதவி தேவை"
            Language.BENGALI -> "চিকিৎসা সাহায্য দরকার"
            Language.GUJARATI -> "તબીબી મદદ જોઈએ છે"
            Language.MARATHI -> "वैद्यकीय मदत हवी आहे"
            Language.KANNADA -> "ವೈದ್ಯಕೀಯ ನೆರವು ಬೇಕಾಗಿದೆ"
            Language.MALAYALAM -> "വൈദ്യസഹായം വേണം"
            Language.TELUGU -> "వైద్య సహాయం కావాలి"
            Language.ODIA -> "ଡାକ୍ତରୀ ସାହାଯ୍ୟ ଦରକାର"
            Language.ENGLISH -> "Medical help needed"
        }
    }

    companion object {
        const val WIRE_PREFIX = "tpl:"

        fun fromAssetKey(key: String): AlertTemplate? =
            entries.firstOrNull { it.assetKey == key }

        fun fromWirePayload(text: String): AlertContent {
            val trimmed = text.trim()
            if (trimmed.startsWith(WIRE_PREFIX)) {
                val key = trimmed.removePrefix(WIRE_PREFIX)
                val template = fromAssetKey(key)
                if (template != null) return AlertContent.Template(template)
            }
            return AlertContent.Custom(trimmed)
        }
    }
}

sealed interface AlertContent {
    data class Template(val template: AlertTemplate) : AlertContent

    data class Custom(val text: String) : AlertContent

    fun toWirePayload(): String = when (this) {
        is Template -> AlertTemplate.WIRE_PREFIX + template.assetKey
        is Custom -> text.trim()
    }
}

data class IncomingAlert(
    val content: AlertContent,
    val language: Language,
    val sequence: Int,
    val receivedAtMs: Long,
)

interface ForcedAudioFocus {
    fun <T> withForcedAlarmAudio(block: () -> T): T
    val isHeld: Boolean
}

interface AlertListener {
    fun onAlertStarted(alert: IncomingAlert) {}
    fun onAlertCompleted(alert: IncomingAlert, durationMs: Long) {}
    fun onDeferredDuringAlert(sequence: Int) {}
    fun onAlertQueued(alert: IncomingAlert, queueDepth: Int) {}
    fun onAlertFailed(alert: IncomingAlert, reason: String) {}
}
