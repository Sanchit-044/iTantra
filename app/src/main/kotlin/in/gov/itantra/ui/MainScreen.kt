package `in`.gov.itantra.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.transport.ConnectionState

@Composable
fun MainScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "iTantra Walkie-Talkie",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Language: ${uiState.currentLanguage.endonym} (${uiState.currentLanguage.englishName})",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (uiState.pairingConfirmed || uiState.peerProfile != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        ProfileAvatar(
                            path = uiState.peerProfile?.photoPath,
                            bytes = uiState.peerProfile?.thumbnailJpeg,
                            modifier = Modifier.size(36.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Talking to ${uiState.talkingToName}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            TextButton(onClick = onOpenSettings) {
                Text("Settings")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "Status: ${uiState.connectionState.name}")
                Spacer(modifier = Modifier.height(8.dp))

                if (uiState.connectionState == ConnectionState.DISCONNECTED || uiState.connectionState == ConnectionState.FAILED) {

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = uiState.connectionMode == ConnectionMode.WIFI_DIRECT_HOST, onClick = { viewModel.setConnectionMode(ConnectionMode.WIFI_DIRECT_HOST) })
                                Text("Host Wi-Fi", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = uiState.connectionMode == ConnectionMode.BLUETOOTH_HOST, onClick = { viewModel.setConnectionMode(ConnectionMode.BLUETOOTH_HOST) })
                                Text("Host BT", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = uiState.connectionMode == ConnectionMode.WIFI_DIRECT_CLIENT, onClick = { viewModel.setConnectionMode(ConnectionMode.WIFI_DIRECT_CLIENT) })
                                Text("Join Wi-Fi", style = MaterialTheme.typography.bodySmall)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = uiState.connectionMode == ConnectionMode.BLUETOOTH_CLIENT,
                                    onClick = {
                                        viewModel.setConnectionMode(ConnectionMode.BLUETOOTH_CLIENT)
                                        viewModel.refreshPairedDevices()
                                    },
                                )
                                Text("Join BT", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    if (uiState.connectionMode == ConnectionMode.BLUETOOTH_CLIENT) {
                        Text("Select Paired Device:", style = MaterialTheme.typography.labelMedium)
                        LazyColumn(modifier = Modifier.heightIn(max = 100.dp).fillMaxWidth().padding(8.dp)) {
                            items(uiState.pairedDevices, key = { it.address }) { device ->
                                val isSelected = uiState.selectedDeviceAddress == device.address
                                Text(
                                    text = device.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.selectDevice(device.address) }
                                        .padding(4.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.connect(uiState.selectedDeviceAddress) },
                        enabled = uiState.connectionMode != ConnectionMode.BLUETOOTH_CLIENT || uiState.selectedDeviceAddress != null
                    ) {
                        Text("Connect")
                    }
                } else {
                    Button(onClick = { viewModel.disconnect() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                        Text("Disconnect")
                    }
                }
            }
        }

        if (uiState.error != null) {
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (uiState.outboundPending > 0) {
            Text(
                text = "${uiState.outboundPending} waiting to send" +
                    if (uiState.outboundFailed > 0) " (${uiState.outboundFailed} failed, will retry)" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (uiState.inbox.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Queued inbox — tap Play. Nothing auto-plays.", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 140.dp).fillMaxWidth()) {
                        items(uiState.inbox, key = { it.id }) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = if (item.unread) "New · ${item.language.endonym}" else item.language.endonym,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                    )
                                }
                                TextButton(
                                    onClick = { viewModel.playInbox(item.id) },
                                    enabled = uiState.playingInboxId == null,
                                ) {
                                    Text(if (uiState.playingInboxId == item.id) "Playing" else "Play")
                                }
                                TextButton(onClick = { viewModel.dismissInbox(item.id) }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        val status = when {
            uiState.isSpeaking && uiState.recognizedText.isNotEmpty() -> uiState.recognizedText
            uiState.isSpeaking -> "Speaking…"
            uiState.isRequestingFloor -> "Waiting for channel…"
            uiState.channelBusy -> "Channel busy"
            uiState.recognizedText.isNotEmpty() -> uiState.recognizedText
            uiState.connectionState == ConnectionState.CONNECTED -> "Ready to speak..."
            else -> "Not connected — PTT will save and send later"
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodyLarge,
            color = when {
                uiState.channelBusy -> MaterialTheme.colorScheme.error
                uiState.isSpeaking -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        val pttHeld = uiState.isSpeaking || uiState.isRequestingFloor
        Button(
            onClick = {
                if (pttHeld) viewModel.stopPtt() else viewModel.startPtt()
            },
            modifier = Modifier.size(120.dp),
            enabled = uiState.connectionState != ConnectionState.CONNECTED || pttHeld || !uiState.channelBusy,
            colors = ButtonDefaults.buttonColors(
                containerColor = when {
                    uiState.isSpeaking -> MaterialTheme.colorScheme.error
                    uiState.channelBusy -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                }
            )
        ) {
            Text(
                text = when {
                    uiState.isSpeaking -> "STOP"
                    uiState.isRequestingFloor -> "WAIT"
                    uiState.channelBusy -> "BUSY"
                    else -> "PTT"
                }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
