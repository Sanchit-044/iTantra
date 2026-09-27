package `in`.gov.itantra.android.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.os.Handler
import android.os.Looper
import `in`.gov.itantra.android.pack.LanguagePackPaths
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.core.audio.AudioFormat
import `in`.gov.itantra.core.diag.MetricAccumulator
import `in`.gov.itantra.core.diag.RealTimeFactorAccumulator
import `in`.gov.itantra.core.tts.TextNormalizer
import `in`.gov.itantra.core.tts.TtsEngine
import `in`.gov.itantra.core.tts.TtsException
import `in`.gov.itantra.core.tts.TtsState
import org.json.JSONObject
import java.nio.LongBuffer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Module B3, VITS-on-ONNX backend.
 *
 * One voice resident at a time, INT8-quantised, CPU only.
 *
 * ## Fallback path
 *
 * When the ONNX model file is absent or corrupt (language pack not downloaded, cache
 * cleared, first-run before pack install), [loadVoice] marks [useFallbackTts] = true
 * and [synthesizeNormalised] routes the utterance through Android [TextToSpeech] instead
 * of throwing. This is the "hackathon fallback" the original comment described -- it is
 * now actually reachable.
 *
 * The fallback surfaces a logcat warning so the missing pack is visible; it does not
 * silently degrade without leaving a trace.
 *
 * ## Lock discipline
 *
 * [lock] protects mutable state (session, tokenizer, activeLanguage, state). It is held
 * only to read/write those fields -- never across a blocking call. Specifically, the
 * ONNX [OrtSession.run] call (which can take 5-15 seconds on a slow device) is
 * intentionally performed *outside* [lock]. This prevents [loadVoice] from blocking
 * behind an in-flight synthesis when a language switch is requested.
 */
