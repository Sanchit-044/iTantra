package `in`.gov.itantra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.transport.ConnectionState

@Composable
fun AlertScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    var customText by rememberSaveable { mutableStateOf("") }
    val ready = uiState.connectionState == ConnectionState.CONNECTED && uiState.pairingConfirmed
    val language = uiState.speakLanguage

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Alert", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = if (ready) {
                "Sends at maximum volume on the other phone. Not a live conversation."
            } else {
                "Connect and confirm pairing on Talk first. Alerts are not queued."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        AlertTemplate.entries.forEach { template ->
            Button(
                onClick = { viewModel.sendAlertTemplate(template) },
                enabled = ready && !uiState.alertSending,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                Text(template.phrase(language))
            }
        }

        Spacer(Modifier.height(4.dp))
        Text("Or type a short alert", style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = customText,
            onValueChange = { if (it.length <= 200) customText = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = ready,
            singleLine = false,
            minLines = 2,
            placeholder = { Text("Free text") },
        )
        OutlinedButton(
            onClick = {
                viewModel.sendCustomAlert(customText)
                customText = ""
            },
            enabled = ready && customText.trim().isNotEmpty() && !uiState.alertSending,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (uiState.alertSending) "Sending…" else "Send typed alert")
        }

        if (uiState.alertStatus != null) {
            Text(uiState.alertStatus!!, style = MaterialTheme.typography.bodySmall)
        }
        if (uiState.error != null) {
            Text(uiState.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
