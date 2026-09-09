package `in`.gov.itantra.android.translate

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import `in`.gov.itantra.android.pack.LanguagePackPaths
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.diag.AppLog
import `in`.gov.itantra.core.translate.BpeTokenizer
import `in`.gov.itantra.core.translate.TranslationEngine
import `in`.gov.itantra.core.translate.TranslationUnavailableException
import org.json.JSONObject
import java.nio.LongBuffer

/**
 * Offline neural machine translation using IndicTrans2 200M (distilled) via ONNX Runtime.
 *
 * ## Architecture
 *
 * IndicTrans2 is a standard Transformer encoder-decoder (seq2seq):
 *
 * 1. **Encoder** — takes source token IDs → produces hidden states.
 * 2. **Decoder** — autoregressively generates target tokens one at a time,
 *    conditioned on the encoder hidden states and all previously emitted tokens.
 *
 * Both models are INT8-quantized for mobile. Greedy decoding (argmax at each step)
 * is used instead of beam search for simplicity and speed — disaster-response
 * messages are short enough that beam search offers negligible quality gain.
 *
 * ## IndicTrans2 framing
 *
 * **Encoder input:**  `[BPE-tokens of source text] [EOS]`
 * The source language is implicit in the script (Devanagari → Hindi, etc.).
 *
 * **Decoder seed:**   `[EOS] [target_lang_tag]`
 * Where `target_lang_tag` is a FLORES-200 code like `hin_Deva`, `ben_Beng`, etc.
 *
 * ## Thread safety
 *
 * ONNX Runtime sessions are thread-safe for concurrent `run()` calls, but
 * [loadModels] / [close] must not race with [translate]. In practice the caller
 * ([ReceivePttTransmissionUseCase]) serialises translation behind a mutex.
 */
