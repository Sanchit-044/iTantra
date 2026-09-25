package `in`.gov.itantra.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.ui.components.LabeledBasicTextField
import `in`.gov.itantra.ui.components.PulseRing
import `in`.gov.itantra.ui.components.SectionHeader
import `in`.gov.itantra.ui.components.StatusDot

private enum class ComposeMode { SPEAK, TYPE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val chrome = UiStrings.forLanguage(uiState.uiLanguage)
    var customText by rememberSaveable { mutableStateOf("") }
    var composeMode by rememberSaveable { mutableStateOf(ComposeMode.SPEAK) }

    LaunchedEffect(Unit) {
        viewModel.refreshWifiState()
    }

    val ready = uiState.canSendAlert
    val language = uiState.currentLanguage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. Dual-Radio Auto Transmission Status Pill (Wi-Fi + Bluetooth Mesh Auto-Flood)
        AutoBroadcastHeader(ready = ready, sending = uiState.alertSending, chrome = chrome)

        // 2. Custom Voice-First SOS Broadcast (Voice on left, preselected)
        SectionHeader(chrome.customMessageTitle)

        CustomMessageCard(
            mode = composeMode,
            onModeChange = { composeMode = it },
            customText = customText,
            onCustomTextChange = { if (it.length <= 200) customText = it },
            onSendTyped = {
                val trimmed = customText.trim()
                if (trimmed.isEmpty()) return@CustomMessageCard
                viewModel.sendCustomAlert(trimmed)
                customText = ""
            },
            sending = uiState.alertSending,
            isRecording = uiState.isRecordingAlertMessage,
            recordingText = uiState.alertRecordingText,
            currentLanguage = language,
            onStartRecording = { viewModel.startAlertRecording() },
            onStopRecording = { viewModel.stopAlertRecording() },
            onCancelRecording = { viewModel.cancelAlertRecording() },
            chrome = chrome,
        )

        // 3. Tactical Quick SOS Alert Grid (Accessible 2x2 Grid + All Clear)
        SectionHeader(chrome.quickAlertsTitle)

        QuickAlertGrid(
            language = language,
            sending = uiState.alertSending,
            onSelectTemplate = { viewModel.sendAlertTemplate(it) },
        )
    }
}

/** Reassuring Auto-Broadcast Status Bar showing automatic dual-channel readiness */
@Composable
private fun AutoBroadcastHeader(ready: Boolean, sending: Boolean, chrome: UiStrings) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = when {
            sending -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ready -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        },
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (!ready && !sending) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(
                            if (ready) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (ready) Icons.Filled.SettingsInputAntenna else Icons.Filled.Info,
                        contentDescription = null,
                        tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        sending -> "Broadcasting Emergency SOS..."
                        ready -> "Auto-Broadcast Ready (All Radios)"
                        else -> "Pairing Required"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        sending -> "Transmitting across Wi-Fi Direct & Bluetooth Mesh..."
                        ready -> "Automatic dual-channel flood: Wi-Fi Direct + Bluetooth Mesh"
                        else -> chrome.alertNeedPair
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ready && !sending) {
                StatusDot(color = ITantraTheme.extended.success, pulsing = true, size = 8.dp)
            }
        }
    }
}

private data class AlertVisual(
    val icon: ImageVector,
    val iconBg: Color,
    val english: String,
)

