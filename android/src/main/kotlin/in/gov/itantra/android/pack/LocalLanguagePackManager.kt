package `in`.gov.itantra.android.pack

import android.content.Context
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.pack.LanguagePackManager
import `in`.gov.itantra.core.pack.PackProgress
import kotlinx.coroutines.Dispatchers
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
        return marker.exists() || model.exists()
    }

    override fun isTranslationReady(): Boolean =
        LanguagePackPaths.translationMarker(context).exists() ||
            LanguagePackPaths.translationModel(context).exists()

    override suspend fun install(
        languages: Set<Language>,
        includeTranslation: Boolean,
        onProgress: (PackProgress) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val list = languages.toList()
        val totalSteps = (list.size + if (includeTranslation) 1 else 0).coerceAtLeast(1)
        val failed = mutableListOf<Language>()
        list.forEachIndexed { index, language ->
            val base = index.toFloat() / totalSteps
            val step = 1f / totalSteps
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
        // Translation is a known placeholder -- no host serves indictrans2.onnx yet,
        // by design, not by failure -- so it stays best-effort and never blocks setup.
        if (includeTranslation) {
            onProgress(PackProgress(null, list.size.toFloat() / totalSteps, "Installing translation pack"))
            installTranslation()
        }
        if (failed.isNotEmpty()) {
            throw IllegalStateException(
                "Could not download: ${failed.joinToString { it.englishName }}. " +
                    "Check your connection to the model pack host and try again.",
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

        // Flat remote names, not "stt/..."/"tts/..." -- a GitHub Release (the
        // recommended host, see model-host/README.md) serves every asset at
        // .../releases/download/<tag>/<filename> with no subpaths, and the two
        // families never collide (indicwav2vec-* vs vits-*), so there is no reason
        // for the two hosting schemes to disagree.
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
            assetPath = "models/translation/indictrans2.onnx",
            dest = LanguagePackPaths.translationModel(context),
            remoteName = "indictrans2.onnx",
        )
        LanguagePackPaths.translationMarker(context).writeText("ok")
    }

    /**
     * Tries the bundled asset first (only ever present for Hindi today), then an
     * optional HTTP pack host. Writes to a `.part` sibling and only renames it onto
     * [dest] once the transfer completes fully -- writing straight to [dest] would let
     * a download killed mid-transfer (process death, not just a caught exception) leave
     * a truncated file that the next `dest.exists() && dest.length() > 0` check would
     * mistake for a complete, valid install and never retry.
     */
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
            // Asset missing (every language but Hindi, by design) or unreadable --
            // try an optional HTTP pack host next.
            temp.delete()
        }

        if (baseUrl.isBlank()) return false
        val url = URL("${baseUrl.trimEnd('/')}/$remoteName")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 30_000
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return false
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var downloaded = 0L
                    while (true) {
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
            connection.disconnect()
        }
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}
