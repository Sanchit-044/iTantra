package `in`.gov.itantra.core.tts

import `in`.gov.itantra.core.Language

/**
 * Module B3 text normalisation: rewrites digits, dates, times, currency and
 * abbreviations into fully spelled-out words before the text reaches the VITS model.
 *
 * This matters more than it looks. VITS is a character/phoneme-conditioned model; it
 * has never seen "15/08" or "NDRF" in training and will either skip them or produce
 * noise. Everything the synthesiser is asked to say must first become ordinary words.
 *
 * Rule order is significant and deliberate: the more specific pattern always runs
 * first, so "15/08/2025" is consumed as a date before its digits can be read as three
 * separate numbers.
 */
class TextNormalizer(
    val language: Language,
    private val lexicon: AbbreviationLexicon = AbbreviationLexicon.forLanguage(language),
) {
    private val numbers = IndicNumberFormatter.forLanguage(language)
    private val nlex = NumberLexicon.forLanguage(language)

    fun normalize(input: String): String {
        var s = input
        s = foldNativeDigits(s)
        s = expandAbbreviations(s)
        s = expandDates(s)
        s = expandTimes(s)
        s = expandCurrency(s)
        s = expandMeasures(s)
        s = expandDigitSequences(s)
        s = expandPlainNumbers(s)
        s = collapseWhitespace(s)
        return s
    }

    // ------------------------------------------------------------------ digits

    /**
     * Maps Devanagari, Tamil and Bengali digit codepoints onto ASCII 0-9 so that a
     * single set of numeric rules serves all three scripts. Users type in their own
     * script; incoming transport messages may use either.
     */
    fun foldNativeDigits(s: String): String {
        if (s.none { it.code in NATIVE_DIGIT_RANGE }) return s
        val sb = StringBuilder(s.length)
        for (ch in s) {
            sb.append(
                when (ch.code) {
                    in 0x0966..0x096F -> ('0' + (ch.code - 0x0966)) // Devanagari
                    in 0x09E6..0x09EF -> ('0' + (ch.code - 0x09E6)) // Bengali
                    in 0x0BE6..0x0BEF -> ('0' + (ch.code - 0x0BE6)) // Tamil
                    else -> ch
                }
            )
        }
        return sb.toString()
    }

    // ----------------------------------------------------------------- patterns

    private fun expandDates(s: String): String =
        DATE_DMY.replace(s) { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].toInt()
            if (d !in 1..31 || mo !in 1..12) m.value else spokenDate(d, mo, normaliseYear(y))
        }.let { partial ->
            DATE_YMD.replace(partial) { m ->
                val y = m.groupValues[1].toInt()
                val mo = m.groupValues[2].toInt()
                val d = m.groupValues[3].toInt()
                if (d !in 1..31 || mo !in 1..12) m.value else spokenDate(d, mo, y)
            }
        }

    private fun normaliseYear(y: Int): Int = when {
        y >= 100 -> y
        // Two-digit years: 00-49 -> 2000s, 50-99 -> 1900s.
        y < 50 -> 2000 + y
        else -> 1900 + y
    }

    private fun spokenDate(day: Int, month: Int, year: Int): String =
        "${numbers.spell(day.toLong())} ${lexicon.months[month - 1]} ${numbers.spell(year.toLong())}"

    private fun expandTimes(s: String): String =
        TIME_HM.replace(s) { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h > 23 || min > 59) {
                m.value
            } else {
                val hourWords = "${numbers.spell(h.toLong())} ${lexicon.hourWord}"
                if (min == 0) hourWords
                else "$hourWords ${numbers.spell(min.toLong())} ${lexicon.minuteWord}"
            }
        }

    private fun expandCurrency(s: String): String =
        CURRENCY.replace(s) { m ->
            val whole = m.groupValues[2].replace(",", "")
            val frac = m.groupValues[3]
            val amount = whole.toLongOrNull() ?: return@replace m.value
            if (frac.isEmpty()) {
                "${numbers.spell(amount)} ${lexicon.rupees}"
            } else {
                val paise = frac.padEnd(2, '0').take(2).toLong()
                "${numbers.spell(amount)} ${lexicon.rupees} ${numbers.spell(paise)} ${lexicon.paise}"
            }
        }

    /** "12 km", "5kg", "30 m" -> number plus the spoken unit name. */
    private fun expandMeasures(s: String): String =
        MEASURE.replace(s) { m ->
            val n = m.groupValues[1].replace(",", "")
            val unit = m.groupValues[2]
            val spoken = lexicon.units[unit] ?: return@replace m.value
            val intPart = n.substringBefore('.')
            val frac = n.substringAfter('.', "")
            val value = intPart.toLongOrNull() ?: return@replace m.value
            "${numbers.spellDecimal(value, frac)} $spoken"
        }

    /**
     * Long digit runs are identifiers, not quantities. A 10-digit phone number read as
     * "nine thousand eight hundred crore..." is unusable, so anything at or above
     * [DIGITWISE_THRESHOLD] digits is read one digit at a time.
     */
    private fun expandDigitSequences(s: String): String =
        LONG_DIGITS.replace(s) { m ->
            if (m.value.length >= DIGITWISE_THRESHOLD) numbers.spellDigitwise(m.value) else m.value
        }

    private fun expandPlainNumbers(s: String): String =
        NUMBER.replace(s) { m ->
            val raw = m.value.replace(",", "")
            val neg = raw.startsWith("-")
            val body = raw.removePrefix("-")
            val intPart = body.substringBefore('.')
            val frac = body.substringAfter('.', "")
            val value = intPart.toLongOrNull() ?: return@replace m.value
            val spoken = numbers.spellDecimal(value, frac)
            if (neg) "${nlex.negative} $spoken" else spoken
        }

    private fun expandAbbreviations(s: String): String {
        if (lexicon.abbreviations.isEmpty()) return s
        var out = s
        // Longest key first so "SDRF" cannot be partially matched by a shorter entry.
        for ((abbr, spoken) in lexicon.sortedAbbreviations) {
            out = Regex("(?<![\\p{L}\\p{N}])${Regex.escape(abbr)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
                .replace(out, Regex.escapeReplacement(spoken))
        }
        return out
    }

    private fun collapseWhitespace(s: String): String =
        s.replace(Regex("[ \\t]+"), " ").trim()

    companion object {
        /** Digit runs at or beyond this length are spoken digit by digit. */
        const val DIGITWISE_THRESHOLD = 7

        private val NATIVE_DIGIT_RANGE = 0x0966..0x0BEF

        private val DATE_DMY = Regex("""\b(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{2,4})\b""")
        private val DATE_YMD = Regex("""\b(\d{4})[/\-](\d{1,2})[/\-](\d{1,2})\b""")
        private val TIME_HM = Regex("""\b(\d{1,2}):(\d{2})(?::\d{2})?\b""")
        private val CURRENCY = Regex("""(₹|Rs\.?|INR)\s*(\d[\d,]*)(?:\.(\d{1,2}))?""", RegexOption.IGNORE_CASE)
        private val MEASURE = Regex("""\b(\d[\d,]*(?:\.\d+)?)\s*(km/h|kmph|km|cm|mm|kg|mg|ml|hrs|hr|min|sec|m|g|l)\b""")
        private val LONG_DIGITS = Regex("""\b\d{7,}\b""")
        private val NUMBER = Regex("""-?\b\d[\d,]*(?:\.\d+)?\b""")
    }
}