/** 2-Column Tactical Quick SOS Alert Grid for instant one-tap access */
@Composable
private fun QuickAlertGrid(
    language: Language,
    sending: Boolean,
    onSelectTemplate: (AlertTemplate) -> Unit,
) {
    val items = listOf(
        AlertTemplate.EMERGENCY_ASSISTANCE to AlertVisual(
            icon = Icons.Filled.Warning,
            iconBg = MaterialTheme.colorScheme.error,
            english = "Emergency SOS",
        ),
        AlertTemplate.MEDICAL_HELP to AlertVisual(
            icon = Icons.Filled.MedicalServices,
            iconBg = MaterialTheme.colorScheme.error,
            english = "Medical Help",
        ),
        AlertTemplate.EVACUATE_IMMEDIATELY to AlertVisual(
            icon = Icons.AutoMirrored.Filled.DirectionsRun,
            iconBg = ITantraTheme.extended.warning,
            english = "Evacuate Now",
        ),
        AlertTemplate.STAY_IN_POSITION to AlertVisual(
            icon = Icons.Filled.Security,
            iconBg = MaterialTheme.colorScheme.primary,
            english = "Hold Position",
        ),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickAlertTile(
                template = items[0].first,
                visual = items[0].second,
                language = language,
                enabled = !sending,
                onClick = { onSelectTemplate(items[0].first) },
                modifier = Modifier.weight(1f),
            )
            QuickAlertTile(
                template = items[1].first,
                visual = items[1].second,
                language = language,
                enabled = !sending,
                onClick = { onSelectTemplate(items[1].first) },
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickAlertTile(
                template = items[2].first,
                visual = items[2].second,
                language = language,
                enabled = !sending,
                onClick = { onSelectTemplate(items[2].first) },
                modifier = Modifier.weight(1f),
            )
            QuickAlertTile(
                template = items[3].first,
                visual = items[3].second,
                language = language,
                enabled = !sending,
                onClick = { onSelectTemplate(items[3].first) },
                modifier = Modifier.weight(1f),
            )
        }

        // Full Width All Clear Tile
        val allClearVisual = AlertVisual(
            icon = Icons.Filled.CheckCircle,
            iconBg = ITantraTheme.extended.success,
            english = "All Clear / Threat Neutralized",
        )
        Surface(
            onClick = { onSelectTemplate(AlertTemplate.ALL_CLEAR) },
            enabled = !sending,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(allClearVisual.iconBg.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        allClearVisual.icon,
                        contentDescription = null,
                        tint = allClearVisual.iconBg,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = AlertTemplate.ALL_CLEAR.phrase(language),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (language != Language.ENGLISH) {
                        Text(
                            text = allClearVisual.english,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = ITantraTheme.extended.success,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun QuickAlertTile(
    template: AlertTemplate,
    visual: AlertVisual,
    language: Language,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(visual.iconBg.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        visual.icon,
                        contentDescription = null,
                        tint = visual.iconBg,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = template.phrase(language),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = visual.english,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomMessageCard(
    mode: ComposeMode,
    onModeChange: (ComposeMode) -> Unit,
    customText: String,
    onCustomTextChange: (String) -> Unit,
    onSendTyped: () -> Unit,
    sending: Boolean,
    isRecording: Boolean,
    recordingText: String,
    currentLanguage: Language,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    chrome: UiStrings,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Sleek Modern Pill Capsule Tab Switcher
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                ) {
                    val speakSelected = mode == ComposeMode.SPEAK
                    Surface(
                        onClick = { onModeChange(ComposeMode.SPEAK) },
                        enabled = !isRecording,
                        shape = CircleShape,
                        color = if (speakSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        border = if (speakSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) else null,
                        shadowElevation = if (speakSelected) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = null,
                                tint = if (speakSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = chrome.speakTab,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (speakSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (speakSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    val typeSelected = mode == ComposeMode.TYPE
                    Surface(
                        onClick = { onModeChange(ComposeMode.TYPE) },
                        enabled = !isRecording,
                        shape = CircleShape,
                        color = if (typeSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        border = if (typeSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)) else null,
                        shadowElevation = if (typeSelected) 2.dp else 0.dp,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                tint = if (typeSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = chrome.typeTab,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (typeSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (typeSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "composeMode",
            ) { current ->
                when (current) {
                    ComposeMode.SPEAK -> SpeakComposer(
                        isRecording = isRecording,
                        recordingText = recordingText,
                        sending = sending,
                        currentLanguage = currentLanguage,
                        onStart = onStartRecording,
                        onStop = onStopRecording,
                        onCancel = onCancelRecording,
                        chrome = chrome,
                    )
                    ComposeMode.TYPE -> TypeComposer(
                        customText = customText,
                        onCustomTextChange = onCustomTextChange,
                        onSend = onSendTyped,
                        sending = sending,
                        chrome = chrome,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeakComposer(
    isRecording: Boolean,
    recordingText: String,
    sending: Boolean,
    currentLanguage: Language,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    chrome: UiStrings,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isRecording) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp),
            ) {
                PulseRing(color = MaterialTheme.colorScheme.error, size = 96.dp)
                PulseRing(color = MaterialTheme.colorScheme.error, size = 96.dp, delayMillis = 600)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(68.dp),
                    shadowElevation = 4.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(32.dp))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Live recognized transcript container
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(color = MaterialTheme.colorScheme.error, pulsing = true, size = 8.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Listening (${currentLanguage.endonym})...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = recordingText.ifBlank { "Speak your emergency broadcast clearly into the mic..." },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (recordingText.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                        color = if (recordingText.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).height(46.dp),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(chrome.pairingCancel)
                }
                Button(
                    onClick = onStop,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1.3f).height(46.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Broadcast", fontWeight = FontWeight.Bold)
                }
            }
        } else {
            Spacer(Modifier.height(4.dp))
            Surface(
                onClick = onStart,
                enabled = !sending,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                shadowElevation = 3.dp,
                modifier = Modifier.size(76.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = chrome.recordVoiceAlert,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Tap to Record Voice SOS",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Converts speech to text and floods over Wi-Fi & Bluetooth",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun TypeComposer(
    customText: String,
    onCustomTextChange: (String) -> Unit,
    onSend: () -> Unit,
    sending: Boolean,
    chrome: UiStrings,
) {
    val quickPhrases = listOf(
        "Need Water & Food",
        "Road Blocked",
        "Injured Person",
        "Safe at Base",
    )

    Column {
        LabeledBasicTextField(
            value = customText,
            onValueChange = onCustomTextChange,
            label = chrome.customMessageTitle,
            hint = chrome.freeText,
            singleLine = false,
            minLines = 3,
            supportingText = "${customText.length} / 200",
            enabled = !sending,
        )

        Spacer(Modifier.height(8.dp))

        // Quick Tag Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            quickPhrases.forEach { phrase ->
                SuggestionChip(
                    onClick = { onCustomTextChange(if (customText.isBlank()) phrase else "$customText $phrase") },
                    label = { Text(phrase, style = MaterialTheme.typography.labelSmall) },
                    shape = CircleShape,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onSend,
            enabled = customText.trim().isNotEmpty() && !sending,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (sending) chrome.sending else "Broadcast", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
    }
}
