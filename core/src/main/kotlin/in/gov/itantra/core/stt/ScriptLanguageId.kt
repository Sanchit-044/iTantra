package `in`.gov.itantra.core.stt

import `in`.gov.itantra.core.Language

/**
 * On-device language ID from text script. Used after a first STT hypothesis and
 * in tests; does not require a neural LID model.
 *
 * ## Why this returns null so readily
 *
 * The caller writes the result straight back into the user's language setting, so a
 * wrong answer does not merely mislabel one utterance -- it silently reconfigures the
 * handset and every subsequent press uses the wrong acoustic model and the wrong voice.
 * A wrong answer is therefore strictly worse than no answer, and every ambiguous case
 * here resolves to null so the caller keeps what the user chose.
 *
 * ## Devanagari
 *
 * Hindi and Marathi share the Devanagari block, so the script alone cannot separate
 * them. The previous implementation broke the tie in Hindi's favour unconditionally,
 * which meant [detectFromText] could never return Marathi: a Marathi speaker with Hindi
 * also installed was switched to Hindi after their first utterance, every time.
 *
 * The tie is now broken on orthographic evidence -- letters that occur in one language
 * and not the other, plus a small closed-class function-word list -- and when there is
 * no evidence either way the result is null rather than a guess.
 */
class ScriptLanguageId : LanguageIdEngine {

    override fun detect(candidates: Set<Language>, pcm: ShortArray?): Language? {
        if (candidates.size == 1) return candidates.first()
        return null
    }

    override fun detectFromText(text: String, candidates: Set<Language>): Language? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()
        if (text.isBlank()) return null

        val counts = HashMap<Script, Int>()
        var scripted = 0
        for (ch in text) {
            val script = scriptOf(ch) ?: continue
            counts[script] = (counts[script] ?: 0) + 1
            scripted++
        }
        // Digits and punctuation alone say nothing about the language.
        if (scripted < MIN_SCRIPTED_CHARS) return null

        val winner = counts.maxByOrNull { it.value } ?: return null
        // Heavily mixed input -- code-switching, transliteration, a stray loanword in
        // another script -- is not a reliable signal for reconfiguring the handset.
        if (winner.value * 100 < scripted * MIN_MAJORITY_PERCENT) return null

