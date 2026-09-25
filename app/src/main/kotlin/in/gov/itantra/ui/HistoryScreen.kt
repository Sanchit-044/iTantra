package `in`.gov.itantra.ui

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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.messages) { msg ->
                    HistoryMessageItem(msg, chrome)
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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
fun HistoryMessageItem(msg: HistoryMessage, chrome: UiStrings) {
    val formatter = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }
    val timeStr = formatter.format(Date(msg.timestampMs))
    val outbound = msg.direction == MessageDirection.OUTBOUND
    val ext = ITantraTheme.extended

    val bgColor = if (outbound) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (outbound) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (outbound) 32.dp else 0.dp, end = if (outbound) 0.dp else 32.dp),
        shape = if (outbound) {
            RoundedCornerShape(topStart = 8.dp, topEnd = 2.dp, bottomEnd = 8.dp, bottomStart = 8.dp)
        } else {
            RoundedCornerShape(topStart = 2.dp, topEnd = 8.dp, bottomEnd = 8.dp, bottomStart = 8.dp)
        },
        color = bgColor,
        contentColor = contentColor,
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (outbound) Icons.AutoMirrored.Filled.CallMade else Icons.AutoMirrored.Filled.CallReceived,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = contentColor.copy(alpha = 0.7f),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (!outbound) chrome.receivedFrom(msg.peerName ?: chrome.unknownPeer) else chrome.sentTo(msg.peerName ?: chrome.allPeers),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
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
