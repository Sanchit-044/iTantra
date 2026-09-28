package `in`.gov.itantra.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.chat.QuickChat
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.core.queue.InboxMessage
import `in`.gov.itantra.core.queue.OutboundMessage
import `in`.gov.itantra.core.queue.OutboundState
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.ui.components.EmptyState
import `in`.gov.itantra.ui.components.ProfileAvatar
import `in`.gov.itantra.ui.components.PulseRing
import `in`.gov.itantra.ui.components.StatusDot
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unified Chat Item Model for chronological Talk stream.
 */
sealed class ChatMessage {
    abstract val id: String
    abstract val timestampMs: Long
    abstract val text: String
    abstract val language: Language

    data class Inbound(
        val inbox: InboxMessage,
    ) : ChatMessage() {
        override val id: String get() = inbox.id
        override val timestampMs: Long get() = inbox.receivedAtMs
        override val text: String get() = inbox.text
        override val language: Language get() = inbox.language
    }

    data class Outbound(
        val outbound: OutboundMessage,
    ) : ChatMessage() {
        override val id: String get() = outbound.id
        override val timestampMs: Long get() = outbound.createdAtMs
        override val text: String get() = outbound.text
        override val language: Language get() = outbound.language
    }
}

/**
 * Modern, structured Talk Screen with a clean Material 3 message feed,
 * streamlined Link Bar, tactical Quick-Chat chips, and high-tactility PTT dock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val chrome = UiStrings.forLanguage(uiState.uiLanguage)
    var showConnectionSheet by remember { mutableStateOf(false) }

    // Merge inbound and outbound messages into chronological chat feed
    val allChatMessages = remember(uiState.inbox, uiState.queuedOutbound) {
        val inList = uiState.inbox.map { ChatMessage.Inbound(it) }
        val outList = uiState.queuedOutbound.map { ChatMessage.Outbound(it) }
        (inList + outList).sortedBy { it.timestampMs }
    }

    // Keep live Talk stream focused on the latest 15 active messages for field responsiveness
    val maxLiveCount = 15
    val hasEarlierMessages = allChatMessages.size > maxLiveCount
    val recentMessages = if (hasEarlierMessages) allChatMessages.takeLast(maxLiveCount) else allChatMessages

    val listState = rememberLazyListState()

    // Auto-scroll to latest message
    LaunchedEffect(recentMessages.size) {
        if (recentMessages.isNotEmpty()) {
            listState.animateScrollToItem(recentMessages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // 1. Sleek Link Status Header
            CompactLinkBar(
                uiState = uiState,
                chrome = chrome,
                onConnect = { showConnectionSheet = true },
                onDisconnect = { viewModel.disconnect() },
            )

            // 2. Low-Profile Quick Chat Chips
            val quickChats = QuickChat.entries.map { it.phrase(uiState.currentLanguage) }
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 540.dp)
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(quickChats) { chat ->
                    ActionChip(
                        text = chat,
                        onClick = { viewModel.sendQuickChat(chat) },
                    )
                }
            }

            // 3. Modern Chronological Chat Feed
            if (recentMessages.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 540.dp)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (hasEarlierMessages) {
                        item(key = "earlier_history_header") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                SuggestionChip(
                                    onClick = onOpenHistory,
                                    label = {
                                        Text(
                                            "View earlier messages in History (${allChatMessages.size - maxLiveCount})",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Medium,
                                        )
                                    },
                                    icon = {
                                        Icon(
                                            Icons.Filled.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                    shape = CircleShape,
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        labelColor = MaterialTheme.colorScheme.primary,
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                    ),
                                )
                            }
                        }
                    }

                    items(recentMessages, key = { it.id }) { msg ->
                        SwipeableChatItem(
                            key = msg.id,
                            onDelete = {
                                when (msg) {
                                    is ChatMessage.Inbound -> viewModel.dismissInbox(msg.inbox.id)
                                    is ChatMessage.Outbound -> viewModel.deleteQueuedMessage(msg.outbound.id)
                                }
                            },
                        ) {
                            when (msg) {
                                is ChatMessage.Inbound -> {
                                    ModernInboundBubble(
                                        item = msg.inbox,
                                        uiState = uiState,
                                        chrome = chrome,
                                        viewModel = viewModel,
                                    )
                                }
                                is ChatMessage.Outbound -> {
                                    ModernOutboundBubble(
                                        item = msg.outbound,
                                        chrome = chrome,
                                        viewModel = viewModel,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 540.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    EmptyState(
                        icon = Icons.Filled.Forum,
                        title = "No recent chats",
                        body = "Press and hold the PTT mic button below to talk, or select a quick phrase above.",
                    )
                }
            }
        }

        // 4. Prominent PTT Floor Dock
        PttDock(uiState = uiState, chrome = chrome, viewModel = viewModel)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (showConnectionSheet) {
        ModalBottomSheet(
            onDismissRequest = { showConnectionSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            ConnectionSettingsSheet(uiState = uiState, chrome = chrome, viewModel = viewModel) {
                showConnectionSheet = false
            }
        }
    }
}

/**
 * Swipe-to-delete wrapper for chat messages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableChatItem(
    key: Any,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                onDelete()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val isDismissing = dismissState.targetValue != SwipeToDismissBoxValue.Settled
            val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                Alignment.CenterStart
            } else {
                Alignment.CenterEnd
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDismissing) Color(0xFFEA4335).copy(alpha = 0.85f) else Color.Transparent)
                    .padding(horizontal = 16.dp),
                contentAlignment = alignment,
            ) {
                if (isDismissing) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete chat",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        content = {
            content()
        },
    )
}

/**
 * Clean Tactical Quick-Chat Action Chip.
 */
