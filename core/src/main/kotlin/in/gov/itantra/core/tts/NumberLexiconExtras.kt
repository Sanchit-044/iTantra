package `in`.gov.itantra.core.tts

import `in`.gov.itantra.core.Language

/**
 * Number tables for languages added after the Hindi / Tamil / Bengali prototype.
 * English uses real spoken forms. Other new languages use a composed 0..99 table
 * with unique tokens so TTS never sees a blank or colliding slot; native-speaker
 * review can replace the tokens without changing call sites.
 */
internal object NumberLexiconExtras {

    private val EN_ONES = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
    )
    private val EN_TEENS = listOf(
        "ten", "eleven", "twelve", "thirteen", "fourteen",
        "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val EN_TENS = listOf(
        "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety",
    )

    val ENGLISH = NumberLexicon(
        language = Language.ENGLISH,
        units = NumberLexicon.composeUnits(EN_ONES, EN_TEENS, EN_TENS, EN_TENS),
        hundreds = (1..9).map { "${EN_ONES[it]} hundred" },
        thousand = "thousand",
        lakh = "lakh",
        crore = "crore",
        decimalPoint = "point",
        negative = "minus",
    )

    val GUJARATI = sequential(Language.GUJARATI, "hajar", "lakh", "karod", "dashansh", "run")
    val MARATHI = sequential(Language.MARATHI, "hajar", "lakh", "koti", "purnank", "run")
    val KANNADA = sequential(Language.KANNADA, "savira", "laksha", "koti", "bindu", "runa")
    val MALAYALAM = sequential(Language.MALAYALAM, "ayiram", "laksham", "koti", "bindu", "runam")
    val TELUGU = sequential(Language.TELUGU, "veyi", "laksha", "koti", "point", "runa")
    val ODIA = sequential(Language.ODIA, "hajar", "lakhya", "koti", "dashamik", "runa")

    private fun sequential(
        language: Language,
        thousand: String,
        lakh: String,
        crore: String,
        decimalPoint: String,
        negative: String,
    ): NumberLexicon {
        val prefix = language.code
        val digitNames = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
        val ones = (0..9).map { "$prefix-n-${digitNames[it]}" }
        val teens = (10..19).map { "$prefix-n-teen-${digitNames[it - 10]}" }
        val tens = (2..9).map { "$prefix-t-${digitNames[it]}" }
        val combining = (2..9).map { "$prefix-c-${digitNames[it]}" }
        return NumberLexicon(
            language = language,
            units = NumberLexicon.composeUnits(ones, teens, tens, combining),
            hundreds = (1..9).map { "$prefix-h-${digitNames[it]}" },
            thousand = thousand,
            lakh = lakh,
            crore = crore,
            decimalPoint = decimalPoint,
            negative = negative,
        )
    }
}
