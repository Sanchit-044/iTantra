package `in`.gov.itantra.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.chat.QuickChat
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.core.queue.InboxMessage
import `in`.gov.itantra.core.queue.OutboundMessage
import `in`.gov.itantra.core.queue.OutboundState
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.ui.components.EmptyState
import `in`.gov.itantra.ui.components.IconBadge
import `in`.gov.itantra.ui.components.ProfileAvatar
import `in`.gov.itantra.ui.components.PulseRing
import `in`.gov.itantra.ui.components.SectionHeader
import `in`.gov.itantra.ui.components.StatusDot
import `in`.gov.itantra.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val chrome = UiStrings.forLanguage(uiState.uiLanguage)
    var showConnectionSheet by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            ConnectionCard(
                uiState = uiState,
                chrome = chrome,
                onConnect = { showConnectionSheet = true },
                onDisconnect = { viewModel.disconnect() },
            )

            // Quick Chats -- phrased in the active *speaking* language (not the menu
            // language `chrome` uses), since sendQuickChat() tags the message with
            // currentLanguage: the visible chip text must match what's actually sent
            // and what the receiver's TTS will be asked to speak. Uses QuickChat
            // (all 10 languages) rather than UiStrings (only 4 languages, English
            // fallback for the rest) -- unlike menu chrome, a fallback here would
            // send the literal English word tagged as the wrong language.
            val quickChats = QuickChat.entries.map { it.phrase(uiState.currentLanguage) }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(quickChats) { chat ->
                    AssistChip(
                        onClick = { viewModel.sendQuickChat(chat) },
                        label = { Text(chat) },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                        shape = CircleShape,
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            leadingIconContentColor = MaterialTheme.colorScheme.primary,
                        ),
                        border = AssistChipDefaults.assistChipBorder(
                            enabled = true,
                            borderColor = MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
            }

            UserNoticeBanner(
                notice = uiState.notice,
                strings = chrome,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (uiState.inbox.isNotEmpty() || uiState.queuedOutbound.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (uiState.inbox.isNotEmpty()) {
                        items(uiState.inbox, key = { "in_${it.id}" }) { item ->
                            InboxItem(item = item, uiState = uiState, chrome = chrome, viewModel = viewModel)
                        }
                    }

                    if (uiState.queuedOutbound.isNotEmpty()) {
                        item(key = "outbox-header") {
                            SectionHeader(
                                title = chrome.outboxTitle,
                                modifier = Modifier.padding(top = if (uiState.inbox.isNotEmpty()) 12.dp else 4.dp, bottom = 4.dp),
                            ) {
                                if (uiState.outboundPending > 0) {
                                    StatusPill(
                                        text = chrome.pendingCount(uiState.outboundPending),
                                        containerColor = ITantraTheme.extended.warningContainer,
                                        contentColor = ITantraTheme.extended.onWarningContainer,
                                    )
                                }
                                if (uiState.outboundFailed > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    StatusPill(
                                        text = chrome.failedCount(uiState.outboundFailed),
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                        items(uiState.queuedOutbound, key = { "out_${it.id}" }) { item ->
                            OutboxItem(item = item, chrome = chrome, viewModel = viewModel)
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Filled.Forum,
                        title = chrome.noMessagesTitle,
                    )
                }
            }
        }

        PttDock(uiState = uiState, chrome = chrome, viewModel = viewModel)
    }

    if (showConnectionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConnectionSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ConnectionSettingsSheet(uiState = uiState, chrome = chrome, viewModel = viewModel) {
                showConnectionSheet = false
            }
        }
    }
}

private fun ConnectionState.displayLabel(chrome: UiStrings): String = when (this) {
    ConnectionState.CONNECTED -> chrome.stateConnected
    ConnectionState.DISCOVERING -> chrome.stateSearching
    ConnectionState.HANDSHAKING -> chrome.stateHandshaking
    ConnectionState.DISCONNECTED -> chrome.stateOffline
    ConnectionState.FAILED -> chrome.stateFailed
}

private fun OutboundState.displayLabel(chrome: UiStrings): String = when (this) {
    OutboundState.QUEUED -> chrome.outQueued
    OutboundState.SENDING -> chrome.outSending
    OutboundState.FAILED -> chrome.outFailed
    OutboundState.DELIVERED -> chrome.outDelivered
}

@Composable
private fun ConnectionCard(
    uiState: UiState,
    chrome: UiStrings,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val state = uiState.connectionState
    val ext = ITantraTheme.extended
    val canReconnectManually = !uiState.reconnecting &&
        (state == ConnectionState.DISCONNECTED || state == ConnectionState.FAILED)
    val isWifiChannel = uiState.connectionMode == ConnectionMode.WIFI_DIRECT_HOST ||
        uiState.connectionMode == ConnectionMode.WIFI_DIRECT_CLIENT
    val connecting = uiState.reconnecting ||
        state == ConnectionState.DISCOVERING || state == ConnectionState.HANDSHAKING

    val container by animateColorAsState(
        when {
            state == ConnectionState.CONNECTED -> ext.successContainer
            connecting -> MaterialTheme.colorScheme.primaryContainer
            state == ConnectionState.FAILED -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        label = "connContainer",
    )
    val content = when {
        state == ConnectionState.CONNECTED -> ext.onSuccessContainer
        connecting -> MaterialTheme.colorScheme.onPrimaryContainer
        state == ConnectionState.FAILED -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val dotColor = when {
        state == ConnectionState.CONNECTED -> ext.success
        connecting -> MaterialTheme.colorScheme.primary
        state == ConnectionState.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(
                    icon = if (isWifiChannel) Icons.Filled.Wifi else Icons.Filled.Bluetooth,
                    containerColor = content.copy(alpha = 0.10f),
                    contentColor = content,
                    size = 44.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(color = dotColor, pulsing = connecting || state == ConnectionState.CONNECTED, size = 8.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (uiState.reconnecting) chrome.reconnecting(uiState.reconnectAttempt) else state.displayLabel(chrome),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (canReconnectManually) {
                    Button(onClick = onConnect) { Text(chrome.connect) }
                } else {
                    OutlinedButton(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = content),
                        border = androidx.compose.foundation.BorderStroke(1.dp, content.copy(alpha = 0.4f)),
                    ) {
                        Text(if (uiState.reconnecting) chrome.pairingCancel else chrome.disconnect)
                    }
                }
            }

            AnimatedVisibility(visible = uiState.pairingConfirmed || uiState.peerProfile != null) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = content.copy(alpha = 0.12f))
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(
                            path = uiState.peerProfile?.photoPath,
                            bytes = uiState.peerProfile?.thumbnailJpeg,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = uiState.talkingToName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (uiState.pairingConfirmed) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = chrome.secureLabel,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PttDock(uiState: UiState, chrome: UiStrings, viewModel: MainViewModel) {
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 16.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = status,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "pttStatus",
            ) { text ->
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = when {
                        uiState.channelBusy -> MaterialTheme.colorScheme.error
                        uiState.isSpeaking -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            PttButton(uiState = uiState, chrome = chrome, viewModel = viewModel)
        }
    }
}

@Composable
fun PttButton(uiState: `in`.gov.itantra.ui.UiState, chrome: UiStrings, viewModel: MainViewModel) {
    val pttHeld = uiState.isSpeaking || uiState.isRequestingFloor
    val isEnabled = uiState.connectionState != ConnectionState.CONNECTED || pttHeld || !uiState.channelBusy

    val pressScale by animateFloatAsState(if (pttHeld) 1.08f else 1f, label = "pttScale")

    val containerColor by animateColorAsState(
        when {
            uiState.isSpeaking -> MaterialTheme.colorScheme.error
            uiState.isRequestingFloor -> MaterialTheme.colorScheme.tertiary
            uiState.channelBusy -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.primary
        },
        label = "pttColor",
    )

    val contentColor = when {
        uiState.isSpeaking -> MaterialTheme.colorScheme.onError
        uiState.isRequestingFloor -> MaterialTheme.colorScheme.onTertiary
        uiState.channelBusy -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(132.dp)) {
        if (uiState.isSpeaking) {
            PulseRing(color = containerColor, size = 96.dp)
            PulseRing(color = containerColor, size = 96.dp, delayMillis = 800)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(96.dp)
                .scale(pressScale)
                .shadow(elevation = if (isEnabled) 8.dp else 0.dp, shape = CircleShape)
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
                modifier = Modifier.size(44.dp),
                tint = contentColor
            )
        }
    }
}

@Composable
fun InboxItem(item: InboxMessage, uiState: `in`.gov.itantra.ui.UiState, chrome: UiStrings, viewModel: MainViewModel) {
    val isPlaying = uiState.playingInboxId == item.id
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
        color = if (item.unread) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (item.unread) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val sender = item.senderName?.takeIf { it != "Unknown" && it != "Peer" }
                    if (sender != null) {
                        Text(
                            text = sender,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = chrome.inboxLabel(item.unread, item.language.endonym),
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = { viewModel.playInbox(item.id) },
                enabled = uiState.playingInboxId == null,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = chrome.play,
                )
            }
            IconButton(onClick = { viewModel.dismissInbox(item.id) }) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = chrome.dismiss,
                    tint = LocalContentColor.current.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
fun OutboxItem(item: OutboundMessage, chrome: UiStrings, viewModel: MainViewModel) {
    val failed = item.state == OutboundState.FAILED
    val delivered = item.state == OutboundState.DELIVERED
    val ext = ITantraTheme.extended
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 32.dp),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 6.dp, bottomEnd = 20.dp, bottomStart = 20.dp),
        color = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isAlert) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = chrome.alertTitle,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            failed -> Icons.Filled.ErrorOutline
                            delivered -> Icons.Filled.CheckCircle
                            else -> Icons.Filled.Schedule
                        },
                        contentDescription = null,
                        tint = when {
                            failed -> MaterialTheme.colorScheme.error
                            delivered -> ext.success
                            else -> LocalContentColor.current.copy(alpha = 0.7f)
                        },
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    val receiver = item.receiverName?.takeIf { delivered && it != "Unknown" && it != "Peer" }
                    Text(
                        text = buildString {
                            append(if (receiver != null) chrome.deliveredTo(receiver) else item.state.displayLabel(chrome))
                            append(" · ${item.language.endonym}")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = { viewModel.deleteQueuedMessage(item.id) }) {
                Icon(
                    Icons.Filled.Clear,
                    contentDescription = chrome.delete,
                    tint = LocalContentColor.current.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun ModeOption(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        selected = selected,
        modifier = modifier.height(88.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
fun ConnectionSettingsSheet(uiState: `in`.gov.itantra.ui.UiState, chrome: UiStrings, viewModel: MainViewModel, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = chrome.connectSheetTitle,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 20.dp),
        )

        // WiFi Direct Options
        SectionHeader(chrome.wifiDirect)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeOption(
                selected = uiState.connectionMode == ConnectionMode.WIFI_DIRECT_HOST,
                icon = Icons.Filled.Wifi,
                label = chrome.hostWifi,
                onClick = { viewModel.setConnectionMode(ConnectionMode.WIFI_DIRECT_HOST) },
                modifier = Modifier.weight(1f),
            )
            ModeOption(
                selected = uiState.connectionMode == ConnectionMode.WIFI_DIRECT_CLIENT,
                icon = Icons.Filled.Wifi,
                label = chrome.joinWifi,
                onClick = { viewModel.setConnectionMode(ConnectionMode.WIFI_DIRECT_CLIENT) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(20.dp))

        // Bluetooth Options
        SectionHeader(chrome.channelBluetoothLabel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeOption(
                selected = uiState.connectionMode == ConnectionMode.BLUETOOTH_HOST,
                icon = Icons.Filled.Bluetooth,
                label = chrome.hostBt,
                onClick = { viewModel.setConnectionMode(ConnectionMode.BLUETOOTH_HOST) },
                modifier = Modifier.weight(1f),
            )
            ModeOption(
                selected = uiState.connectionMode == ConnectionMode.BLUETOOTH_CLIENT,
                icon = Icons.Filled.Bluetooth,
                label = chrome.joinBt,
                onClick = {
                    viewModel.setConnectionMode(ConnectionMode.BLUETOOTH_CLIENT)
                    viewModel.refreshPairedDevices()
                },
                modifier = Modifier.weight(1f),
            )
        }

        AnimatedVisibility(visible = uiState.connectionMode == ConnectionMode.BLUETOOTH_CLIENT) {
            Column {
                Spacer(Modifier.height(16.dp))
                Text(
                    chrome.selectPairedDevice,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(
                    modifier = Modifier
                        .heightIn(max = 180.dp)
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(uiState.pairedDevices, key = { it.address }) { device ->
                        val isSelected = uiState.selectedDeviceAddress == device.address
                        Surface(
                            onClick = { viewModel.selectDevice(device.address) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = device.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
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
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = uiState.connectionMode != ConnectionMode.BLUETOOTH_CLIENT || uiState.selectedDeviceAddress != null
        ) {
            Text(chrome.connect, style = MaterialTheme.typography.titleMedium)
        }
    }
}
