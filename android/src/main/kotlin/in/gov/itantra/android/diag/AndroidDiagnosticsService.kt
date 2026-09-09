package `in`.gov.itantra.android.diag

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import `in`.gov.itantra.android.stt.OnnxCtcSttEngine
import `in`.gov.itantra.android.transport.StreamTransport
import `in`.gov.itantra.android.tts.VitsOnnxTtsEngine
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.diag.DiagnosticsService
import `in`.gov.itantra.core.diag.DiagnosticsSink
import `in`.gov.itantra.core.diag.DiagnosticsSnapshot
import `in`.gov.itantra.core.diag.RollingMeanAccumulator
import `in`.gov.itantra.core.diag.RollingRealTimeFactorAccumulator
import `in`.gov.itantra.core.diag.SttMetrics
import `in`.gov.itantra.core.diag.SystemMetrics
import `in`.gov.itantra.core.diag.TransportMetrics
import `in`.gov.itantra.core.diag.TtsMetrics
import `in`.gov.itantra.core.diag.WerTestSession
import `in`.gov.itantra.core.stt.SttEngine
import `in`.gov.itantra.core.stt.SttState
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.Transport
import `in`.gov.itantra.core.tts.TtsEngine
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * Module B7 -- the live metrics source for the Analysis screen.
 *
 * Everything here is measured, not estimated. Where a figure cannot be obtained or
 * has not been exercised, the field is null. A plausible-looking zero for an
 * unmeasured quantity is worse than "not measured".
 */