class IndicTransOnnxTranslationEngine(
    private val context: Context,
) : TranslationEngine, AutoCloseable {

    private val lock = Any()
    private var encoderSession: OrtSession? = null
    private var decoderSession: OrtSession? = null
    private var tokenizer: BpeTokenizer? = null

    @Volatile
    private var loaded = false

    override val isAvailable: Boolean
        get() {
            if (loaded) return true
            return LanguagePackPaths.translationEncoder(context).exists()
                && LanguagePackPaths.translationDecoder(context).exists()
                && LanguagePackPaths.translationVocab(context).exists()
        }

    override fun translate(text: String, source: Language, target: Language): String {
        if (source == target) return text
        if (text.isBlank()) return text

        val (tok, enc, dec) = synchronized(lock) {
            ensureLoaded()
            val t = tokenizer ?: throw TranslationUnavailableException("Tokenizer not loaded")
            val e = encoderSession ?: throw TranslationUnavailableException("Encoder not loaded")
            val d = decoderSession ?: throw TranslationUnavailableException("Decoder not loaded")
            Triple(t, e, d)
        }

        val tgtLangId = tok.langTagId(BpeTokenizer.floresToCode(target))
            ?: throw TranslationUnavailableException(
                "Unknown target language tag for ${target.englishName}"
            )

        val sourceIds = tok.encode(text)
        if (sourceIds.isEmpty()) return text

        val encoderInput = LongArray(sourceIds.size + 1).also { arr ->
            for (i in sourceIds.indices) arr[i] = sourceIds[i].toLong()
            arr[sourceIds.size] = tok.eosId.toLong()
        }

        val env = OrtEnvironment.getEnvironment()

        val encoderHidden = runEncoder(env, enc, encoderInput)
            ?: throw TranslationUnavailableException("Encoder produced null output")

        try {
            val decodedIds = greedyDecode(env, dec, encoderHidden, encoderInput.size, tgtLangId)
            val result = tok.decode(decodedIds)

            if (result.isBlank()) {
                AppLog.w(TAG, "ONNX translation returned blank for: $text")
                throw TranslationUnavailableException(
                    "Translation model returned empty result for ${source.englishName} → ${target.englishName}"
                )
            }

            AppLog.d(TAG, "${source.code}→${target.code}: \"$text\" → \"$result\"")
            return result
        } finally {
            encoderHidden.close()
        }
    }

    // ---- Model loading ----

    /** Must be called inside synchronized(lock). */
    private fun ensureLoaded() {
        if (loaded) return
        loadModels()
    }

    private fun loadModels() {
        val encoderFile = LanguagePackPaths.translationEncoder(context)
        val decoderFile = LanguagePackPaths.translationDecoder(context)
        val vocabFile = LanguagePackPaths.translationVocab(context)

        if (!encoderFile.exists() || !decoderFile.exists() || !vocabFile.exists()) {
            throw TranslationUnavailableException(
                "Translation model files not found. Install the translation pack to enable " +
                    "cross-language communication."
            )
        }

        try {
            val env = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }

            encoderSession = env.createSession(encoderFile.absolutePath, opts)
            decoderSession = env.createSession(decoderFile.absolutePath, opts)
            tokenizer = loadTokenizer(vocabFile.readText(Charsets.UTF_8))
            loaded = true

            AppLog.i(TAG, "IndicTrans2 ONNX models loaded " +
                "(encoder=${encoderFile.length() / 1024}KB, decoder=${decoderFile.length() / 1024}KB)")
        } catch (e: TranslationUnavailableException) {
            throw e
        } catch (e: Exception) {
            close()
            throw TranslationUnavailableException("Failed to load IndicTrans2 ONNX models: ${e.message}")
        }
    }

    private fun loadTokenizer(json: String): BpeTokenizer {
        val root = JSONObject(json)

        val vocabObj = root.getJSONObject("vocab")
        val vocab = HashMap<String, Int>(vocabObj.length())
        for (key in vocabObj.keys()) {
            vocab[key] = vocabObj.getInt(key)
        }

        val mergesArr = root.getJSONArray("merges")
        val merges = ArrayList<String>(mergesArr.length())
        for (i in 0 until mergesArr.length()) {
            merges.add(mergesArr.getString(i))
        }

        val specialObj = root.getJSONObject("special_tokens")
        val specialTokens = HashMap<String, Int>(specialObj.length())
        for (key in specialObj.keys()) {
            specialTokens[key] = specialObj.getInt(key)
        }

        val langObj = root.getJSONObject("lang_tags")
        val langTags = HashMap<String, Int>(langObj.length())
        for (key in langObj.keys()) {
            langTags[key] = langObj.getInt(key)
        }

        return BpeTokenizer.build(vocab, merges, specialTokens, langTags)
    }

    // ---- Encoder ----

    private fun runEncoder(
        env: OrtEnvironment,
        session: OrtSession,
        inputIds: LongArray,
    ): OnnxTensor? {
        val inputTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(inputIds),
            longArrayOf(1, inputIds.size.toLong()),
        )
        val attentionMask = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(LongArray(inputIds.size) { 1L }),
            longArrayOf(1, inputIds.size.toLong()),
        )

        try {
            val inputs = buildMap {
                for (name in session.inputNames) {
                    when {
                        name.contains("input_ids") || name == "input" -> put(name, inputTensor)
                        name.contains("attention_mask") || name == "mask" -> put(name, attentionMask)
                    }
                }
                if (isEmpty()) {
                    val names = session.inputNames.toList()
                    if (names.isNotEmpty()) put(names[0], inputTensor)
                    if (names.size > 1) put(names[1], attentionMask)
                }
            }

            val result = session.run(inputs)
            try {
                val outputName = session.outputNames.firstOrNull()
                    ?: throw TranslationUnavailableException("Encoder has no output")

                val hidden = result[outputName].get()
                if (hidden is OnnxTensor) {
                    // Clone the tensor data so we can safely close the Result.
                    // OrtSession.Result owns the native memory of all its outputs;
                    // we must copy before closing, or the tensor becomes dangling.
                    val shape = (hidden.info as ai.onnxruntime.TensorInfo).shape
                    val floatData = hidden.floatBuffer
                    val copy = FloatArray(floatData.remaining())
                    floatData.get(copy)
                    return OnnxTensor.createTensor(
                        env,
                        java.nio.FloatBuffer.wrap(copy),
                        shape,
                    )
                } else {
                    AppLog.e(TAG, "Unexpected encoder output type: ${hidden?.javaClass}")
                    return null
                }
            } finally {
                result.close()
            }
        } catch (e: TranslationUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw TranslationUnavailableException("Encoder inference failed: ${e.message}")
        } finally {
            inputTensor.close()
            attentionMask.close()
        }
    }

    // ---- Greedy decoder ----

    /**
     * Autoregressively decode target tokens using greedy search (argmax).
     *
     * The decoder is run once per output token. At each step it receives:
     * - `decoder_input_ids`: all previously generated tokens `[1, seq_so_far]`
     * - `encoder_hidden_states`: the encoder output `[1, src_len, hidden]`
     * - `encoder_attention_mask`: all-ones `[1, src_len]`
     *
     * This is the "no-cache" variant — simple but O(n²) in output length.
     * For disaster-response messages (typically ≤ 20 tokens) this is fast enough.
     */
    private fun greedyDecode(
        env: OrtEnvironment,
        session: OrtSession,
        encoderHidden: OnnxTensor,
        srcLen: Int,
        tgtLangId: Int,
    ): IntArray {
        val tok = tokenizer ?: return IntArray(0)

        val generatedIds = mutableListOf(tok.eosId, tgtLangId)
        val encoderMask = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(LongArray(srcLen) { 1L }),
            longArrayOf(1, srcLen.toLong()),
        )

        val inputNames = session.inputNames.toList()

        try {
            for (step in 0 until MAX_OUTPUT_TOKENS) {
                val decoderInputIds = LongArray(generatedIds.size) { generatedIds[it].toLong() }
                val decoderTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(decoderInputIds),
                    longArrayOf(1, decoderInputIds.size.toLong()),
                )

                try {
                    val inputs = buildDecoderInputs(
                        inputNames, decoderTensor, encoderHidden, encoderMask,
                    )
                    val result = session.run(inputs)
                    try {
                        val logits = extractLogits(result)
                        if (logits == null) {
                            AppLog.e(TAG, "Decoder returned no logits at step $step")
                            break
                        }

                        val lastLogits = lastTimeStep(logits)
                        val nextToken = argmax(lastLogits)

                        if (nextToken == tok.eosId || nextToken == tok.padId) break

                        generatedIds += nextToken
                    } finally {
                        result.close()
                    }
                } finally {
                    decoderTensor.close()
                }
            }
        } finally {
            encoderMask.close()
        }

        return generatedIds.drop(2).toIntArray()
    }

    /**
     * Build the decoder input map, matching whatever names the exported ONNX model uses.
     *
     * Common naming conventions from optimum export:
     * - `input_ids` / `decoder_input_ids`
     * - `encoder_hidden_states` / `encoder_last_hidden_state`
     * - `encoder_attention_mask`
     */
    private fun buildDecoderInputs(
        names: List<String>,
        decoderIds: OnnxTensor,
        encoderHidden: OnnxTensor,
        encoderMask: OnnxTensor,
    ): Map<String, OnnxTensor> {
        val map = HashMap<String, OnnxTensor>(names.size)

        for (name in names) {
            when {
                name.contains("decoder") && name.contains("input") -> map[name] = decoderIds
                name.contains("encoder") && name.contains("hidden") -> map[name] = encoderHidden
                name.contains("encoder") && name.contains("last") -> map[name] = encoderHidden
                name.contains("encoder") && name.contains("attention") -> map[name] = encoderMask
                name.contains("encoder") && name.contains("mask") -> map[name] = encoderMask
                name == "input_ids" && !map.containsValue(decoderIds) -> map[name] = decoderIds
            }
        }

        if (map.isEmpty() && names.size >= 3) {
            map[names[0]] = decoderIds
            map[names[1]] = encoderHidden
            map[names[2]] = encoderMask
        }

        return map
    }

    private fun extractLogits(result: OrtSession.Result): Any? {
        for (name in result.map.keys) {
            if (name.contains("logits") || name.contains("output") || name.contains("lm_head")) {
                return result[name].get().value
            }
        }
        return result[0]?.value
    }

    /**
     * Extract the logits for the last time step from a `[1, seq, vocab]` tensor.
     */
    @Suppress("UNCHECKED_CAST")
    private fun lastTimeStep(raw: Any?): FloatArray {
        return when (raw) {
            is Array<*> -> {
                val batch = raw[0]
                when (batch) {
                    is Array<*> -> (batch as Array<FloatArray>).last()
                    is FloatArray -> batch
                    else -> FloatArray(0)
                }
            }
            is FloatArray -> raw
            else -> FloatArray(0)
        }
    }

    private fun argmax(logits: FloatArray): Int {
        if (logits.isEmpty()) return 0
        var maxIdx = 0
        var maxVal = logits[0]
        for (i in 1 until logits.size) {
            if (logits[i] > maxVal) {
                maxVal = logits[i]
                maxIdx = i
            }
        }
        return maxIdx
    }

    // ---- Cleanup ----

    override fun close() {
        synchronized(lock) {
            encoderSession?.close()
            decoderSession?.close()
            encoderSession = null
            decoderSession = null
            tokenizer = null
            loaded = false
        }
    }

    private companion object {
        const val TAG = "IndicTransOnnx"

        /**
         * Hard limit on generated tokens to prevent runaway decoding.
         *
         * Disaster-response messages are rarely longer than 30 words.
         * 128 tokens gives ample headroom for verbose Bengali/Malayalam translations.
         */
        const val MAX_OUTPUT_TOKENS = 128
    }
}
