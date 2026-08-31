package `in`.gov.itantra.harness

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import `in`.gov.itantra.android.audio.AudioTrackSink
import `in`.gov.itantra.android.stt.OnnxCtcSttEngine
import `in`.gov.itantra.android.tts.VitsOnnxTtsEngine
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.core.audio.WavCodec
import `in`.gov.itantra.core.diag.MetricAccumulator
import `in`.gov.itantra.core.stt.EndpointTrigger
import `in`.gov.itantra.core.stt.SttListener
import `in`.gov.itantra.core.stt.SttResult
import `in`.gov.itantra.core.tts.ChunkedSpeaker
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Module B4 -- the round-trip gate.
 *
 * Feeds microphone input into the B1 STT module and pipes the resulting text straight
 * into the B3 TTS module on the same handset. Nothing leaves the device.
 *
 * This is a gate, not a demo: B5 transport work should not start until this passes
 * reliably, because a fault anywhere in capture, endpointing, decoding, normalisation,
 * chunking or playback is far cheaper to find here than through a radio link.
 *
 * Two modes:
 *  - [roundTripFromLiveMicrophone] needs a person to speak. Skipped unless
 *    -e itantra.live true is passed, so an unattended CI run does not hang waiting
 *    for a voice that never comes.
 *  - [roundTripFromRecordedAudio] replays a bundled WAV through the same pipeline and
 *    runs unattended.
 */
@RunWith(AndroidJUnit4::class)
class RoundTripTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val language = Language.HINDI

    private val sttLatency = MetricAccumulator("b4.stt")
    private val ttsLatency = MetricAccumulator("b4.tts")

    @Test
    fun roundTripFromLiveMicrophone() {
        assumeTrue(
            "Live microphone run not requested; pass -e itantra.live true to enable.",
            InstrumentationRegistry.getArguments().getString("itantra.live") == "true",
        )

        val stt = OnnxCtcSttEngine(context)
        val tts = VitsOnnxTtsEngine(context)
        val speaker = ChunkedSpeaker(tts)

        try {
            stt.loadModel(language)
            tts.loadVoice(language)

            val done = CountDownLatch(1)
            var recognised: SttResult? = null

            Log.i(TAG, "SPEAK NOW in ${language.endonym} -- stops after 800 ms of silence.")
            stt.start(object : SttListener {
                override fun onPartial(text: String) = Log.d(TAG, "partial: $text")

                override fun onFinal(result: SttResult) {
                    recognised = result
                    done.countDown()
                }

                override fun onError(error: `in`.gov.itantra.core.stt.SttException) {
                    Log.e(TAG, "STT error", error)
                    done.countDown()
                }
            })

            assertTrueOrFail(
                done.await(LIVE_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "no final STT result within ${LIVE_TIMEOUT_SECONDS}s",
            )

            val result = recognised ?: error("no result captured")
            Log.i(TAG, "recognised: \"${result.text}\" (${result.trigger}, " +
                "${result.finalisationLatencyMs} ms to finalise)")
            sttLatency.recordMs(result.finalisationLatencyMs)

            assertTrueOrFail(result.text.isNotBlank(), "STT returned empty text")
            assertTrueOrFail(
                result.trigger == EndpointTrigger.SILENCE,
                "expected a silence endpoint, got ${result.trigger}",
            )

            speak(speaker, result.text)
        } finally {
            stt.close()
            tts.close()
        }
    }

    /**
     * Unattended variant: a bundled utterance is decoded and then spoken back. Exercises
     * the same STT-to-TTS join without needing a human in the room.
     */
    @Test
    fun roundTripFromRecordedAudio() {
        val sample = loadSample()
        assumeTrue("No bundled round-trip sample; see docs/CORPUS.md.", sample != null)

        val tts = VitsOnnxTtsEngine(context)
        val speaker = ChunkedSpeaker(tts)
        val decoder = `in`.gov.itantra.android.stt.OnnxCtcFileDecoder(context)

        try {
            val text = decoder.decode(sample!!, language)
            Log.i(TAG, "decoded: \"$text\"")
            assertTrueOrFail(text.isNotBlank(), "decoder returned empty text for the bundled sample")
            speak(speaker, text)
        } finally {
            decoder.close()
            tts.close()
        }
    }

    /** Speaks [text] and records how long the first audio took to arrive. */
    private fun speak(speaker: ChunkedSpeaker, text: String) {
        AudioTrackSink(format = `in`.gov.itantra.core.audio.AudioFormat.TTS_22K).use { sink ->
            var firstAudioMs = -1L
            var underruns = 0

            speaker.speak(text, language, sink, object : ChunkedSpeaker.SpeechListener {
                override fun onSpeechStarted(latencyToFirstAudioMs: Long) {
                    firstAudioMs = latencyToFirstAudioMs
                }

                override fun onUnderrun(chunkIndex: Int) {
                    underruns++
                }

                override fun onError(error: `in`.gov.itantra.core.tts.TtsException) {
                    Log.e(TAG, "TTS error", error)
                }
            })

            Log.i(TAG, "time to first audio: $firstAudioMs ms, underruns: $underruns")
            ttsLatency.recordMs(firstAudioMs.coerceAtLeast(0))

            assertTrueOrFail(firstAudioMs >= 0, "playback never started")
            // An underrun means synthesis is slower than real time on this device, which
            // is precisely the 2 GB-budget signal worth surfacing rather than tolerating.
            if (underruns > 0) {
                Log.w(TAG, "WARNING: $underruns underrun(s) -- RTF above 1.0 on this device")
            }

            File(context.getExternalFilesDir(null), "b4-round-trip.txt").writeText(
                "time-to-first-audio-ms=$firstAudioMs\nunderruns=$underruns\n"
            )
        }
    }

    private fun loadSample(): AudioClip? = runCatching {
        context.assets.open("roundtrip/${language.code}-sample.wav").use {
            WavCodec.decode(it.readBytes(), "roundtrip sample")
        }
    }.getOrNull()

    private fun assertTrueOrFail(condition: Boolean, message: String) {
        if (!condition) throw AssertionError(message)
    }

    private companion object {
        const val TAG = "iTantra.B4"
        const val LIVE_TIMEOUT_SECONDS = 30L
    }
}
