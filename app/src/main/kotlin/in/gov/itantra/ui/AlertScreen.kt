package `in`.gov.itantra.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.ui.components.IconBadge
import `in`.gov.itantra.ui.components.PulseRing
import `in`.gov.itantra.ui.components.SectionHeader

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
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // Only surface a banner when something needs attention (not paired / sending).
        if (!ready || uiState.alertSending) {
            AlertHeader(ready = ready, sending = uiState.alertSending, chrome = chrome)
            Spacer(Modifier.height(24.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }

        ChannelSelector(
            selectedChannel = uiState.alertChannel,
            onChannelSelected = { viewModel.setAlertChannel(it) },
            chrome = chrome,
        )

        Spacer(Modifier.height(24.dp))

        SectionHeader(chrome.quickAlertsTitle)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AlertTemplate.entries.forEach { template ->
                QuickAlertButton(
                    text = template.phrase(language),
                    enabled = !uiState.alertSending,
                    onClick = { viewModel.sendAlertTemplate(template) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))

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

        uiState.notice?.let {
            Spacer(Modifier.height(20.dp))
            UserNoticeBanner(notice = uiState.notice, strings = chrome)
        }
    }
}

@Composable
private fun AlertHeader(ready: Boolean, sending: Boolean, chrome: UiStrings) {
    val container = if (ready) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (ready) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp,
                        color = content,
                    )
                }
                IconBadge(
                    icon = if (ready) Icons.Filled.Campaign else Icons.Filled.Info,
                    containerColor = content.copy(alpha = 0.12f),
                    contentColor = content,
                    size = 44.dp,
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

@Composable
private fun QuickAlertButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        ),
        contentPadding = PaddingValues(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = Icons.Filled.Warning,
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                size = 40.dp,
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
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
        Spacer(Modifier.height(10.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            val options = listOf(
                AlertChannel.ALL to chrome.channelAllLabel,
                AlertChannel.WIFI to chrome.channelWifiLabel,
                AlertChannel.BLUETOOTH to chrome.channelBluetoothLabel,
            )
            options.forEachIndexed { index, (channel, label) ->
                SegmentedButton(
                    selected = channel == selectedChannel,
                    onClick = { onChannelSelected(channel) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(label, maxLines = 1)
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
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == ComposeMode.TYPE,
                    onClick = { onModeChange(ComposeMode.TYPE) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    enabled = !isRecording,
                ) {
                    Text(chrome.typeTab)
                }
                SegmentedButton(
                    selected = mode == ComposeMode.SPEAK,
                    onClick = { onModeChange(ComposeMode.SPEAK) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    enabled = !isRecording,
                ) {
                    Text(chrome.speakTab)
                }
            }
            Spacer(Modifier.height(16.dp))

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
    Column {
        OutlinedTextField(
            value = customText,
            onValueChange = onCustomTextChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 3,
            placeholder = { Text(chrome.freeText) },
            supportingText = {
                Text(
                    text = "${customText.length} / 200",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                )
            },
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onSend,
            enabled = customText.trim().isNotEmpty() && !sending,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
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
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp)) {
                    Text(chrome.pairingCancel)
                }
                Button(
                    onClick = onStop,
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
