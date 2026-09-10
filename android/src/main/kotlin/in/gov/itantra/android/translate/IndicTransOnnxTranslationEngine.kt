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
 * **Encoder input:**  `[source_lang_tag] [target_lang_tag] [BPE-tokens of source text] [EOS]`
 * Both tags are required -- the reference tokenizer's `_src_tokenize` splits its
 * input as `"{src_lang} {tgt_lang} {text}"` and prepends both tags before the
 * text's own BPE pieces. Crucially, **both tags are looked up in the source
 * vocabulary**, including the target-language tag: the whole encoder input is one
 * sequence fed through the encoder's embedding table, which only knows `src_encoder`
 * ids. `tgt_encoder` has no language-tag entries at all -- there is nothing to look
 * them up as on that side.
 *
 * **Decoder seed:**   `[decoder_start_token_id]` -- one token, not a language tag.
 * IndicTrans2 has no decoder-side language conditioning (no `forced_bos_token_id`,
 * no per-language decoder start token in `modeling_indictrans.py`): the target
 * language is communicated entirely by the tag already baked into the encoder
 * input above, which the decoder sees through cross-attention. The start token's
 * id must still come from `tgt_encoder` (decoder embeddings are indexed by the
 * target vocabulary) -- it happens to equal that side's `</s>` id.
 *
 * **Two vocabularies, not one.** IndicTrans2 pairs a single shared BPE model
 * (`model.SRC` and `model.TGT` are byte-identical, so merge rules are shared)
 * with *two different* token-id dictionaries -- `src_encoder` for the entire
 * encoder input (source text *and* both language tags), `tgt_encoder` for
 * decoding output and building `decoder_input_ids`. They agree on fewer than 50
 * of ~122.7k ids, so [srcTokenizer] and [tgtTokenizer] are two separate
 * [BpeTokenizer] instances, never used for the other side's job.
 *
 * ## Known limitation: Hindi ↔ Marathi only for now
 *
 * IndicTrans2 pivots **every** Indic-script sentence through Devanagari
 * internally -- confirmed straight from AI4Bharat's own reference inference
 * code (`IndicTransToolkit/processor.pyx`): source text is transliterated
 * script → Devanagari before BPE encoding, and the model's raw output --
 * always Devanagari -- is transliterated Devanagari → target script afterward.
 * That transliteration layer is not implemented on-device (it needs a
 * structural Unicode mapping table per Brahmic script, not a quick fix), so
 * [translate] refuses any pair outside [SCRIPT_SAFE_LANGUAGES] rather than
 * silently returning the right words in the wrong script -- confirmed
 * reproduced during export verification: hin_Deva → ben_Beng produced the
 * correct Bengali sentence spelled in Devanagari letters, not Bengali script.
 * English is *also* excluded despite being Latin (no transliteration needed):
 * this checkpoint verified correct for eng_Latn → hin_Deva but garbled for the
 * reverse direction, which needs AI4Bharat's separate indic-en checkpoint
 * instead (see [SCRIPT_SAFE_LANGUAGES]'s doc). [ChainedTranslationEngine] falls
 * back to [DictionaryTranslationEngine] (or, failing that, the caller's own
 * untranslated-original fallback) for anything refused here.
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
    private var srcTokenizer: BpeTokenizer? = null
    private var tgtTokenizer: BpeTokenizer? = null

    private data class LoadedModels(
        val srcTokenizer: BpeTokenizer,
        val tgtTokenizer: BpeTokenizer,
        val encoder: OrtSession,
        val decoder: OrtSession,
    )

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

        // See the class doc's "Known limitation" section: without on-device
        // Devanagari transliteration, any other language pair would silently
        // come back as the right words in the wrong script.
        if (source !in SCRIPT_SAFE_LANGUAGES || target !in SCRIPT_SAFE_LANGUAGES) {
            throw TranslationUnavailableException(
                "ONNX translation is limited to Hindi and Marathi for now; " +
                    "${source.englishName} → ${target.englishName} is not yet supported."
            )
        }

        val (srcTok, tgtTok, enc, dec) = synchronized(lock) {
            ensureLoaded()
            val s = srcTokenizer ?: throw TranslationUnavailableException("Source tokenizer not loaded")
            val t = tgtTokenizer ?: throw TranslationUnavailableException("Target tokenizer not loaded")
            val e = encoderSession ?: throw TranslationUnavailableException("Encoder not loaded")
            val d = decoderSession ?: throw TranslationUnavailableException("Decoder not loaded")
            LoadedModels(s, t, e, d)
        }

        // Both language tags are encoder input, so both are looked up via the SOURCE
        // vocabulary -- see the class doc. Using tgtTok here would fail outright
        // (tgt_encoder carries no language tags) and would be wrong even if it didn't.
        val srcLangId = srcTok.langTagId(BpeTokenizer.floresToCode(source))
            ?: throw TranslationUnavailableException(
                "Unknown source language tag for ${source.englishName}"
            )
        val tgtLangId = srcTok.langTagId(BpeTokenizer.floresToCode(target))
            ?: throw TranslationUnavailableException(
                "Unknown target language tag for ${target.englishName}"
            )

        val sourceIds = srcTok.encode(text)
        if (sourceIds.isEmpty()) return text

        // [src_lang_tag] [tgt_lang_tag] [BPE tokens] [EOS] -- see the class doc for why
        // both tags are required, not just the trailing EOS.
        val encoderInput = LongArray(sourceIds.size + 3).also { arr ->
            arr[0] = srcLangId.toLong()
            arr[1] = tgtLangId.toLong()
            for (i in sourceIds.indices) arr[i + 2] = sourceIds[i].toLong()
            arr[sourceIds.size + 2] = srcTok.eosId.toLong()
        }

        val env = OrtEnvironment.getEnvironment()

        val encoderHidden = runEncoder(env, enc, encoderInput)
            ?: throw TranslationUnavailableException("Encoder produced null output")

        try {
            // Decoder seed is a single decoder_start_token_id (== tgtTok's own </s> id),
            // not a language tag -- see the class doc.
            val decodedIds = greedyDecode(env, dec, encoderHidden, encoderInput.size, tgtTok)
            val result = tgtTok.decode(decodedIds)

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
            val (src, tgt) = loadTokenizers(vocabFile.readText(Charsets.UTF_8))
            srcTokenizer = src
            tgtTokenizer = tgt
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

    /**
     * IndicTrans2's tokenizer is two dictionaries (`src`/`tgt`) sharing one BPE merge
     * list, not one shared vocabulary -- see the class doc. [export_translation_models.py]
     * writes exactly this shape.
     */
    private fun loadTokenizers(json: String): Pair<BpeTokenizer, BpeTokenizer> {
        val root = JSONObject(json)

        val mergesArr = root.getJSONArray("merges")
        val merges = ArrayList<String>(mergesArr.length())
        for (i in 0 until mergesArr.length()) {
            merges.add(mergesArr.getString(i))
        }

        fun buildSide(sideKey: String): BpeTokenizer {
            val side = root.getJSONObject(sideKey)

            val vocabObj = side.getJSONObject("vocab")
            val vocab = HashMap<String, Int>(vocabObj.length())
            for (key in vocabObj.keys()) vocab[key] = vocabObj.getInt(key)

            val specialObj = side.getJSONObject("special_tokens")
            val specialTokens = HashMap<String, Int>(specialObj.length())
            for (key in specialObj.keys()) specialTokens[key] = specialObj.getInt(key)

            val langObj = side.getJSONObject("lang_tags")
            val langTags = HashMap<String, Int>(langObj.length())
            for (key in langObj.keys()) langTags[key] = langObj.getInt(key)

            return BpeTokenizer.build(vocab, merges, specialTokens, langTags)
        }

        return buildSide("src") to buildSide("tgt")
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
     * Seeded with a single `decoder_start_token_id` (== [tok]'s `</s>` id) --
     * IndicTrans2 has no decoder-side language tag; see the class doc.
     *
     * This is the "no-cache" variant — simple but O(n²) in output length.
     * For disaster-response messages (typically ≤ 20 tokens) this is fast enough.
     */
    private fun greedyDecode(
        env: OrtEnvironment,
        session: OrtSession,
        encoderHidden: OnnxTensor,
        srcLen: Int,
        tok: BpeTokenizer,
    ): IntArray {
        val generatedIds = mutableListOf(tok.eosId)
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

        return generatedIds.drop(1).toIntArray()
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
        // OrtSession.Result's own `map` field is private; the public surface is
        // Iterable<Map.Entry<String, OnnxValue>> (or indexed get(int)/get(String)).
        for (entry in result) {
            if (entry.key.contains("logits") || entry.key.contains("output") || entry.key.contains("lm_head")) {
                return entry.value.value
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
            srcTokenizer = null
            tgtTokenizer = null
            loaded = false
        }
    }

    private companion object {
        const val TAG = "IndicTransOnnx"

        /**
         * Languages [translate] will actually serve: both Devanagari script, so
         * neither side needs IndicTrans2's internal script transliteration (see the
         * class doc's "Known limitation" section). Every other Indic [Language] uses a
         * different Brahmic script and would come back as the right words in the wrong
         * script.
         *
         * English is deliberately excluded too, despite being Latin (no
         * transliteration needed either): verification with this checkpoint
         * (`indictrans2-indic-indic-dist-320M`) showed hin_Deva → eng_Latn producing
         * garbled Devanagari-script output while eng_Latn → hin_Deva translated
         * correctly -- this model variant is built for Indic↔Indic and is evidently
         * unreliable for Indic→English specifically (AI4Bharat ships a separate
         * `indictrans2-indic-en-dist-200M` checkpoint for that direction, not used
         * here). One working direction isn't enough confidence to ship the other.
         */
        val SCRIPT_SAFE_LANGUAGES = setOf(Language.HINDI, Language.MARATHI)

        /**
         * Hard limit on generated tokens to prevent runaway decoding.
         *
         * Disaster-response messages are rarely longer than 30 words.
         * 128 tokens gives ample headroom for verbose Bengali/Malayalam translations.
         */
        const val MAX_OUTPUT_TOKENS = 128
    }
}
