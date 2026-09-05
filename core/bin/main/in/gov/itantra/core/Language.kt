package `in`.gov.itantra.core

/**
 * Official PS 26173 language set: nine Indian languages plus English.
 *
 * [wire] is the stable on-air identifier used by the transport packet header
 * (Module B5). It is an explicit constant rather than [ordinal] so that
 * reordering this enum can never silently change the wire format.
 *
 * Hindi / Tamil / Bengali keep their original wire codes (0x01–0x03).
 */
enum class Language(
    val code: String,
    val bcp47: String,
    val endonym: String,
    val englishName: String,
    val wire: Byte,
) {
    HINDI("hi", "hi-IN", "हिन्दी", "Hindi", 0x01),
    TAMIL("ta", "ta-IN", "தமிழ்", "Tamil", 0x02),
    BENGALI("bn", "bn-IN", "বাংলা", "Bengali", 0x03),
    GUJARATI("gu", "gu-IN", "ગુજરાતી", "Gujarati", 0x04),
    MARATHI("mr", "mr-IN", "मराठी", "Marathi", 0x05),
    KANNADA("kn", "kn-IN", "ಕನ್ನಡ", "Kannada", 0x06),
    MALAYALAM("ml", "ml-IN", "മലയാളം", "Malayalam", 0x07),
    TELUGU("te", "te-IN", "తెలుగు", "Telugu", 0x08),
    ODIA("or", "or-IN", "ଓଡ଼ିଆ", "Odia", 0x09),
    ENGLISH("en", "en-IN", "English", "English", 0x0A),
    ;

    companion object {
        val DEFAULT: Language = HINDI

        fun fromCode(code: String): Language? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }

        fun fromWire(b: Byte): Language? = entries.firstOrNull { it.wire == b }

        /** Throwing variant for decode paths where an unknown language is a protocol error. */
        fun requireWire(b: Byte): Language =
            fromWire(b) ?: throw IllegalArgumentException(
                "Unknown language wire code 0x${b.toInt().and(0xFF).toString(16)}"
            )
    }
}
