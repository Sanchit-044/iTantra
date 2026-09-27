package `in`.gov.itantra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.data.history.HistoryMessage
import `in`.gov.itantra.data.history.MessageDirection
import `in`.gov.itantra.data.history.MessageStatus
import `in`.gov.itantra.ui.components.EmptyState
import `in`.gov.itantra.ui.components.StatusPill
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** History tab content. The tab's top bar (with Clear) lives in [MainContent]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    chrome: UiStrings,
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        FilterBar(
            chrome = chrome,
            direction = uiState.directionFilter,
            status = uiState.statusFilter,
            onDirection = viewModel::setDirectionFilter,
            onStatus = viewModel::setStatusFilter,
        )

        if (uiState.messages.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.History,
                    title = if (uiState.directionFilter != null || uiState.statusFilter != null) {
                        chrome.noFilterMatch
                    } else {
                        chrome.noMessagesTitle
                    },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages, key = { it.id }) { msg ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart || value == SwipeToDismissBoxValue.StartToEnd) {
                                viewModel.deleteMessage(msg.id)
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
                                        contentDescription = "Delete",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        content = {
                            HistoryMessageItem(msg, chrome, onDelete = viewModel::deleteMessage)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterBar(
    chrome: UiStrings,
    direction: MessageDirection?,
    status: MessageStatus?,
    onDirection: (MessageDirection?) -> Unit,
    onStatus: (MessageStatus?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HistoryChip(chrome.inbound, direction == MessageDirection.INBOUND) {
            onDirection(if (direction == MessageDirection.INBOUND) null else MessageDirection.INBOUND)
        }
        HistoryChip(chrome.outbound, direction == MessageDirection.OUTBOUND) {
            onDirection(if (direction == MessageDirection.OUTBOUND) null else MessageDirection.OUTBOUND)
        }
        VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant)
        listOf(
            MessageStatus.DELIVERED to chrome.outDelivered,
            MessageStatus.RECEIVED to chrome.statusReceived,
            MessageStatus.QUEUED to chrome.outQueued,
            MessageStatus.FAILED to chrome.outFailed,
        ).forEach { (value, label) ->
            HistoryChip(label, status == value) {
                onStatus(if (status == value) null else value)
            }
        }
    }
}

@Composable
private fun HistoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null,
        shape = CircleShape,
    )
}

@Composable
fun HistoryMessageItem(
    msg: HistoryMessage,
    chrome: UiStrings,
    onDelete: (String) -> Unit = {},
) {
    val formatter = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }
    val timeStr = formatter.format(Date(msg.timestampMs))
    val outbound = msg.direction == MessageDirection.OUTBOUND
    val ext = ITantraTheme.extended
    val contentColor = MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 13.dp, vertical = 10.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (outbound) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = contentColor.copy(alpha = 0.7f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (!outbound) chrome.receivedFrom(msg.peerName ?: chrome.unknownPeer) else chrome.sentTo(msg.peerName ?: chrome.allPeers),
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val displayText = remember(msg.text, msg.language) {
                AlertTemplate.resolveDisplayText(msg.text, msg.language)
            }
            Text(
                text = displayText,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 14.5.sp,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            if (msg.isAlert) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 2.dp),
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    val locDistLabel = if (outbound) {
                        if (!msg.peerName.isNullOrBlank()) {
                            "Receiver: ${msg.peerName} (~${String.format(Locale.US, "%.1f", msg.distanceMeters ?: 3.5f)}m)"
                        } else {
                            "Target Proximity: ~${String.format(Locale.US, "%.1f", msg.distanceMeters ?: 3.5f)}m"
                        }
                    } else {
                        "${msg.locationLabel ?: "Emergency Beacon"} · ~${String.format(Locale.US, "%.1f", msg.distanceMeters ?: 3.5f)}m away"
                    }
                    Text(
                        text = locDistLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFD32F2F),
                        fontSize = 11.5.sp,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val (statusBg, statusFg) = when (msg.status) {
                        MessageStatus.DELIVERED -> ext.successContainer to ext.onSuccessContainer
                        MessageStatus.FAILED -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                        MessageStatus.QUEUED -> ext.warningContainer to ext.onWarningContainer
                        MessageStatus.RECEIVED -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    StatusPill(
                        text = msg.status.displayLabel(chrome),
                        containerColor = statusBg,
                        contentColor = statusFg,
                    )
                    if (msg.isAlert) {
                        StatusPill(
                            text = chrome.alertTitle,
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            icon = Icons.Filled.Warning,
                        )
                    }
                    StatusPill(
                        text = msg.language.endonym,
                        containerColor = contentColor.copy(alpha = 0.08f),
                        contentColor = contentColor,
                    )
                }

                IconButton(
                    onClick = { onDelete(msg.id) },
                    modifier = Modifier.size(26.dp),
                ) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Delete chat",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

private fun MessageStatus.displayLabel(chrome: UiStrings): String = when (this) {
    MessageStatus.SENT -> chrome.statusSent
    MessageStatus.DELIVERED -> chrome.outDelivered
    MessageStatus.FAILED -> chrome.outFailed
    MessageStatus.RECEIVED -> chrome.statusReceived
    MessageStatus.QUEUED -> chrome.outQueued
}
