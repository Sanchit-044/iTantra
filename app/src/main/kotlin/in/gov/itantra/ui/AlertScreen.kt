package `in`.gov.itantra.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import `in`.gov.itantra.ui.components.IconBadge
import `in`.gov.itantra.ui.components.PulseRing
import `in`.gov.itantra.ui.components.SectionHeader
import `in`.gov.itantra.ui.components.StatusPill

private enum class ComposeMode { TYPE, SPEAK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val chrome = UiStrings.forLanguage(uiState.uiLanguage)
    var customText by rememberSaveable { mutableStateOf("") }
    var composeMode by rememberSaveable { mutableStateOf(ComposeMode.TYPE) }

    LaunchedEffect(Unit) {
        viewModel.refreshWifiState()
    }

    val ready = uiState.canSendAlert
    val language = uiState.currentLanguage

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
    ) {
        // Warning or sending state banner
        if (!ready || uiState.alertSending) {
            AlertHeader(ready = ready, sending = uiState.alertSending, chrome = chrome)
            Spacer(Modifier.height(18.dp))
        }

        // 1. Radio Transmission Channel Selector
        ChannelSelector(
            selectedChannel = uiState.alertChannel,
            onChannelSelected = { viewModel.setAlertChannel(it) },
            chrome = chrome,
        )

        Spacer(Modifier.height(20.dp))

        // 2. Tactical Mission Quick Alerts (Unified Cards)
        SectionHeader(chrome.quickAlertsTitle)
        Spacer(Modifier.height(10.dp))

        val quickAlertTemplates = listOf(
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
            AlertTemplate.ALL_CLEAR to AlertVisual(
                icon = Icons.Filled.CheckCircle,
                iconBg = ITantraTheme.extended.success,
                english = "All Clear",
            ),
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickAlertTemplates.forEach { (template, visual) ->
                QuickAlertCard(
                    template = template,
                    visual = visual,
                    language = language,
                    enabled = !uiState.alertSending,
                    onClick = { viewModel.sendAlertTemplate(template) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // 4. Custom Broadcast Message Card (Type or Speak)
        SectionHeader(chrome.customMessageTitle)
        Spacer(Modifier.height(10.dp))

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
            onStartRecording = { viewModel.startAlertRecording() },
            onStopRecording = { viewModel.stopAlertRecording() },
            onCancelRecording = { viewModel.cancelAlertRecording() },
            chrome = chrome,
        )
    }
}

private data class AlertVisual(
    val icon: ImageVector,
    val iconBg: Color,
    val english: String,
)

@Composable
private fun QuickAlertCard(
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
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(visual.iconBg.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    visual.icon,
                    contentDescription = null,
                    tint = visual.iconBg,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.phrase(language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (language != Language.ENGLISH) {
                    Text(
                        text = visual.english,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun AlertHeader(ready: Boolean, sending: Boolean, chrome: UiStrings) {
    val container = if (ready) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (ready) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        border = if (ready) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        strokeWidth = 3.dp,
                        color = content,
                    )
                }
                IconBadge(
                    icon = if (ready) Icons.Filled.Campaign else Icons.Filled.Info,
                    containerColor = content.copy(alpha = 0.12f),
                    contentColor = content,
                    size = 40.dp,
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = when {
                    sending -> chrome.sending
                    ready -> chrome.alertReadyHelp
                    else -> chrome.alertNeedPair
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelSelector(
    selectedChannel: AlertChannel,
    onChannelSelected: (AlertChannel) -> Unit,
    chrome: UiStrings,
) {
    Column {
        SectionHeader(chrome.broadcastChannel)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                AlertChannel.ALL to (chrome.channelAllLabel to Icons.Filled.SettingsInputAntenna),
                AlertChannel.WIFI to (chrome.channelWifiLabel to Icons.Filled.Wifi),
                AlertChannel.BLUETOOTH to (chrome.channelBluetoothLabel to Icons.Filled.Bluetooth),
            )
            options.forEachIndexed { index, (channel, pair) ->
                val (label, icon) = pair
                SegmentedButton(
                    selected = channel == selectedChannel,
                    onClick = { onChannelSelected(channel) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    icon = {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                ) {
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
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
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    chrome: UiStrings,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == ComposeMode.TYPE,
                    onClick = { onModeChange(ComposeMode.TYPE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    enabled = !isRecording,
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                ) {
                    Text(chrome.typeTab)
                }
                SegmentedButton(
                    selected = mode == ComposeMode.SPEAK,
                    onClick = { onModeChange(ComposeMode.SPEAK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    enabled = !isRecording,
                    icon = { Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp)) },
                ) {
                    Text(chrome.speakTab)
                }
            }
            Spacer(Modifier.height(14.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "composeMode",
            ) { current ->
                when (current) {
                    ComposeMode.TYPE -> TypeComposer(
                        customText = customText,
                        onCustomTextChange = onCustomTextChange,
                        onSend = onSendTyped,
                        sending = sending,
                        chrome = chrome,
                    )
                    ComposeMode.SPEAK -> SpeakComposer(
                        isRecording = isRecording,
                        recordingText = recordingText,
                        sending = sending,
                        onStart = onStartRecording,
                        onStop = onStopRecording,
                        onCancel = onCancelRecording,
                        chrome = chrome,
                    )
                }
            }
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
        `in`.gov.itantra.ui.components.LabeledBasicTextField(
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
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (sending) chrome.sending else chrome.sendTyped, style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun SpeakComposer(
    isRecording: Boolean,
    recordingText: String,
    sending: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit,
    chrome: UiStrings,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        if (isRecording) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(104.dp)) {
                PulseRing(color = MaterialTheme.colorScheme.error, size = 72.dp)
                PulseRing(color = MaterialTheme.colorScheme.error, size = 72.dp, delayMillis = 800)
                IconBadgeCircle()
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = recordingText.ifBlank { chrome.recordingAlert },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(chrome.pairingCancel)
                }
                Button(
                    onClick = onStop,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(chrome.sendTyped)
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            FilledIconButton(
                onClick = onStart,
                enabled = !sending,
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = chrome.recordVoiceAlert, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = chrome.recordVoiceAlert,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun IconBadgeCircle() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(32.dp))
        }
    }
}
