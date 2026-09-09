package `in`.gov.itantra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.diag.DiagnosticsSnapshot
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.core.transport.TransportKind
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onOpenSettings: () -> Unit,
    uiLanguage: Language = Language.ENGLISH,
    viewModel: DiagnosticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val chrome = UiStrings.forLanguage(uiLanguage)
    val context = LocalContext.current

    DisposableEffect(Unit) {
        viewModel.startPolling()
        onDispose { viewModel.stopPolling() }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chrome.analysisHelp,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.material3.IconButton(
                    onClick = { viewModel.share(context) },
                    enabled = uiState.snapshot != null,
                ) {
                    Icon(Icons.Filled.Share, contentDescription = chrome.shareJson, tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (uiState.shareError != null) {
                Text(
                    text = uiState.shareError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            val snap = uiState.snapshot
            if (snap == null) {
                Text(chrome.waitingSample)
            } else {
                SttCard(snap, uiState, chrome, onTestMode = viewModel::setTestMode)
                TtsCard(snap)
                TransportCard(snap)
                SystemCard(snap)
            }
        }
    }
}

@Composable
private fun SttCard(
    snap: DiagnosticsSnapshot,
    uiState: DiagnosticsUiState,
    chrome: UiStrings,
    onTestMode: (Boolean) -> Unit,
) {
    val stt = snap.stt
    MetricCard("STT") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(chrome.werTestMode, modifier = Modifier.weight(1f))
            Switch(checked = uiState.testMode, onCheckedChange = onTestMode)
        }
        MetricRow("Current WER", formatWer(stt.measuredWer, stt.werSampleCount))
        if (uiState.testMode) {
            MetricRow("Scored sentences", "${stt.werSampleCount} / 10")
            MetricRow("Speak this", stt.nextTestPrompt ?: "—")
        }
        MetricRow("Model", stt.backend)
        MetricRow("Model size", formatMb(stt.modelSizeBytes))
        MetricRow("Avg latency (last 10)", formatMs(stt.avgFinalisationLatencyMs))
        MetricRow("Current language", stt.activeLanguage?.endonym ?: "—")
    }
}

@Composable
private fun TtsCard(snap: DiagnosticsSnapshot) {
    val tts = snap.tts
    MetricCard("TTS") {
        MetricRow("Model", tts.backend)
        MetricRow("Model size", formatMb(tts.modelSizeBytes))
        MetricRow("Avg synthesis", formatMs(tts.avgSynthesisMs))
        MetricRow("Avg RTF", formatRtf(tts.avgRealTimeFactor))
        MetricRow("Android TTS fallbacks", tts.androidFallbackCount.toString())
    }
}

@Composable
private fun TransportCard(snap: DiagnosticsSnapshot) {
    val tx = snap.transport
    MetricCard("Transport") {
        MetricRow("Connection", formatKind(tx.kind))
        MetricRow("State", tx.state.name)
        MetricRow("Round-trip", tx.roundTripMs?.let { "$it ms" } ?: "—")
        MetricRow("Packets sent", tx.packetsSent.toString())
        MetricRow("Packets received", tx.packetsReceived.toString())
        MetricRow("Packets discarded", tx.packetsDiscarded.toString())
    }
}

@Composable
private fun SystemCard(snap: DiagnosticsSnapshot) {
    val sys = snap.system
    MetricCard("System") {
        MetricRow("App RAM", formatMb(sys.appMemoryBytes))
        MetricRow("Model RAM loaded", formatMb(sys.modelRamBytes))
        MetricRow("CPU", formatCpu(sys.cpuLoad))
        MetricRow("Device", sys.deviceModel ?: "—")
        MetricRow("Android", sys.androidVersion ?: "—")
        MetricRow("APK size", formatMb(sys.apkSizeBytes))
    }
}

@Composable
private fun MetricCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatMb(bytes: Long?): String {
    if (bytes == null || bytes < 0) return "—"
    return "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

private fun formatMs(value: Double?): String {
    if (value == null || value.isNaN() || value.isInfinite()) return "—"
    return "${value.roundToInt()} ms"
}

private fun formatRtf(value: Double?): String {
    if (value == null || value.isNaN() || value.isInfinite()) return "—"
    return "%.2f".format(value)
}

private fun formatCpu(value: Double?): String {
    if (value == null || value.isNaN() || value.isInfinite()) return "—"
    return "${(value * 100).roundToInt()}%"
}

private fun formatWer(value: Double?, samples: Int): String {
    if (value == null || samples <= 0 || value.isNaN() || value.isInfinite()) return "—"
    return "%.1f%%".format(value * 100.0)
}

private fun formatKind(kind: TransportKind?): String = when (kind) {
    TransportKind.WIFI_DIRECT -> "Wi-Fi Direct"
    TransportKind.BLUETOOTH_RFCOMM -> "Bluetooth"
    TransportKind.LAN -> "LAN / Hotspot"
    TransportKind.LOOPBACK -> "Loopback"
    null -> "—"
}
