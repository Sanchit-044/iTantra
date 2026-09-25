package `in`.gov.itantra.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.diag.DiagnosticsSnapshot
import `in`.gov.itantra.core.diag.SttMetrics
import `in`.gov.itantra.core.diag.SystemMetrics
import `in`.gov.itantra.core.diag.TtsMetrics
import `in`.gov.itantra.core.diag.TransportMetrics
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.TransportKind
import `in`.gov.itantra.ui.components.IconBadge
import `in`.gov.itantra.ui.components.InlineMessage
import `in`.gov.itantra.ui.components.StatusDot
import `in`.gov.itantra.ui.components.StatusPill
import kotlin.math.roundToInt

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Live Telemetry Monitor & Export Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(
                    color = if (uiState.snapshot != null) ITantraTheme.extended.success else MaterialTheme.colorScheme.outline,
                    pulsing = uiState.snapshot != null,
                    size = 12.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (uiState.snapshot != null) "Telemetry Live" else "Connecting Telemetry",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (uiState.snapshot != null) "Polling at 1 Hz · On-Device Diagnostics" else "Waiting for metric stream...",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = { viewModel.share(context) },
                    enabled = uiState.snapshot != null,
                    shape = CircleShape,
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(chrome.shareJson, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        uiState.shareError?.let {
            InlineMessage(text = it, isError = true)
        }

        val snap = uiState.snapshot
        if (snap == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(strokeWidth = 3.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        chrome.waitingSample,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            // 2. KPI Summary Micro-Grid (4 Key Health Indicators)
            KpiSummaryGrid(snap)

            // 3. Speech-to-Text (STT) Metrics Card
            SttCard(snap.stt, uiState, chrome, onTestMode = viewModel::setTestMode)

            // 4. Text-to-Speech (TTS) Metrics Card
            TtsCard(snap.tts, chrome)

            // 5. Offline Radio & Mesh Transport Card
            TransportCard(snap.transport, chrome)

            // 6. Device Hardware & Memory Budget Card
            SystemCard(snap.system, chrome)
        }
    }
}

/** 2x2 grid of key system health indicators. */
@Composable
private fun KpiSummaryGrid(snap: DiagnosticsSnapshot) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KpiTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Mic,
                iconColor = MaterialTheme.colorScheme.primary,
                iconBg = MaterialTheme.colorScheme.primaryContainer,
                title = "STT Latency",
                value = formatMs(snap.stt.avgFinalisationLatencyMs),
                subtitle = if (snap.stt.modelLoaded) "Model Loaded" else "Idle",
            )
            KpiTile(
                modifier = Modifier.weight(1f),
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                iconColor = MaterialTheme.colorScheme.secondary,
                iconBg = MaterialTheme.colorScheme.secondaryContainer,
                title = "TTS RTF",
                value = formatRtf(snap.tts.avgRealTimeFactor),
                subtitle = "< 1.0x Real-time",
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KpiTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.SwapHoriz,
                iconColor = MaterialTheme.colorScheme.tertiary,
                iconBg = MaterialTheme.colorScheme.tertiaryContainer,
                title = "Radio RTT",
                value = snap.transport.roundTripMs?.let { "$it ms" } ?: "—",
                subtitle = formatKind(snap.transport.kind),
            )
            KpiTile(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Memory,
                iconColor = MaterialTheme.colorScheme.primary,
                iconBg = MaterialTheme.colorScheme.surfaceVariant,
                title = "CPU / RAM",
                value = formatCpu(snap.system.cpuLoad),
                subtitle = formatMb(snap.system.appMemoryBytes),
            )
        }
    }
}

