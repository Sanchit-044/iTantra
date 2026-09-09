package `in`.gov.itantra.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.core.transport.ConnectionState

import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.text.font.FontWeight

@Composable
fun AlertScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val chrome = UiStrings.forLanguage(uiState.uiLanguage)
    var customText by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) {
        viewModel.refreshWifiState()
    }

    val ready = uiState.canSendAlert
    val language = uiState.currentLanguage
    val isP2pConnected = uiState.connectionState == ConnectionState.CONNECTED && uiState.pairingConfirmed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Broadcast Channel Selector
        AlertChannelSelector(
            selectedChannel = uiState.alertChannel,
            onChannelSelected = { viewModel.setAlertChannel(it) }
        )

        // Pre-defined Alerts
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            AlertTemplate.entries.forEach { template ->
                val interactionSource = remember { MutableInteractionSource() }
                
                Button(
                    onClick = { viewModel.sendAlertTemplate(template) },
                    enabled = !uiState.alertSending,
                    modifier = Modifier.fillMaxWidth().height(60.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    ),
                    interactionSource = interactionSource
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = template.phrase(language), 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        
        // Custom Alert
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(chrome.orTypeAlert, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customText,
                    onValueChange = { if (it.length <= 200) customText = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = true,
                    singleLine = false,
                    minLines = 3,
                    placeholder = { Text(chrome.freeText) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val trimmed = customText.trim()
                        if (trimmed.isEmpty()) return@Button
                        viewModel.sendCustomAlert(trimmed)
                        customText = ""
                    },
                    enabled = customText.trim().isNotEmpty() && !uiState.alertSending,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (uiState.alertSending) chrome.sending else chrome.sendTyped)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }

        UserNoticeBanner(notice = uiState.notice, strings = chrome, modifier = Modifier.padding(top = 16.dp))
        Spacer(Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertChannelSelector(
    selectedChannel: `in`.gov.itantra.ui.AlertChannel,
    onChannelSelected: (`in`.gov.itantra.ui.AlertChannel) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Broadcast Channel",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                `in`.gov.itantra.ui.AlertChannel.entries.forEach { channel ->
                    val isSelected = channel == selectedChannel
                    FilterChip(
                        selected = isSelected,
                        onClick = { onChannelSelected(channel) },
                        label = { Text(channel.name) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (selectedChannel) {
                    `in`.gov.itantra.ui.AlertChannel.ALL -> "Alerts will be broadcast over both Wi-Fi and Bluetooth simultaneously for maximum reach."
                    `in`.gov.itantra.ui.AlertChannel.WIFI -> "Alerts will only be broadcast over Wi-Fi (LAN & P2P). Bluetooth is disabled for alerts."
                    `in`.gov.itantra.ui.AlertChannel.BLUETOOTH -> "Alerts will only be broadcast over Bluetooth. Wi-Fi is disabled for alerts."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
