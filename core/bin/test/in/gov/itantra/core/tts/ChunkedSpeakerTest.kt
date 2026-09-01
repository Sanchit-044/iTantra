package `in`.gov.itantra.core.tts

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.RecordingSink
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChunkedSpeakerTest {

    private val multiClause = "पानी तेज़ी से बढ़ रहा है। सभी लोग ऊँची जगह पर जाएँ। देर न करें।"

    @Test
    fun `speaks every chunk in order`() {
        val engine = FakeTtsEngine()
        val sink = RecordingSink()
        val spokenOrder = mutableListOf<Int>()

        ChunkedSpeaker(engine).speak(
            multiClause, Language.HINDI, sink,
            object : ChunkedSpeaker.SpeechListener {
                override fun onChunkStarted(index: Int, total: Int, text: String) {
                    spokenOrder += index
                }
            },
        )

        assertEquals(spokenOrder.sorted(), spokenOrder, "chunks played out of order")
        assertTrue(sink.sampleCount > 0)
        assertTrue(engine.synthesisedChunks.size >= 2, "text was not chunked")
    }

    /**
     * The behaviour the whole chunking design exists for: playback of the first clause
     * begins while later clauses are still inside the model. If this regressed to
     * "synthesise everything, then play", the user would wait for the full utterance.
     */
    @Test
    fun `playback starts before the last chunk has been synthesised`() {
        val synthesised = AtomicInteger(0)
        val engine = FakeTtsEngine(
            synthesisDelayMs = 40,
            onSynthesise = { synthesised.incrementAndGet() },
        )
        var synthesisedWhenAudioStarted = -1
        var totalChunks = -1

        ChunkedSpeaker(engine).speak(
            multiClause, Language.HINDI, RecordingSink(),
            object : ChunkedSpeaker.SpeechListener {
                override fun onChunkStarted(index: Int, total: Int, text: String) {
                    if (index == 0) {
                        synthesisedWhenAudioStarted = synthesised.get()
                        totalChunks = total
                    }
                }
            },
        )

        assertTrue(totalChunks >= 2, "test needs multiple chunks, got $totalChunks")
        assertTrue(
            synthesisedWhenAudioStarted < totalChunks,
            "playback waited for all $totalChunks chunks to synthesise " +
                "($synthesisedWhenAudioStarted done at first audio) -- the pipeline is not overlapping",
        )
    }

    @Test
    fun `reports latency to first audio`() {
        var latency = -1L
        ChunkedSpeaker(FakeTtsEngine()).speak(
            multiClause, Language.HINDI, RecordingSink(),
            object : ChunkedSpeaker.SpeechListener {
                override fun onSpeechStarted(latencyToFirstAudioMs: Long) { latency = latencyToFirstAudioMs }
            },
        )
        assertTrue(latency >= 0, "latency to first audio was never reported")
    }

    @Test
    fun `text is normalised before synthesis`() {
        val engine = FakeTtsEngine()
        ChunkedSpeaker(engine).speak("15/08/2025 को निकलें", Language.HINDI, RecordingSink())

        val all = engine.synthesisedChunks.joinToString(" ")
        assertFalse(all.any { it.isDigit() }, "raw digits reached the model: $all")
        assertTrue(all.contains("अगस्त"), "date was not normalised: $all")
    }

    @Test
    fun `empty text produces no synthesis and completes cleanly`() {
        val engine = FakeTtsEngine()
        var completed = false
        ChunkedSpeaker(engine).speak(
            "   ", Language.HINDI, RecordingSink(),
            object : ChunkedSpeaker.SpeechListener {
                override fun onCompleted(spokenChunks: Int, cancelled: Boolean) {
                    completed = true
                    assertEquals(0, spokenChunks)
                }
            },
        )
        assertTrue(completed)
        assertTrue(engine.synthesisedChunks.isEmpty())
    }

    @Test
    fun `synthesis failure is reported and does not hang`() {
        val failing = object : TtsEngine by FakeTtsEngine() {
            override fun synthesizeNormalised(text: String, language: Language) =
                throw IllegalStateException("model not loaded")
        }
        var error: TtsException? = null
        ChunkedSpeaker(failing).speak(
            multiClause, Language.HINDI, RecordingSink(),
            object : ChunkedSpeaker.SpeechListener {
                override fun onError(e: TtsException) { error = e }
            },
        )
        assertTrue(error != null, "a synthesis failure was swallowed")
    }

    @Test
    fun `completion reports the chunk count and that it was not cancelled`() {
        val engine = FakeTtsEngine()
        var spoken = -1
        var wasCancelled = true
        ChunkedSpeaker(engine).speak(
            multiClause, Language.HINDI, RecordingSink(),
            object : ChunkedSpeaker.SpeechListener {
                override fun onCompleted(spokenChunks: Int, cancelled: Boolean) {
                    spoken = spokenChunks
                    wasCancelled = cancelled
                }
            },
        )
        assertTrue(spoken > 0, "no chunks reported as spoken")
        assertFalse(wasCancelled, "a normal completion was reported as cancelled")
        assertEquals(engine.synthesisedChunks.size, spoken, "not every synthesised chunk was played")
    }
}
