package `in`.gov.itantra.android.stt

import android.content.Context
import `in`.gov.itantra.android.audio.MicrophoneSource
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.core.audio.AudioFormat
import `in`.gov.itantra.core.diag.MetricAccumulator
import `in`.gov.itantra.core.diag.RealTimeFactorAccumulator
import `in`.gov.itantra.core.stt.EndpointTrigger
import `in`.gov.itantra.core.stt.SilenceEndpointer
import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.stt.SttException
import `in`.gov.itantra.core.stt.SttListener
import `in`.gov.itantra.core.stt.SttResult
import `in`.gov.itantra.core.stt.SttState
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Module B1 -- the production STT engine, running IndicWav2Vec CTC on ONNX Runtime.
 *
 * This is the only STT backend. Vosk was dropped because it publishes no Tamil or
 * Bengali acoustic model; see docs/STT-BACKEND.md.
 *
 * ## How partial results work, and what they cost
 *
 * wav2vec2 is not a streaming model. It has no incremental decoder state to carry
 * forward, so a partial result can only be produced by re-running the model over the
 * audio captured so far. That is genuinely more expensive than a Kaldi-style online
 * decoder and it is worth being explicit about rather than hiding:
 *
 *  - Partials are produced at most every [partialIntervalMs], not every frame.
 *  - A partial decode runs on its own single-thread executor. If the previous one has
 *    not finished, the next is **skipped** rather than queued. Queueing would build an
 *    unbounded backlog on a slow device and make the final result arrive late, which is
 *    the one thing that must stay fast.
 *  - Cost grows with utterance length, because each partial re-decodes the whole
 *    utterance. [maxUtteranceMs] bounds that, and the endpointer normally closes an
 *    utterance long before the cap.
 *
 * The final result is always a fresh full-utterance decode, never a recycled partial,
 * so partial-decode skipping can never degrade accuracy.
 *
 * One model is resident at a time; [loadModel] frees before it allocates.
 */
