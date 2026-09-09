package `in`.gov.itantra.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.data.history.HistoryMessage
import `in`.gov.itantra.data.history.MessageDirection
import `in`.gov.itantra.data.history.MessageStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    chrome: UiStrings,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chrome.historyTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = chrome.back)
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterDialog = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = chrome.filter)
                    }
                    IconButton(onClick = { viewModel.clearHistory() }) {
                        Icon(Icons.Default.Delete, contentDescription = chrome.clearHistory)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Active Filters Banner
            if (uiState.directionFilter != null || uiState.statusFilter != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Filtered by: ${
                                listOfNotNull(
                                    uiState.directionFilter?.name,
                                    uiState.statusFilter?.name
                                ).joinToString(", ")
                            }",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        TextButton(onClick = {
                            viewModel.setDirectionFilter(null)
                            viewModel.setStatusFilter(null)
                        }) {
                            Text(chrome.clearFilters)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.messages) { msg ->
                    HistoryMessageItem(msg, chrome)
                }
            }
        }
    }

    if (showFilterDialog) {
        FilterDialog(
            currentDirection = uiState.directionFilter,
            currentStatus = uiState.statusFilter,
            chrome = chrome,
            onApply = { dir, stat ->
                viewModel.setDirectionFilter(dir)
                viewModel.setStatusFilter(stat)
                showFilterDialog = false
            },
            onDismiss = { showFilterDialog = false }
        )
    }
}

@Composable
fun HistoryMessageItem(msg: HistoryMessage, chrome: UiStrings) {
    val formatter = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()) }
    val timeStr = formatter.format(Date(msg.timestampMs))

    val bgColor = if (msg.direction == MessageDirection.OUTBOUND) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }

    val contentColor = if (msg.direction == MessageDirection.OUTBOUND) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor, contentColor = contentColor)
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (msg.direction == MessageDirection.INBOUND) {
                        chrome.receivedFrom(msg.peerName ?: chrome.radiosUnknown)
                    } else {
                        chrome.sentTo(msg.peerName ?: chrome.allUnknown)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = timeStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = msg.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Badge(
                    containerColor = when (msg.status) {
                        MessageStatus.DELIVERED -> Color(0xFF4CAF50)
                        MessageStatus.FAILED -> MaterialTheme.colorScheme.error
                        MessageStatus.QUEUED -> Color(0xFFFF9800)
                        MessageStatus.RECEIVED -> Color(0xFF2196F3)
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    contentColor = Color.White
                ) {
                    Text(
                        msg.status.name,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (msg.isAlert) {
                        Badge(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
                            Text(chrome.alertBadge, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                    }
                    Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                        Text(msg.language.name, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FilterDialog(
    currentDirection: MessageDirection?,
    currentStatus: MessageStatus?,
    chrome: UiStrings,
    onApply: (MessageDirection?, MessageStatus?) -> Unit,
    onDismiss: () -> Unit
) {
    var direction by remember { mutableStateOf(currentDirection) }
    var status by remember { mutableStateOf(currentStatus) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(chrome.filterHistoryTitle) },
        text = {
            Column {
                Text(chrome.direction, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    FilterChip(
                        selected = direction == MessageDirection.INBOUND,
                        onClick = { direction = if (direction == MessageDirection.INBOUND) null else MessageDirection.INBOUND },
                        label = { Text(chrome.inbound) }
                    )
                    FilterChip(
                        selected = direction == MessageDirection.OUTBOUND,
                        onClick = { direction = if (direction == MessageDirection.OUTBOUND) null else MessageDirection.OUTBOUND },
                        label = { Text(chrome.outbound) }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(chrome.statusLabel, fontWeight = FontWeight.Bold)
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        FilterChip(
                            selected = status == MessageStatus.DELIVERED,
                            onClick = { status = if (status == MessageStatus.DELIVERED) null else MessageStatus.DELIVERED },
                            label = { Text(chrome.delivered) }
                        )
                        FilterChip(
                            selected = status == MessageStatus.RECEIVED,
                            onClick = { status = if (status == MessageStatus.RECEIVED) null else MessageStatus.RECEIVED },
                            label = { Text(chrome.received) }
                        )
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        FilterChip(
                            selected = status == MessageStatus.QUEUED,
                            onClick = { status = if (status == MessageStatus.QUEUED) null else MessageStatus.QUEUED },
                            label = { Text(chrome.queuedLabel) }
                        )
                        FilterChip(
                            selected = status == MessageStatus.FAILED,
                            onClick = { status = if (status == MessageStatus.FAILED) null else MessageStatus.FAILED },
                            label = { Text(chrome.failedLabel) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(direction, status) }) {
                Text(chrome.apply)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(chrome.cancel)
            }
        }
    )
}
