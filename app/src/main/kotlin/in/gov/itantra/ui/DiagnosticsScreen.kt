package `in`.gov.itantra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import `in`.gov.itantra.ui.components.IconBadge
import `in`.gov.itantra.ui.components.InlineMessage
import `in`.gov.itantra.ui.components.StatusDot
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

    run {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(
                        color = if (uiState.snapshot != null) ITantraTheme.extended.success else MaterialTheme.colorScheme.outline,
                        pulsing = uiState.snapshot != null,
                    )
                    Spacer(Modifier.weight(1f))
                    FilledTonalIconButton(
                        onClick = { viewModel.share(context) },
                        enabled = uiState.snapshot != null,
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = chrome.shareJson)
                    }
                }
            }
            uiState.shareError?.let { InlineMessage(text = it, isError = true) }

            val snap = uiState.snapshot
            if (snap == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            chrome.waitingSample,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                SttCard(snap, uiState, chrome, onTestMode = viewModel::setTestMode)
                TtsCard(snap, chrome)
                TransportCard(snap, chrome)
                SystemCard(snap, chrome)
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
    MetricCard(chrome.sttTitle, Icons.Filled.Mic) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(chrome.werTestMode, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
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
private fun TtsCard(snap: DiagnosticsSnapshot, chrome: UiStrings) {
    val tts = snap.tts
    MetricCard(chrome.ttsTitle, Icons.AutoMirrored.Filled.VolumeUp) {
        MetricRow("Model", tts.backend)
        MetricRow("Model size", formatMb(tts.modelSizeBytes))
        MetricRow("Avg synthesis", formatMs(tts.avgSynthesisMs))
        MetricRow("Avg RTF", formatRtf(tts.avgRealTimeFactor))
        MetricRow("Android TTS fallbacks", tts.androidFallbackCount.toString())
    }
}

@Composable
private fun TransportCard(snap: DiagnosticsSnapshot, chrome: UiStrings) {
    val tx = snap.transport
    MetricCard(chrome.transportTitle, Icons.Filled.SwapHoriz) {
        MetricRow("Connection", formatKind(tx.kind))
        MetricRow("State", tx.state.name)
        MetricRow("Round-trip", tx.roundTripMs?.let { "$it ms" } ?: "—")
        MetricRow("Packets sent", tx.packetsSent.toString())
        MetricRow("Packets received", tx.packetsReceived.toString())
        MetricRow("Packets discarded", tx.packetsDiscarded.toString())
    }
}

@Composable
private fun SystemCard(snap: DiagnosticsSnapshot, chrome: UiStrings) {
    val sys = snap.system
    MetricCard(chrome.systemTitle, Icons.Filled.Memory) {
        MetricRow("CPU", formatCpu(sys.cpuLoad))
        MetricRow("Device", sys.deviceModel ?: "—")
        MetricRow("Android", sys.androidVersion ?: "—")
    }
}

@Composable
private fun MetricCard(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = icon,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 36.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = if (value == "—") MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
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