class OnnxCtcSttEngine(
    context: Context,
    private val microphone: MicrophoneSource = MicrophoneSource(),
    override val silenceTimeoutMs: Long = SilenceEndpointer.DEFAULT_SILENCE_TIMEOUT_MS,
    /** Minimum spacing between partial decodes. */
    private val partialIntervalMs: Long = 600,
    /** Safety cap on a single utterance. */
    private val maxUtteranceMs: Long = 20_000,
) : SttEngine {

    private val decoder = OnnxCtcDecoder(context)

    @Volatile
    override var state: SttState = SttState.IDLE
        private set

    override val activeLanguage: Language? get() = decoder.activeLanguage

    /** Exposed to Module B7. */
    val finalisationLatency = MetricAccumulator("stt.finalisation")
    val realTimeFactor = RealTimeFactorAccumulator()

    val loadedModelSizeBytes: Long? get() = decoder.loadedModelSizeBytes

    private val endpointer = SilenceEndpointer(silenceTimeoutMs = silenceTimeoutMs, frameMs = FRAME_MS)
    private val listening = AtomicBoolean(false)
    private val externalStopRequested = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)
    private val partialInFlight = AtomicBoolean(false)

    private var worker: Thread? = null
    private val partialExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "itantra-stt-partial").apply { isDaemon = true }
    }

    override fun loadModel(language: Language) {
        try {
            decoder.load(language)
            state = SttState.MODEL_LOADED
        } catch (e: Exception) {
            state = SttState.ERROR
            throw SttException("failed to load CTC model for ${language.code}", e)
        }
    }

    override fun unloadModel() {
        stop()
        decoder.unload()
        if (state != SttState.ERROR) state = SttState.IDLE
    }

    override fun start(listener: SttListener) {
        val lang = decoder.activeLanguage ?: throw SttException("start() called before loadModel()")
        if (!listening.compareAndSet(false, true)) return

        externalStopRequested.set(false)
        cancelRequested.set(false)
        endpointer.reset()
        state = SttState.LISTENING

        worker = Thread({ captureLoop(lang, listener) }, "itantra-stt").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /**
     * Ends the current utterance. This is what a future push-to-talk release handler
     * calls -- this class contains no PTT logic.
     */
    override fun stop() {
        if (!listening.get()) return
        externalStopRequested.set(true)
        worker?.join(WORKER_JOIN_TIMEOUT_MS)
        worker = null
    }

    override fun cancel() {
        if (!listening.get()) return
        cancelRequested.set(true)
        externalStopRequested.set(true)
        worker?.join(WORKER_JOIN_TIMEOUT_MS)
        worker = null
    }

    override fun close() {
        unloadModel()
        partialExecutor.shutdownNow()
    }

    private fun captureLoop(language: Language, listener: SttListener) {
        val frame = ShortArray(FRAME_SAMPLES)
        val utterance = GrowableAudioBuffer(AudioFormat.STT_16K.samplesForMs(4_000))
        val maxSamples = AudioFormat.STT_16K.samplesForMs(maxUtteranceMs.toInt())
        val fullTranscript = StringBuilder()

        var speaking = false
        var lastPartialAtMs = 0L
        var trigger = EndpointTrigger.EXTERNAL_STOP

        try {
            microphone.open(AudioFormat.STT_16K.sampleRate)
            val startedAtMs = System.currentTimeMillis()

            while (listening.get() && !externalStopRequested.get()) {
                val n = microphone.read(frame)
                if (n <= 0) continue

                utterance.append(frame, n)

                when (endpointer.onFrame(frame, 0, n)) {
                    SilenceEndpointer.Event.SPEECH_STARTED -> {
                        if (!speaking) {
                            speaking = true
                            listener.onSpeechStateChanged(true)
                        }
                    }

                    SilenceEndpointer.Event.ENDPOINT -> {
                        // Pause Detection: The speaker paused for a breath. 
                        // Decode the chunk, append with a comma to recreate cadence, and reset buffer!
                        if (utterance.size > 0) {
                            val clip = utterance.snapshot()
                            val text = decoder.decode(clip, language)
                            if (text.isNotBlank()) {
                                if (fullTranscript.isNotEmpty()) fullTranscript.append(", ")
                                fullTranscript.append(text)
                                
                                // Fire a partial to immediately show the punctuation
                                listener.onPartial(fullTranscript.toString())
                            }
                            utterance.reset()
                            endpointer.reset()
                            speaking = false
                        }
                    }

                    SilenceEndpointer.Event.NONE -> Unit
                }

                if (utterance.size >= maxSamples) {
                    // Hitting this means the endpointer never fired -- usually sustained
                    // noise misclassified as speech. Close the utterance rather than
                    // growing without bound, and let the caller see the trigger.
                    trigger = EndpointTrigger.EXTERNAL_STOP
                    break
                }

                val now = System.currentTimeMillis()
                if (speaking && now - lastPartialAtMs >= partialIntervalMs) {
                    lastPartialAtMs = now
                    schedulePartial(utterance.snapshot(), language, listener, fullTranscript.toString())
                }
            }

            if (cancelRequested.get()) {
                listener.onFinal(
                    SttResult(
                        text = "",
                        language = language,
                        confidence = null,
                        trigger = EndpointTrigger.CANCELLED,
                        utteranceDurationMs = utterance.durationMs,
                        finalisationLatencyMs = 0,
                    )
                )
                return
            }

            val endOfSpeechMs = System.currentTimeMillis()
            val clip = utterance.snapshot()
            
            // Decode the final trailing chunk
            val text = if (clip.pcm.isNotEmpty()) decoder.decode(clip, language) else ""
            
            var finalTranscript = fullTranscript.toString()
            if (text.isNotBlank()) {
                if (finalTranscript.isNotEmpty()) finalTranscript += ", "
                finalTranscript += text
            }
            
            val finalisationMs = System.currentTimeMillis() - endOfSpeechMs

            finalisationLatency.recordMs(finalisationMs)
            realTimeFactor.record(finalisationMs, clip.durationMs)

            listener.onFinal(
                SttResult(
                    text = finalTranscript,
                    language = language,
                    // Greedy CTC exposes no calibrated confidence. Reporting a
                    // fabricated one would be worse than reporting none.
                    confidence = null,
                    trigger = trigger,
                    utteranceDurationMs = endOfSpeechMs - startedAtMs,
                    finalisationLatencyMs = finalisationMs,
                )
            )
        } catch (e: Exception) {
            state = SttState.ERROR
            listener.onError(SttException("STT capture loop failed", e))
        } finally {
            microphone.close()
            if (speaking) listener.onSpeechStateChanged(false)
            listening.set(false)
            if (state == SttState.LISTENING) state = SttState.MODEL_LOADED
        }
    }

    /** Runs a partial decode unless one is already in flight; skips rather than queues. */
    private fun schedulePartial(clip: AudioClip, language: Language, listener: SttListener, prefix: String) {
        if (!partialInFlight.compareAndSet(false, true)) return
        partialExecutor.execute {
            try {
                val text = decoder.decode(clip, language)
                val fullText = if (prefix.isEmpty()) text else if (text.isBlank()) prefix else "$prefix, $text"
                if (fullText.isNotBlank() && listening.get()) listener.onPartial(fullText)
            } catch (e: Exception) {
                // A failed partial is not worth surfacing; the final decode is what
                // matters and it runs independently.
            } finally {
                partialInFlight.set(false)
            }
        }
    }

    private companion object {
        const val FRAME_MS = 20
        val FRAME_SAMPLES = AudioFormat.STT_16K.samplesForMs(FRAME_MS)
        const val WORKER_JOIN_TIMEOUT_MS = 5_000L
    }
}

/**
 * Append-only PCM buffer that grows geometrically.
 *
 * Exists so the capture loop never allocates per frame: at 20 ms frames that would be
 * 50 allocations a second during an utterance, which on a low-end device is enough GC
 * pressure to cause audible dropouts.
 */
class GrowableAudioBuffer(initialCapacity: Int) {
    private var data = ShortArray(initialCapacity.coerceAtLeast(1024))

    var size: Int = 0
        private set

    val durationMs: Long get() = AudioFormat.STT_16K.msForSamples(size)

    @Synchronized
    fun append(samples: ShortArray, count: Int) {
        ensureCapacity(size + count)
        System.arraycopy(samples, 0, data, size, count)
        size += count
    }

    /** An immutable copy of the audio so far, safe to hand to another thread. */
    @Synchronized
    fun snapshot(): AudioClip = AudioClip(data.copyOf(size), AudioFormat.STT_16K)

    @Synchronized
    fun reset() {
        size = 0
    }

    private fun ensureCapacity(needed: Int) {
        if (needed <= data.size) return
        var cap = data.size
        while (cap < needed) cap *= 2
        data = data.copyOf(cap)
    }
}
