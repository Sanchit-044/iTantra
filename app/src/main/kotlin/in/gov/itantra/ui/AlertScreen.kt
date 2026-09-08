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
        // Prominent Connection Mode & Status Card
        AlertStatusHeader(
            isWifiConnected = uiState.isWifiConnected,
            isP2pConnected = isP2pConnected,
            talkingToName = uiState.talkingToName,
            chrome = chrome
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

@Composable
private fun AlertStatusHeader(
    isWifiConnected: Boolean,
    isP2pConnected: Boolean,
    talkingToName: String,
    chrome: UiStrings,
) {
    val title: String
    val subtitle: String
    val badgeColor: Color
    val containerColor: Color

    if (isWifiConnected) {
        title = "Wi-Fi LAN Broadcast Active"
        subtitle = "Connected to local Wi-Fi / Hotspot. Emergency alerts will reach ALL phones on this network with zero pairing."
        badgeColor = Color(0xFF388E3C) // Muted Forest Green
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    } else if (isP2pConnected) {
        title = "P2P Direct Link Active"
        subtitle = "Direct encrypted channel active with $talkingToName."
        badgeColor = Color(0xFF1976D2) // Muted Blue
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    } else {
        title = "Offline / Unpaired"
        subtitle = "Connect devices to the same Wi-Fi router/hotspot OR pair a peer to send instant emergency alerts."
        badgeColor = Color(0xFFD84315) // Muted Amber
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(badgeColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isWifiConnected || isP2pConnected) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
