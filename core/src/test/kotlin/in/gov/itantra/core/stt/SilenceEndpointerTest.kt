package `in`.gov.itantra.core.stt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SilenceEndpointerTest {

    private val frameMs = 20
    private fun endpointer() = SilenceEndpointer(silenceTimeoutMs = 800, frameMs = frameMs)

    /** Feeds [ms] worth of frames at [db] and returns the first non-NONE event, if any. */
    private fun feed(e: SilenceEndpointer, db: Double, ms: Int): SilenceEndpointer.Event? {
        var found: SilenceEndpointer.Event? = null
        repeat(ms / frameMs) {
            val ev = e.onFrameDb(db)
            if (ev != SilenceEndpointer.Event.NONE && found == null) found = ev
        }
        return found
    }

    private val quiet = -70.0
    private val loud = -20.0

    @Test
    fun `leading silence never endpoints`() {
        val e = endpointer()
        // Five seconds of silence with no speech at all: an open mic in a quiet room
        // must not manufacture an empty utterance.
        val ev = feed(e, quiet, 5_000)
        assertNull(ev, "endpointer fired without any speech having occurred")
        assertFalse(e.hasSpeech)
    }

    @Test
    fun `speech then 800ms of silence endpoints`() {
        val e = endpointer()
        assertEquals(SilenceEndpointer.Event.SPEECH_STARTED, feed(e, loud, 400))
        assertTrue(e.hasSpeech)

        // 780 ms is under the threshold: still no endpoint.
        assertNull(feed(e, quiet, 780))

        // Crossing 800 ms fires exactly once.
        assertEquals(SilenceEndpointer.Event.ENDPOINT, feed(e, quiet, 40))
    }

    @Test
    fun `endpoint fires only once per utterance`() {
        val e = endpointer()
        feed(e, loud, 400)
        feed(e, quiet, 820)
        // Another two seconds of silence must not produce a second ENDPOINT.
        assertNull(feed(e, quiet, 2_000), "endpointer fired more than once for one utterance")
    }

    @Test
    fun `brief pause inside speech does not endpoint`() {
        val e = endpointer()
        feed(e, loud, 400)
        // A 300 ms gap -- a stop consonant or a breath -- must not cut the sentence.
        assertNull(feed(e, quiet, 300))
        feed(e, loud, 200)
        // The silence counter must have reset: another 780 ms is still not enough.
        assertNull(feed(e, quiet, 780))
        assertEquals(SilenceEndpointer.Event.ENDPOINT, feed(e, quiet, 40))
    }

    @Test
    fun `reset allows a new utterance`() {
        val e = endpointer()
        feed(e, loud, 400)
        feed(e, quiet, 820)
        e.reset()
        assertFalse(e.hasSpeech)
        assertEquals(SilenceEndpointer.Event.SPEECH_STARTED, feed(e, loud, 400))
    }

    @Test
    fun `adapts to a noisy environment instead of treating noise as speech`() {
        val e = endpointer()
        // Sustained background noise at -45 dB: a generator, a crowd. A fixed
        // threshold would classify all of this as speech and never endpoint.
        feed(e, -45.0, 2_000)
        assertFalse(e.hasSpeech, "steady background noise was misclassified as speech")

        // Real speech well above the learned floor is still detected.
        assertEquals(SilenceEndpointer.Event.SPEECH_STARTED, feed(e, -20.0, 400))
    }

    @Test
    fun `rms of pure silence is the floor value`() {
        assertEquals(-100.0, SilenceEndpointer.rmsDb(ShortArray(320)))
    }

    @Test
    fun `rms of full scale is near zero dbfs`() {
        val full = ShortArray(320) { Short.MAX_VALUE }
        assertTrue(SilenceEndpointer.rmsDb(full) > -0.1, "full-scale RMS should be ~0 dBFS")
    }

    @Test
    fun `default timeout is the specified 1500ms`() {
        assertEquals(1500L, SilenceEndpointer.DEFAULT_SILENCE_TIMEOUT_MS)
    }

    @Test
    fun `distant speech at minus 50 dBFS is detected`() {
        // Before the retuning, minSpeechLevelDb was -40 dBFS, which silently discarded
        // any frame below that level. -50 dBFS is typical for a speaker ~1 m away.
        val e = endpointer()
        val result = feed(e, -50.0, 400)
        assertEquals(
            SilenceEndpointer.Event.SPEECH_STARTED,
            result,
            "distant speech at -50 dBFS should be detected; gate was not lowered",
        )
    }

    @Test
    fun `single noise burst does not raise floor enough to block subsequent speech`() {
        // With RISE_ALPHA=0.25, a single -35 dBFS transient raised the adaptive floor by
        // 6-7 dB, making the detector temporarily deaf to -50 dBFS distant speech.
        // With RISE_ALPHA=0.15, the same burst raises it only ~3.75 dB.
        val e = endpointer()
        // Give the floor a chance to settle around ambient (-65 dBFS).
        feed(e, -65.0, 500)
        // Simulate a door slam / clap at -35 dBFS for one frame (20 ms).
        e.onFrameDb(-35.0)
        // Immediately after, distant speech at -50 dBFS should still be detectable.
        val result = feed(e, -50.0, 400)
        assertEquals(
            SilenceEndpointer.Event.SPEECH_STARTED,
            result,
            "speech at -50 dBFS was not detected after a transient noise burst; " +
                "RISE_ALPHA may be too high",
        )
    }
}
