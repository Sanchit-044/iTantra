package `in`.gov.itantra.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import `in`.gov.itantra.core.profile.OperatorProfile
import `in`.gov.itantra.core.profile.ProfileRules
import `in`.gov.itantra.core.profile.ProfileStore
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FileProfileStore(
    context: Context,
) : ProfileStore {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _profile = MutableStateFlow(read())
    override val snapshot: OperatorProfile get() = _profile.value
    override val profile: StateFlow<OperatorProfile> = _profile.asStateFlow()

    override suspend fun save(name: String) {
        val normalized = ProfileRules.normalizeName(name)
        require(ProfileRules.isComplete(normalized, photoFile().isValidPhoto())) {
            "name and photo are required"
        }
        prefs.edit()
            .putBoolean(KEY_DONE, true)
            .putString(KEY_NAME, normalized)
            .apply()
        _profile.update { read() }
    }

    override fun thumbnailJpeg(): ByteArray? = compressFile(photoFile(), THUMB_MAX_EDGE, THUMB_QUALITY)

    override fun photoPath(): String? = photoFile().takeIf { it.isValidPhoto() }?.absolutePath

    fun photoFile(): File = File(app.filesDir, PHOTO_RELATIVE).apply { parentFile?.mkdirs() }

    fun writePhotoFromUri(uri: Uri) {
        val source = app.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("could not read photo")
        val decoded = source.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("could not decode photo")
        try {
            val scaled = scale(decoded, DISPLAY_MAX_EDGE)
            photoFile().outputStream().use { out ->
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, DISPLAY_QUALITY, out)) {
                    throw IllegalStateException("could not save photo")
                }
            }
            if (scaled !== decoded) scaled.recycle()
        } finally {
            decoded.recycle()
        }
        _profile.update { read() }
    }

    private fun read(): OperatorProfile {
        val name = prefs.getString(KEY_NAME, "") ?: ""
        val file = photoFile()
        val present = file.isValidPhoto()
        return OperatorProfile(
            name = name,
            photoPresent = present,
            photoPath = if (present) file.absolutePath else null,
            recorded = prefs.getBoolean(KEY_DONE, false),
        )
    }

    private companion object {
        const val PREFS_NAME = "itantra_profile"
        const val KEY_DONE = "profile_done"
        const val KEY_NAME = "profile_name"
        const val PHOTO_RELATIVE = "profile/avatar.jpg"
        const val DISPLAY_MAX_EDGE = 512
        const val DISPLAY_QUALITY = 80
        const val THUMB_MAX_EDGE = 128
        const val THUMB_QUALITY = 70

        fun File.isValidPhoto(): Boolean = isFile && length() > 32

        fun scale(src: Bitmap, maxEdge: Int): Bitmap {
            val edge = maxOf(src.width, src.height)
            if (edge <= maxEdge) return src
            val ratio = maxEdge.toFloat() / edge
            val w = (src.width * ratio).toInt().coerceAtLeast(1)
            val h = (src.height * ratio).toInt().coerceAtLeast(1)
            return Bitmap.createScaledBitmap(src, w, h, true)
        }

        fun compressFile(file: File, maxEdge: Int, quality: Int): ByteArray? {
            if (!file.isValidPhoto()) return null
            val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            return try {
                val scaled = scale(decoded, maxEdge)
                val out = ByteArrayOutputStream()
                var q = quality
                scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
                while (out.size() > ProfileRules.MAX_THUMB_BYTES && q > 30) {
                    out.reset()
                    q -= 15
                    scaled.compress(Bitmap.CompressFormat.JPEG, q, out)
                }
                if (scaled !== decoded) scaled.recycle()
                val bytes = out.toByteArray()
                bytes.takeIf { it.size <= ProfileRules.MAX_THUMB_BYTES }
            } finally {
                decoded.recycle()
            }
        }
    }
}
