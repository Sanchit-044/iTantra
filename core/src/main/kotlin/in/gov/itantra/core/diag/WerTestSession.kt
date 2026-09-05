package `in`.gov.itantra.core.diag

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.eval.Wer

/**
 * Live WER for the diagnostics screen.
 *
 * WER is only meaningful against a known reference. This session is that test mode:
 * the operator is prompted with a short phrase, the next non-empty final hypothesis
 * is scored against it, and the last [maxPairs] pairs are aggregated as corpus WER
 * (errors over reference words), not a mean of per-utterance rates.
 *
 * Disabled by default. With no pairs, [wer] is null -- never 0.0 -- so an idle
 * device cannot be quoted as having perfect accuracy.
 */
class WerTestSession(private val maxPairs: Int = 10) {

    data class Pair(val reference: String, val hypothesis: String)

    @Volatile
    var enabled: Boolean = false
        private set

    private val lock = Any()
    private val pairs = ArrayDeque<Pair>()
    private val cursor = mutableMapOf<Language, Int>()

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) reset()
    }

    fun peekPrompt(language: Language): String? {
        if (!enabled) return null
        val script = scriptFor(language)
        if (script.isEmpty()) return null
        val i = synchronized(lock) { cursor[language] ?: 0 }
        return script[i % script.size]
    }

    /**
     * Scores [hypothesis] against the current prompt. Blank, cancelled, or disabled
     * input is ignored and does not consume the prompt.
     */
    fun recordHypothesis(language: Language, hypothesis: String): Double? {
        if (!enabled) return null
        val hyp = hypothesis.trim()
        if (hyp.isEmpty()) return null

        synchronized(lock) {
            if (!enabled) return null
            val script = scriptFor(language)
            if (script.isEmpty()) return null
            val index = cursor[language] ?: 0
            val ref = script[index % script.size]
            if (Wer.tokenize(ref).isEmpty()) return null
            pairs.addLast(Pair(ref, hyp))
            while (pairs.size > maxPairs) pairs.removeFirst()
            cursor[language] = (index + 1) % script.size
        }
        return wer
    }

    val wer: Double?
        get() = synchronized(lock) {
            if (pairs.isEmpty()) return null
            var refWords = 0
            var errors = 0
            for (pair in pairs) {
                val result = Wer.score(pair.reference, pair.hypothesis)
                if (result.referenceWords == 0) continue
                refWords += result.referenceWords
                errors += result.errors
            }
            if (refWords == 0) null else errors.toDouble() / refWords
        }

    val pairCount: Int get() = synchronized(lock) { pairs.size }

    fun snapshotPairs(): List<Pair> = synchronized(lock) { pairs.toList() }

    fun reset() {
        synchronized(lock) {
            pairs.clear()
            cursor.clear()
        }
    }

    companion object {
        fun scriptFor(language: Language): List<String> = when (language) {
            Language.HINDI -> HINDI
            Language.TAMIL -> TAMIL
            Language.BENGALI -> BENGALI
            else -> emptyList()
        }

        // Short, speakable prompts. These are test references, not claimed field WER.
        private val HINDI = listOf(
            "नमस्ते मेरा नाम भारत है",
            "पानी बढ़ रहा है",
            "कृपया मदद भेजें",
            "हम सुरक्षित हैं",
            "स्थिति सामान्य है",
            "एक दो तीन चार",
            "आग लग गई है",
            "सड़क बंद है",
            "मैं यहाँ हूँ",
            "समझ गया",
        )
        private val TAMIL = listOf(
            "வணக்கம் என் பெயர் இந்தியா",
            "தண்ணீர் உயர்கிறது",
            "தயவுசெய்து உதவி அனுப்புங்கள்",
            "நாங்கள் பாதுகாப்பாக இருக்கிறோம்",
            "நிலைமை சாதாரணம்",
            "ஒன்று இரண்டு மூன்று நான்கு",
            "தீ விபத்து ஏற்பட்டுள்ளது",
            "சாலை மூடப்பட்டுள்ளது",
            "நான் இங்கே இருக்கிறேன்",
            "புரிந்தது",
        )
        private val BENGALI = listOf(
            "নমস্কার আমার নাম ভারত",
            "জল বাড়ছে",
            "অনুগ্রহ করে সাহায্য পাঠান",
            "আমরা নিরাপদ",
            "পরিস্থিতি স্বাভাবিক",
            "এক দুই তিন চার",
            "আগুন লেগেছে",
            "রাস্তা বন্ধ",
            "আমি এখানে আছি",
            "বুঝেছি",
        )
    }
}
