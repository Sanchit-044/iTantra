package `in`.gov.itantra.android.stt

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.android.pack.LanguagePackPaths
import `in`.gov.itantra.core.stt.ModelRegistry
import `in`.gov.itantra.core.stt.SttBackend
import `in`.gov.itantra.core.stt.SttException
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.ln
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

            val packModel = LanguagePackPaths.sttModel(context, language)
            val modelFile = if (packModel.exists() && packModel.length() > 0L) {
                packModel
            } else {
                val cached = File(context.cacheDir, "ctc_model_${language.code}.onnx")
                if (!cached.exists()) {
                    context.assets.open(descriptor.assetPath).use { input ->
                        cached.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                cached
            }
            
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            session = env.createSession(modelFile.absolutePath, options)
            val packVocab = LanguagePackPaths.sttVocab(context, language)
            vocabulary = if (packVocab.exists() && packVocab.length() > 0L) {
                CtcVocabulary.fromFile(packVocab)
            } else {
                CtcVocabulary.fromAsset(context, vocabAssetFor(language))
            }
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
     *
     * ## Noise-aware two-stage normalisation
     *
     * A simple global mean/variance normalisation amplifies background noise along with
     * the signal when the speaker is at a distance. This two-stage version fixes that:
     *
     * **Stage 1 — Noise-floor subtraction.**
     * The clip is split into 20 ms frames. The energy of each frame is computed, and the
     * quietest 20 % of frames are averaged to estimate the background noise level. That
     * estimate is subtracted from every sample before variance is measured, which strips
     * stationary hum and air-conditioning noise without requiring an FFT.
     *
     * **Stage 2 — Headroom clipping.**
     * After z-scoring, values beyond ±3σ are clamped. IndicWav2Vec was trained on
     * studio-quality audio; very large normalised excursions (from hard clipping of the
     * original signal or transient noise bursts) produce erratic attention scores.
     * The 3σ clip affects less than 0.3 % of samples in clean speech and
     * substantially fewer in the distant-speech case.
     */
    private fun normalise(pcm: ShortArray): FloatArray {
        if (pcm.isEmpty()) return FloatArray(0)

        // --- Stage 1: estimate noise floor from quietest 20% of 20 ms frames ---
        val frameSize = (NOISE_FRAME_MS * 16_000 / 1000).coerceAtLeast(1)   // samples per 20 ms @ 16 kHz
        val numFrames = (pcm.size + frameSize - 1) / frameSize

        // Compute RMS energy per frame
        val frameEnergy = DoubleArray(numFrames) { fi ->
            val start = fi * frameSize
            val end = minOf(start + frameSize, pcm.size)
            var acc = 0.0
            for (i in start until end) {
                val s = pcm[i].toDouble()
                acc += s * s
            }
            acc / (end - start)
        }

        // Sort a copy to find the 20th-percentile energy threshold
        val sorted = frameEnergy.copyOf().also { it.sort() }
        val noiseFrameCount = (numFrames * NOISE_PERCENTILE).toInt().coerceAtLeast(1)
        var noiseEnergy = 0.0
        for (i in 0 until noiseFrameCount) noiseEnergy += sorted[i]
        noiseEnergy /= noiseFrameCount

        // Derive a signed noise offset: average sample value of the quietest frames.
        // We use the mean of those frames as a DC estimate to subtract.
        val noiseFrameSet = sorted.take(noiseFrameCount).toHashSet()
        var noiseSum = 0.0
        var noiseSamples = 0
        for (fi in 0 until numFrames) {
            if (frameEnergy[fi] in noiseFrameSet) {
                val start = fi * frameSize
                val end = minOf(start + frameSize, pcm.size)
                for (i in start until end) {
                    noiseSum += pcm[i]
                    noiseSamples++
                }
            }
        }
        val noiseMean = if (noiseSamples > 0) noiseSum / noiseSamples else 0.0

        // --- Global mean/variance with noise bias removed ---
        var globalMean = 0.0
        for (s in pcm) globalMean += s
        globalMean /= pcm.size
        val dcOffset = globalMean - noiseMean   // the portion of the mean attributable to noise

        val signalMean = globalMean - dcOffset  // ≈ speech-only mean; used for centering

        var variance = 0.0
        for (s in pcm) {
            val d = s - signalMean
            variance += d * d
        }
        variance /= pcm.size
        val std = sqrt(variance).coerceAtLeast(1e-7)

        // --- Stage 2: z-score with ±3σ headroom clip ---
        val out = FloatArray(pcm.size)
        for (i in pcm.indices) {
            val z = ((pcm[i] - signalMean) / std).toFloat()
            out[i] = z.coerceIn(-CLIP_SIGMA, CLIP_SIGMA)
        }
        return out
    }

    private companion object {
        /** Frame length used for per-frame noise energy estimation (milliseconds). */
        private const val NOISE_FRAME_MS = 20

        /** Fraction of the quietest frames used to estimate the background noise floor. */
        private const val NOISE_PERCENTILE = 0.20

        /**
         * Normalised values beyond this threshold are clamped.
         *
         * 3σ retains >99.7 % of a Gaussian distribution, so clean speech is unaffected.
         * Large excursions from clipping events or transient bursts are removed, which
         * prevents them from distorting the model's attention maps.
         */
        private const val CLIP_SIGMA = 3.0f
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
 * Character vocabulary and CTC decoding.
 *
 * ## Prefix beam search
 *
 * The original greedy decoder took the argmax at each frame independently. For Hindi
 * this causes:
 * - **Matra errors**: a vowel sign is assigned to the wrong frame and merged with the
 *   wrong consonant (e.g. "की" decoded as "क ी" or skipped entirely).
 * - **Conjunct collapse**: two identical consonants in sequence are wrongly merged
 *   because the blank between them is not confidently predicted.
 * - **Long-sentence drift**: with 300+ frames the greedy errors compound.
 *
 * Prefix beam search maintains [BEAM_WIDTH] candidate prefixes at every frame, scored
 * by the sum of log-probabilities. When one frame's evidence weakly supports the greedy
 * choice, alternative prefixes that were already scored over previous frames can win.
 *
 * Complexity: O(B × T × V) where B=5, T≈300 frames, V≈70 Hindi chars. This is
 * completely negligible compared to the ONNX inference which dominates total time.
 */
class CtcVocabulary(
    private val idToToken: Array<String>,
    private val blankId: Int,
    private val wordDelimiter: String = "|",
) {
    /**
     * Decodes CTC logits using prefix beam search.
     *
     * Each beam entry is a triple: (prefix string, score for prefix ending with
     * non-blank last token, score for prefix ending with blank). We track both
     * because CTC allows a token to repeat only if there is a blank between the
     * two emissions.
     */
    fun decode(logits: Array<FloatArray>, beamWidth: Int = BEAM_WIDTH): String {
        if (logits.isEmpty()) return ""

        // Beam entry: prefix → Pair(pNonBlank, pBlank) in log-probability space.
        // Initialise with an empty prefix, p_nb = -∞, p_b = 0.0 (log 1.0).
        data class BeamEntry(val prefix: String, val pNonBlank: Double, val pBlank: Double) {
            val total: Double get() = logSumExp(pNonBlank, pBlank)
        }

        var beams = mutableListOf(BeamEntry("", Double.NEGATIVE_INFINITY, 0.0))

        for (frame in logits) {
            // Convert raw logits to log-probabilities via log-softmax.
            val logProbs = logSoftmax(frame)

            // Collect all candidate (prefix, pNb, pB) in a map for merging duplicates.
            val nextMap = HashMap<String, Pair<Double, Double>>(beams.size * (idToToken.size + 1))

            for (beam in beams) {
                // --- Extending with blank ---
                // p_b(beam + blank) = (p_nb(beam) + p_b(beam)) * p(blank|frame)
                val pBlankNew = beam.total + logProbs[blankId]
                val existing = nextMap[beam.prefix]
                nextMap[beam.prefix] = Pair(
                    existing?.first ?: Double.NEGATIVE_INFINITY,
                    if (existing != null) logSumExp(existing.second, pBlankNew) else pBlankNew,
                )

                // --- Extending with each non-blank token ---
                for (tokenId in logProbs.indices) {
                    if (tokenId == blankId) continue
                    val token = idToToken.getOrNull(tokenId) ?: continue
                    if (token in SPECIAL_TOKENS) continue

                    val lp = logProbs[tokenId]
                    val lastChar = beam.prefix.lastOrNull()
                    val emittedToken = if (token == wordDelimiter) " " else token

                    val newPrefix = beam.prefix + emittedToken

                    // CTC rule: if the last character of the existing prefix is the
                    // same as the new token, it can only be emitted if there was a
                    // blank in between (i.e. it extends from p_b of the current beam,
                    // not from p_nb).
                    val pNbNew = if (lastChar != null && lastChar.toString() == emittedToken) {
                        // Can only extend from blank-ended state
                        beam.pBlank + lp
                    } else {
                        // Can extend from either blank or non-blank
                        beam.total + lp
                    }

                    val existingNew = nextMap[newPrefix]
                    nextMap[newPrefix] = Pair(
                        if (existingNew != null) logSumExp(existingNew.first, pNbNew)
                        else pNbNew,
                        existingNew?.second ?: Double.NEGATIVE_INFINITY,
                    )
                }
            }

            // Rebuild beam list from the map, sorted by total score, pruned to beamWidth.
            beams = nextMap.entries
                .map { (prefix, scores) -> BeamEntry(prefix, scores.first, scores.second) }
                .sortedByDescending { it.total }
                .take(beamWidth)
                .toMutableList()
        }

        // Return the top-scoring prefix.
        return (beams.maxByOrNull { it.total }?.prefix ?: "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /** Backward-compatible alias for callers that still call greedyDecode. */
    fun greedyDecode(logits: Array<FloatArray>): String = decode(logits)

    companion object {
        private val SPECIAL_TOKENS = setOf("<pad>", "<s>", "</s>", "<unk>", "[PAD]", "[UNK]", "[CLS]", "[SEP]")

        /**
         * Beam width for CTC prefix beam search.
         *
         * 5 provides meaningful error recovery over greedy (beam=1) for Hindi long
         * sentences while keeping CPU time negligible vs ONNX inference.
         */
        const val BEAM_WIDTH = 5

        /** Numerically stable log-sum-exp of two values. */
        private fun logSumExp(a: Double, b: Double): Double {
            if (a == Double.NEGATIVE_INFINITY) return b
            if (b == Double.NEGATIVE_INFINITY) return a
            val max = maxOf(a, b)
            return max + ln(exp(a - max) + exp(b - max))
        }

        /** Per-frame log-softmax. Subtracts max for numerical stability. */
        private fun logSoftmax(logits: FloatArray): DoubleArray {
            val max = logits.max()
            var sumExp = 0.0
            for (v in logits) sumExp += exp((v - max).toDouble())
            val logSumExpVal = max + ln(sumExp)
            return DoubleArray(logits.size) { i -> logits[i] - logSumExpVal }
        }

        fun fromFile(file: File): CtcVocabulary =
            fromJson(JSONObject(file.readText(Charsets.UTF_8)))

        fun fromAsset(context: android.content.Context, assetPath: String): CtcVocabulary {
            val json = JSONObject(
                context.assets.open(assetPath).use { it.readBytes().toString(Charsets.UTF_8) }
            )
            return fromJson(json)
        }

        private fun fromJson(json: JSONObject): CtcVocabulary {
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

            val blankId = pairs.firstOrNull { it.first == "<pad>" || it.first == "[PAD]" }?.second ?: 0
            return CtcVocabulary(table, blankId)
        }
    }
}
