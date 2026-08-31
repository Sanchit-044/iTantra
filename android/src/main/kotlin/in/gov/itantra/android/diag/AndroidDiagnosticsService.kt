package `in`.gov.itantra.android.diag

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import `in`.gov.itantra.android.transport.StreamTransport
import `in`.gov.itantra.android.tts.VitsOnnxTtsEngine
import `in`.gov.itantra.core.diag.DiagnosticsService
import `in`.gov.itantra.core.diag.DiagnosticsSnapshot
import `in`.gov.itantra.core.diag.MetricAccumulator
import `in`.gov.itantra.core.diag.RealTimeFactorAccumulator
import `in`.gov.itantra.core.diag.SttMetrics
import `in`.gov.itantra.core.diag.SystemMetrics
import `in`.gov.itantra.core.diag.TransportMetrics
import `in`.gov.itantra.core.diag.TtsMetrics
import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.stt.SttState
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.Transport
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Module B7 -- the live metrics source.
 *
 * Everything here is measured, not estimated. Where a figure genuinely cannot be
 * obtained on the current device or has not been exercised yet, the field is null. That
 * is deliberate: a diagnostics screen that displays a plausible-looking zero for an
 * unmeasured quantity is worse than one that says "not measured", because it invites
 * someone to quote it.
 */
class AndroidDiagnosticsService(
    private val context: Context,
    private val sttEngine: () -> SttEngine?,
    private val ttsEngine: () -> VitsOnnxTtsEngine?,
    private val transport: () -> Transport?,
    private val sttBackendName: String,
) : DiagnosticsService {

    /** Fed by the STT engine wrapper on each final result. */
    val sttLatency = MetricAccumulator("stt.finalisation")
    val sttRealTimeFactor = RealTimeFactorAccumulator()
    private val utterances = AtomicLong(0)

    /** Set by a Module B2 evaluation run; null until one has actually been executed. */
    @Volatile
    var measuredWer: Double? = null

    @Volatile
    var sttModelSizeBytes: Long? = null

    private val ttsUnderruns = AtomicLong(0)
    private val ttsUtterances = AtomicLong(0)

    fun recordUtterance(finalisationMs: Long, processingMs: Long, audioMs: Long) {
        sttLatency.recordMs(finalisationMs)
        sttRealTimeFactor.record(processingMs, audioMs)
        utterances.incrementAndGet()
    }

    fun recordTtsUnderrun() = ttsUnderruns.incrementAndGet()

    fun recordTtsUtterance() = ttsUtterances.incrementAndGet()

    override fun snapshot(): DiagnosticsSnapshot {
        val stt = sttEngine()
        val tts = ttsEngine()
        val tx = transport()

        return DiagnosticsSnapshot(
            capturedAtMs = System.currentTimeMillis(),
            stt = SttMetrics(
                backend = sttBackendName,
                activeLanguage = stt?.activeLanguage,
                modelLoaded = stt?.state == SttState.MODEL_LOADED || stt?.state == SttState.LISTENING,
                modelSizeBytes = sttModelSizeBytes,
                avgFinalisationLatencyMs = sttLatency.meanMs,
                avgRealTimeFactor = sttRealTimeFactor.value,
                utterancesProcessed = utterances.get(),
                measuredWer = measuredWer,
            ),
            tts = TtsMetrics(
                backend = "VITS/ONNX-INT8",
                activeLanguage = tts?.activeLanguage,
                voiceLoaded = tts?.loadedModelSizeBytes != null,
                modelSizeBytes = tts?.loadedModelSizeBytes,
                avgSynthesisMs = tts?.synthesisLatency?.meanMs,
                avgRealTimeFactor = tts?.realTimeFactor?.value,
                utterancesSynthesised = ttsUtterances.get(),
                underruns = ttsUnderruns.get(),
            ),
            transport = TransportMetrics(
                kind = tx?.kind,
                state = tx?.state ?: ConnectionState.DISCONNECTED,
                roundTripMs = tx?.lastRoundTripMs,
                packetsSent = tx?.stats?.packetsSent ?: 0,
                packetsReceived = tx?.stats?.packetsReceived ?: 0,
                packetsDiscarded = tx?.stats?.packetsDiscarded ?: 0,
                sendFailures = tx?.stats?.sendFailures ?: 0,
                bytesSent = tx?.stats?.bytesSent ?: 0,
                bytesReceived = tx?.stats?.bytesReceived ?: 0,
                channelBusy = (tx as? StreamTransport)?.isChannelBusy ?: false,
                pairingConfirmed = (tx as? StreamTransport)?.isPairingConfirmed ?: false,
            ),
            system = systemMetrics(),
        )
    }

    private fun systemMetrics(): SystemMetrics {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val deviceMemory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }

        val debugInfo = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }

        return SystemMetrics(
            // getTotalPss() is the figure that matters for "does this fit": it counts
            // shared pages proportionally rather than double-counting framework memory.
            // Reported in KB by the platform, so scaled here.
            appMemoryBytes = debugInfo.totalPss.toLong() * 1024,
            javaHeapBytes = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() },
            nativeHeapBytes = Debug.getNativeHeapAllocatedSize(),
            totalDeviceRamBytes = deviceMemory.totalMem,
            availableRamBytes = deviceMemory.availMem,
            cpuLoad = processCpuLoad(),
            apkSizeBytes = apkSizeBytes(),
            lowMemory = deviceMemory.lowMemory,
        )
    }

    /**
     * Process CPU load sampled from /proc/self/stat between calls.
     *
     * Returns null on the first call, since a rate needs two samples. Android 8+
     * restricts reading other processes' stat files, but a process may always read its
     * own, so this stays available on modern releases.
     */
    private fun processCpuLoad(): Double? {
        val ticks = readProcessTicks() ?: return null
        val nowNanos = System.nanoTime()

        val prevTicks = lastCpuTicks
        val prevNanos = lastCpuSampleNanos
        lastCpuTicks = ticks
        lastCpuSampleNanos = nowNanos

        if (prevTicks == null || prevNanos == 0L) return null

        val elapsedSeconds = (nowNanos - prevNanos) / 1_000_000_000.0
        if (elapsedSeconds <= 0) return null

        val deltaTicks = (ticks - prevTicks).coerceAtLeast(0)
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        return (deltaTicks / CLOCK_TICKS_PER_SECOND / elapsedSeconds / cores).coerceIn(0.0, 1.0)
    }

    private fun readProcessTicks(): Long? = try {
        val fields = File("/proc/self/stat").readText().split(" ")
        // Fields 14 and 15 (1-indexed) are utime and stime.
        val utime = fields.getOrNull(13)?.toLongOrNull()
        val stime = fields.getOrNull(14)?.toLongOrNull()
        if (utime == null || stime == null) null else utime + stime
    } catch (e: Exception) {
        null
    }

    private fun apkSizeBytes(): Long? {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val appInfo = info.applicationInfo ?: return null
            val base = File(appInfo.sourceDir).length()
            // Split APKs: models may be packaged into a separate split, so the base APK
            // alone would understate the real install size.
            val splits = appInfo.splitSourceDirs?.sumOf { File(it).length() } ?: 0L
            base + splits
        } catch (e: Exception) {
            null
        }
    }

    @Volatile private var lastCpuTicks: Long? = null
    @Volatile private var lastCpuSampleNanos: Long = 0

    private companion object {
        /** Android's USER_HZ is 100 on every supported ABI. */
        const val CLOCK_TICKS_PER_SECOND = 100.0

        @Suppress("unused")
        val PID = Process.myPid()
    }
}
