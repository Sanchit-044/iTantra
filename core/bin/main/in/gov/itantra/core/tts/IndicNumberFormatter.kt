package `in`.gov.itantra.core.tts

import `in`.gov.itantra.core.Language

/**
 * Converts integers and decimals to their spoken form using the Indian numbering
 * system (crore / lakh / thousand / hundred), which all three target languages share.
 *
 * Pure and side-effect free, so the whole of Module B3's number handling is unit
 * testable without loading a TTS model.
 */
class IndicNumberFormatter(private val lex: NumberLexicon) {

    /**
     * Spoken form of [value]. Handles the full Long range except [Long.MIN_VALUE],
     * which has no positive counterpart and is rejected.
     */
    fun spell(value: Long): String {
        require(value != Long.MIN_VALUE) { "Long.MIN_VALUE cannot be negated" }
        if (value == 0L) return lex.units[0]
        if (value < 0) return "${lex.negative} ${spell(-value)}"

        val parts = mutableListOf<String>()
        var remaining = value

        // Above one crore the Indian system repeats in crore units, e.g. 1,00,00,00,00,000
        // is "one lakh crore". Recursing on the crore count handles arbitrary magnitude.
        val crores = remaining / CRORE
        if (crores > 0) {
            parts += spell(crores)
            parts += lex.crore
            remaining %= CRORE
        }

        val lakhs = remaining / LAKH
        if (lakhs > 0) {
            parts += spellUnder100(lakhs.toInt())
            parts += lex.lakh
            remaining %= LAKH
        }

        val thousands = remaining / 1000
        if (thousands > 0) {
            parts += spellUnder100(thousands.toInt())
            parts += lex.thousand
            remaining %= 1000
        }

        val hundreds = remaining / 100
        if (hundreds > 0) {
            parts += lex.hundreds[(hundreds - 1).toInt()]
            remaining %= 100
        }

        if (remaining > 0) parts += spellUnder100(remaining.toInt())

        return parts.joinToString(" ")
    }

    /**
     * Spoken form of a decimal. The fractional part is read digit by digit, which is
     * how quantities are actually spoken in all three languages ("3.25" is read
     * "three point two five", not "three point twenty-five").
     */
    fun spellDecimal(intPart: Long, fractionDigits: String): String {
        if (fractionDigits.isEmpty()) return spell(intPart)
        val digits = fractionDigits.map { lex.units[Character.digit(it, 10)] }
        return "${spell(intPart)} ${lex.decimalPoint} ${digits.joinToString(" ")}"
    }

    /** Reads each character of [digits] separately -- used for phone numbers, IDs, codes. */
    fun spellDigitwise(digits: String): String =
        digits.mapNotNull { ch ->
            val d = Character.digit(ch, 10)
            if (d in 0..9) lex.units[d] else null
        }.joinToString(" ")

    private fun spellUnder100(n: Int): String {
        require(n in 0..99) { "expected 0..99, got $n" }
        return lex.units[n]
    }

    companion object {
        private const val LAKH = 100_000L
        private const val CRORE = 10_000_000L

        fun forLanguage(language: Language): IndicNumberFormatter =
            IndicNumberFormatter(NumberLexicon.forLanguage(language))
    }
}