class VitsOnnxTtsEngine(
    private val context: Context,
    private val voices: Map<Language, VitsVoiceDescriptor> = VitsVoiceDescriptor.DEFAULTS,
    /** Speaking rate. Above 1.0 is slower. Alerts may want a slightly slower rate. */
    private val lengthScale: Float = 1.0f,
    private val noiseScale: Float = 0.667f,
    private val noiseScaleW: Float = 0.8f,
) : TtsEngine {

    /** Where a voice's ONNX graph and its token vocabulary live inside the APK. */
    data class VitsVoiceDescriptor(
        val language: Language,
        val modelAsset: String,
        val vocabAsset: String,
        val sampleRate: Int = 16_000,
    ) {
        companion object {
            val DEFAULTS: Map<Language, VitsVoiceDescriptor> =
                Language.entries.associateWith { lang ->
                    VitsVoiceDescriptor(
                        language = lang,
                        modelAsset = "models/tts/vits-${lang.code}-int8.onnx",
                        vocabAsset = "models/tts/vits-${lang.code}-vocab.json",
                    )
                }
        }
    }

    @Volatile
    override var state: TtsState = TtsState.IDLE
        private set

    @Volatile
    override var activeLanguage: Language? = null
        private set

    override var outputFormat: AudioFormat = AudioFormat(16_000)
        private set

    private var environment: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var tokenizer: VitsTokenizer? = null
    private val normalizers = mutableMapOf<Language, TextNormalizer>()

    /** Exposed to Module B7. */
    val synthesisLatency = MetricAccumulator("tts.synthesis")
    val realTimeFactor = RealTimeFactorAccumulator()

    @Volatile
    var loadedModelSizeBytes: Long? = null
        private set

    private val fallbackCount = AtomicLong(0)
    private val synthesisedCount = AtomicLong(0)
    private val underrunCount = AtomicLong(0)

    val androidTtsFallbackCount: Long get() = fallbackCount.get()
    val utterancesSynthesised: Long get() = synthesisedCount.get()
    val underruns: Long get() = underrunCount.get()

    /**
     * Guards mutable ONNX state: session, tokenizer, environment, activeLanguage, state.
     * NOT held across blocking ONNX inference -- see class-level KDoc.
     */
    private val lock = Any()

    /**
     * True when the ONNX voice failed to load and [synthesizeNormalised] should route
     * through the Android TTS system engine instead.
     */
    @Volatile
    private var useFallbackTts = false

    /**
     * Lazily initialised Android TTS engine used when the ONNX pack is unavailable.
     * Accessed only from synthesis threads; guarded by [fallbackTtsLock].
     */
    private var fallbackTts: TextToSpeech? = null
    @Volatile
    private var fallbackTtsReady = false
    private val fallbackTtsLock = Any()

    override fun loadVoice(language: Language) {
        synchronized(lock) {
            loadVoiceLocked(language)
        }
    }

    private fun loadVoiceLocked(language: Language) {
        if (activeLanguage == language && session != null) {
            useFallbackTts = false
            return
        }
        val descriptor = voices[language]
            ?: throw TtsException("no bundled VITS voice for ${language.code}")

        // Free before allocating: two resident voices would breach the memory budget.
        unloadVoiceLocked()

        try {
            val env = OrtEnvironment.getEnvironment()

            val packModel = LanguagePackPaths.ttsModel(context, language)
            val modelFile = if (packModel.exists() && packModel.length() > 0L) {
                packModel
            } else {
                val cached = java.io.File(context.cacheDir, "vits_model_${language.code}.onnx")
                if (!cached.exists()) {
                    context.assets.open(descriptor.modelAsset).use { input ->
                        cached.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                cached
            }

            val options = OrtSession.SessionOptions().apply {
                // Two threads. The target device is a low-core handset and oversubscribing
                // makes synthesis slower, not faster, while competing with audio playback.
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                // No NNAPI / GPU execution provider: the target device has no usable GPU,
                // and NNAPI INT8 support across low-end OEM drivers is inconsistent
                // enough that CPU is both faster and far more predictable.
            }

            session = env.createSession(modelFile.absolutePath, options)
            environment = env
            // The vocabulary must come from wherever the model came from. Loading an
            // APK-bundled vocabulary alongside a sideloaded pack model silently pairs a
            // graph with the wrong symbol table: inference succeeds and the audio is
            // noise. OnnxCtcDecoder already prefers the pack; this now matches it.
            val packVocab = LanguagePackPaths.ttsVocab(context, language)
            tokenizer = if (packVocab.exists() && packVocab.length() > 0L) {
                VitsTokenizer.fromFile(packVocab, language)
            } else {
                VitsTokenizer.fromAsset(context, descriptor.vocabAsset, language)
            }
            outputFormat = AudioFormat(descriptor.sampleRate)
            loadedModelSizeBytes = modelFile.length()
            activeLanguage = language
            useFallbackTts = false
            state = TtsState.VOICE_LOADED
        } catch (e: Exception) {
            // ONNX pack is missing or corrupt. Mark the fallback path so that
            // synthesizeNormalised routes through Android TTS instead of throwing
            // silently. The warning is intentionally prominent -- a missing pack is a
            // configuration problem that needs to be visible in logcat.
            android.util.Log.w(
                "iTantra-TTS",
                "VITS pack unavailable for ${language.code} — falling back to Android TTS. " +
                    "Cause: ${e.message}",
            )
            useFallbackTts = true
            activeLanguage = language
            state = TtsState.VOICE_LOADED
        }
    }

    override fun unloadVoice() {
        synchronized(lock) { unloadVoiceLocked() }
    }

    private fun unloadVoiceLocked() {
        session?.close()
        session = null
        tokenizer = null
        activeLanguage = null
        loadedModelSizeBytes = null
        useFallbackTts = false
        // Shut down the Android TTS engine if it was created for the fallback path.
        synchronized(fallbackTtsLock) {
            fallbackTts?.shutdown()
            fallbackTts = null
            fallbackTtsReady = false
        }
        // The OrtEnvironment is a process-wide singleton and is deliberately NOT closed:
        // closing it would tear down the runtime shared with the STT backend.
        if (state != TtsState.ERROR) state = TtsState.IDLE
    }

    override fun synthesize(text: String, language: Language): AudioClip {
        val normalizer = normalizers.getOrPut(language) { TextNormalizer(language) }
        return synthesizeNormalised(normalizer.normalize(text), language)
    }

    override fun synthesizeNormalised(text: String, language: Language): AudioClip {
        // Fast path: ONNX pack is unavailable — use Android TTS and return.
        if (useFallbackTts) {
            android.util.Log.w(
                "iTantra-TTS",
                "VITS unavailable for ${language.code}; routing utterance through Android TTS",
            )
            fallbackCount.incrementAndGet()
            return synthesizeViaAndroidTts(text, language)
        }

        // Grab lightweight references while holding the lock (cheap), then release
        // before the heavy ONNX inference. This means loadVoice() can run concurrently
        // with an in-flight synthesis without blocking.
        val env: OrtEnvironment
        val s: OrtSession
        val tok: VitsTokenizer
        synchronized(lock) {
            env = environment ?: throw TtsException("no ONNX environment")
            s = session ?: throw TtsException("no ONNX session loaded")
            tok = tokenizer ?: throw TtsException("no tokenizer loaded")
            state = TtsState.SYNTHESISING
        }

        val startedAt = System.currentTimeMillis()
        try {
            val lowerText = text.lowercase()
            val tokens = tok.encode(lowerText)
            android.util.Log.d(
                "ReceivePttUseCase",
                "Encoded text '$lowerText' into ${tokens.size} tokens: ${tokens.joinToString()}",
            )
            if (tokens.isEmpty()) {
                synchronized(lock) { if (state == TtsState.SYNTHESISING) state = TtsState.VOICE_LOADED }
                return AudioClip(ShortArray(0), outputFormat)
            }

            val inputTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(tokens),
                longArrayOf(1, tokens.size.toLong()),
            )
            val inputLengthsTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(longArrayOf(tokens.size.toLong())),
                longArrayOf(1),
            )
            val scalesTensor = OnnxTensor.createTensor(
                env,
                java.nio.FloatBuffer.wrap(floatArrayOf(noiseScale, lengthScale, noiseScaleW)),
                longArrayOf(3),
            )

            val inputs = mutableMapOf<String, OnnxTensor>()
            val names = s.inputNames
            android.util.Log.d("ReceivePttUseCase", "ONNX model expects inputs: $names")

            if (names.contains("input")) inputs["input"] = inputTensor
            else if (names.contains("text")) inputs["text"] = inputTensor
            else inputs[names.firstOrNull() ?: "input"] = inputTensor

            // Only pass input_lengths if the model expects it
            if (names.contains("input_lengths")) inputs["input_lengths"] = inputLengthsTensor
            else if (names.contains("text_lengths")) inputs["text_lengths"] = inputLengthsTensor

            // Only pass scales if the model expects it
            if (names.contains("scales")) inputs["scales"] = scalesTensor
            else if (names.contains("noise_scale")) {
                inputs["noise_scale"] = OnnxTensor.createTensor(env, floatArrayOf(noiseScale))
                inputs["length_scale"] = OnnxTensor.createTensor(env, floatArrayOf(lengthScale))
                inputs["noise_scale_w"] = OnnxTensor.createTensor(env, floatArrayOf(noiseScaleW))
            }

            android.util.Log.d("ReceivePttUseCase", "Running ONNX with ${inputs.size} inputs: ${inputs.keys}")

            // ↓ ONNX inference — outside synchronized(lock) so loadVoice() never blocks here.
            val result = s.run(inputs)

            val audioFloatArray = result[0].value
            val pcm = toPcm16(audioFloatArray)

            // Release tensors in the correct order: dynamic extras first, then base tensors.
            inputs.values
                .filter { it !== inputTensor && it !== inputLengthsTensor && it !== scalesTensor }
                .forEach { it.close() }
            inputTensor.close()
            inputLengthsTensor.close()
            scalesTensor.close()
            result.close()

            val elapsed = System.currentTimeMillis() - startedAt
            if (elapsed >= 0) synthesisLatency.recordMs(elapsed)
            synthesisedCount.incrementAndGet()

            var maxAmp = 0
            for (sample in pcm) {
                val abs = kotlin.math.abs(sample.toInt())
                if (abs > maxAmp) maxAmp = abs
            }
            android.util.Log.d("iTantra-TTS", "Synthesized ${pcm.size} samples. Max amplitude: $maxAmp")

            val durationMs = outputFormat.msForSamples(pcm.size)
            realTimeFactor.record(elapsed, durationMs)

            synchronized(lock) { if (state == TtsState.SYNTHESISING) state = TtsState.VOICE_LOADED }
            return AudioClip(pcm, outputFormat)
        } catch (e: Exception) {
            synchronized(lock) { state = TtsState.ERROR }
            val inputNames = try { s.inputNames } catch (_: Exception) { "unknown" }
            android.util.Log.e("iTantra-TTS", "VITS ONNX inference failed. Expected inputs: $inputNames", e)
            throw TtsException("VITS ONNX inference failed", e)
        }
    }

    /**
     * The input and output names the loaded graph actually declares.
     */
    fun describeGraph(): String {
        synchronized(lock) {
            val s = session ?: return "no voice loaded"
            return "inputs=${s.inputNames.toList()} outputs=${s.outputNames.toList()}"
        }
    }

    /**
     * Speak [text] via the Android platform TTS engine. Used when the ONNX voice pack
     * is unavailable. The audio goes directly to the device speaker via the platform
     * engine; this method returns a silent [AudioClip] so the [ChunkedSpeaker] pipeline
     * can complete cleanly without special-casing the fallback.
     *
     * Blocks until the utterance finishes or [FALLBACK_TTS_TIMEOUT_MS] elapses,
     * whichever is first.
     */
    private fun synthesizeViaAndroidTts(text: String, language: Language): AudioClip {
        val tts = ensureFallbackTts(language)
        val latch = CountDownLatch(1)
        val utteranceId = "iTantra-${System.nanoTime()}"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) = latch.countDown()
            override fun onError(id: String?) = latch.countDown()
            @Deprecated("Deprecated in Java")
            override fun onError(id: String, errorCode: Int) = latch.countDown()
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (!latch.await(FALLBACK_TTS_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
            android.util.Log.w("iTantra-TTS", "Android TTS timed out for utterance: $utteranceId")
        }
        // Return silent clip; actual audio came out of the platform engine directly.
        return AudioClip(ShortArray(0), outputFormat)
    }

    /**
     * Lazily initialises the Android [TextToSpeech] engine for the fallback path.
     * The engine is created once and reused for subsequent utterances.
     *
     * ## Main-thread requirement
     *
     * [TextToSpeech] MUST be constructed on the main thread on many OEM implementations
     * of Android 8–11. The `OnInitListener` callback is posted to the Looper of the
     * thread that called the constructor. A background thread (e.g. [kotlinx.coroutines.Dispatchers.IO])
     * has no Looper, so the callback is never delivered, [initLatch] times out silently,
     * and every subsequent `speak()` call returns a silent clip. This is exactly the
     * symptom reported on low-end devices.
     *
     * Fix: post the constructor to [Looper.getMainLooper] via [Handler] so the callback
     * arrives on the main thread's Looper regardless of which thread called us.
     */
    private fun ensureFallbackTts(language: Language): TextToSpeech {
        synchronized(fallbackTtsLock) {
            fallbackTts?.takeIf { fallbackTtsReady }?.let { return it }
        }

        val initLatch = CountDownLatch(1)
        val ref = arrayOfNulls<TextToSpeech>(1)

        val initCallback = TextToSpeech.OnInitListener { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = ref[0]?.setLanguage(localeFor(language))
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    android.util.Log.w(
                        "iTantra-TTS",
                        "Android TTS language ${localeFor(language)} not available on this device; " +
                            "will attempt with default locale",
                    )
                    // Try device default as last resort — anything is better than silence.
                    ref[0]?.setLanguage(Locale.getDefault())
                }
                synchronized(fallbackTtsLock) { fallbackTtsReady = true }
                android.util.Log.d("iTantra-TTS", "Android TTS ready for ${language.code}")
            } else {
                android.util.Log.e(
                    "iTantra-TTS",
                    "Android TTS init failed with status=$status for ${language.code}",
                )
            }
            initLatch.countDown()
        }

        // Construct on main thread. If we ARE on the main thread already (unlikely but
        // possible in tests), post still works — the Looper will drain it immediately.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            val tts = TextToSpeech(context, initCallback)
            ref[0] = tts
        } else {
            Handler(Looper.getMainLooper()).post {
                val tts = TextToSpeech(context, initCallback)
                ref[0] = tts
            }
        }

        val inited = initLatch.await(FALLBACK_TTS_INIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!inited) {
            android.util.Log.e(
                "iTantra-TTS",
                "Android TTS init timed out after ${FALLBACK_TTS_INIT_TIMEOUT_MS} ms for ${language.code}",
            )
        }

        val tts = ref[0] ?: throw TtsException(
            "Android TTS constructor returned null for ${language.code}",
        )
        synchronized(fallbackTtsLock) { fallbackTts = tts }
        return tts
    }

    /** Maps our [Language] enum to the nearest Android/Java [Locale]. */
    private fun localeFor(language: Language): Locale = when (language) {
        Language.HINDI -> Locale("hi", "IN")
        Language.ENGLISH -> Locale.ENGLISH
        Language.GUJARATI -> Locale("gu", "IN")
        Language.MARATHI -> Locale("mr", "IN")
        Language.TAMIL -> Locale("ta", "IN")
        Language.KANNADA -> Locale("kn", "IN")
        Language.MALAYALAM -> Locale("ml", "IN")
        Language.TELUGU -> Locale("te", "IN")
        Language.ODIA -> Locale("or", "IN")
        Language.BENGALI -> Locale("bn", "IN")
    }

    /**
     * VITS emits float samples in roughly [-1, 1]. The export shape varies between
     * checkpoints -- [1, 1, N], [1, N] or [N] -- so the output is flattened rather than
     * assuming one layout, and clipped on conversion because the model can overshoot
     * slightly and a raw cast would wrap into loud noise.
     */
    private fun toPcm16(raw: Any?): ShortArray {
        val floats = flatten(raw)
        
        // Find max amplitude for peak normalization
        var maxAmp = 0f
        for (f in floats) {
            val absF = kotlin.math.abs(f)
            if (absF > maxAmp) maxAmp = absF
        }
        
        // Scale to 95% of maximum 16-bit PCM volume for consistent loudness
        val scale = if (maxAmp > 0.01f) {
            0.95f / maxAmp
        } else {
            1.0f
        }

        val out = ShortArray(floats.size)
        for (i in floats.indices) {
            val v = (floats[i] * scale * 32767f).coerceIn(-32768f, 32767f)
            out[i] = v.toInt().toShort()
        }
        return out
    }

    private fun flatten(value: Any?): FloatArray = when (value) {
        is FloatArray -> value
        is Array<*> -> value.flatMap { flatten(it).asList() }.toFloatArray()
        else -> throw TtsException("unexpected VITS output type: ${value?.javaClass}")
    }

    override fun close() = unloadVoice()

    private companion object {
        /** Timeout for a single Android TTS utterance (ms). */
        const val FALLBACK_TTS_TIMEOUT_MS = 15_000L

        /** Timeout waiting for Android TTS engine to initialise (ms). */
        const val FALLBACK_TTS_INIT_TIMEOUT_MS = 5_000L
    }
}

