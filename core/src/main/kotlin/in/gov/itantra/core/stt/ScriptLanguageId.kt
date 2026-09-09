package `in`.gov.itantra.core.stt

import `in`.gov.itantra.core.Language

/**
 * On-device language ID from text script. Used after a first STT hypothesis and
 * in tests; does not require a neural LID model.
 *
 * Devanagari is shared by Hindi and Marathi, so script alone cannot separate them.
 * When both are installed the tie is broken by [DevanagariLexicon]; Hindi still wins
 * when the text carries no lexical evidence, because it is the default language.
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

        val scores = mutableMapOf<Language, Int>()
        for (ch in text) {
            for (lang in scriptMatches(ch)) {
                if (lang in candidates) {
                    scores[lang] = (scores[lang] ?: 0) + 1
                }
            }
        }
        if (scores.isEmpty()) return null
        val best = scores.values.max()
        val winners = scores.filterValues { it == best }.keys

        // Hindi and Marathi share every Devanagari codepoint, so they always tie here
        // and the tie below would hand every Marathi utterance to Hindi. Break it on
        // vocabulary instead; DevanagariLexicon returns null when the text carries no
        // evidence, which falls through to the Hindi default unchanged.
        if (Language.HINDI in winners && Language.MARATHI in winners) {
            DevanagariLexicon.disambiguate(text)?.let { return it }
        }
        if (Language.HINDI in winners) return Language.HINDI
        return winners.minBy { it.wire }
    }

    companion object {
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
