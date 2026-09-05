package `in`.gov.itantra.android.pack

import android.content.Context
import `in`.gov.itantra.core.Language
import java.io.File

object LanguagePackPaths {
    fun root(context: Context): File = File(context.filesDir, "models")

    fun sttDir(context: Context, language: Language): File =
        File(root(context), "stt/${language.code}")

    fun ttsDir(context: Context, language: Language): File =
        File(root(context), "tts/${language.code}")

    fun translationDir(context: Context): File = File(root(context), "translation")

    fun sttModel(context: Context, language: Language): File =
        File(sttDir(context, language), "indicwav2vec-${language.code}-int8.onnx")

    fun sttVocab(context: Context, language: Language): File =
        File(sttDir(context, language), "indicwav2vec-${language.code}-vocab.json")

    fun ttsModel(context: Context, language: Language): File =
        File(ttsDir(context, language), "vits-${language.code}-int8.onnx")

    fun ttsVocab(context: Context, language: Language): File =
        File(ttsDir(context, language), "vits-${language.code}-vocab.json")

    fun translationModel(context: Context): File =
        File(translationDir(context), "indictrans2.onnx")

    fun languageMarker(context: Context, language: Language): File =
        File(sttDir(context, language), ".ready")

    fun translationMarker(context: Context): File =
        File(translationDir(context), ".ready")
}
