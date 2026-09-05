package `in`.gov.itantra.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.transport.ConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedDeviceAddress by remember { mutableStateOf<String?>(null) }

    // Pairing confirmation dialog
    if (uiState.pairingInfo != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPairing() },
            title = { Text("Confirm Pairing") },
            text = { 
                Text("Do you see the same code on the other device?\n\nCode: ${uiState.pairingInfo?.code}")
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmPairing() }) {
                    Text("Yes, Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPairing() }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
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
            }
            TextButton(onClick = onOpenSettings) {
                Text("Settings")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Connection status & controls
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
                    
                    // Connection Mode Selector
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
                                RadioButton(selected = uiState.connectionMode == ConnectionMode.BLUETOOTH_CLIENT, onClick = { viewModel.setConnectionMode(ConnectionMode.BLUETOOTH_CLIENT); viewModel.refreshPairedDevices() })
                                Text("Join BT", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    
                    if (uiState.connectionMode == ConnectionMode.BLUETOOTH_CLIENT) {
                        Text("Select Paired Device:", style = MaterialTheme.typography.labelMedium)
                        LazyColumn(modifier = Modifier.heightIn(max = 100.dp).fillMaxWidth().padding(8.dp)) {
                            items(uiState.pairedDevices) { device ->
                                val isSelected = selectedDeviceAddress == device.address
                                Text(
                                    text = device.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedDeviceAddress = device.address }
                                        .padding(4.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = { viewModel.connect(selectedDeviceAddress) },
                        enabled = uiState.connectionMode != ConnectionMode.BLUETOOTH_CLIENT || selectedDeviceAddress != null
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

        Spacer(modifier = Modifier.weight(1f))

        // Live text display
        Text(
            text = if (uiState.recognizedText.isEmpty()) "Ready to speak..." else uiState.recognizedText,
            style = MaterialTheme.typography.bodyLarge,
            color = if (uiState.isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (uiState.isSpeaking) viewModel.stopPtt() else viewModel.startPtt()
            },
            modifier = Modifier.size(120.dp),
            enabled = uiState.connectionState == ConnectionState.CONNECTED,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (uiState.isSpeaking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        ) {
            Text(text = if (uiState.isSpeaking) "STOP" else "PTT")
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}
