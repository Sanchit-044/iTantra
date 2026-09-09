package `in`.gov.itantra.android.audio

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat as AndroidAudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import `in`.gov.itantra.core.audio.AudioFormat
import `in`.gov.itantra.core.audio.AudioSink
import kotlin.math.roundToInt

/**
 * Microphone capture for Module B1.
 *
 * ## Source selection
 *
 * Tries `UNPROCESSED` first (API 24+, our minSdk). `UNPROCESSED` bypasses all OEM DSP
 * — beam-forming, equalisation, AGC — and gives the flattest raw signal, which is what
 * IndicWav2Vec was trained on. Some OEM ROMs apply an undocumented AGC inside
 * `VOICE_RECOGNITION` that crushes distant speech to near-zero; `UNPROCESSED` avoids
 * that entirely. If `UNPROCESSED` fails to initialise (device/ROM doesn't support it)
 * we fall back to `VOICE_RECOGNITION`.
 *
 * ## Software gain
 *
 * Speaking from ~1 metre away typically delivers −50 to −45 dBFS, which is below the
 * endpointer's hard gate (−52 dBFS after retuning). A configurable software gain
 * multiplier is applied after each read to bring distant speech up to a level the
 * acoustic model sees as well-normalised.
 *
 * The gain is intentionally **not applied** when the hardware `NoiseSuppressor` is
 * active: the platform suppressor already normalises levels for the near-field case, and
 * stacking gain on top of it would clip close-speech.
 *
 * Default gain = ×4 (+12 dB). All samples are clamped to Int16 range so the gain can
 * never produce hard arithmetic overflow even if speech is louder than expected.
 */
class MicrophoneSource(
    /**
     * Linear gain multiplier applied to each sample read from the mic.
     * 1.0 = unity (no change). 4.0 = +12 dB, the default tuned for ~1 m distance.
     * Set to 1.0 in unit-test stubs if you inject a fake source.
     *
     * Calibration note: if the device already has a very hot microphone and you hear
     * clipping artefacts in the STT output, lower this toward 2.0. If recognition is
     * still poor at distance, raise toward 6.0. The Int16 clamp prevents hard overflow
     * in either direction.
     */
    private val softwareGain: Float = DEFAULT_SOFTWARE_GAIN,
) {

    private var record: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    /** True when hardware NoiseSuppressor was successfully attached to this session. */
    private var hardwareNsActive = false

    val isOpen: Boolean get() = record != null

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked by the calling layer.
    fun open(sampleRate: Int) {
        if (record != null) return

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AndroidAudioFormat.CHANNEL_IN_MONO,
            AndroidAudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer > 0) { "AudioRecord reported an invalid buffer size ($minBuffer)" }

        // Four times the minimum: enough slack that a scheduling hiccup on a loaded
        // 2 GB device does not drop frames mid-utterance, without adding real latency.
        val bufferBytes = minBuffer * 4

        // Prefer UNPROCESSED (raw mic, no OEM DSP) to get the flattest signal for the
        // acoustic model. Fall back to VOICE_RECOGNITION if the device/ROM rejects it.
        val r = tryCreateAudioRecord(
            source = MediaRecorder.AudioSource.UNPROCESSED,
            sampleRate = sampleRate,
            bufferBytes = bufferBytes,
        ) ?: tryCreateAudioRecord(
            source = MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate = sampleRate,
            bufferBytes = bufferBytes,
        ) ?: error("AudioRecord failed to initialise with both UNPROCESSED and VOICE_RECOGNITION")

        // Attach hardware effects. If NoiseSuppressor succeeds, we skip the software
        // gain because the platform already normalises levels for the near-field path.
        if (NoiseSuppressor.isAvailable()) {
            val ns = NoiseSuppressor.create(r.audioSessionId)
            if (ns != null) {
                ns.enabled = true
                noiseSuppressor = ns
                hardwareNsActive = true
            }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(r.audioSessionId)?.apply { enabled = true }
        }

        r.startRecording()
        record = r
    }

    /**
     * Blocking read of up to [into].size samples. Returns the sample count, or -1.
     *
     * When [hardwareNsActive] is false (UNPROCESSED path or devices without a platform
     * suppressor), a software gain is applied to each sample so that distant speech
     * reaches a level the endpointer and acoustic model can use reliably.
     */
    fun read(into: ShortArray): Int {
        val n = record?.read(into, 0, into.size) ?: return -1
        if (n > 0 && !hardwareNsActive && softwareGain != 1.0f) {
            applyGain(into, n, softwareGain)
        }
        return n
    }

    fun close() {
        noiseSuppressor?.release(); noiseSuppressor = null
        echoCanceler?.release(); echoCanceler = null
        hardwareNsActive = false
        record?.let {
            if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
            it.release()
        }
        record = null
    }

    private companion object {
        /**
         * Default linear gain applied when no hardware NoiseSuppressor is available.
         * ×4 ≈ +12 dB — enough to lift −52 dBFS distant speech to −40 dBFS where the
         * endpointer reliably fires. Calibrate per-device if needed.
         */
        const val DEFAULT_SOFTWARE_GAIN = 4.0f

        /** Try to create an [AudioRecord] with the given source; return null on failure. */
        @SuppressLint("MissingPermission")
        fun tryCreateAudioRecord(source: Int, sampleRate: Int, bufferBytes: Int): AudioRecord? =
            try {
                AudioRecord(
                    source,
                    sampleRate,
                    AndroidAudioFormat.CHANNEL_IN_MONO,
                    AndroidAudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes,
                ).takeIf { it.state == AudioRecord.STATE_INITIALIZED }
                    ?.also { /* successfully created */ }
                    ?: null.also { /* state not INITIALIZED, will be released by GC */ }
            } catch (_: Exception) {
                null
            }

        /**
         * Applies [gain] to the first [count] samples in [buf], clamping to Int16 range.
         * In-place, no allocation. Inlined per-sample multiply-and-clamp is ~2 ns/sample
         * on a Cortex-A55 — negligible next to ONNX inference.
         */
        fun applyGain(buf: ShortArray, count: Int, gain: Float) {
            for (i in 0 until count) {
                buf[i] = (buf[i] * gain).roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
            }
        }
    }
}

