package `in`.gov.itantra.core.transport

/**
 * A scheduler that never runs anything on its own; tests advance it explicitly.
 *
 * This is what makes the busy-channel retry logic deterministic instead of a sleep, and
 * lets the 1-second retry interval be asserted rather than waited out.
 */
class FakeScheduler : Scheduler {
    private data class Task(val delayMs: Long, val run: () -> Unit, var cancelled: Boolean = false)

    private val tasks = mutableListOf<Task>()

    val pendingCount: Int get() = synchronized(tasks) { tasks.count { !it.cancelled } }

    override fun schedule(delayMs: Long, task: () -> Unit): Cancellable {
        val t = Task(delayMs, task)
        synchronized(tasks) { tasks += t }
        return object : Cancellable {
            override fun cancel() { synchronized(tasks) { t.cancelled = true } }
        }
    }

    /** Runs every currently-scheduled task once, in scheduling order. */
    fun runPending() {
        val snapshot = synchronized(tasks) {
            val s = tasks.filter { !it.cancelled }.toList()
            tasks.clear()
            s
        }
        snapshot.forEach { it.run() }
    }

    /** The delays that were requested, for asserting the retry interval. */
    fun requestedDelays(): List<Long> = synchronized(tasks) { tasks.map { it.delayMs } }
}
