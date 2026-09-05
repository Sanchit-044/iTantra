package `in`.gov.itantra.android.stt

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.core.stt.ModelRegistry
import `in`.gov.itantra.core.stt.SttBackend
import `in`.gov.itantra.core.stt.SttException
import org.json.JSONObject
import java.nio.FloatBuffer
import kotlin.math.sqrt

/**
 * IndicWav2Vec CTC acoustic model running on ONNX Runtime.
 *
 * wav2vec2 consumes raw waveform directly -- there is no mel-spectrogram front end to
 * implement or to get subtly wrong, which is a real advantage over a Kaldi-style
 * pipeline. The model emits per-frame logits over a character vocabulary; greedy CTC
 * collapsing turns those into text.
 *
 * This class holds exactly one model at a time, like every other model holder in the
 * app, and shares the process-wide [OrtEnvironment] with the VITS TTS engine so the two
 * do not each pay for a runtime.
 */
class OnnxCtcDecoder(
    private val context: Context,
) : AutoCloseable {

    private var session: OrtSession? = null
    private var vocabulary: CtcVocabulary? = null
    private var loadedLanguage: Language? = null

    @Volatile
    var loadedModelSizeBytes: Long? = null
        private set

    val activeLanguage: Language? get() = loadedLanguage

    fun load(language: Language) {
        if (loadedLanguage == language && session != null) return
        val descriptor = ModelRegistry.descriptor(SttBackend.ONNX_CTC, language)
            ?: throw SttException("no ONNX CTC model bundled for ${language.code}")

        unload()

        try {
            val env = OrtEnvironment.getEnvironment()
            
            val modelFile = java.io.File(context.cacheDir, "ctc_model_${language.code}.onnx")
            if (!modelFile.exists()) {
                context.assets.open(descriptor.assetPath).use { input ->
                    modelFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            session = env.createSession(modelFile.absolutePath, options)
            vocabulary = CtcVocabulary.fromAsset(context, vocabAssetFor(language))
            loadedModelSizeBytes = modelFile.length()
            loadedLanguage = language
        } catch (e: Exception) {
            throw SttException("failed to load ONNX CTC model for ${language.code}", e)
        }
    }

    fun unload() {
        session?.close()
        session = null
        vocabulary = null
        loadedLanguage = null
        loadedModelSizeBytes = null
    }

    /** Decode a complete clip. Blocking. */
    fun decode(clip: AudioClip, language: Language): String {
        if (loadedLanguage != language) load(language)
        val s = session ?: throw SttException("no model loaded")
        val vocab = vocabulary ?: throw SttException("no vocabulary loaded")
        if (clip.pcm.isEmpty()) return ""

        val env = OrtEnvironment.getEnvironment()
        val samples = normalise(clip.pcm)

        var input: OnnxTensor? = null
        try {
            input = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(samples),
                longArrayOf(1, samples.size.toLong()),
            )
            val inputName = s.inputNames.firstOrNull()
                ?: throw SttException("the CTC model declares no inputs")

            return s.run(mapOf(inputName to input)).use { results ->
                vocab.greedyDecode(logitsOf(results[0].value))
            }
        } catch (e: Exception) {
            throw SttException("CTC decode failed for ${language.code}", e)
        } finally {
            input?.close()
        }
    }

    /**
     * wav2vec2 expects zero-mean, unit-variance input. Skipping this normalisation is a
     * classic and very confusing failure: the model runs, produces plausible-looking
     * logits, and returns near-gibberish, because it was trained on normalised audio.
     */
    private fun normalise(pcm: ShortArray): FloatArray {
        val out = FloatArray(pcm.size)
        var mean = 0.0
        for (s in pcm) mean += s
        mean /= pcm.size

        var variance = 0.0
        for (s in pcm) {
            val d = s - mean
            variance += d * d
        }
        variance /= pcm.size
        val std = sqrt(variance).coerceAtLeast(1e-7)

        for (i in pcm.indices) out[i] = ((pcm[i] - mean) / std).toFloat()
        return out
    }

    /** Flattens the [1, T, V] logits tensor into a [T][V] array. */
    private fun logitsOf(raw: Any?): Array<FloatArray> = when (raw) {
        is Array<*> -> {
            @Suppress("UNCHECKED_CAST")
            when (val first = raw.firstOrNull()) {
                is Array<*> -> (raw[0] as Array<FloatArray>)   // [1, T, V]
                is FloatArray -> raw as Array<FloatArray>      // [T, V]
                else -> throw SttException("unexpected CTC logits shape: ${first?.javaClass}")
            }
        }
        else -> throw SttException("unexpected CTC output type: ${raw?.javaClass}")
    }

    override fun close() = unload()

    private fun vocabAssetFor(language: Language): String =
        "models/stt/onnx/indicwav2vec-${language.code}-vocab.json"
}

/**
 * Character vocabulary and greedy CTC collapsing.
 *
 * Pure logic apart from asset loading, and deliberately simple: greedy decoding with no
 * language model. An n-gram rescorer would improve WER meaningfully, but adding one
 * before measuring the greedy baseline would make it impossible to tell whether a
 * disappointing Tamil or Bengali score came from the acoustic model or from a poor LM.
 */
class CtcVocabulary(
    private val idToToken: Array<String>,
    private val blankId: Int,
    private val wordDelimiter: String = "|",
) {
    fun greedyDecode(logits: Array<FloatArray>): String {
        val sb = StringBuilder()
        var previousId = -1

        for (frame in logits) {
            var bestId = 0
            var best = Float.NEGATIVE_INFINITY
            for (i in frame.indices) {
                if (frame[i] > best) { best = frame[i]; bestId = i }
            }

            // CTC collapsing: drop repeats of the same label, then drop blanks.
            if (bestId != previousId && bestId != blankId) {
                val token = idToToken.getOrNull(bestId)
                if (token != null && !SPECIAL_TOKENS.contains(token)) {
                    sb.append(if (token == wordDelimiter) " " else token)
                }
            }
            previousId = bestId
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    companion object {
        private val SPECIAL_TOKENS = setOf("<pad>", "<s>", "</s>", "<unk>")

        fun fromAsset(context: Context, assetPath: String): CtcVocabulary {
            val json = JSONObject(
                context.assets.open(assetPath).use { it.readBytes().toString(Charsets.UTF_8) }
            )
            // HuggingFace vocab.json is token -> id; invert it.
            val keys = json.keys()
            val pairs = mutableListOf<Pair<String, Int>>()
            while (keys.hasNext()) {
                val k = keys.next()
                pairs += k to json.getInt(k)
            }
            val size = (pairs.maxOfOrNull { it.second } ?: -1) + 1
            val table = Array(size) { "" }
            for ((token, id) in pairs) table[id] = token

            val blankId = pairs.firstOrNull { it.first == "<pad>" }?.second ?: 0
            return CtcVocabulary(table, blankId)
        }
    }
}
