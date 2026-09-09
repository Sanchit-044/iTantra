package `in`.gov.itantra.core.tts

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.audio.AudioClip
import `in`.gov.itantra.core.audio.AudioSink
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Turns a whole message into audible speech that starts before the whole message has
 * been synthesised.
 *
 * ## Pipeline
 *
 * ```
 * text ──► ClauseChunker ──► [chunk0, chunk1, chunk2 … chunkN]
 *                                │
 *                     ┌──────────┼──────────┐
 *              synth thread 0  synth thread 1   (both call engine.synthesizeNormalised)
 *                     └──────────┼──────────┘
 *                            coordinator
 *                      (resolves futures in order,
 *                       puts AudioClip into queue)
 *                                │
 *                          consumer (caller)
 *                       (plays each clip via AudioSink)
 * ```
 *
 * ## Why parallel synthesis
 *
 * ONNX Runtime sessions are thread-safe: concurrent calls to `OrtSession.run()` on the
 * same session are explicitly supported. Using [synthesisParallelism] = 2 threads means
 * that while the consumer is playing chunk N, threads are synthesising chunk N+1 *and*
 * chunk N+2 at the same time. This keeps the queue full and eliminates the silence gap
 * that appeared in the original single-producer design when synthesis was slower than
 * playback of the previous chunk.
 *
 * Keep [synthesisParallelism] at 2 for the 2 GB / ≤8 core target device. Higher values
 * compete with audio thread scheduling and can cause mic/speaker underruns.
 *
 * ## Gapless playback
 *
 * AudioTrack in `MODE_STREAM` applies back-pressure on [AudioSink.write]: the writer
 * blocks until the hardware has consumed enough buffer space before accepting the next
 * chunk. Consecutive chunks therefore butt up against each other with no silence, as
 * long as the producer keeps the [queue] non-empty. The parallel synthesis design exists
 * to uphold that "queue non-empty" invariant.
 */
class ChunkedSpeaker(
    private val engine: TtsEngine,
    private val chunker: ClauseChunker = ClauseChunker(),
    /**
     * How many synthesised clauses may sit in the queue. Four is enough to smooth out
     * jitter while bounding memory: at 16 kHz a long clause (≤60 chars) is roughly
     * 100 KB, so the queue costs well under a megabyte.
     */
    private val queueDepth: Int = 4,
    /**
     * Number of synthesis tasks to run concurrently.
     *
     * Two threads halve wall-clock synthesis time for multi-chunk utterances because the
     * ONNX session is thread-safe and chunks are independent. While chunk N plays, N+1
     * *and* N+2 are already being synthesised, so the queue stays full.
     */
    private val synthesisParallelism: Int = 2,
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
     * Speak [text] into [sink].
     *
     * Synthesis runs on a [synthesisParallelism]-thread pool; playback drives the
     * calling thread. Keeping the consumer call synchronous is deliberate: Module B6
     * drives it inside an audio-focus scope, and a blocking call makes "hold focus for
     * exactly as long as we are speaking" trivially correct.
     */
    fun speak(
        text: String,
        language: Language,
        sink: AudioSink,
        listener: SpeechListener? = null,
    ): SpeechHandle {
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

        // --- Parallel synthesis pool ---
        // Submit all synthesis tasks immediately. The pool runs two chunks concurrently;
        // remaining tasks queue inside the executor and start as threads free up.
        val synthPool = Executors.newFixedThreadPool(
            synthesisParallelism.coerceIn(1, chunks.size),
        ) { r -> Thread(r, "itantra-tts-synth").also { it.isDaemon = true } }

        val futures: List<Future<AudioClip>> = chunks.map { chunk ->
            synthPool.submit(Callable {
                if (shutdown.get() || cancelRequested.get()) {
                    // Cancelled before we got to this chunk — return silence so the
                    // coordinator can drain cleanly without blocking forever.
                    AudioClip(ShortArray(0), engine.outputFormat)
                } else {
                    engine.synthesizeNormalised(chunk, language)
                }
            })
        }
        // Prevent new tasks from being submitted; running tasks continue.
        synthPool.shutdown()

        // --- Coordinator thread ---
        // Resolves futures in speaking ORDER and feeds the consumer queue. Running in
        // its own thread so it can block on future.get() without stalling the consumer.
        val coordinator = threadFactory(
            Runnable {
                try {
                    for (future in futures) {
                        if (shutdown.get() || cancelRequested.get()) break
                        try {
                            val clip = future.get()
                            if (shutdown.get() || cancelRequested.get()) break
                            queue.put(clip)
                        } catch (_: CancellationException) {
                            break
                        } catch (e: ExecutionException) {
                            try {
                                queue.put(Failure(TtsException("synthesis failed", e.cause)))
                            } catch (_: InterruptedException) {
                                Thread.currentThread().interrupt()
                            }
                            break
                        }
                    }
                    queue.put(END)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            },
            "itantra-tts-coord",
        )
        coordinator.start()

        // --- Consumer loop ---
        var spoken = 0
        var firstAudio = true
        try {
            loop@ while (!cancelRequested.get()) {
                var item = queue.poll(UNDERRUN_WARN_MS, TimeUnit.MILLISECONDS)
                if (item == null) {
                    // The next chunk is not ready yet. With parallel synthesis this should
                    // be rare for short-to-medium utterances; it can still happen on very
                    // slow devices or for the first chunk of an unusually long sentence.
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
                        if (item.pcm.isEmpty()) {
                            // Silent clip produced by a cancelled synthesis slot — skip it
                            // but don't count it as spoken; the coordinator already sent END.
                            continue@loop
                        }
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
            // Cancel any synthesis tasks that have not yet started.
            synthPool.shutdownNow()
            futures.forEach { it.cancel(true) }
            coordinator.interrupt()
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
         * How long to wait for the next chunk before reporting an underrun.
         *
         * With parallel synthesis the next chunk is typically ready before this timeout
         * fires, so underruns should be infrequent. The value is intentionally generous
         * (500 ms) so a brief compute spike on a loaded device does not spam the log.
         * The consumer always blocks indefinitely after reporting; no audio is skipped.
         */
        const val UNDERRUN_WARN_MS = 500L
    }
}
