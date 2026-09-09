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
 * Copies bundled assets when present, optionally fetches packs from [baseUrl],
 * and always writes a `.ready` marker so the UI can record the user's selection
 * even before ONNX weights are dropped in.
 *
 * [baseUrl] empty (the default) means no network fetch — only assets + markers.
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
        list.forEachIndexed { index, language ->
            onProgress(
                PackProgress(
                    language,
                    index.toFloat() / (list.size + if (includeTranslation) 1 else 0).coerceAtLeast(1),
                    "Installing ${language.englishName}",
                )
            )
            installLanguage(language)
        }
        if (includeTranslation) {
            onProgress(PackProgress(null, 0.95f, "Installing translation pack"))
            installTranslation()
        }
        onProgress(PackProgress(null, 1f, "Done"))
    }

    override suspend fun uninstall(language: Language) {
        withContext(Dispatchers.IO) {
            LanguagePackPaths.sttDir(context, language).deleteRecursively()
            LanguagePackPaths.ttsDir(context, language).deleteRecursively()
        }
    }

    private fun installLanguage(language: Language) {
        LanguagePackPaths.sttDir(context, language).mkdirs()
        LanguagePackPaths.ttsDir(context, language).mkdirs()

        copyAssetOrDownload(
            assetPath = "models/stt/onnx/indicwav2vec-${language.code}-int8.onnx",
            dest = LanguagePackPaths.sttModel(context, language),
            remoteName = "stt/indicwav2vec-${language.code}-int8.onnx",
        )
        copyAssetOrDownload(
            assetPath = "models/stt/onnx/indicwav2vec-${language.code}-vocab.json",
            dest = LanguagePackPaths.sttVocab(context, language),
            remoteName = "stt/indicwav2vec-${language.code}-vocab.json",
        )
        copyAssetOrDownload(
            assetPath = "models/tts/vits-${language.code}-int8.onnx",
            dest = LanguagePackPaths.ttsModel(context, language),
            remoteName = "tts/vits-${language.code}-int8.onnx",
        )
        copyAssetOrDownload(
            assetPath = "models/tts/vits-${language.code}-vocab.json",
            dest = LanguagePackPaths.ttsVocab(context, language),
            remoteName = "tts/vits-${language.code}-vocab.json",
        )
        LanguagePackPaths.languageMarker(context, language).writeText(language.code)
    }

    private fun installTranslation() {
        LanguagePackPaths.translationDir(context).mkdirs()
        copyAssetOrDownload(
            assetPath = "models/translation/indictrans2.onnx",
            dest = LanguagePackPaths.translationModel(context),
            remoteName = "translation/indictrans2.onnx",
        )
        LanguagePackPaths.translationMarker(context).writeText("ok")
    }

    private fun copyAssetOrDownload(assetPath: String, dest: File, remoteName: String) {
        if (dest.exists() && dest.length() > 0L) return
        dest.parentFile?.mkdirs()
        try {
            context.assets.open(assetPath).use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
            return
        } catch (_: Exception) {
            // Asset missing — try an optional HTTP pack host next.
        }
        if (baseUrl.isBlank()) return
        val url = URL("${baseUrl.trimEnd('/')}/$remoteName")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 30_000
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return
            connection.inputStream.use { input ->
                dest.outputStream().use { input.copyTo(it) }
            }
        } catch (_: Exception) {
            dest.delete()
        } finally {
            connection.disconnect()
        }
    }
}
