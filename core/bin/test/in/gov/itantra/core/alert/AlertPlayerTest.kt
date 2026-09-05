package `in`.gov.itantra.core.alert

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioFormat
import `in`.gov.itantra.core.audio.AudioSink
import `in`.gov.itantra.core.audio.RecordingSink
import `in`.gov.itantra.core.tts.ChunkedSpeaker
import `in`.gov.itantra.core.tts.FakeTtsEngine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AlertPlayerTest {

    private lateinit var focus: FakeForcedAudioFocus
    private lateinit var templates: FakeTemplateAudioSource
    private lateinit var sink: RecordingSink
    private lateinit var tts: FakeTtsEngine

    private fun player(
        listener: AlertListener? = null,
        ttsDelayMs: Long = 0,
    ): AlertPlayer {
        focus = FakeForcedAudioFocus()
        templates = FakeTemplateAudioSource()
        sink = RecordingSink()
        tts = FakeTtsEngine(synthesisDelayMs = ttsDelayMs)
        return AlertPlayer(
            focus = focus,
            templates = templates,
            speaker = ChunkedSpeaker(tts),
            sinkProvider = { _: AudioFormat -> sink as AudioSink },
            listener = listener,
        )
    }

    private fun alert(content: AlertContent, seq: Int = 1) =
        IncomingAlert(content, Language.HINDI, seq, receivedAtMs = 0L)

    // ---------------------------------------------------------------- B6 core

    @Test
    fun `template path forces alarm audio focus`() {
        val p = player()
        p.play(alert(AlertContent.Template(AlertTemplate.EVACUATE_IMMEDIATELY)))

        assertEquals(1, focus.acquisitions.get(), "template alert did not force audio focus")
        assertTrue(sink.sampleCount > 0, "template alert produced no audio")
    }

    /**
     * The assertion the brief specifically asked for: verify that the custom-text path
     * actually triggers forced focus rather than assuming it inherits the behaviour of
     * the WAV path. These are different branches with different playback mechanics, and
     * this is exactly the sort of thing that silently regresses.
     */
    @Test
    fun `custom text path also forces alarm audio focus`() {
        val p = player()
        p.play(alert(AlertContent.Custom("तुरंत ऊँची जगह पर जाएँ। पानी बढ़ रहा है।")))

        assertEquals(
            1, focus.acquisitions.get(),
            "custom-text alert bypassed forced audio focus -- it does NOT inherit it from the WAV path",
        )
        assertTrue(sink.sampleCount > 0, "custom-text alert produced no audio")
        assertTrue(tts.synthesisedChunks.isNotEmpty(), "TTS was never invoked for the custom path")
    }

    /**
     * Stronger than counting acquisitions: prove the audio is actually written while
     * focus is held. Requesting focus and then playing after releasing it would satisfy
     * a naive check but would still play at whatever volume the handset happened to be on.
     */
    @Test
    fun `audio is written while focus is held on both paths`() {
        for (content in listOf(
            AlertContent.Template(AlertTemplate.MEDICAL_HELP),
            AlertContent.Custom("चिकित्सा आपातकाल"),
        )) {
            var focusHeldDuringWrite: Boolean? = null
            val f = FakeForcedAudioFocus()
            val observing = object : AudioSink {
                override val format = AudioFormat.TTS_22K
                override fun write(samples: ShortArray, offset: Int, count: Int): Int {
                    if (focusHeldDuringWrite == null) focusHeldDuringWrite = f.isHeld
                    return count
                }
                override fun drain() {}
                override fun flush() {}
                override fun close() {}
            }
            val p = AlertPlayer(
                focus = f,
                templates = FakeTemplateAudioSource(),
                speaker = ChunkedSpeaker(FakeTtsEngine()),
                sinkProvider = { observing },
            )
            p.play(alert(content))

            assertEquals(
                true, focusHeldDuringWrite,
                "audio for $content was written outside the forced-focus scope",
            )
        }
    }

    @Test
    fun `focus is released after playback`() {
        val p = player()
        p.play(alert(AlertContent.Template(AlertTemplate.ALL_CLEAR)))
        assertFalse(focus.isHeld, "audio focus was not released; handset would stay at max alarm volume")
    }

    @Test
    fun `missing template wav falls back to tts and still releases focus`() {
        val f = FakeForcedAudioFocus()
        val failing = object : TemplateAudioSource {
            override fun load(template: AlertTemplate, language: Language) =
                throw IllegalStateException("asset missing")
        }
        val tts = FakeTtsEngine()
        val p = AlertPlayer(
            focus = f,
            templates = failing,
            speaker = ChunkedSpeaker(tts),
            sinkProvider = { RecordingSink() },
        )
        p.play(alert(AlertContent.Template(AlertTemplate.STAY_IN_POSITION)))
        assertFalse(f.isHeld, "focus leaked after a missing WAV fallback")
        assertTrue(tts.synthesisedChunks.isNotEmpty(), "TTS fallback was not used")
    }

    @Test
    fun `focus is released even when playback fails`() {
        val f = FakeForcedAudioFocus()
        var failure: String? = null
        val p = AlertPlayer(
            focus = f,
            templates = FakeTemplateAudioSource(),
            speaker = ChunkedSpeaker(FakeTtsEngine()),
            sinkProvider = {
                object : AudioSink {
                    override val format = AudioFormat.TTS_22K
                    override fun write(samples: ShortArray, offset: Int, count: Int): Int =
                        throw IllegalStateException("sink failed")
                    override fun drain() {}
                    override fun flush() {}
                    override fun close() {}
                }
            },
            listener = object : AlertListener {
                override fun onAlertFailed(alert: IncomingAlert, reason: String) { failure = reason }
            },
        )
        p.play(alert(AlertContent.Template(AlertTemplate.STAY_IN_POSITION)))

        assertFalse(f.isHeld, "focus leaked after a failed alert -- handset stuck at max volume")
        assertEquals("sink failed", failure)
    }

    @Test
    fun `template assets are loaded per language`() {
        val p = player()
        p.play(
            IncomingAlert(
                AlertContent.Template(AlertTemplate.EVACUATE_IMMEDIATELY),
                Language.TAMIL, 1, 0L,
            )
        )
        assertEquals(
            AlertTemplate.EVACUATE_IMMEDIATELY to Language.TAMIL,
            templates.loads.single(),
        )
    }

    @Test
    fun `all five templates resolve a distinct asset path per language`() {
        val paths = Language.entries.flatMap { lang ->
            AlertTemplate.entries.map { it.assetPath(lang) }
        }
        val expected = Language.entries.size * AlertTemplate.entries.size
        assertEquals(expected, paths.size)
        assertEquals(expected, paths.toSet().size, "template asset paths collide")
    }

    // ------------------------------------------------- non-interruptibility

    @Test
    fun `a normal message cannot play while an alert is in progress`() {
        val deferred = mutableListOf<Int>()
        val p = player(
            listener = object : AlertListener {
                override fun onDeferredDuringAlert(sequence: Int) { deferred += sequence }
            },
            ttsDelayMs = 60,
        )

        val alertRunning = CountDownLatch(1)
        val normalAttempted = CountDownLatch(1)
        var normalPlayed = true

        val alertThread = Thread {
            alertRunning.countDown()
            p.play(alert(AlertContent.Custom("खतरा। तुरंत निकलें। ऊपर जाएँ।")))
        }
        alertThread.start()
        alertRunning.await()

        val normalThread = Thread {
            // Poll until the alert is genuinely active, then try to play normal audio.
            while (!p.isAlertActive) Thread.onSpinWait()
            normalPlayed = p.tryPlayNormal(sequence = 99) { /* would play a normal message */ }
            normalAttempted.countDown()
        }
        normalThread.start()

        assertTrue(normalAttempted.await(5, TimeUnit.SECONDS), "normal playback attempt never completed")
        alertThread.join(5_000)

        assertFalse(normalPlayed, "a normal message interrupted an alert")
        assertEquals(listOf(99), deferred, "the deferred normal message was not reported")
    }

    @Test
    fun `a normal message plays once no alert is active`() {
        val p = player()
        var played = false
        assertTrue(p.tryPlayNormal(sequence = 1) { played = true })
        assertTrue(played)
    }

    @Test
    fun `a second alert queues and plays after the first`() {
        val queued = mutableListOf<Int>()
        val completed = mutableListOf<Int>()
        val p = player(
            listener = object : AlertListener {
                override fun onAlertQueued(alert: IncomingAlert, queueDepth: Int) { queued += alert.sequence }
                override fun onAlertCompleted(alert: IncomingAlert, durationMs: Long) {
                    synchronized(completed) { completed += alert.sequence }
                }
            },
            ttsDelayMs = 60,
        )

        val first = Thread { p.play(alert(AlertContent.Custom("पहला खतरा संदेश है।"), seq = 1)) }
        first.start()
        while (!p.isAlertActive) Thread.onSpinWait()

        // Arrives mid-playback: must not interrupt, must not be dropped.
        p.play(alert(AlertContent.Custom("दूसरा खतरा संदेश है।"), seq = 2))
        first.join(10_000)

        assertEquals(listOf(2), queued, "the second alert was not queued")
        assertEquals(listOf(1, 2), completed, "alerts did not play in order, or one was lost")
    }
}
