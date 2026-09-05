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
        fun normalize(text: String): String = text.replace(Regex("\\s+"), " ").trim()

        val DEFAULT_PHRASES: Map<PhraseKey, String> = buildMap {
            fun add(source: Language, target: Language, from: String, to: String) {
                this[PhraseKey(source, target, normalize(from))] = to
            }

            // Hindi ↔ Tamil
            add(Language.HINDI, Language.TAMIL, "मुझे मदद चाहिए", "எனக்கு உதவி வேண்டும்")
            add(Language.TAMIL, Language.HINDI, "எனக்கு உதவி வேண்டும்", "मुझे मदद चाहिए")
            add(Language.HINDI, Language.TAMIL, "पानी बढ़ रहा है", "தண்ணீர் உயர்கிறது")
            add(Language.TAMIL, Language.HINDI, "தண்ணீர் உயர்கிறது", "पानी बढ़ रहा है")
            add(Language.HINDI, Language.TAMIL, "सब लोग सुरक्षित हैं", "அனைவரும் பாதுகாப்பாக உள்ளனர்")
            add(Language.TAMIL, Language.HINDI, "அனைவரும் பாதுகாப்பாக உள்ளனர்", "सब लोग सुरक्षित हैं")

            // Hindi ↔ English
            add(Language.HINDI, Language.ENGLISH, "मुझे मदद चाहिए", "I need help")
            add(Language.ENGLISH, Language.HINDI, "I need help", "मुझे मदद चाहिए")
            add(Language.HINDI, Language.ENGLISH, "पानी बढ़ रहा है", "The water is rising")
            add(Language.ENGLISH, Language.HINDI, "The water is rising", "पानी बढ़ रहा है")

            // Tamil ↔ English
            add(Language.TAMIL, Language.ENGLISH, "எனக்கு உதவி வேண்டும்", "I need help")
            add(Language.ENGLISH, Language.TAMIL, "I need help", "எனக்கு உதவி வேண்டும்")

            // Hindi ↔ Bengali
            add(Language.HINDI, Language.BENGALI, "मुझे मदद चाहिए", "আমার সাহায্য দরকার")
            add(Language.BENGALI, Language.HINDI, "আমার সাহায্য দরকার", "मुझे मदद चाहिए")
        }
    }
}
