package `in`.gov.itantra.android.tts

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
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
        val sampleRate: Int = 22_050,
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

    override var outputFormat: AudioFormat = AudioFormat.TTS_22K
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

    private val lock = Any()

    override fun loadVoice(language: Language) {
        synchronized(lock) {
            loadVoiceLocked(language)
        }
    }

    private fun loadVoiceLocked(language: Language) {
        if (activeLanguage == language && session != null) return
        val descriptor = voices[language]
            ?: throw TtsException("no bundled VITS voice for ${language.code}")

        // Free before allocating: two resident voices would breach the memory budget.
        unloadVoiceLocked()

        try {
            val env = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open(descriptor.modelAsset).use { it.readBytes() }

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

            session = env.createSession(modelBytes, options)
            environment = env
            tokenizer = VitsTokenizer.fromAsset(context, descriptor.vocabAsset)
            outputFormat = AudioFormat(descriptor.sampleRate)
            loadedModelSizeBytes = modelBytes.size.toLong()
            activeLanguage = language
            state = TtsState.VOICE_LOADED
        } catch (e: Exception) {
            state = TtsState.ERROR
            throw TtsException("failed to load VITS voice for ${language.code}", e)
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
            if (activeLanguage != language) loadVoiceLocked(language)
            val s = session ?: throw TtsException("no voice loaded")
            val tok = tokenizer ?: throw TtsException("no tokenizer loaded")
            val env = environment ?: throw TtsException("ONNX environment unavailable")

            val ids = tok.encode(text)
            if (ids.isEmpty()) return AudioClip(ShortArray(0), outputFormat)

            val startedAt = System.currentTimeMillis()
            state = TtsState.SYNTHESISING

            var inputTensor: OnnxTensor? = null
            var lengthTensor: OnnxTensor? = null
            var scalesTensor: OnnxTensor? = null
            try {
                inputTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(ids),
                    longArrayOf(1, ids.size.toLong()),
                )
                lengthTensor = OnnxTensor.createTensor(
                    env,
                    LongBuffer.wrap(longArrayOf(ids.size.toLong())),
                    longArrayOf(1),
                )
                scalesTensor = OnnxTensor.createTensor(
                    env,
                    floatArrayOf(noiseScale, lengthScale, noiseScaleW),
                )

                // Input names follow the common Piper/Coqui VITS export convention.
                // CONFIRM these against the actual exported graph before integration:
                // a mismatch fails at runtime with an opaque ORT error. Call
                // [describeGraph] once after loading to print the real names.
                val inputs = mapOf(
                    "input" to inputTensor,
                    "input_lengths" to lengthTensor,
                    "scales" to scalesTensor,
                )

                val clip = s.run(inputs).use { results ->
                    AudioClip(toPcm16(results[0].value), outputFormat)
                }

                val elapsed = System.currentTimeMillis() - startedAt
                synthesisLatency.recordMs(elapsed)
                realTimeFactor.record(elapsed, clip.durationMs)
                state = TtsState.VOICE_LOADED
                return clip
            } catch (e: Exception) {
                state = TtsState.ERROR
                // Explicitly NOT falling back to android.speech.tts.TextToSpeech.
                throw TtsException("VITS synthesis failed for ${language.code}", e)
            } finally {
                inputTensor?.close()
                lengthTensor?.close()
                scalesTensor?.close()
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
            val id = symbolToId[ch.toString()] ?: continue
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
            val symbols = json.getJSONObject("symbol_to_id")
            val map = HashMap<String, Long>(symbols.length())
            val keys = symbols.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = symbols.getLong(k)
            }
            return VitsTokenizer(
                symbolToId = map,
                padId = json.optLong("pad_id", 0L),
                interleavePad = json.optBoolean("interleave_pad", true),
            )
        }
    }
}
