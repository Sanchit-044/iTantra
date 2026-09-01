package `in`.gov.itantra.core

/**
 * The complete language scope of the iTantra prototype.
 *
 * Deliberately three entries. English is NOT present and must not be added:
 * the problem statement scopes this prototype to Hindi, Tamil and Bengali only.
 *
 * [wire] is the stable on-air identifier used by the transport packet header
 * (Module B5). It is an explicit constant rather than [ordinal] so that
 * reordering this enum can never silently change the wire format.
 */
enum class Language(
    val code: String,
    val bcp47: String,
    val endonym: String,
    val wire: Byte,
) {
    HINDI("hi", "hi-IN", "हिन्दी", 0x01),
    TAMIL("ta", "ta-IN", "தமிழ்", 0x02),
    BENGALI("bn", "bn-IN", "বাংলা", 0x03),
    ;

    companion object {
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
