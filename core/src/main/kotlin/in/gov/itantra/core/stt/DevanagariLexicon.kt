package `in`.gov.itantra.core.stt

import `in`.gov.itantra.core.Language

/**
 * Separates Hindi from Marathi, which [ScriptLanguageId] cannot do on codepoints
 * alone: both languages are written in the same Devanagari block (U+0900–U+097F),
 * so every character scores identically for both and the tie always fell to Hindi.
 * The practical effect was that a Marathi speaker who also had Hindi installed was
 * detected as Hindi on every utterance.
 *
 * The signals used here are lexical rather than acoustic, so this runs on the STT
 * hypothesis and needs no extra model:
 *
 *  - **ळ** (U+0933 LETTER LLA) is used in Marathi and effectively absent from
 *    standard Hindi orthography, so it carries [LETTER_WEIGHT] on its own.
 *  - Closed-class function words that differ between the two languages
 *    (Marathi आहे / नाही / मी / आणि versus Hindi है / नहीं / मैं / और). Content
 *    words shared by both languages are deliberately excluded — they add noise
 *    without adding evidence.
 *
 * Returns null when the evidence is level, which keeps the caller's existing
 * "Hindi wins ties" fallback intact for text that carries no signal either way.
 */
object DevanagariLexicon {

    /** One ळ is worth more than one function word: it is close to a categorical marker. */
    const val LETTER_WEIGHT = 2

    private const val MARATHI_ONLY_LETTER = 'ळ'

    /**
     * Marathi function words with a distinct Hindi counterpart. Every entry here is
     * one that Hindi expresses differently, which is what makes it evidence.
     */
    private val MARATHI_TOKENS: Set<String> = setOf(
        // copula and negation: Hindi uses है / हैं / नहीं
        "आहे", "आहेत", "आहोत", "नाही", "नाहीत", "होय", "नको",
        // pronouns: Hindi uses मैं / मुझे / हम / तुम्हें
        "मी", "मला", "आम्ही", "तुम्ही", "तुला", "त्यांना",
        // interrogatives and conjunctions: Hindi uses क्या / कैसे / और / लेकिन
        "काय", "कसे", "कसा", "कशी", "आणि", "पण",
        // postposition: Hindi uses में
        "मध्ये", "इथे", "तिथे", "खूप",
        // vocabulary that differs from the Hindi cognate
        "मदत", "मदतीची", "गरज", "हवी", "हवे", "अन्न", "पाणी",
        "रस्ता", "सर्व", "सर्वजण", "पाठवा", "थांबा", "राहा",
        "पडा", "लागली", "वाढत", "झाले",
    )

    /**
     * Hindi function words with a distinct Marathi counterpart. का / की / के are kept
     * because Marathi inflects the same genitive as चा / ची / चे.
     */
    private val HINDI_TOKENS: Set<String> = setOf(
        // copula and negation
        "है", "हैं", "नहीं", "था", "थे", "थी",
        // pronouns
        "मैं", "मुझे", "हम", "आप", "तुम्हें", "यह", "वह", "ये", "वे",
        // interrogatives and conjunctions
        "क्या", "कैसे", "और", "लेकिन",
        // postpositions and genitive
        "में", "को", "का", "की", "के",
        // vocabulary that differs from the Marathi cognate
        "मदद", "चाहिए", "जरूरत", "ज़रूरत", "खाना", "पानी", "रास्ता",
        "सब", "लोग", "तुरंत", "भेजें", "निकलें", "रहें", "गई", "बढ़",
        "रहा", "रही", "रहे",
    )

    /** Splits on anything that is not a letter or a Devanagari combining mark. */
    private val TOKEN_SEPARATOR = Regex("[^\\p{L}\\p{M}]+")

    /**
     * Scores [text] for Marathi against Hindi. Positive favours Marathi, negative
     * favours Hindi, zero means no evidence. Exposed for tests and diagnostics.
     */
    fun marathiBias(text: String): Int {
        var score = 0
        if (text.contains(MARATHI_ONLY_LETTER)) score += LETTER_WEIGHT
        for (token in text.split(TOKEN_SEPARATOR)) {
            if (token.isEmpty()) continue
            if (token in MARATHI_TOKENS) score++
            if (token in HINDI_TOKENS) score--
        }
        return score
    }

    /**
     * [Language.MARATHI] or [Language.HINDI] when the text carries evidence either
     * way, null when it is level and the caller should keep its own default.
     */
    fun disambiguate(text: String): Language? = when (marathiBias(text).let { it.compareTo(0) }) {
        1 -> Language.MARATHI
        -1 -> Language.HINDI
        else -> null
    }
}
