package `in`.gov.itantra.core.pack

import `in`.gov.itantra.core.Language
import java.text.Normalizer

/**
 * Ultra-low-bitrate Script Packing compression.
 *
 * Standard UTF-8 spends three bytes per character on every Indic script.
 * Since the frame header declares the language, the receiver knows which 128-code-point
 * block applies, and subtracting the block base yields a single byte per character.
 *
 * Three bytes become one byte (67% bandwidth reduction!).
 *
 * Escape sequence 0x1B is used for non-Indic characters (e.g. Latin digits, punctuation,
 * ZWJ U+200D and ZWNJ U+200C).
 */
object ScriptPacker {
    private const val ESCAPE = 0x1B
    private const val BLOCK_START = 0x80
    private const val BLOCK_SIZE = 128

    /**
     * Packs [text] for [language].
     */
    fun pack(
        text: String,
        language: Language,
    ): ByteArray {
        val normalised = Normalizer.normalize(text, Normalizer.Form.NFC)
        val out = ArrayList<Byte>(normalised.length + 8)
        val base = language.blockBase

        var i = 0
        while (i < normalised.length) {
            val cp = normalised.codePointAt(i)
            i += Character.charCount(cp)

            when {
                cp <= 0x7F && cp != ESCAPE -> out.add(cp.toByte())

                base != null && cp >= base && cp < base + BLOCK_SIZE ->
                    out.add((BLOCK_START + (cp - base)).toByte())

                else -> {
                    out.add(ESCAPE.toByte())
                    out.add(((cp shr 16) and 0xFF).toByte())
                    out.add(((cp shr 8) and 0xFF).toByte())
                    out.add((cp and 0xFF).toByte())
                }
            }
        }
        return out.toByteArray()
    }

    /**
     * Unpacks [bytes] into a Unicode string.
     */
    fun unpack(
        bytes: ByteArray,
        language: Language,
    ): String {
        val sb = StringBuilder(bytes.size)
        val base = language.blockBase

        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b == ESCAPE -> {
                    require(i + 3 < bytes.size) {
                        "Truncated escape sequence at offset $i of ${bytes.size}"
                    }
                    val cp =
                        ((bytes[i + 1].toInt() and 0xFF) shl 16) or
                            ((bytes[i + 2].toInt() and 0xFF) shl 8) or
                            (bytes[i + 3].toInt() and 0xFF)
                    require(Character.isValidCodePoint(cp)) { "Invalid code point $cp at offset $i" }
                    sb.appendCodePoint(cp)
                    i += 4
                }

                b < BLOCK_START -> {
                    sb.append(b.toChar())
                    i += 1
                }

                else -> {
                    requireNotNull(base) {
                        "Byte 0x${b.toString(16)} needs a script block, but ${language.code} has none"
                    }
                    sb.appendCodePoint(base + (b - BLOCK_START))
                    i += 1
                }
            }
        }
        return sb.toString()
    }

    /**
     * Whether packing is worth doing for this text.
     */
    fun isWorthPacking(
        text: String,
        language: Language,
    ): Boolean = pack(text, language).size < text.toByteArray(Charsets.UTF_8).size
}
