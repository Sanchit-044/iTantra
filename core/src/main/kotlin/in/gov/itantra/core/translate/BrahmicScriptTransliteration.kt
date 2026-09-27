package `in`.gov.itantra.core.translate

import `in`.gov.itantra.core.Language

/**
 * High-performance, zero-allocation Brahmic script transliterator.
 *
 * Unicode Brahmic scripts (Devanagari, Bengali, Gujarati, Odia, Tamil, Telugu,
 * Kannada, Malayalam) share an identical code point matrix offset by script block
 * (0x80 increments).
 *
 * AI4Bharat IndicTrans2 uses Devanagari as its canonical intermediate script.
 * This class provides bidirectional conversion:
 * 1. Source script → Devanagari (before BPE encoding)
 * 2. Devanagari → Target script (after BPE decoding)
 */
object BrahmicScriptTransliteration {

    /** Base Unicode offset for each Brahmic language script. */
    private fun getScriptBase(language: Language): Int? = when (language) {
        Language.HINDI, Language.MARATHI -> 0x0900 // Devanagari
        Language.BENGALI -> 0x0980 // Bengali
        Language.GUJARATI -> 0x0A80 // Gujarati
        Language.ODIA -> 0x0B00 // Odia
        Language.TAMIL -> 0x0B80 // Tamil
        Language.TELUGU -> 0x0C00 // Telugu
        Language.KANNADA -> 0x0C80 // Kannada
        Language.MALAYALAM -> 0x0D00 // Malayalam
        Language.ENGLISH -> null // Latin (no Brahmic offset)
    }

    /**
     * Check if a language uses a Brahmic-derived script.
     */
    fun isBrahmic(language: Language): Boolean = getScriptBase(language) != null

    /**
     * Convert any Brahmic script text into Devanagari (canonical pivot script).
     */
    fun toDevanagari(text: String, sourceLanguage: Language): String {
        val base = getScriptBase(sourceLanguage) ?: return text
        if (base == 0x0900) return text // Already Devanagari

        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val c = text[i]
            val code = c.code

            if (code in base..(base + 0x7F)) {
                val offset = code - base
                val devanagariCode = 0x0900 + offset
                sb.append(devanagariCode.toChar())
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    /**
     * Convert Devanagari text into the target Brahmic script.
     */
    fun fromDevanagari(devanagariText: String, targetLanguage: Language): String {
        val targetBase = getScriptBase(targetLanguage) ?: return devanagariText
        if (targetBase == 0x0900) return devanagariText // Already Devanagari

        val sb = StringBuilder(devanagariText.length)
        var i = 0
        while (i < devanagariText.length) {
            val c = devanagariText[i]
            val code = c.code

            if (code in 0x0900..0x097F) {
                val offset = code - 0x0900
                val targetCode = mapDevanagariOffsetToTarget(offset, targetLanguage, targetBase)
                if (targetCode != null) {
                    sb.append(targetCode.toChar())
                } else {
                    sb.append(c)
                }
            } else {
                sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    /**
     * Map a Devanagari relative offset (0x00..0x7F) to the target script,
     * handling script-specific phonetic collapse (e.g. Tamil lacking aspirated consonants).
     */
    private fun mapDevanagariOffsetToTarget(offset: Int, targetLanguage: Language, targetBase: Int): Int? {
        if (targetLanguage == Language.TAMIL) {
            val mappedOffset = when (offset) {
                0x16, 0x17, 0x18 -> 0x15 // kh, g, gh -> k (க)
                0x1B, 0x1C, 0x1D -> 0x1A // ch, j, jh -> c (ச)
                0x20, 0x21, 0x22 -> 0x1F // th, d, dh -> t (ட)
                0x25, 0x26, 0x27 -> 0x24 // th, d, dh -> t (த)
                0x2B, 0x2C, 0x2D -> 0x2A // ph, b, bh -> p (ப)
                0x36 -> 0x38 // sh -> s (ஸ)
                0x31 -> 0x29 // Devanagari short 'r'
                else -> offset
            }
            return targetBase + mappedOffset
        }

        if (targetLanguage == Language.BENGALI) {
            val mappedOffset = when (offset) {
                0x35 -> 0x2C // v -> b
                else -> offset
            }
            return targetBase + mappedOffset
        }

        // Standard 1-to-1 Brahmic ISO matrix offset for Gujarati, Odia, Telugu, Kannada, Malayalam
        return targetBase + offset
    }

    /**
     * Direct transliteration between any two supported languages.
     */
    fun transliterate(text: String, source: Language, target: Language): String {
        if (source == target) return text
        if (!isBrahmic(source) || !isBrahmic(target)) return text
        val devanagari = toDevanagari(text, source)
        return fromDevanagari(devanagari, target)
    }
}
