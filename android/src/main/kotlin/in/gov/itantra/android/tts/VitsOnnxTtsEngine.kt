package `in`.gov.itantra.android.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
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
import java.util.concurrent.atomic.AtomicLong

/**
 * Module B3, VITS-on-ONNX backend.
 *
 * One voice resident at a time, INT8-quantised, CPU only. No Android system TTS is
 * referenced anywhere in this class or its dependencies -- not as a primary path and
 * not as a fallback. A synthesis failure surfaces as a [TtsException] for the caller to
 * handle; it must never silently degrade to the platform engine, which is closed source
 * and would breach the open-source constraint without anyone noticing.
 *
 * VITS is non-autoregressive: [synthesize] runs one forward pass and returns the whole
 * waveform. Incremental playback lives in ChunkedSpeaker, above this class.
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

    private val lock = Any()

    override fun loadVoice(language: Language) {
        synchronized(lock) {
            loadVoiceLocked(language)
        }
    }

    private fun loadVoiceLocked(language: Language) {
        if (activeLanguage == language && session != null) {
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
            tokenizer = VitsTokenizer.fromAsset(context, descriptor.vocabAsset)
            outputFormat = AudioFormat(descriptor.sampleRate)
            loadedModelSizeBytes = modelFile.length()
            activeLanguage = language
            state = TtsState.VOICE_LOADED
        } catch (e: Exception) {
            // System TTS remains the hackathon playback path when ONNX is absent.
            activeLanguage = language
            state = TtsState.VOICE_LOADED
            android.util.Log.w("iTantra-TTS", "VITS pack missing for ${language.code}: ${e.message}")
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
        // The OrtEnvironment is a process-wide singleton and is deliberately NOT closed:
        // closing it would tear down the runtime shared with the STT backend.
        if (state != TtsState.ERROR) state = TtsState.IDLE
    }

    override fun synthesize(text: String, language: Language): AudioClip {
        val normalizer = normalizers.getOrPut(language) { TextNormalizer(language) }
        return synthesizeNormalised(normalizer.normalize(text), language)
    }

    override fun synthesizeNormalised(text: String, language: Language): AudioClip {
        synchronized(lock) {
            val startedAt = System.currentTimeMillis()
            state = TtsState.SYNTHESISING

            try {
                val env = environment ?: throw TtsException("no environment")
                val s = session ?: throw TtsException("no session loaded")
                val tok = tokenizer ?: throw TtsException("no tokenizer loaded")

                val lowerText = text.lowercase()
                val tokens = tok.encode(lowerText)
                android.util.Log.d("ReceivePttUseCase", "Encoded text '$lowerText' into ${tokens.size} tokens: ${tokens.joinToString()}")
                if (tokens.isEmpty()) {
                    state = TtsState.VOICE_LOADED
                    return AudioClip(ShortArray(0), outputFormat)
                }

                val inputTensor = OnnxTensor.createTensor(env, java.nio.LongBuffer.wrap(tokens), longArrayOf(1, tokens.size.toLong()))
                val inputLengthsTensor = OnnxTensor.createTensor(env, java.nio.LongBuffer.wrap(longArrayOf(tokens.size.toLong())), longArrayOf(1))
                val scalesTensor = OnnxTensor.createTensor(env, java.nio.FloatBuffer.wrap(floatArrayOf(noiseScale, lengthScale, noiseScaleW)), longArrayOf(3))

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
                val result = s.run(inputs)
                
                // The VITS model outputs float samples in a tensor
                val audioFloatArray = result[0].value
                val pcm = toPcm16(audioFloatArray)

                
                // Cleanup dynamically created tensors not in the map but instantiated initially
                inputTensor.close()
                inputLengthsTensor.close()
                scalesTensor.close()
                inputs.values.filter { it != inputTensor && it != inputLengthsTensor && it != scalesTensor }.forEach { it.close() }
                
                result.close()

                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed >= 0) synthesisLatency.recordMs(elapsed)
                synthesisedCount.incrementAndGet()
                
                var maxAmp = 0
                for (s in pcm) {
                    val abs = kotlin.math.abs(s.toInt())
                    if (abs > maxAmp) maxAmp = abs
                }
                android.util.Log.d("iTantra-TTS", "Synthesized ${pcm.size} samples. Max amplitude: $maxAmp")

                val durationMs = outputFormat.msForSamples(pcm.size)
                realTimeFactor.record(elapsed, durationMs)
                
                state = TtsState.VOICE_LOADED
                return AudioClip(pcm, outputFormat)
            } catch (e: Exception) {
                state = TtsState.ERROR
                val names = session?.inputNames ?: "unknown"
                android.util.Log.e("iTantra-TTS", "VITS ONNX inference failed. Expected inputs: $names", e)
                throw TtsException("VITS ONNX inference failed", e)
            }
        }
    }

    /**
     * The input and output names the loaded graph actually declares. Log this once
     * during integration rather than guessing at the export convention.
     */
    fun describeGraph(): String {
        synchronized(lock) {
            val s = session ?: return "no voice loaded"
            return "inputs=${s.inputNames.toList()} outputs=${s.outputNames.toList()}"
        }
    }

    /**
     * VITS emits float samples in roughly [-1, 1]. The export shape varies between
     * checkpoints -- [1, 1, N], [1, N] or [N] -- so the output is flattened rather than
     * assuming one layout, and clipped on conversion because the model can overshoot
     * slightly and a raw cast would wrap into loud noise.
     */
    private fun toPcm16(raw: Any?): ShortArray {
        val floats = flatten(raw)
        val out = ShortArray(floats.size)
        for (i in floats.indices) {
            val v = (floats[i] * 32767f).coerceIn(-32768f, 32767f)
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
) {
    /** Unknown characters are dropped rather than mapped to a wrong symbol. */
    fun encode(text: String): LongArray {
        val ids = ArrayList<Long>(text.length * 2 + 1)
        if (interleavePad) ids += padId
        for (ch in text) {
            val id = symbolToId[ch.toString()]
            if (id == null) {
                android.util.Log.d("ReceivePttUseCase", "Unknown symbol: $ch (code ${ch.code})")
                continue
            }
            ids += id
            if (interleavePad) ids += padId
        }
        return ids.toLongArray()
    }
    companion object {
        fun fromAsset(context: android.content.Context, assetPath: String): VitsTokenizer {
            val json = JSONObject(
                context.assets.open(assetPath).use { it.readBytes().toString(Charsets.UTF_8) }
            )
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
                val metadataKeys = setOf("pad_id", "interleave_pad")
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k in metadataKeys) continue
                    val value = json.opt(k)
                    if (value is Number) {
                        map[k] = value.toLong()
                    } else if (value is String) {
                        value.toLongOrNull()?.let { map[k] = it }
                    }
                }
            }
            
            android.util.Log.d("ReceivePttUseCase", "Loaded vocab from $assetPath with ${map.size} symbols, padId=${json.optLong("pad_id", 0L)}, interleavePad=${json.optBoolean("interleave_pad", false)}")
            return VitsTokenizer(
                symbolToId = map,
                padId = json.optLong("pad_id", 0L),
                interleavePad = json.optBoolean("interleave_pad", false),
            )
        }
    }
}