class AndroidDiagnosticsService(
    private val context: Context,
    private val sttEngine: () -> SttEngine?,
    private val ttsEngine: () -> TtsEngine?,
) : DiagnosticsService, DiagnosticsSink {

    private val sttLatency = RollingMeanAccumulator(10)
    private val sttRealTimeFactor = RollingRealTimeFactorAccumulator(10)
    private val utterances = AtomicLong(0)
    private val werSession = WerTestSession()

    @Volatile
    var attachedTransport: Transport? = null

    /** Language the operator is currently using; used for WER prompts before STT loads. */
    @Volatile
    var currentLanguage: Language = Language.HINDI

    val testModeActive: Boolean get() = werSession.enabled
    val nextTestPrompt: String? get() = werSession.peekPrompt(promptLanguage())
    val werPairCount: Int get() = werSession.pairCount

    fun setWerTestMode(enabled: Boolean) {
        werSession.setEnabled(enabled)
    }

    override fun onSttFinal(
        text: String,
        language: Language,
        finalisationMs: Long,
        audioMs: Long,
        cancelled: Boolean,
    ) {
        if (cancelled) return
        if (finalisationMs >= 0) sttLatency.recordMs(finalisationMs)
        if (audioMs > 0) sttRealTimeFactor.record(finalisationMs.coerceAtLeast(0), audioMs)
        if (text.isNotBlank()) {
            utterances.incrementAndGet()
            currentLanguage = language
            werSession.recordHypothesis(language, text)
        }
    }

    /**
     * Liveness ping used for RTT. No-op when disconnected, busy, or not a stream
     * transport -- must never throw into the UI poll loop.
     */
    fun probeRoundTrip() {
        val tx = attachedTransport as? StreamTransport ?: return
        if (tx.state != ConnectionState.CONNECTED) return
        if (tx.isChannelBusy) return
        runCatching { tx.sendHeartbeat(currentLanguage) }
    }

    override fun snapshot(): DiagnosticsSnapshot {
        val stt = sttEngine()
        val tts = ttsEngine()
        val onnxStt = stt as? OnnxCtcSttEngine
        val vits = tts as? VitsOnnxTtsEngine
        val tx = attachedTransport
        val sttSize = onnxStt?.loadedModelSizeBytes
        val ttsSize = vits?.loadedModelSizeBytes
        val modelRam = listOfNotNull(sttSize, ttsSize).takeIf { it.isNotEmpty() }?.sum()
        val promptLang = promptLanguage(stt)

        return DiagnosticsSnapshot(
            capturedAtMs = System.currentTimeMillis(),
            stt = SttMetrics(
                backend = STT_BACKEND,
                activeLanguage = stt?.activeLanguage ?: currentLanguage,
                modelLoaded = stt?.state == SttState.MODEL_LOADED || stt?.state == SttState.LISTENING,
                modelSizeBytes = sttSize,
                avgFinalisationLatencyMs = sttLatency.mean ?: onnxStt?.finalisationLatency?.meanMs,
                avgRealTimeFactor = sttRealTimeFactor.value ?: onnxStt?.realTimeFactor?.value,
                utterancesProcessed = utterances.get(),
                measuredWer = if (werSession.enabled) werSession.wer else null,
                werSampleCount = if (werSession.enabled) werSession.pairCount else 0,
                testModeActive = werSession.enabled,
                nextTestPrompt = werSession.peekPrompt(promptLang),
            ),
            tts = TtsMetrics(
                backend = if ((vits?.androidTtsFallbackCount ?: 0L) > 0) {
                    TTS_FALLBACK_BACKEND
                } else {
                    TTS_BACKEND
                },
                activeLanguage = tts?.activeLanguage,
                voiceLoaded = vits?.loadedModelSizeBytes != null,
                modelSizeBytes = ttsSize,
                avgSynthesisMs = vits?.synthesisLatency?.meanMs,
                avgRealTimeFactor = vits?.realTimeFactor?.value,
                utterancesSynthesised = vits?.utterancesSynthesised ?: 0L,
                underruns = vits?.underruns ?: 0L,
                androidFallbackCount = vits?.androidTtsFallbackCount ?: 0L,
            ),
            transport = TransportMetrics(
                kind = tx?.kind,
                state = tx?.state ?: ConnectionState.DISCONNECTED,
                roundTripMs = tx?.lastRoundTripMs?.takeIf { it >= 0 },
                packetsSent = tx?.stats?.packetsSent ?: 0,
                packetsReceived = tx?.stats?.packetsReceived ?: 0,
                packetsDiscarded = tx?.stats?.packetsDiscarded ?: 0,
                sendFailures = tx?.stats?.sendFailures ?: 0,
                bytesSent = tx?.stats?.bytesSent ?: 0,
                bytesReceived = tx?.stats?.bytesReceived ?: 0,
                channelBusy = (tx as? StreamTransport)?.isChannelBusy ?: false,
                pairingConfirmed = (tx as? StreamTransport)?.isPairingConfirmed ?: false,
            ),
            system = systemMetrics(modelRam),
        )
    }

    private fun promptLanguage(stt: SttEngine? = sttEngine()): Language =
        stt?.activeLanguage ?: currentLanguage

    private fun systemMetrics(modelRamBytes: Long?): SystemMetrics {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val deviceMemory = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
        val debugInfo = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val pss = debugInfo.totalPss.toLong() * 1024
        val apk = apkSizeBytes()?.takeIf { it > 0 }

        return SystemMetrics(
            appMemoryBytes = pss.takeIf { it > 0 },
            javaHeapBytes = Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() },
            nativeHeapBytes = Debug.getNativeHeapAllocatedSize().takeIf { it >= 0 },
            totalDeviceRamBytes = deviceMemory.totalMem.takeIf { it > 0 },
            availableRamBytes = deviceMemory.availMem.takeIf { it >= 0 },
            cpuLoad = processCpuLoad(),
            apkSizeBytes = apk,
            lowMemory = deviceMemory.lowMemory,
            modelRamBytes = modelRamBytes,
            deviceModel = deviceLabel(),
            androidVersion = androidLabel(),
        )
    }

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
        val utime = fields.getOrNull(13)?.toLongOrNull()
        val stime = fields.getOrNull(14)?.toLongOrNull()
        if (utime == null || stime == null) null else utime + stime
    } catch (_: Exception) {
        null
    }

    private fun apkSizeBytes(): Long? {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val appInfo = info.applicationInfo ?: return null
            val base = File(appInfo.sourceDir).length()
            val splits = appInfo.splitSourceDirs?.sumOf { File(it).length() } ?: 0L
            base + splits
        } catch (_: Exception) {
            null
        }
    }

    private fun deviceLabel(): String {
        val maker = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        return when {
            maker.isEmpty() && model.isEmpty() -> "unknown"
            maker.isEmpty() -> model
            model.isEmpty() -> maker
            model.startsWith(maker, ignoreCase = true) -> model
            else -> "$maker $model"
        }
    }

    private fun androidLabel(): String {
        val release = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "?"
        return "Android $release (API ${Build.VERSION.SDK_INT})"
    }

    @Volatile private var lastCpuTicks: Long? = null
    @Volatile private var lastCpuSampleNanos: Long = 0

    private companion object {
        const val CLOCK_TICKS_PER_SECOND = 100.0
        const val STT_BACKEND = "IndicWav2Vec CTC / ONNX INT8"
        const val TTS_BACKEND = "VITS / ONNX INT8"
        const val TTS_FALLBACK_BACKEND = "Android TTS (fallback)"

        @Suppress("unused")
        val PID = Process.myPid()
    }
}