@Composable
private fun ActionChip(
    text: String,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
        },
        leadingIcon = {
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            labelColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
    )
}

/**
 * Compact, modern Link Status Bar with peer info and connection toggle.
 */
@Composable
private fun CompactLinkBar(
    uiState: UiState,
    chrome: UiStrings,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val state = uiState.connectionState
    val isConnected = state == ConnectionState.CONNECTED
    val isWifi = uiState.connectionMode == ConnectionMode.WIFI_DIRECT_HOST ||
        uiState.connectionMode == ConnectionMode.WIFI_DIRECT_CLIENT
    val isConnecting = uiState.reconnecting ||
        state == ConnectionState.DISCOVERING || state == ConnectionState.HANDSHAKING

    val statusDotColor = when {
        isConnected -> MaterialTheme.colorScheme.primary
        isConnecting -> MaterialTheme.colorScheme.tertiary
        state == ConnectionState.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    Surface(
        onClick = onConnect,
        modifier = Modifier.fillMaxWidth(),
        color = if (isConnected) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isConnected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Peer avatar / icon
            if (isConnected && uiState.peerProfile != null) {
                ProfileAvatar(
                    path = uiState.peerProfile?.photoPath,
                    bytes = uiState.peerProfile?.thumbnailJpeg,
                    modifier = Modifier.size(38.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (isWifi) Icons.Filled.Wifi else Icons.Filled.Bluetooth,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Peer Name & Status
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isConnected) uiState.talkingToName else chrome.appTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isConnected && uiState.pairingConfirmed) {
                        Spacer(Modifier.width(5.dp))
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = chrome.secureLabel,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color = statusDotColor, pulsing = isConnecting || isConnected, size = 7.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = when {
                            uiState.reconnecting -> chrome.reconnecting(uiState.reconnectAttempt)
                            isConnected -> "Connected (${if (isWifi) "Wi-Fi" else "BT"}) · ${uiState.currentLanguage.endonym}"
                            else -> state.displayLabel(chrome)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Action Button
            if (!isConnected && !isConnecting) {
                FilledTonalButton(
                    onClick = onConnect,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Text(
                        chrome.connect,
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onDisconnect,
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp),
                ) {
                    Text(
                        if (uiState.reconnecting) chrome.pairingCancel else chrome.disconnect,
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
/**
 * Modern Inbound Message Bubble (Left Aligned) matching WhatsApp compact card styling.
 */
@Composable
private fun ModernInboundBubble(
    item: InboxMessage,
    uiState: UiState,
    chrome: UiStrings,
    viewModel: MainViewModel,
) {
    val isDark = MaterialTheme.colorScheme.background.toArgb() < -0x800000
    val isPlaying = uiState.playingInboxId == item.id
    val senderName = item.senderName?.takeIf { it.isNotBlank() && it != "Unknown" && it != "Peer" } ?: uiState.talkingToName
    val timeStr = formatTime(item.receivedAtMs)
    val isAlert = item.isAlert

    val bubbleBg = when {
        isAlert -> if (isDark) Color(0xFF441C1C) else Color(0xFFFFEBEE)
        isDark -> Color(0xFF202C33) // Standard dark chat bubble
        else -> Color(0xFFFFFFFF) // Standard light pure white card
    }
    val contentTextColor = when {
        isAlert -> if (isDark) Color(0xFFFF8A80) else Color(0xFFB71C1C)
        isDark -> Color(0xFFE9EDEF)
        else -> Color(0xFF111B21)
    }
    val metaTextColor = if (isDark) Color(0xFF8696A0) else Color(0xFF667781)
    val audioIconColor = if (isAlert) Color(0xFFD32F2F) else if (isDark) Color(0xFF25D366) else Color(0xFF008069)

    var showSenderName by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 40.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        // Sender Avatar (Tapping shows sender name snackbar & toggles sender tag)
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isAlert -> Color(0xFFD32F2F)
                        isDark -> Color(0xFF1F352E)
                        else -> Color(0xFFE7FCE3)
                    },
                    CircleShape,
                )
                .clickable {
                    showSenderName = !showSenderName
                    viewModel.showSnackbar(if (isAlert) "Emergency Alert from: $senderName" else "Sender: $senderName")
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isAlert) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = "SOS",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Text(
                    text = senderName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFF25D366) else Color(0xFF008069),
                )
            }
        }

        Spacer(Modifier.width(6.dp))

        // Bubble Body (Hugs text comfortably, max 290dp)
        Surface(
            modifier = Modifier.widthIn(min = 60.dp, max = 290.dp),
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomEnd = 14.dp, bottomStart = 3.dp),
            color = bubbleBg,
            border = if (isAlert) androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFFFCDD2))
                     else if (isDark) androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF2A3942))
                     else androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFE2E8F0)),
            shadowElevation = if (isDark) 0.dp else 0.5.dp,
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 11.dp,
                    end = 11.dp,
                    top = if (isAlert) 6.dp else 8.dp,
                    bottom = if (isAlert) 5.dp else 7.dp,
                )
            ) {
                if (isAlert) {
                    // Clean, single-line alert badge + sender & distance
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(bottom = 3.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFD32F2F).copy(alpha = 0.12f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = "ALERT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD32F2F),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                        val senderLabel = buildString {
                            append(senderName)
                            val dist = item.distanceMeters ?: 3.5f
                            append(" · ~${String.format(Locale.US, "%.1f", dist)}m")
                        }
                        Text(
                            text = senderLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD32F2F),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else if (showSenderName) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 3.dp),
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF25D366) else Color(0xFF008069),
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = senderName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color(0xFF25D366) else Color(0xFF008069),
                            fontSize = 11.5.sp,
                        )
                    }
                }

                // Message Text
                val displayText = remember(item.text, item.language) {
                    AlertTemplate.resolveDisplayText(item.text, item.language)
                }
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.5.sp,
                    lineHeight = 20.sp,
                    color = contentTextColor,
                )

                Spacer(Modifier.height(2.dp))

                // Inline Bottom Meta (Time + Replay)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = metaTextColor,
                    )

                    IconButton(
                        onClick = { viewModel.playInbox(item.id) },
                        enabled = uiState.playingInboxId == null || isPlaying,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            if (isPlaying) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = chrome.play,
                            tint = audioIconColor,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Modern Outbound Message Bubble (Right Aligned) matching WhatsApp compact card styling.
 */
@Composable
private fun ModernOutboundBubble(
    item: OutboundMessage,
    chrome: UiStrings,
    viewModel: MainViewModel,
) {
    val isDark = MaterialTheme.colorScheme.background.toArgb() < -0x800000
    val failed = item.state == OutboundState.FAILED
    val delivered = item.state == OutboundState.DELIVERED
    val isQueued = item.state == OutboundState.QUEUED || item.state == OutboundState.SENDING
    val timeStr = formatTime(item.createdAtMs)

    val bubbleBg = when {
        failed -> if (isDark) Color(0xFF441C1C) else Color(0xFFFFEBEE)
        isDark -> Color(0xFF005C4B) // Standard WhatsApp Dark Emerald
        else -> Color(0xFFD9FDD3) // Standard WhatsApp Light Mint Green
    }
    val contentTextColor = when {
        failed -> if (isDark) Color(0xFFFF8A80) else Color(0xFFB71C1C)
        isDark -> Color(0xFFE9EDEF)
        else -> Color(0xFF111B21)
    }
    val metaTextColor = if (isDark) Color(0xFF8696A0) else Color(0xFF667781)
    val checkIconColor = when {
        failed -> Color(0xFFEA4335)
        delivered -> Color(0xFF53BDEB) // Standard WhatsApp Blue Double-Check
        else -> metaTextColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 40.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom,
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 60.dp, max = 290.dp),
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 3.dp),
            color = bubbleBg,
            border = when {
                failed -> androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFFFCDD2))
                isDark -> androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF005C4B))
                else -> androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFC7F3BF))
            },
            shadowElevation = if (isDark) 0.dp else 0.5.dp,
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 11.dp,
                    end = 11.dp,
                    top = if (item.isAlert) 6.dp else 8.dp,
                    bottom = if (item.isAlert) 5.dp else 7.dp,
                )
            ) {
                if (item.isAlert) {
                    // Clean, single-line alert badge + status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(bottom = 3.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFD32F2F).copy(alpha = 0.12f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = chrome.alertTitle,
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(11.dp),
                                )
                                Text(
                                    text = "ALERT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD32F2F),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                        val statusText = when {
                            delivered -> "Delivered"
                            item.state == OutboundState.SENDING -> "Broadcasting..."
                            failed -> "Failed"
                            else -> "Broadcast"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFD32F2F),
                            fontSize = 11.sp,
                        )
                    }
                }

                // Message Text
                val displayText = remember(item.text, item.language) {
                    AlertTemplate.resolveDisplayText(item.text, item.language)
                }
                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.5.sp,
                    lineHeight = 20.sp,
                    color = contentTextColor,
                )

                Spacer(Modifier.height(2.dp))

                // Inline Bottom Meta (Time + Receiver Name + Status Checkmarks / Pending / Failed Actions)
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (!item.receiverName.isNullOrBlank()) {
                        val receiverLabel = if (item.isAlert) {
                            "to ${item.receiverName} (~${String.format(Locale.US, "%.1f", item.distanceMeters ?: 3.5f)}m)"
                        } else {
                            "to ${item.receiverName}"
                        }
                        Text(
                            text = receiverLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (delivered) Color(0xFF53BDEB) else metaTextColor,
                        )
                    }

                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = metaTextColor,
                    )

                    // Delivery Checks & Pending / Failed status
                    when {
                        failed -> {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = "Failed",
                                tint = checkIconColor,
                                modifier = Modifier.size(16.dp),
                            )
                            // Retry Button for failed chats
                            IconButton(
                                onClick = { viewModel.retryQueuedMessage(item.id) },
                                modifier = Modifier.size(22.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Retry send",
                                    tint = Color(0xFFEA4335),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            // Cross Button to cancel/delete failed message
                            IconButton(
                                onClick = { viewModel.deleteQueuedMessage(item.id) },
                                modifier = Modifier.size(22.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Delete failed chat",
                                    tint = Color(0xFFEA4335),
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        delivered -> {
                            Icon(
                                Icons.Filled.DoneAll,
                                contentDescription = "Delivered",
                                tint = Color(0xFF53BDEB), // Blue double checkmarks
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        item.state == OutboundState.SENDING -> {
                            if (item.isAlert) {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = "Broadcasting Alert",
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(15.dp),
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Sending",
                                    tint = checkIconColor,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                        isQueued -> {
                            Icon(
                                Icons.Filled.Schedule,
                                contentDescription = "Queued",
                                tint = checkIconColor,
                                modifier = Modifier.size(14.dp),
                            )
                            // Cross Button for pending / queued chats
                            IconButton(
                                onClick = { viewModel.deleteQueuedMessage(item.id) },
                                modifier = Modifier.size(22.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Cancel pending chat",
                                    tint = metaTextColor,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Clean PTT Dock positioned at the bottom of the Talk Screen.
 */
@Composable
private fun PttDock(uiState: UiState, chrome: UiStrings, viewModel: MainViewModel) {
    val isReceiving = uiState.isPlayingAudio || uiState.playingInboxId != null
    val isConnected = uiState.connectionState == ConnectionState.CONNECTED
    val status = when {
        uiState.isSpeaking && uiState.recognizedText.isNotEmpty() -> uiState.recognizedText
        uiState.isSpeaking -> chrome.speaking
        uiState.isRequestingFloor -> chrome.waitingChannel
        isReceiving && uiState.receivingText.isNotEmpty() -> "Playing: ${uiState.receivingText}"
        isReceiving -> "Playing voice message..."
        uiState.channelBusy -> chrome.channelBusy
        uiState.recognizedText.isNotEmpty() -> uiState.recognizedText
        isConnected -> "Ready to talk"
        else -> "Offline · Hold to record"
    }

    var isMinimized by remember { mutableStateOf(false) }

    // Auto-expand when speaking, requesting floor, or receiving voice message
    LaunchedEffect(uiState.isSpeaking, uiState.isRequestingFloor, isReceiving) {
        if (uiState.isSpeaking || uiState.isRequestingFloor || isReceiving) {
            isMinimized = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = if (isMinimized) 6.dp else 8.dp, start = 14.dp, end = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Header: Status Bar + Audio Output Device Switcher + Minimize / Expand Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (uiState.isSpeaking) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                    } else if (isReceiving) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = Color(0xFF00A884),
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = status,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (uiState.isSpeaking || isReceiving) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = when {
                            isReceiving -> Color(0xFF00A884)
                            uiState.channelBusy -> MaterialTheme.colorScheme.error
                            uiState.isSpeaking -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Audio Output Device Quick Selector (Speaker, Earpiece, Bluetooth)
                    IconButton(
                        onClick = { viewModel.toggleAudioOutputDevice() },
                        modifier = Modifier.size(28.dp),
                    ) {
                        val (icon, desc) = when (uiState.audioOutputDevice) {
                            AudioOutputDevice.SPEAKER -> Icons.Filled.VolumeUp to "Loudspeaker"
                            AudioOutputDevice.EARPIECE -> Icons.Filled.PhoneInTalk to "Phone Earpiece"
                            AudioOutputDevice.BLUETOOTH -> Icons.Filled.Headset to "Bluetooth Headset"
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = "Output: $desc (tap to switch)",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp),
                        )
                    }

                    // Minimize / Expand Toggle
                    IconButton(
                        onClick = { isMinimized = !isMinimized },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = if (isMinimized) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isMinimized) "Expand PTT" else "Minimize PTT",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
            }

            // Expanded Full PTT View
            AnimatedVisibility(
                visible = !isMinimized,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(2.dp))
                    PttButton(uiState = uiState, chrome = chrome, viewModel = viewModel)
                }
            }

            // Minimized Compact Bar View
            AnimatedVisibility(
                visible = isMinimized,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isReceiving) "Playing incoming voice..." else "Mic minimized · Tap arrow or hold mic",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isReceiving) Color(0xFF00A884) else MaterialTheme.colorScheme.outline,
                    )
                    MiniPttButton(uiState = uiState, chrome = chrome, viewModel = viewModel)
                }
            }
        }
    }
}

/**
 * Compact Push-To-Talk Button for minimized dock mode.
 */
@Composable
fun MiniPttButton(uiState: UiState, chrome: UiStrings, viewModel: MainViewModel) {
    val isReceiving = uiState.isPlayingAudio || uiState.playingInboxId != null
    val pttHeld = uiState.isSpeaking || uiState.isRequestingFloor
    val isEnabled = uiState.connectionState != ConnectionState.CONNECTED || pttHeld || !uiState.channelBusy

    val containerColor by animateColorAsState(
        when {
            uiState.isSpeaking -> MaterialTheme.colorScheme.error
            uiState.isRequestingFloor -> MaterialTheme.colorScheme.tertiary
            isReceiving -> Color(0xFF00A884)
            uiState.channelBusy -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.primary
        },
        label = "miniPttColor",
    )

    val contentColor = when {
        uiState.isSpeaking -> MaterialTheme.colorScheme.onError
        uiState.isRequestingFloor -> MaterialTheme.colorScheme.onTertiary
        isReceiving -> Color.White
        uiState.channelBusy -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .shadow(elevation = if (isEnabled) 2.dp else 0.dp, shape = CircleShape)
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
                isReceiving -> Icons.AutoMirrored.Filled.VolumeUp
                uiState.channelBusy -> Icons.Filled.MicOff
                else -> Icons.Filled.Mic
            },
            contentDescription = chrome.ptt,
            modifier = Modifier.size(18.dp),
            tint = contentColor,
        )
    }
}

/**
 * Responsive Push-To-Talk Button with active transmission & playback animations.
 */
@Composable
fun PttButton(uiState: UiState, chrome: UiStrings, viewModel: MainViewModel) {
    val isReceiving = uiState.isPlayingAudio || uiState.playingInboxId != null
    val pttHeld = uiState.isSpeaking || uiState.isRequestingFloor
    val isEnabled = uiState.connectionState != ConnectionState.CONNECTED || pttHeld || !uiState.channelBusy

    val pressScale by animateFloatAsState(if (pttHeld) 1.08f else 1f, label = "pttScale")

    val containerColor by animateColorAsState(
        when {
            uiState.isSpeaking -> MaterialTheme.colorScheme.error
            uiState.isRequestingFloor -> MaterialTheme.colorScheme.tertiary
            isReceiving -> Color(0xFF00A884)
            uiState.channelBusy -> MaterialTheme.colorScheme.surfaceContainerHighest
            else -> MaterialTheme.colorScheme.primary
        },
        label = "pttColor",
    )

    val contentColor = when {
        uiState.isSpeaking -> MaterialTheme.colorScheme.onError
        uiState.isRequestingFloor -> MaterialTheme.colorScheme.onTertiary
        isReceiving -> Color.White
        uiState.channelBusy -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(92.dp)) {
        if (uiState.isSpeaking) {
            PulseRing(color = containerColor, size = 70.dp)
            PulseRing(color = containerColor, size = 70.dp, delayMillis = 800)
        } else if (isReceiving) {
            PulseRing(color = Color(0xFF00A884), size = 70.dp)
            PulseRing(color = Color(0xFF00A884), size = 70.dp, delayMillis = 700)
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(70.dp)
                .scale(pressScale)
                .shadow(elevation = if (isEnabled) 4.dp else 0.dp, shape = CircleShape)
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
                    isReceiving -> Icons.AutoMirrored.Filled.VolumeUp
                    uiState.channelBusy -> Icons.Filled.MicOff
                    else -> Icons.Filled.Mic
                },
                contentDescription = chrome.ptt,
                modifier = Modifier.size(32.dp),
                tint = contentColor
            )
        }
    }
}

