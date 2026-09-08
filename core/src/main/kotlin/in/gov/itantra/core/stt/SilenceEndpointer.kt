package `in`.gov.itantra.core.stt

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Energy-based voice-activity detector and utterance endpointer.
 *
 * This is the pure, deterministic half of Module B1's sentence-boundary rule:
 * "fire on 800 ms of silence". It has no Android or inference-engine dependency, so it
 * can be unit-tested against synthetic frames and stays identical across backends.
 *
 * Behaviour:
 *  - Leading silence never endpoints. The utterance must contain speech first,
 *    otherwise holding the mic open in a quiet room would emit empty results.
 *  - The noise floor adapts. A fixed dB threshold fails badly in the field
 *    (a running generator, wind, a crowd), which is the operating environment this
 *    app targets. The floor tracks ambient level up quickly and down slowly, and
 *    speech is declared only when a frame exceeds the floor by [speechMarginDb].
 *  - Brief dips inside a word (stop consonants, breaths) do not end the utterance;
 *    [silenceTimeoutMs] must elapse continuously.
 */
class SilenceEndpointer(
    val silenceTimeoutMs: Long = DEFAULT_SILENCE_TIMEOUT_MS,
    private val frameMs: Int = 20,
    /**
     * How far above the adaptive noise floor a frame must sit to count as speech.
     *
     * Reduced from 9 dB to 6 dB to pair with the lower [minSpeechLevelDb] gate:
     * the absolute gate does the heavy lifting; the adaptive margin handles noisy
     * environments on top of that.
     */
    private val speechMarginDb: Double = 6.0,
    /**
     * Absolute gate. A frame quieter than this is silence no matter what the adaptive
     * floor says.
     *
     * This is the primary threshold, and the adaptive floor can only raise it, never
     * lower it. That ordering is what makes the detector work from the very first
     * frame: at the instant the mic opens there is no history to learn a floor from,
     * and any scheme that seeds the floor from early audio will mistake immediate
     * speech for ambient noise -- which is precisely what push-to-talk produces.
     *
     * Lowered from -40 dBFS to -52 dBFS. Distant speech (~1 m from the mic) typically
     * arrives at -50 to -45 dBFS. The old gate cut that off entirely, causing the
     * endpointer to treat real speech as silence and never open the utterance.
     * Quiet-room ambient noise sits at -65 to -60 dBFS, so the 6 dB speech margin
     * still cleanly separates signal from noise in a field environment.
     */
    private val minSpeechLevelDb: Double = -52.0,
    /**
     * Speech must persist this long before the utterance is considered started.
     *
     * Reduced from 120 ms to 80 ms. Distant-speech onsets are weaker, and the
     * previous value caused the detector to miss the first syllable of a short word
     * when the speaker was more than ~50 cm away.
     */
    private val minSpeechMs: Int = 80,
) {
    enum class Event {
        /** Nothing notable this frame. */
        NONE,

        /** Speech has been confirmed; the utterance is now open. */
        SPEECH_STARTED,

        /** [silenceTimeoutMs] of continuous silence has elapsed since speech ended. */
        ENDPOINT,
    }

    private var noiseFloorDb = INITIAL_NOISE_FLOOR_DB
    private var speechStarted = false
    private var consecutiveSpeechMs = 0
    private var consecutiveSilenceMs = 0
    private var endpointFired = false

    /** True once speech has been confirmed in the current utterance. */
    val hasSpeech: Boolean get() = speechStarted

    /** Current adaptive noise floor in dBFS. Exposed for diagnostics (Module B7). */
    val noiseFloor: Double get() = noiseFloorDb

    /**
     * The level a frame must exceed right now to count as speech. The adaptive floor
     * only ever raises this above [minSpeechLevelDb], so a noisy environment makes the
     * detector stricter but can never make it deaf.
     */
    val speechThresholdDb: Double
        get() = maxOf(minSpeechLevelDb, noiseFloorDb + speechMarginDb)

    /** Milliseconds of continuous silence observed since the last speech frame. */
    val trailingSilenceMs: Int get() = consecutiveSilenceMs

    /**
     * Prepare for a new utterance. The learned noise floor is deliberately retained:
     * the room does not change between sentences, and re-learning it every time would
     * make the first ~200 ms of each utterance unreliable.
     */
    fun reset() {
        speechStarted = false
        consecutiveSpeechMs = 0
        consecutiveSilenceMs = 0
        endpointFired = false
    }

    /** Full reset including the learned acoustic environment. */
    fun resetIncludingNoiseFloor() {
        reset()
        noiseFloorDb = INITIAL_NOISE_FLOOR_DB
    }

    /**
     * Feed one frame of 16-bit PCM. [count] samples from [offset] are read.
     * Frames are assumed to be [frameMs] long; the caller does the framing.
     */
    fun onFrame(pcm: ShortArray, offset: Int = 0, count: Int = pcm.size - offset): Event =
        onFrameDb(rmsDb(pcm, offset, count))

    /** Frame-level entry point taking a pre-computed dBFS level. Testable directly. */
    fun onFrameDb(db: Double): Event {
        val isSpeech = db > speechThresholdDb

        if (isSpeech) {
            consecutiveSilenceMs = 0
            consecutiveSpeechMs += frameMs
        } else {
            consecutiveSpeechMs = 0
            consecutiveSilenceMs += frameMs
            // Adapt only on non-speech frames; otherwise loud speech drags the floor
            // up and desensitises the detector for the rest of the utterance.
            adaptNoiseFloor(db)
        }

        if (!speechStarted) {
            if (consecutiveSpeechMs >= minSpeechMs) {
                speechStarted = true
                return Event.SPEECH_STARTED
            }
            return Event.NONE
        }

        if (!endpointFired && consecutiveSilenceMs >= silenceTimeoutMs) {
            endpointFired = true
            return Event.ENDPOINT
        }
        return Event.NONE
    }

    private fun adaptNoiseFloor(db: Double) {
        // Rise fast (the environment really did get louder), decay slow (do not let a
        // single quiet frame make the detector hair-trigger).
        val alpha = if (db > noiseFloorDb) RISE_ALPHA else DECAY_ALPHA
        noiseFloorDb += alpha * (db - noiseFloorDb)
        noiseFloorDb = noiseFloorDb.coerceIn(-80.0, 0.0)
    }

    companion object {
        /**
         * Default silent period before closing an utterance.
         *
         * Extended from 1200 ms to 1500 ms. When speaking from a distance, gaps between
         * words are perceived as longer and the speaker may pause to breathe more often.
         * The extra 300 ms prevents the endpointer from cutting an utterance short in
         * the middle of a sentence.
         */
        const val DEFAULT_SILENCE_TIMEOUT_MS = 1500L

        /**
         * Starts low and is raised by observed ambient noise. Never seeded from the
         * first frame -- see [minSpeechLevelDb].
         */
        private const val INITIAL_NOISE_FLOOR_DB = -60.0

        /**
         * How fast the adaptive floor rises toward a louder environment.
         *
         * Reduced from 0.25 to 0.15. A brief loud transient (a door slam, a clap)
         * previously raised the floor fast enough to silence the detector for several
         * hundred milliseconds afterward. The lower value filters out single-frame
         * noise bursts while still tracking a genuinely louder room over ~1 second.
         */
        private const val RISE_ALPHA = 0.15
        private const val DECAY_ALPHA = 0.02
        private const val FULL_SCALE = 32768.0

        /** Root-mean-square level of a PCM frame in dBFS. Floored at -100. */
        fun rmsDb(pcm: ShortArray, offset: Int = 0, count: Int = pcm.size - offset): Double {
            if (count <= 0) return -100.0
            val end = min(offset + count, pcm.size)
            var acc = 0.0
            for (i in offset until end) {
                val v = pcm[i] / FULL_SCALE
                acc += v * v
            }
            val n = max(1, end - offset)
            val rms = sqrt(acc / n)
            if (rms <= 1e-9) return -100.0
            return 20.0 * log10(rms)
        }
    }
}
