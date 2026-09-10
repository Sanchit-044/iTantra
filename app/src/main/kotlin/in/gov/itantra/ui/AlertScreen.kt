package `in`.gov.itantra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.lang.UiStrings

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
        Spacer(Modifier.height(16.dp))

        AlertHeader(ready = ready, sending = uiState.alertSending, chrome = chrome)

        Spacer(Modifier.height(20.dp))

        ChannelSelector(
            selectedChannel = uiState.alertChannel,
            onChannelSelected = { viewModel.setAlertChannel(it) },
            chrome = chrome,
        )

        Spacer(Modifier.height(24.dp))

        SectionLabel(chrome.quickAlertsTitle)
        Spacer(Modifier.height(8.dp))
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

        SectionLabel(chrome.customMessageTitle)
        Spacer(Modifier.height(8.dp))
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
    Column {
        if (sending) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = chrome.sending,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        } else {
            Text(
                text = if (ready) chrome.alertReadyHelp else chrome.alertNeedPair,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ready) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun QuickAlertButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
        SectionLabel(chrome.broadcastChannel)
        Spacer(Modifier.height(8.dp))
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
                    icon = {
                        if (channel == selectedChannel) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    },
                ) {
                    Text(label)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = chrome.channelHelp,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
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

            when (mode) {
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
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSend,
            enabled = customText.trim().isNotEmpty() && !sending,
            modifier = Modifier.align(Alignment.End),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(if (sending) chrome.sending else chrome.sendTyped)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
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
            Text(
                text = recordingText.ifBlank { chrome.recordingAlert },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel) {
                    Text(chrome.pairingCancel)
                }
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(chrome.sendTyped)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        } else {
            FilledIconButton(
                onClick = onStart,
                enabled = !sending,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(Icons.Filled.Mic, contentDescription = chrome.recordVoiceAlert, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = chrome.recordVoiceAlert,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
