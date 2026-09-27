package `in`.gov.itantra.android.pack

import android.content.Context
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.pack.LanguagePackManager
import `in`.gov.itantra.core.pack.PackProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Copies bundled assets when present, optionally fetches packs from [baseUrl], and
 * writes a `.ready` marker for a language once all four of its files are actually
 * present -- never before. A marker written unconditionally would make a failed or
 * skipped (e.g. [baseUrl] blank) install indistinguishable from a real one: the UI
 * would report success, [isLanguagePackReady] would say yes, and the failure would
 * only surface later and much less clearly, as an STT/TTS load error with no obvious
 * connection to "the pack never actually downloaded."
 *
 * [baseUrl] empty (the default) means no network fetch — only assets + markers. Only
 * Hindi (the default language) is bundled in the APK; every other language's files
 * live in the repo's `model-host/` directory and must be served from somewhere
 * reachable at [baseUrl] — see `model-host/README.md`.
 */
class LocalLanguagePackManager(
    private val context: Context,
    private val baseUrl: String = "",
) : LanguagePackManager {

    override fun isLanguagePackReady(language: Language): Boolean {
        val marker = LanguagePackPaths.languageMarker(context, language)
        val model = LanguagePackPaths.sttModel(context, language)
        if (marker.exists() || (model.exists() && model.length() > 0L)) return true
        if (language == Language.HINDI && isHindiAssetPresent()) {
            try {
                LanguagePackPaths.sttDir(context, language).mkdirs()
                marker.writeText(language.code)
            } catch (_: Exception) {}
            return true
        }
        return false
    }

    private fun isHindiAssetPresent(): Boolean {
        return try {
            context.assets.open("models/stt/onnx/indicwav2vec-hi-vocab.json").use { true }
        } catch (_: Exception) {
            false
        }
    }

    private fun isTranslationAvailable(): Boolean {
        return try {
            context.assets.open("models/translation/indictrans2-vocab.json").use { true }
        } catch (_: Exception) {
            false
        }
    }

    override fun isTranslationReady(): Boolean =
        LanguagePackPaths.translationMarker(context).exists() ||
            LanguagePackPaths.translationEncoder(context).exists()

    override suspend fun install(
        languages: Set<Language>,
        includeTranslation: Boolean,
        onProgress: (PackProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val list = languages.toList()
        if (list.isEmpty()) {
            onProgress(PackProgress(null, 1f, "Done"))
            return@withContext
        }
        val failed = mutableListOf<Language>()
        list.forEachIndexed { index, language ->
            if (!isActive) return@withContext
            val base = index.toFloat() / list.size
            val step = 1f / list.size
            val ok = installLanguage(language) { fileFraction, fileLabel ->
                onProgress(
                    PackProgress(
                        language,
                        (base + step * fileFraction).coerceIn(0f, 1f),
                        "Installing ${language.englishName}: $fileLabel",
                    )
                )
            }
            if (!ok) failed += language
        }
        if (includeTranslation && isTranslationAvailable()) {
            installTranslation()
        }
        if (failed.isNotEmpty()) {
            throw IllegalStateException(
                "Could not download: ${failed.joinToString { it.englishName }}. " +
                    "Check your internet connection and try again.",
            )
        }
        onProgress(PackProgress(null, 1f, "Done"))
    }

    override suspend fun uninstall(language: Language) {
        withContext(Dispatchers.IO) {
            LanguagePackPaths.sttDir(context, language).deleteRecursively()
            LanguagePackPaths.ttsDir(context, language).deleteRecursively()
        }
    }

    /** One language's four files, in the order they should download. Returns whether all four are present. */
    private fun installLanguage(language: Language, onFileProgress: (fraction: Float, label: String) -> Unit): Boolean {
        LanguagePackPaths.sttDir(context, language).mkdirs()
        LanguagePackPaths.ttsDir(context, language).mkdirs()

        val files = listOf(
            Triple(
                "models/stt/onnx/indicwav2vec-${language.code}-int8.onnx",
                LanguagePackPaths.sttModel(context, language),
                "indicwav2vec-${language.code}-int8.onnx",
            ),
            Triple(
                "models/stt/onnx/indicwav2vec-${language.code}-vocab.json",
                LanguagePackPaths.sttVocab(context, language),
                "indicwav2vec-${language.code}-vocab.json",
            ),
            Triple(
                "models/tts/vits-${language.code}-int8.onnx",
                LanguagePackPaths.ttsModel(context, language),
                "vits-${language.code}-int8.onnx",
            ),
            Triple(
                "models/tts/vits-${language.code}-vocab.json",
                LanguagePackPaths.ttsVocab(context, language),
                "vits-${language.code}-vocab.json",
            ),
        )
        val step = 1f / files.size
        var allOk = true
        files.forEachIndexed { index, (assetPath, dest, remoteName) ->
            val base = index * step
            val ok = copyAssetOrDownload(assetPath, dest, remoteName) { downloaded, total ->
                val fileFraction = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
                onFileProgress(base + step * fileFraction, remoteName)
            }
            if (!ok) allOk = false
        }
        if (allOk) {
            LanguagePackPaths.languageMarker(context, language).writeText(language.code)
        }
        return allOk
    }

    private fun installTranslation() {
        LanguagePackPaths.translationDir(context).mkdirs()
        copyAssetOrDownload(
            assetPath = "models/translation/indictrans2-encoder-int8.onnx",
            dest = LanguagePackPaths.translationEncoder(context),
            remoteName = "indictrans2-encoder-int8.onnx",
        )
        copyAssetOrDownload(
            assetPath = "models/translation/indictrans2-decoder-int8.onnx",
            dest = LanguagePackPaths.translationDecoder(context),
            remoteName = "indictrans2-decoder-int8.onnx",
        )
        copyAssetOrDownload(
            assetPath = "models/translation/indictrans2-vocab.json",
            dest = LanguagePackPaths.translationVocab(context),
            remoteName = "indictrans2-vocab.json",
        )
        LanguagePackPaths.translationMarker(context).writeText("ok")
    }

    private fun copyAssetOrDownload(
        assetPath: String,
        dest: File,
        remoteName: String,
        onBytes: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Boolean {
        if (dest.exists() && dest.length() > 0L) {
            onBytes(1, 1)
            return true
        }
        dest.parentFile?.mkdirs()
        val temp = File(dest.parentFile, "${dest.name}.part")

        try {
            context.assets.open(assetPath).use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            }
            if (temp.length() > 0L && temp.renameTo(dest)) {
                onBytes(1, 1)
                return true
            }
            temp.delete()
        } catch (_: Exception) {
            temp.delete()
        }

        if (baseUrl.isBlank()) return false
        val initialUrl = "${baseUrl.trimEnd('/')}/$remoteName"
        var connection: HttpURLConnection? = null
        return try {
            connection = openConnectionWithRedirects(initialUrl)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                temp.delete()
                return false
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var downloaded = 0L
                    while (true) {
                        if (Thread.currentThread().isInterrupted) {
                            temp.delete()
                            return false
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onBytes(downloaded, total)
                    }
                }
            }
            if (temp.length() > 0L && temp.renameTo(dest)) {
                true
            } else {
                temp.delete()
                false
            }
        } catch (_: Exception) {
            temp.delete()
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun openConnectionWithRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0
        while (redirects < 5) {
            val url = URL(currentUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; iTantra)")
            }
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308
            ) {
                val newUrl = connection.getHeaderField("Location")
                connection.disconnect()
                if (newUrl != null) {
                    currentUrl = newUrl
                    redirects++
                    continue
                }
            }
            return connection
        }
        throw java.io.IOException("Too many redirects for $initialUrl")
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}