/**
 * An [AudioSink] backed by AudioTrack.
 *
 * [usage] and [contentType] decide which stream the audio lands on. Alerts pass
 * USAGE_ALARM so they ride the alarm stream that Module B6 forces to maximum; ordinary
 * speech uses USAGE_ASSISTANCE_SONIFICATION. Mixing these up would mean an alert plays
 * at media volume, which is the failure the alert module exists to prevent.
 */
class AudioTrackSink(
    override val format: AudioFormat,
    private val usage: Int = AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
    private val contentType: Int = AudioAttributes.CONTENT_TYPE_SPEECH,
    private val sessionId: Int = AudioManager.AUDIO_SESSION_ID_GENERATE,
) : AudioSink, AutoCloseable {

    private var totalWritten = 0


    private val minBuffer = AudioTrack.getMinBufferSize(
        format.sampleRate,
        AndroidAudioFormat.CHANNEL_OUT_MONO,
        AndroidAudioFormat.ENCODING_PCM_16BIT,
    ).coerceAtLeast(4096)

    private val track: AudioTrack = AudioTrack.Builder()
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(contentType)
                .build()
        )
        .setAudioFormat(
            AndroidAudioFormat.Builder()
                .setEncoding(AndroidAudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(format.sampleRate)
                .setChannelMask(AndroidAudioFormat.CHANNEL_OUT_MONO)
                .build()
        )
        // 4× minimum buffer: gives enough headroom to absorb brief synthesis pauses
        // without underrunning the hardware. At 16 kHz mono PCM16, minBuffer ≈ 3–6 KB
        // so this costs ≈12–24 KB -- negligible, but prevents gaps between chunks.
        .setBufferSizeInBytes(minBuffer * 4)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .setSessionId(sessionId)
        .build()

    init {
        track.play()
    }

    /**
     * Blocking write. AudioTrack in MODE_STREAM applies back-pressure, which is what
     * keeps clause-chunked playback gapless: the writer simply blocks until the device
     * has consumed enough, so consecutive chunks butt up against each other with no
     * silence inserted between them.
     */
    override fun write(samples: ShortArray, offset: Int, count: Int): Int {
        val n = track.write(samples, offset, count, AudioTrack.WRITE_BLOCKING)
        if (n > 0) totalWritten += n
        return if (n < 0) 0 else n
    }

    override fun drain() {
        val expectedFrames = totalWritten
        var stuckCount = 0
        var lastHead = track.playbackHeadPosition
        // Poll up to 3 seconds of non-advancing playback head before giving up.
        // The old guard of 20 × 50 ms = 1 s could cut off the last syllable on a
        // loaded device where the hardware advances the head in bursts.
        while (track.playbackHeadPosition < expectedFrames && stuckCount < 60) {
            Thread.sleep(50)
            val currentHead = track.playbackHeadPosition
            if (currentHead == lastHead) {
                stuckCount++
            } else {
                stuckCount = 0
                lastHead = currentHead
            }
        }
        track.stop()
    }

    override fun flush() {
        track.pause()
        track.flush()
        track.play()
    }

    override fun close() {
        try {
            track.stop()
        } catch (_: IllegalStateException) {
            // Already stopped; nothing to do.
        }
        track.release()
    }

    private companion object {
        const val TAIL_POLL_MS = 5L
    }
}