@Composable
private fun KpiTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    title: String,
    value: String,
    subtitle: String,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (value == "—") MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SttCard(
    stt: SttMetrics,
    uiState: DiagnosticsUiState,
    chrome: UiStrings,
    onTestMode: (Boolean) -> Unit,
) {
    SectionCard(
        title = chrome.sttTitle,
        icon = Icons.Filled.Mic,
        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
        iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        badge = {
            StatusPill(
                text = if (stt.modelLoaded) "Model Active" else "Idle",
                containerColor = if (stt.modelLoaded) ITantraTheme.extended.successContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (stt.modelLoaded) ITantraTheme.extended.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        // WER Evaluation Mode Box
        Surface(
            color = if (uiState.testMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(
                1.dp,
                if (uiState.testMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            ),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = chrome.werTestMode,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Score accuracy against standard corpus",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = uiState.testMode, onCheckedChange = onTestMode)
                }

                if (uiState.testMode) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Scored Sentences", style = MaterialTheme.typography.labelMedium)
                        Text(
                            "${stt.werSampleCount} / 10",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (stt.werSampleCount / 10f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        drawStopIndicator = {},
                    )

                    Spacer(Modifier.height(10.dp))
                    Text("Prompt to Speak:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stt.nextTestPrompt ?: "—",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }

        MetricRow("Current WER", formatWer(stt.measuredWer, stt.werSampleCount), highlight = stt.measuredWer != null)
        MetricRow("Active Language", stt.activeLanguage?.endonym ?: "—")
        MetricRow("Inference Engine", stt.backend)
        MetricRow("Model Memory Size", formatMb(stt.modelSizeBytes))
        MetricRow("Avg Latency (Last 10)", formatMs(stt.avgFinalisationLatencyMs))
        MetricRow("Real-Time Factor (RTF)", formatRtf(stt.avgRealTimeFactor))
        MetricRow("Utterances Processed", stt.utterancesProcessed.toString(), isLast = true)
    }
}

@Composable
private fun TtsCard(tts: TtsMetrics, chrome: UiStrings) {
    SectionCard(
        title = chrome.ttsTitle,
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        badge = {
            StatusPill(
                text = if (tts.voiceLoaded) "Voice Active" else "Idle",
                containerColor = if (tts.voiceLoaded) ITantraTheme.extended.successContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (tts.voiceLoaded) ITantraTheme.extended.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        MetricRow("Synthesis Engine", tts.backend)
        MetricRow("Voice Model Size", formatMb(tts.modelSizeBytes))
        MetricRow("Avg Synthesis Duration", formatMs(tts.avgSynthesisMs))
        MetricRow("Real-Time Factor (RTF)", formatRtf(tts.avgRealTimeFactor))
        MetricRow("Buffer Underruns", tts.underruns.toString())
        MetricRow("Android Fallback Count", tts.androidFallbackCount.toString(), isLast = true)
    }
}

@Composable
private fun TransportCard(tx: TransportMetrics, chrome: UiStrings) {
    SectionCard(
        title = chrome.transportTitle,
        icon = Icons.Filled.SwapHoriz,
        iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
        iconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        badge = {
            StatusPill(
                text = tx.state.name,
                containerColor = if (tx.state == ConnectionState.CONNECTED) ITantraTheme.extended.successContainer else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (tx.state == ConnectionState.CONNECTED) ITantraTheme.extended.onSuccessContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        MetricRow("Transport Medium", formatKind(tx.kind))
        MetricRow("Round-Trip Latency (RTT)", tx.roundTripMs?.let { "$it ms" } ?: "—")
        MetricRow("Packets Sent", tx.packetsSent.toString())
        MetricRow("Packets Received", tx.packetsReceived.toString())
        MetricRow("Packets Discarded", tx.packetsDiscarded.toString())
        MetricRow(
            "Data Transferred",
            "${formatMb(tx.bytesSent)} ↑  ·  ${formatMb(tx.bytesReceived)} ↓",
        )
        MetricRow(
            "Pairing / Security",
            if (tx.pairingConfirmed) "AES-256-GCM (Paired)" else "Unconfirmed",
            isLast = true,
        )
    }
}

@Composable
private fun SystemCard(sys: SystemMetrics, chrome: UiStrings) {
    SectionCard(
        title = chrome.systemTitle,
        icon = Icons.Filled.Memory,
        iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
        iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        // CPU Load Progress Indicator
        val cpuLoad = sys.cpuLoad ?: 0.0
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "CPU Load",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = formatCpu(sys.cpuLoad),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { cpuLoad.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                drawStopIndicator = {},
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        MetricRow("App Process RAM (PSS)", formatMb(sys.appMemoryBytes))
        MetricRow("Java Heap In-Use", formatMb(sys.javaHeapBytes))
        MetricRow("Native ONNX Heap", formatMb(sys.nativeHeapBytes))
        MetricRow("Total Device RAM", formatMb(sys.totalDeviceRamBytes))
        MetricRow("Device Model", sys.deviceModel ?: "—")
        MetricRow("Android Version", sys.androidVersion ?: "—", isLast = true)
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(
                    icon = icon,
                    containerColor = iconContainerColor,
                    contentColor = iconContentColor,
                    size = 38.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                badge?.invoke()
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun MetricRow(
    label: String,
    value: String,
    highlight: Boolean = false,
    isLast: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
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
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
            color = when {
                highlight -> MaterialTheme.colorScheme.primary
                value == "—" -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End,
        )
    }
    if (!isLast) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }
}

// ------------------------------------------------------------------ Formatting Utilities

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
