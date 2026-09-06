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

/**
 * Microphone capture for Module B1.
 *
 * Uses VOICE_RECOGNITION rather than MIC as the source. That is not cosmetic: MIC
 * applies the handset's tuning for recordings, which on many devices includes AGC and
 * aggressive processing that harms recognition accuracy. VOICE_RECOGNITION asks the
 * platform for the flattest signal it can give, which is what an acoustic model wants.
 */
class MicrophoneSource {

    private var record: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null

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

        val r = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AndroidAudioFormat.CHANNEL_IN_MONO,
            AndroidAudioFormat.ENCODING_PCM_16BIT,
            bufferBytes,
        )
        check(r.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialise" }

        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(r.audioSessionId)?.apply { enabled = true }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            echoCanceler = AcousticEchoCanceler.create(r.audioSessionId)?.apply { enabled = true }
        }

        r.startRecording()
        record = r
    }

    /** Blocking read of up to [into].size samples. Returns the sample count, or -1. */
    fun read(into: ShortArray): Int =
        record?.read(into, 0, into.size) ?: -1

    fun close() {
        noiseSuppressor?.release(); noiseSuppressor = null
        echoCanceler?.release(); echoCanceler = null
        record?.let {
            if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
            it.release()
        }
        record = null
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
        .setBufferSizeInBytes(minBuffer * 2)
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
        while (track.playbackHeadPosition < expectedFrames && stuckCount < 20) {
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
