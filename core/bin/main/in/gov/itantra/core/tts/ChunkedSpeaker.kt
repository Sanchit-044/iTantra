package `in`.gov.itantra.core.tts

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.core.audio.AudioSink
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Turns a whole message into audible speech that starts before the whole message has
 * been synthesised.
 *
 * The pipeline is: normalise -> chunk at clause boundaries -> synthesise chunk N+1 on
 * a producer thread while chunk N is being written to the sink. Playback of the first
 * clause therefore begins after one clause of synthesis rather than after the entire
 * utterance, which is the point of Module B3's chunking requirement.
 *
 * "Gapless" here means the consumer never starves as long as synthesis runs faster
 * than real time (RTF < 1). [SpeechListener.onUnderrun] reports the case where it does
 * not, which is exactly the signal Module B7 needs in order to flag a device that
 * cannot meet the budget.
 */
class ChunkedSpeaker(
    private val engine: TtsEngine,
    private val chunker: ClauseChunker = ClauseChunker(),
    /**
     * How many synthesised clauses may sit in the queue. Two is enough to cover
     * jitter while bounding memory: at 22.05 kHz a long clause is roughly 200 KB, so
     * the queue costs well under a megabyte.
     */
    private val queueDepth: Int = 2,
    private val threadFactory: (Runnable, String) -> Thread =
        { r, n -> Thread(r, n).apply { isDaemon = true } },
) {
    interface SpeechListener {
        /** Fired once, when the first sample of the utterance reaches the sink. */
        fun onSpeechStarted(latencyToFirstAudioMs: Long) {}

        /** Fired as each chunk begins playing. */
        fun onChunkStarted(index: Int, total: Int, text: String) {}

        /** Synthesis fell behind playback; audio stalled before this chunk. */
        fun onUnderrun(chunkIndex: Int) {}

        fun onCompleted(spokenChunks: Int, cancelled: Boolean) {}

        fun onError(error: TtsException) {}
    }

    /** Handle for an in-flight utterance. */
    class SpeechHandle internal constructor(
        private val cancelRequested: AtomicBoolean,
        private val done: CountDownLatch,
    ) {
        /** Request that playback stop at the next chunk boundary or sooner. */
        fun cancel() = cancelRequested.set(true)

        fun isCancelled(): Boolean = cancelRequested.get()

        fun await(timeoutMs: Long = Long.MAX_VALUE): Boolean =
            done.await(timeoutMs, TimeUnit.MILLISECONDS)
    }

    private val active = AtomicBoolean(false)

    /**
     * Speak [text] into [sink]. Blocks the calling thread until playback finishes;
     * synthesis runs on a separate producer thread. Keeping this call synchronous is
     * deliberate: Module B6 drives it inside an audio-focus scope, and a blocking call
     * makes "hold focus for exactly as long as we are speaking" trivially correct.
     */
    fun speak(
        text: String,
        language: Language,
        sink: AudioSink,
        listener: SpeechListener? = null,
    ): SpeechHandle {
        // Two distinct flags. cancelRequested means the caller asked us to stop and is
        // reported back through onCompleted; shutdown is the internal signal that
        // unblocks the producer once the consumer has left the loop for any reason.
        val cancelRequested = AtomicBoolean(false)
        val shutdown = AtomicBoolean(false)
        val done = CountDownLatch(1)
        val handle = SpeechHandle(cancelRequested, done)

        val normalised = TextNormalizer(language).normalize(text)
        val chunks = chunker.chunk(normalised)
        if (chunks.isEmpty()) {
            listener?.onCompleted(0, false)
            done.countDown()
            return handle
        }

        if (!active.compareAndSet(false, true)) {
            listener?.onError(TtsException("ChunkedSpeaker is already speaking"))
            done.countDown()
            return handle
        }

        val startedAtMs = System.currentTimeMillis()
        val queue = ArrayBlockingQueue<Any>(queueDepth + 1)

        val producer = threadFactory(
            Runnable {
                try {
                    for (c in chunks) {
                        if (shutdown.get() || cancelRequested.get()) break
                        val clip = engine.synthesizeNormalised(c, language)
                        if (shutdown.get() || cancelRequested.get()) break
                        queue.put(clip)
                    }
                    queue.put(END)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (e: Throwable) {
                    try {
                        queue.put(Failure(TtsException("synthesis failed", e)))
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            },
            "itantra-tts-synth",
        )
        producer.start()

        var spoken = 0
        var firstAudio = true
        try {
            loop@ while (!cancelRequested.get()) {
                var item = queue.poll(UNDERRUN_WARN_MS, TimeUnit.MILLISECONDS)
                if (item == null) {
                    // Nothing ready: the model is slower than playback for this chunk.
                    listener?.onUnderrun(spoken)
                    item = queue.take()
                }
                when (item) {
                    END -> break@loop
                    is Failure -> {
                        listener?.onError(item.error)
                        break@loop
                    }
                    is AudioClip -> {
                        if (firstAudio) {
                            listener?.onSpeechStarted(System.currentTimeMillis() - startedAtMs)
                            firstAudio = false
                        }
                        listener?.onChunkStarted(spoken, chunks.size, chunks[spoken])
                        writeFully(sink, item, cancelRequested)
                        spoken++
                    }
                }
            }
            if (cancelRequested.get()) sink.flush() else sink.drain()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            cancelRequested.set(true)
            sink.flush()
        } finally {
            shutdown.set(true)
            producer.interrupt()
            active.set(false)
            listener?.onCompleted(spoken, cancelRequested.get())
            done.countDown()
        }
        return handle
    }

    private fun writeFully(sink: AudioSink, clip: AudioClip, cancelRequested: AtomicBoolean) {
        var off = 0
        while (off < clip.pcm.size) {
            if (cancelRequested.get()) return
            val n = sink.write(clip.pcm, off, clip.pcm.size - off)
            if (n <= 0) return
            off += n
        }
    }

    private class Failure(val error: TtsException)

    private companion object {
        val END = Any()

        /**
         * If the next chunk is not ready within this long, playback has stalled.
         * Reported rather than hidden: a device that underruns cannot meet the
         * real-time budget, and Module B7 should surface that rather than mask it.
         */
        const val UNDERRUN_WARN_MS = 50L
    }
}