        val inScript = winner.key.languages.filter { it in candidates }
        return when {
            inScript.isEmpty() -> null
            inScript.size == 1 -> inScript.single()
            winner.key == Script.DEVANAGARI -> disambiguateDevanagari(text, candidates)
            // No other script in this set maps to more than one language; if one ever
            // does, refusing to guess is the correct default.
            else -> null
        }
    }

    /**
     * Separates Hindi from Marathi on orthographic evidence.
     *
     * Both signals are weighted rather than treated as proof: no single letter settles
     * the question, because every marker here does occur occasionally in the other
     * language (loanwords, proper nouns, dialect). Function words are weighted higher
     * than letters because they are closed-class and far harder to produce by accident.
     *
     * Returns null on a tie -- including the common case of a short sentence written in
     * letters shared by both languages, where there is genuinely nothing to go on.
     */
    private fun disambiguateDevanagari(text: String, candidates: Set<Language>): Language? {
        val hindiInstalled = Language.HINDI in candidates
        val marathiInstalled = Language.MARATHI in candidates
        if (hindiInstalled && !marathiInstalled) return Language.HINDI
        if (marathiInstalled && !hindiInstalled) return Language.MARATHI
        if (!hindiInstalled && !marathiInstalled) return null

        var hindi = 0
        var marathi = 0

        for (ch in text) {
            if (ch in MARATHI_LETTERS) marathi += LETTER_WEIGHT
            if (ch in HINDI_LETTERS) hindi += LETTER_WEIGHT
        }
        for (word in tokenize(text)) {
            if (word in MARATHI_WORDS) marathi += WORD_WEIGHT
            if (word in HINDI_WORDS) hindi += WORD_WEIGHT
        }

        return when {
            marathi > hindi -> Language.MARATHI
            hindi > marathi -> Language.HINDI
            // No evidence, or evidence for both in equal measure. Keep the user's choice.
            else -> null
        }
    }

    /** Splits on anything that is not a Devanagari letter or combining mark. */
    private fun tokenize(text: String): List<String> =
        text.split(*TOKEN_SEPARATORS).filter { it.isNotEmpty() }

    private enum class Script(val languages: List<Language>) {
        DEVANAGARI(listOf(Language.HINDI, Language.MARATHI)),
        BENGALI(listOf(Language.BENGALI)),
        GUJARATI(listOf(Language.GUJARATI)),
        ODIA(listOf(Language.ODIA)),
        TAMIL(listOf(Language.TAMIL)),
        TELUGU(listOf(Language.TELUGU)),
        KANNADA(listOf(Language.KANNADA)),
        MALAYALAM(listOf(Language.MALAYALAM)),
        LATIN(listOf(Language.ENGLISH)),
    }

    /**
     * The script a single character belongs to, or null when it carries no language
     * signal: ASCII digits, punctuation, whitespace, the shared danda, and the native
     * digit ranges (which are script-specific but routinely typed for any language and
     * are stripped by the TTS normaliser anyway).
     */
    private fun scriptOf(ch: Char): Script? {
        val c = ch.code
        // Danda and double danda are shared across the Indic scripts.
        if (c == 0x0964 || c == 0x0965) return null
        return when (c) {
            in 0x0900..0x097F -> if (isNativeDigit(c, 0x0966)) null else Script.DEVANAGARI
            in 0x0980..0x09FF -> if (isNativeDigit(c, 0x09E6)) null else Script.BENGALI
            in 0x0A80..0x0AFF -> if (isNativeDigit(c, 0x0AE6)) null else Script.GUJARATI
            in 0x0B00..0x0B7F -> if (isNativeDigit(c, 0x0B66)) null else Script.ODIA
            in 0x0B80..0x0BFF -> if (isNativeDigit(c, 0x0BE6)) null else Script.TAMIL
            in 0x0C00..0x0C7F -> if (isNativeDigit(c, 0x0C66)) null else Script.TELUGU
            in 0x0C80..0x0CFF -> if (isNativeDigit(c, 0x0CE6)) null else Script.KANNADA
            in 0x0D00..0x0D7F -> if (isNativeDigit(c, 0x0D66)) null else Script.MALAYALAM
            else -> if (c < 0x0080 && ch.isLetter()) Script.LATIN else null
        }
    }

    private fun isNativeDigit(c: Int, zero: Int): Boolean = c in zero..(zero + 9)

    companion object {
        /**
         * Below this many script-bearing characters there is not enough text to justify
         * reconfiguring the handset's language.
         */
        private const val MIN_SCRIPTED_CHARS = 2

        /**
         * The winning script must account for at least this share of the script-bearing
         * characters. Below it the text is mixed and no single language is implied.
         */
        private const val MIN_MAJORITY_PERCENT = 60

        private const val LETTER_WEIGHT = 2
        private const val WORD_WEIGHT = 3

        private val TOKEN_SEPARATORS: CharArray = charArrayOf(
            ' ', '\t', '\n', '\r', ' ',
            '।', '॥', '.', ',', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '{', '}',
            '-', '–', '—', '/', '\\', '|',
        )

        /**
         * Letters that occur in Marathi and are rare-to-absent in standard Hindi:
         * LLA, RRA, and the candra vowels Marathi uses for English loanwords.
         */
        private val MARATHI_LETTERS: Set<Char> = setOf(
            'ळ', // ळ  LLA
            'ऱ', // ऱ  RRA
            'ॲ', // ॲ  CANDRA A
            'ऍ', // ऍ  CANDRA E
        )

        /**
         * Nukta letters. Hindi writes Perso-Arabic loanwords with these; Marathi
         * generally does not. The bare combining nukta is included so the decomposed
         * spelling of the same letters counts too.
         */
        private val HINDI_LETTERS: Set<Char> = setOf(
            '़', // ़  combining nukta
            'क़', // क़
            'ख़', // ख़
            'ग़', // ग़
            'ड़', // ड़
            'ढ़', // ढ़
            'फ़', // फ़
        )

        /**
         * Closed-class Marathi function words with no Hindi counterpart spelled the
         * same way. Kept disjoint from [HINDI_WORDS]; a test enforces that.
         */
        private val MARATHI_WORDS: Set<String> = setOf(
            "आहे", "आहेत", "आहेस", "नाही", "नाहीत",
            "मला", "तुला", "आम्ही", "तुम्ही", "त्यांना",
            "काय", "कसे", "कशी", "कुठे", "कधी",
            "होते", "होता", "झाले", "झाला", "केले", "करतो", "करते",
            "पाहिजे", "मध्ये", "आणि", "किंवा", "पण",
            "माझा", "माझी", "माझे", "तुझा", "तुझी", "त्याचा", "त्यांचा",
            "इथे", "तिथे", "खूप", "लवकर", "मदत",
        )

        /**
         * Closed-class Hindi function words with no Marathi counterpart spelled the
         * same way. Words common to both -- कृपया, धन्यवाद and similar -- are
         * deliberately in neither list; they would add noise to both sides equally.
         */
        private val HINDI_WORDS: Set<String> = setOf(
            "है", "हैं", "हूँ", "हूं", "नहीं",
            "मुझे", "तुझे", "हम", "आप", "उन्हें",
            "क्या", "कैसे", "कैसी", "कहाँ", "कहां", "कब",
            "था", "थी", "थे", "हुआ", "गया", "किया", "करता", "करती",
            "चाहिए", "में", "और", "या", "लेकिन",
            "मेरा", "मेरी", "मेरे", "तुम्हारा", "उसका", "उनका",
            "यहाँ", "यहां", "वहाँ", "वहां", "बहुत", "जल्दी", "मदद",
        )

        /**
         * The languages a single character is compatible with.
         *
         * Retained for callers outside this class. Note that a Devanagari character
         * returns both Hindi and Marathi: the pair can only be separated across a whole
         * string, which is what [detectFromText] does.
         */
        fun scriptMatches(ch: Char): Set<Language> {
            val c = ch.code
            return when {
                c in 0x0900..0x097F -> setOf(Language.HINDI, Language.MARATHI)
                c in 0x0980..0x09FF -> setOf(Language.BENGALI)
                c in 0x0A80..0x0AFF -> setOf(Language.GUJARATI)
                c in 0x0B00..0x0B7F -> setOf(Language.ODIA)
                c in 0x0B80..0x0BFF -> setOf(Language.TAMIL)
                c in 0x0C00..0x0C7F -> setOf(Language.TELUGU)
                c in 0x0C80..0x0CFF -> setOf(Language.KANNADA)
                c in 0x0D00..0x0D7F -> setOf(Language.MALAYALAM)
                ch.isLetter() && c < 0x0080 -> setOf(Language.ENGLISH)
                else -> emptySet()
            }
        }
    }
}
