package `in`.gov.itantra.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.chat.QuickChat
import `in`.gov.itantra.ui.components.ProfileAvatar
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.core.transport.ConnectionState
import perfetto.protos.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val chrome = UiStrings.forLanguage(uiState.uiLanguage)
    var showConnectionSheet by remember { mutableStateOf(false) }

    Scaffold(

        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            PttButton(uiState = uiState, chrome = chrome, viewModel = viewModel)
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            if (uiState.pairingConfirmed || uiState.peerProfile != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(12.dp),
                    ) {
                        ProfileAvatar(
                            path = uiState.peerProfile?.photoPath,
                            bytes = uiState.peerProfile?.thumbnailJpeg,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = chrome.connectedTo,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = uiState.talkingToName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Connection Status Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (uiState.connectionState == ConnectionState.DISCONNECTED || uiState.connectionState == ConnectionState.FAILED) {
                            showConnectionSheet = true
                        } else {
                            viewModel.disconnect()
                        }
                    },
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.connectionState == ConnectionState.CONNECTED) 
                        MaterialTheme.colorScheme.secondaryContainer 
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = chrome.statusPrefix,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = uiState.connectionState.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (uiState.connectionState == ConnectionState.CONNECTED) 
                                MaterialTheme.colorScheme.onSecondaryContainer 
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (uiState.connectionState == ConnectionState.DISCONNECTED || uiState.connectionState == ConnectionState.FAILED) {
                        Button(onClick = { showConnectionSheet = true }) {
                            Text(chrome.connect)
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.disconnect() }) {
                            Text(chrome.disconnect)
                        }
                    }
                }
            }

            // Quick Chats. The chip is labelled and sent in the speech language, so
            // the receiver gets text that matches the language on the packet header.
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(QuickChat.entries.toList()) { chat ->
                    SuggestionChip(
                        onClick = { viewModel.sendQuickChat(chat) },
                        label = { Text(chat.phrase(uiState.currentLanguage)) }
                    )
                }
            }

            UserNoticeBanner(
                notice = uiState.notice,
                strings = chrome,
                modifier = Modifier.padding(top = 16.dp),
            )

            if (uiState.inbox.isNotEmpty() || uiState.queuedOutbound.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (uiState.queuedOutbound.isNotEmpty()) {
                        item {
                            Text(
                                text = chrome.outbox(uiState.outboundPending, uiState.outboundFailed),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                            )
                        }
                        items(uiState.queuedOutbound, key = { "out_${it.id}" }) { item ->
                            OutboxItem(item = item, chrome = chrome, viewModel = viewModel)
                        }
                    }

                    if (uiState.inbox.isNotEmpty()) {
                        item {
                            Text(
                                text = chrome.inboxTitle,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp, top = if (uiState.queuedOutbound.isNotEmpty()) 16.dp else 8.dp)
                            )
                        }
                        items(uiState.inbox, key = { "in_${it.id}" }) { item ->
                            InboxItem(item = item, uiState = uiState, chrome = chrome, viewModel = viewModel)
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
            
            // Status Text (Speaking/Listening)
            val status = when {
                uiState.isSpeaking && uiState.recognizedText.isNotEmpty() -> uiState.recognizedText
                uiState.isSpeaking -> chrome.speaking
                uiState.isRequestingFloor -> chrome.waitingChannel
                uiState.channelBusy -> chrome.channelBusy
                uiState.recognizedText.isNotEmpty() -> uiState.recognizedText
                uiState.connectionState == ConnectionState.CONNECTED -> chrome.readyToSpeak
                else -> chrome.notConnectedQueue
            }
            
            Box(
                modifier = Modifier.padding(bottom = 140.dp).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyLarge,
                    color = when {
                        uiState.channelBusy -> MaterialTheme.colorScheme.error
                        uiState.isSpeaking -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }

    if (showConnectionSheet) {
        ModalBottomSheet(onDismissRequest = { showConnectionSheet = false }) {
            ConnectionSettingsSheet(uiState = uiState, chrome = chrome, viewModel = viewModel) {
                showConnectionSheet = false
            }
        }
    }
}

@Composable
fun PttButton(uiState: `in`.gov.itantra.ui.UiState, chrome: UiStrings, viewModel: MainViewModel) {
    val pttHeld = uiState.isSpeaking || uiState.isRequestingFloor
    val isEnabled = uiState.connectionState != ConnectionState.CONNECTED || pttHeld || !uiState.channelBusy
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (uiState.isSpeaking) 1.15f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )
    
    val containerColor = when {
        uiState.isSpeaking -> MaterialTheme.colorScheme.error
        uiState.channelBusy -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    
    val contentColor = when {
        uiState.channelBusy -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(bottom = 16.dp)
            .size(100.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(containerColor.copy(alpha = if (isEnabled) 1f else 0.5f))
            .pointerInput(isEnabled) {
                if (!isEnabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        if (!pttHeld) viewModel.startPtt()
                        try {
                            tryAwaitRelease()
                        } finally {
                            viewModel.stopPtt()
                        }
                    }
                )
            }
    ) {
        Icon(
            imageVector = when {
                uiState.isSpeaking -> Icons.Filled.Stop
                uiState.isRequestingFloor -> Icons.Filled.SettingsInputAntenna
                uiState.channelBusy -> Icons.Filled.MicOff
                else -> Icons.Filled.Mic
            },
            contentDescription = chrome.ptt,
            modifier = Modifier.size(48.dp),
            tint = contentColor
        )
    }
}

@Composable
fun InboxItem(item: `in`.gov.itantra.core.queue.InboxMessage, uiState: `in`.gov.itantra.ui.UiState, chrome: UiStrings, viewModel: MainViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.unread) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val senderInfo = if (item.senderName != null && item.senderName != "Unknown" && item.senderName != "Peer") " (from ${item.senderName})" else ""
                Text(
                    text = chrome.inboxLabel(item.unread, item.language.endonym) + senderInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.unread) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.unread) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = { viewModel.playInbox(item.id) },
                enabled = uiState.playingInboxId == null,
                modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            ) {
                Icon(
                    if (uiState.playingInboxId == item.id) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = chrome.play,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { viewModel.dismissInbox(item.id) }) {
                Icon(Icons.Filled.Clear, contentDescription = chrome.dismiss, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun OutboxItem(item: `in`.gov.itantra.core.queue.OutboundMessage, chrome: UiStrings, viewModel: MainViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.state == `in`.gov.itantra.core.queue.OutboundState.FAILED) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val statusText = if (item.state == `in`.gov.itantra.core.queue.OutboundState.DELIVERED && item.receiverName != null && item.receiverName != "Unknown" && item.receiverName != "Peer") {
                    "${item.state.name} to ${item.receiverName} (${item.language.endonym})"
                } else {
                    "${item.state.name} (${item.language.endonym})"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (item.state == `in`.gov.itantra.core.queue.OutboundState.FAILED) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (item.isAlert) chrome.alertPrefixed(item.text) else item.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.state == `in`.gov.itantra.core.queue.OutboundState.FAILED) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { viewModel.deleteQueuedMessage(item.id) }) {
                Icon(Icons.Filled.Clear, contentDescription = chrome.delete, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ConnectionSettingsSheet(uiState: `in`.gov.itantra.ui.UiState, chrome: UiStrings, viewModel: MainViewModel, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Text(
            text = chrome.connectToChannel,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        // WiFi Direct Options
        Text(chrome.wifiDirect, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(
                onClick = { viewModel.setConnectionMode(ConnectionMode.WIFI_DIRECT_HOST) },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (uiState.connectionMode == ConnectionMode.WIFI_DIRECT_HOST) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
            ) {
                Icon(Icons.Filled.Wifi, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(chrome.hostWifi)
            }
            OutlinedButton(
                onClick = { viewModel.setConnectionMode(ConnectionMode.WIFI_DIRECT_CLIENT) },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (uiState.connectionMode == ConnectionMode.WIFI_DIRECT_CLIENT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
            ) {
                Icon(Icons.Filled.Wifi, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(chrome.joinWifi)
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        // Bluetooth Options
        Text(chrome.bluetooth, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(
                onClick = { viewModel.setConnectionMode(ConnectionMode.BLUETOOTH_HOST) },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (uiState.connectionMode == ConnectionMode.BLUETOOTH_HOST) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
            ) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(chrome.hostBt)
            }
            OutlinedButton(
                onClick = {
                    viewModel.setConnectionMode(ConnectionMode.BLUETOOTH_CLIENT)
                    viewModel.refreshPairedDevices()
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (uiState.connectionMode == ConnectionMode.BLUETOOTH_CLIENT) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                )
            ) {
                Icon(Icons.Filled.Bluetooth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(chrome.joinBt)
            }
        }
        
        if (uiState.connectionMode == ConnectionMode.BLUETOOTH_CLIENT) {
            Spacer(Modifier.height(8.dp))
            Text(chrome.selectPairedDevice, style = MaterialTheme.typography.labelSmall)
            LazyColumn(modifier = Modifier.heightIn(max = 150.dp).fillMaxWidth().padding(top = 8.dp)) {
                items(uiState.pairedDevices, key = { it.address }) { device ->
                    val isSelected = uiState.selectedDeviceAddress == device.address
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).clickable { viewModel.selectDevice(device.address) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = device.name,
                            modifier = Modifier.padding(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                viewModel.connect(uiState.selectedDeviceAddress)
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = uiState.connectionMode != ConnectionMode.BLUETOOTH_CLIENT || uiState.selectedDeviceAddress != null
        ) {
            Text(chrome.connect, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(16.dp))
    }
}
