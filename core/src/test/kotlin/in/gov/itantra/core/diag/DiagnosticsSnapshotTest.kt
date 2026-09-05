package `in`.gov.itantra.core.diag

import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.TransportKind
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsSnapshotTest {

    @Test
    fun `json writes null for unmeasured fields never a fake zero latency or wer`() {
        val json = snapshot().toJson()
        assertTrue(json.contains("\"avgFinalisationLatencyMs\":null"))
        assertTrue(json.contains("\"measuredWer\":null"))
        assertTrue(json.contains("\"roundTripMs\":null"))
        assertTrue(json.contains("\"androidFallbackCount\":0"))
        assertTrue(json.contains("\"deviceModel\":\"Test Phone\""))
        assertFalse(json.contains("NaN"))
    }

    @Test
    fun `json escapes quotes in device model`() {
        val json = snapshot(device = "Pixel \"5\"").toJson()
        assertTrue(json.contains("Pixel \\\"5\\\""))
    }

    private fun snapshot(device: String = "Test Phone") = DiagnosticsSnapshot(
        capturedAtMs = 1L,
        stt = SttMetrics(
            backend = "test",
            activeLanguage = Language.HINDI,
            modelLoaded = false,
            modelSizeBytes = null,
            avgFinalisationLatencyMs = null,
            avgRealTimeFactor = null,
            utterancesProcessed = 0,
            measuredWer = null,
        ),
        tts = TtsMetrics(
            backend = "test",
            activeLanguage = null,
            voiceLoaded = false,
            modelSizeBytes = null,
            avgSynthesisMs = null,
            avgRealTimeFactor = null,
            utterancesSynthesised = 0,
            underruns = 0,
        ),
        transport = TransportMetrics(
            kind = TransportKind.WIFI_DIRECT,
            state = ConnectionState.DISCONNECTED,
            roundTripMs = null,
            packetsSent = 0,
            packetsReceived = 0,
            packetsDiscarded = 0,
            sendFailures = 0,
            bytesSent = 0,
            bytesReceived = 0,
            channelBusy = false,
            pairingConfirmed = false,
        ),
        system = SystemMetrics(
            appMemoryBytes = null,
            javaHeapBytes = null,
            nativeHeapBytes = null,
            totalDeviceRamBytes = null,
            availableRamBytes = null,
            cpuLoad = null,
            apkSizeBytes = null,
            lowMemory = false,
            deviceModel = device,
            androidVersion = "Android 14 (API 34)",
        ),
    )
}
