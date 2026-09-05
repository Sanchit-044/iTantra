package `in`.gov.itantra.core.diag

interface AppLogger {
    fun d(tag: String, msg: String)
    fun i(tag: String, msg: String)
    fun w(tag: String, msg: String, t: Throwable? = null)
    fun e(tag: String, msg: String, t: Throwable? = null)
}

object NoOpLogger : AppLogger {
    override fun d(tag: String, msg: String) {}
    override fun i(tag: String, msg: String) {}
    override fun w(tag: String, msg: String, t: Throwable?) {}
    override fun e(tag: String, msg: String, t: Throwable?) {}
}

/**
 * Global logging facade for :core components to log to Logcat without Android dependencies.
 */
object AppLog {
    var logger: AppLogger = NoOpLogger

    fun d(tag: String, msg: String) = logger.d(tag, msg)
    fun i(tag: String, msg: String) = logger.i(tag, msg)
    fun w(tag: String, msg: String, t: Throwable? = null) = logger.w(tag, msg, t)
    fun e(tag: String, msg: String, t: Throwable? = null) = logger.e(tag, msg, t)
}
