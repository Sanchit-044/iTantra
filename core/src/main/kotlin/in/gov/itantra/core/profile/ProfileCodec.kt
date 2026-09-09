package `in`.gov.itantra.core.profile

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PROFILE packet body. Name is required; thumbnail may be omitted if too large
 * or the peer has no photo.
 *
 *     u8   version
 *     u16  nameLen
 *     u8[] name (UTF-8)
 *     u32  thumbLen
 *     u8[] thumbnail JPEG (optional)
 */
object ProfileCodec {
    const val VERSION: Byte = 1

    fun encode(name: String, thumbnailJpeg: ByteArray?): ByteArray {
        val normalized = ProfileRules.normalizeName(name)
        val nameBytes = normalized.toByteArray(Charsets.UTF_8)
        val thumb = thumbnailJpeg
            ?.takeIf { it.isNotEmpty() && it.size <= ProfileRules.MAX_THUMB_BYTES }
            ?: ByteArray(0)
        return ByteBuffer.allocate(1 + 2 + nameBytes.size + 4 + thumb.size)
            .order(ByteOrder.BIG_ENDIAN)
            .apply {
                put(VERSION)
                putShort(nameBytes.size.toShort())
                put(nameBytes)
                putInt(thumb.size)
                if (thumb.isNotEmpty()) put(thumb)
            }
            .array()
    }

    fun decode(payload: ByteArray): OperatorProfile? {
        if (payload.size < 7) return null
        return try {
            val buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN)
            if (buf.get() != VERSION) return null
            val nameLen = buf.short.toInt() and 0xFFFF
            if (nameLen !in 1..ProfileRules.MAX_NAME_CHARS * 4) return null
            if (buf.remaining() < nameLen + 4) return null
            val nameBytes = ByteArray(nameLen).also { buf.get(it) }
            val name = ProfileRules.normalizeName(nameBytes.toString(Charsets.UTF_8))
            if (name.isEmpty()) return null
            val thumbLen = buf.int
            if (thumbLen < 0 || thumbLen > ProfileRules.MAX_THUMB_BYTES) return null
            if (buf.remaining() < thumbLen) return null
            val thumb = if (thumbLen == 0) {
                null
            } else {
                ByteArray(thumbLen).also { buf.get(it) }
            }
            OperatorProfile(
                name = name,
                photoPresent = thumb != null,
                thumbnailJpeg = thumb,
            )
        } catch (_: Exception) {
            null
        }
    }
}
