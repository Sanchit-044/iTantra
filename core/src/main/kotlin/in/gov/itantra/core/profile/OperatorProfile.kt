package `in`.gov.itantra.core.profile

import kotlinx.coroutines.flow.Flow

/**
 * Local operator identity, or a peer identity received after pairing.
 * [photoPath] is this phone's file (or a cached peer thumbnail). Not sent on the wire.
 * [thumbnailJpeg] is the compact JPEG used for PROFILE packets and peer avatars.
 */
data class OperatorProfile(
    val name: String = "",
    val photoPresent: Boolean = false,
    val photoPath: String? = null,
    val thumbnailJpeg: ByteArray? = null,
    val recorded: Boolean = false,
) {
    val displayName: String
        get() = ProfileRules.normalizeName(name).ifBlank { FALLBACK_NAME }

    /** Saved on this phone with both a name and a photo file still present. */
    val isComplete: Boolean
        get() = recorded && ProfileRules.isComplete(name, photoPresent)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OperatorProfile) return false
        return name == other.name &&
            photoPresent == other.photoPresent &&
            photoPath == other.photoPath &&
            recorded == other.recorded &&
            thumbnailJpeg.contentEquals(other.thumbnailJpeg)
    }

    override fun hashCode(): Int {
        var r = name.hashCode()
        r = 31 * r + photoPresent.hashCode()
        r = 31 * r + (photoPath?.hashCode() ?: 0)
        r = 31 * r + recorded.hashCode()
        r = 31 * r + (thumbnailJpeg?.contentHashCode() ?: 0)
        return r
    }

    companion object {
        const val FALLBACK_NAME = "Peer"
    }
}

object ProfileRules {
    const val MAX_NAME_CHARS = 40
    const val MAX_THUMB_BYTES = 12 * 1024

    fun normalizeName(raw: String): String =
        raw.trim().replace(WHITESPACE, " ").take(MAX_NAME_CHARS)

    fun isComplete(name: String, photoPresent: Boolean): Boolean =
        normalizeName(name).isNotEmpty()

    private val WHITESPACE = Regex("\\s+")
}

interface ProfileStore {
    val snapshot: OperatorProfile
    val profile: Flow<OperatorProfile>

    suspend fun save(name: String)

    fun thumbnailJpeg(): ByteArray?

    fun photoPath(): String?
}
