package `in`.gov.itantra.core.diag

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.TransportKind

/**
 * Module B7 -- the pollable metric surface.
 *
 * Plain data with a hand-rolled [toJson]. No serialization library and no screen: this
 * is the data source a future diagnostics UI reads from, and keeping it dependency-free
 * means it can also be dumped to logcat or a file during field testing without pulling
 * anything extra into the 2 GB budget.
 *
 * Every field that cannot be measured yet is nullable, and null means "not measured"
 * rather than zero. That distinction matters: reporting 0 ms latency for a model that
 * has never run would be a fabricated number, and the whole point of this module is to
 * report what was actually observed.
 */
data class DiagnosticsSnapshot(
    val capturedAtMs: Long,
    val stt: SttMetrics,
    val tts: TtsMetrics,
    val transport: TransportMetrics,
    val system: SystemMetrics,
) {
    fun toJson(): String = buildString {
        append("{")
        append("\"capturedAtMs\":").append(capturedAtMs).append(',')
        append("\"stt\":").append(stt.toJson()).append(',')
        append("\"tts\":").append(tts.toJson()).append(',')
        append("\"transport\":").append(transport.toJson()).append(',')
        append("\"system\":").append(system.toJson())
        append("}")
    }
}

data class SttMetrics(
    val backend: String,
    val activeLanguage: Language?,
    val modelLoaded: Boolean,
    /** On-disk size of the resident model, bytes. Null if no model is loaded. */
    val modelSizeBytes: Long?,
    /** Mean time from end-of-utterance to final result. Null until an utterance runs. */
    val avgFinalisationLatencyMs: Double?,
    /** Mean processing time over audio duration. Below 1.0 is faster than real time. */
    val avgRealTimeFactor: Double?,
    val utterancesProcessed: Long,
    /** Populated only when a Module B2 evaluation has been run on this device. */
    val measuredWer: Double?,
) {
    fun toJson(): String = jsonObject(
        "backend" to jsonString(backend),
        "activeLanguage" to jsonString(activeLanguage?.code),
        "modelLoaded" to modelLoaded.toString(),
        "modelSizeBytes" to jsonNumber(modelSizeBytes),
        "avgFinalisationLatencyMs" to jsonNumber(avgFinalisationLatencyMs),
        "avgRealTimeFactor" to jsonNumber(avgRealTimeFactor),
        "utterancesProcessed" to utterancesProcessed.toString(),
        "measuredWer" to jsonNumber(measuredWer),
    )
}

data class TtsMetrics(
    val backend: String,
    val activeLanguage: Language?,
    val voiceLoaded: Boolean,
    val modelSizeBytes: Long?,
    /** Mean wall-clock time for one synthesise call. */
    val avgSynthesisMs: Double?,
    /**
     * Real-time factor: synthesis time over produced audio duration. This is the
     * number that decides whether clause-chunked playback can stay gapless; above 1.0
     * the pipeline underruns and speech stutters.
     */
    val avgRealTimeFactor: Double?,
    val utterancesSynthesised: Long,
    val underruns: Long,
) {
    fun toJson(): String = jsonObject(
        "backend" to jsonString(backend),
        "activeLanguage" to jsonString(activeLanguage?.code),
        "voiceLoaded" to voiceLoaded.toString(),
        "modelSizeBytes" to jsonNumber(modelSizeBytes),
        "avgSynthesisMs" to jsonNumber(avgSynthesisMs),
        "avgRealTimeFactor" to jsonNumber(avgRealTimeFactor),
        "utterancesSynthesised" to utterancesSynthesised.toString(),
        "underruns" to underruns.toString(),
    )
}

data class TransportMetrics(
    val kind: TransportKind?,
    val state: ConnectionState,
    val roundTripMs: Long?,
    val packetsSent: Long,
    val packetsReceived: Long,
    val packetsDiscarded: Long,
    val sendFailures: Long,
    val bytesSent: Long,
    val bytesReceived: Long,
    val channelBusy: Boolean,
    val pairingConfirmed: Boolean,
) {
    fun toJson(): String = jsonObject(
        "kind" to jsonString(kind?.name),
        "state" to jsonString(state.name),
        "roundTripMs" to jsonNumber(roundTripMs),
        "packetsSent" to packetsSent.toString(),
        "packetsReceived" to packetsReceived.toString(),
        "packetsDiscarded" to packetsDiscarded.toString(),
        "sendFailures" to sendFailures.toString(),
        "bytesSent" to bytesSent.toString(),
        "bytesReceived" to bytesReceived.toString(),
        "channelBusy" to channelBusy.toString(),
        "pairingConfirmed" to pairingConfirmed.toString(),
    )
}

data class SystemMetrics(
    /** Proportional set size of this process, bytes -- the honest per-app RAM figure. */
    val appMemoryBytes: Long?,
    /** Java heap in use, bytes. */
    val javaHeapBytes: Long?,
    /** Native heap in use, bytes. Where the STT and TTS models actually live. */
    val nativeHeapBytes: Long?,
    /** Total device RAM, bytes. The 2 GB budget is checked against this. */
    val totalDeviceRamBytes: Long?,
    val availableRamBytes: Long?,
    /** Process CPU load in 0..1, or null where the platform does not expose it. */
    val cpuLoad: Double?,
    /** Installed APK size, bytes. */
    val apkSizeBytes: Long?,
    val lowMemory: Boolean,
) {
    fun toJson(): String = jsonObject(
        "appMemoryBytes" to jsonNumber(appMemoryBytes),
        "javaHeapBytes" to jsonNumber(javaHeapBytes),
        "nativeHeapBytes" to jsonNumber(nativeHeapBytes),
        "totalDeviceRamBytes" to jsonNumber(totalDeviceRamBytes),
        "availableRamBytes" to jsonNumber(availableRamBytes),
        "cpuLoad" to jsonNumber(cpuLoad),
        "apkSizeBytes" to jsonNumber(apkSizeBytes),
        "lowMemory" to lowMemory.toString(),
    )
}

/** The service a future diagnostics screen polls. */
interface DiagnosticsService {
    fun snapshot(): DiagnosticsSnapshot
}

// ------------------------------------------------------------------ JSON helpers

private fun jsonObject(vararg fields: Pair<String, String>): String =
    fields.joinToString(",", "{", "}") { (k, v) -> "\"$k\":$v" }

private fun jsonNumber(v: Number?): String = v?.toString() ?: "null"

private fun jsonString(v: String?): String =
    if (v == null) "null" else "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