/**
 * Connection settings sheet bottom modal.
 */
@Composable
private fun ConnectionSettingsSheet(
    uiState: UiState,
    chrome: UiStrings,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Connection Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Choose transport channel for direct offline link",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        Spacer(Modifier.height(14.dp))

        // Transport Mode Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val isWifi = uiState.connectionMode == ConnectionMode.WIFI_DIRECT_HOST ||
                uiState.connectionMode == ConnectionMode.WIFI_DIRECT_CLIENT
            ModeOption(
                selected = isWifi,
                icon = Icons.Filled.Wifi,
                label = chrome.channelWifiLabel,
                onClick = { viewModel.setConnectionMode(ConnectionMode.WIFI_DIRECT_HOST) },
                modifier = Modifier.weight(1f),
            )
            ModeOption(
                selected = !isWifi,
                icon = Icons.Filled.Bluetooth,
                label = chrome.channelBluetoothLabel,
                onClick = { viewModel.setConnectionMode(ConnectionMode.BLUETOOTH_HOST) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(16.dp))

        // Action Connect / Reconnect Button
        Button(
            onClick = {
                viewModel.connect()
                onDismiss()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(chrome.connect)
        }

        Spacer(Modifier.height(12.dp))
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
        modifier = modifier.height(76.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                 else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun formatTime(ms: Long): String {
    val df = SimpleDateFormat("HH:mm", Locale.getDefault())
    return df.format(Date(ms))
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