/**
 * Character-level token mapping for a VITS voice.
 *
 * Indic VITS checkpoints are typically trained on graphemes rather than phonemes,
 * which conveniently avoids shipping a phonemiser for three languages. The vocabulary
 * is loaded from a JSON map bundled next to the model.
 */
class VitsTokenizer(
    private val symbolToId: Map<String, Long>,
    private val padId: Long,
    /** Whether the export expects a pad token interleaved between symbols. */
    private val interleavePad: Boolean,
    /** Id of the vocabulary's unknown-symbol token, when it declares one. */
    private val unkId: Long? = null,
    /** Only used to label diagnostics. */
    private val languageCode: String = "?",
) {
    /**
     * Maps [text] onto model symbol ids.
     *
     * ## Unknown characters
     *
     * A character the vocabulary does not contain is mapped to the vocabulary's
     * unknown-symbol token when it has one, and dropped otherwise. Dropping is the last
     * resort, not the default: it used to be the only behaviour, and it made a whole
     * class of bug invisible. A lexicon entry written in the wrong script -- an English
     * abbreviation expansion routed to an Odia voice, say -- is not a character or two
     * out of place, it is every character of the phrase, and the utterance came out
     * truncated with nothing above debug level to say why.
     *
     * The drop count is therefore summarised at warn level, and a run where most of the
     * text was unmappable is logged as an error, because that is the signature of a
     * model and vocabulary that do not belong to each other.
     */
    fun encode(text: String): LongArray {
        val ids = ArrayList<Long>(text.length * 2 + 1)
        if (interleavePad) ids += padId

        var mapped = 0
        var substituted = 0
        var dropped = 0
        val unmappable = LinkedHashSet<Char>()

        for (raw in text) {
            // Tabs and newlines carry a word boundary that the vocabulary only ever
            // spells as a plain space; without this they are dropped and words merge.
            val ch = if (raw.isWhitespace()) ' ' else raw
            val id = symbolToId[ch.toString()]
            when {
                id != null -> {
                    ids += id
                    mapped++
                }
                unkId != null -> {
                    ids += unkId
                    substituted++
                    if (unmappable.size < MAX_REPORTED_SYMBOLS) unmappable += ch
                }
                else -> {
                    dropped++
                    if (unmappable.size < MAX_REPORTED_SYMBOLS) unmappable += ch
                    continue
                }
            }
            if (interleavePad) ids += padId
        }

        val unmapped = substituted + dropped
        if (unmapped > 0) {
            val detail = unmappable.joinToString(" ") { "'$it'(U+%04X)".format(it.code) }
            val total = mapped + unmapped
            // A handful of stray symbols is ordinary; most of the text being unmappable
            // is a configuration fault and must not be reported as routine.
            if (total > 0 && unmapped * 100 >= total * MISMATCH_PERCENT) {
                android.util.Log.e(
                    "iTantra-TTS",
                    "Vocabulary mismatch for $languageCode: $unmapped of $total characters " +
                        "are not in this voice's vocabulary. The text is probably in the wrong " +
                        "script for the voice, or the model and vocabulary are from different " +
                        "exports. Unmappable: $detail",
                )
            } else {
                android.util.Log.w(
                    "iTantra-TTS",
                    "$languageCode: $unmapped of $total characters not in vocabulary " +
                        "(${if (unkId != null) "substituted with <unk>" else "dropped"}): $detail",
                )
            }
        }
        return ids.toLongArray()
    }

    companion object {
        /** Cap on distinct unmappable characters named in one log line. */
        private const val MAX_REPORTED_SYMBOLS = 12

        /**
         * Share of unmappable characters above which the failure is logged as an error
         * rather than a warning.
         */
        private const val MISMATCH_PERCENT = 50

        /** Sentinel for "the vocabulary declares no explicit unk_id". */
        private const val NO_UNK_ID = -1L

        /** Vocabulary keys that are metadata rather than pronounceable symbols. */
        private val METADATA_KEYS = setOf("pad_id", "interleave_pad", "unk_id")

        /** Conventional spellings of the unknown-symbol token across export toolchains. */
        private val UNK_KEYS = listOf("<unk>", "[UNK]", "<UNK>", "unk")

        fun fromFile(file: java.io.File, language: Language): VitsTokenizer =
            fromJson(
                JSONObject(file.readText(Charsets.UTF_8)),
                source = file.absolutePath,
                languageCode = language.code,
            )

        fun fromAsset(
            context: android.content.Context,
            assetPath: String,
            language: Language,
        ): VitsTokenizer {
            val json = JSONObject(
                context.assets.open(assetPath).use { it.readBytes().toString(Charsets.UTF_8) }
            )
            return fromJson(json, source = assetPath, languageCode = language.code)
        }

        private fun fromJson(
            json: JSONObject,
            source: String,
            languageCode: String,
        ): VitsTokenizer {
            val map = HashMap<String, Long>()

            if (json.has("phoneme_id_map")) {
                val symbols = json.getJSONObject("phoneme_id_map")
                val keys = symbols.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = symbols.getJSONArray(k).getLong(0)
                }
            } else if (json.has("symbol_to_id")) {
                val symbols = json.getJSONObject("symbol_to_id")
                val keys = symbols.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = symbols.getLong(k)
                }
            } else {
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k in METADATA_KEYS) continue
                    val value = json.opt(k)
                    if (value is Number) {
                        map[k] = value.toLong()
                    } else if (value is String) {
                        value.toLongOrNull()?.let { map[k] = it }
                    }
                }
            }

            if (map.isEmpty()) {
                throw TtsException("VITS vocabulary at $source contains no symbols")
            }

            val padId = json.optLong("pad_id", 0L)
            val interleavePad = json.optBoolean("interleave_pad", true)
            // In the MMS exports id 0 is both the interleaved pad and a real character
            // ('k' in English, 'फ' in Hindi). That is how those graphs were trained, so
            // the symbol stays in the map -- removing it would make that one letter
            // unpronounceable. Only a token that is *only* a pad is excluded, and the
            // unknown token is never allowed to alias the pad.
            val unkId = (
                json.optLong("unk_id", NO_UNK_ID).takeIf { it != NO_UNK_ID }
                    ?: UNK_KEYS.firstNotNullOfOrNull { map[it] }
                )?.takeIf { it != padId }

            android.util.Log.d(
                "iTantra-TTS",
                "Loaded $languageCode vocab from $source: ${map.size} symbols, " +
                    "padId=$padId, interleavePad=$interleavePad, unkId=${unkId ?: "none"}",
            )
            if (!map.containsKey(" ")) {
                android.util.Log.w(
                    "iTantra-TTS",
                    "$languageCode vocab from $source has no space symbol; word boundaries " +
                        "will be lost in synthesis",
                )
            }
            return VitsTokenizer(
                symbolToId = map,
                padId = padId,
                interleavePad = interleavePad,
                unkId = unkId,
                languageCode = languageCode,
            )
        }
    }
}
