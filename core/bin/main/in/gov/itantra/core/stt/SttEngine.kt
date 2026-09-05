package `in`.gov.itantra.core.stt

import `in`.gov.itantra.core.Language

/** Why a final result was emitted. */
enum class EndpointTrigger {
    /** [SttEngine.silenceTimeoutMs] of continuous silence elapsed after speech. */
    SILENCE,

    /** A caller invoked [SttEngine.stop] -- e.g. the future push-to-talk release. */
    EXTERNAL_STOP,

    /** The caller invoked [SttEngine.cancel]; the result is partial and discardable. */
    CANCELLED,
}

data class SttResult(
    val text: String,
    val language: Language,
    /** Engine-reported confidence in 0..1, or null if the backend does not supply one. */
    val confidence: Float?,
    val trigger: EndpointTrigger,
    /** Wall-clock span of the captured utterance. */
    val utteranceDurationMs: Long,
    /** Time from end-of-utterance to this callback: the user-perceived STT latency. */
    val finalisationLatencyMs: Long,
)

/**
 * Callbacks are delivered on the engine worker thread, never the caller thread.
 * Implementations must not block inside these methods.
 */
interface SttListener {
    /** Fired repeatedly as decoding progresses. Text is unstable and may be revised. */
    fun onPartial(text: String) {}

    /** Fired exactly once per utterance. */
    fun onFinal(result: SttResult)

    fun onError(error: SttException) {}

    /** Fired when the endpointer transitions between speech and silence. */
    fun onSpeechStateChanged(speaking: Boolean) {}
}

enum class SttState { IDLE, MODEL_LOADED, LISTENING, ERROR }

class SttException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Module B1 -- the speech-to-text engine contract.
 *
 * Deliberate design points:
 *  - [stop] is a plain public method. This module knows nothing about push-to-talk;
 *    the future PTT-release handler simply calls [stop]. No PTT logic lives here.
 *  - Exactly one acoustic model is resident at a time. [loadModel] with a different
 *    language must fully release the previous model before allocating the next.
 *  - The interface is backend-neutral. Keeping it so is what made swapping the
 *    backend cheap when Vosk turned out to have no Tamil or Bengali model; the
 *    shipping backend is IndicWav2Vec CTC on ONNX. See docs/STT-BACKEND.md.
 */
interface SttEngine : AutoCloseable {

    val state: SttState

    /** The language whose model is currently resident, or null if none. */
    val activeLanguage: Language?

    /** Silence required to auto-endpoint an utterance. Specified as 800 ms. */
    val silenceTimeoutMs: Long

    /**
     * Load (or switch to) the acoustic model for [language].
     *
     * If another model is resident it is unloaded first; this call is the only
     * supported way to change language. Blocking and potentially slow (hundreds of
     * ms to seconds) -- call off the main thread.
     */
    fun loadModel(language: Language)

    /** Release the resident model and all native memory it holds. Idempotent. */
    fun unloadModel()

    /**
     * Begin capturing and decoding. Requires a loaded model.
     * Emits partials continuously and exactly one [SttListener.onFinal] per utterance.
     */
    fun start(listener: SttListener)

    /**
     * End the current utterance now and flush a final result with
     * [EndpointTrigger.EXTERNAL_STOP]. Safe to call when not listening (no-op).
     */
    fun stop()

    /** Abandon the current utterance without emitting a usable final result. */
    fun cancel()
}
