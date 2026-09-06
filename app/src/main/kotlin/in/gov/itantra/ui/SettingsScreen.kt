package `in`.gov.itantra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.theme.ThemeMode

@Composable
fun SettingsHubScreen(
    onOpenProfile: () -> Unit,
    onOpenLanguages: () -> Unit,
    onBack: () -> Unit,
    mainViewModel: MainViewModel = hiltViewModel(),
    themeViewModel: ThemeSettingsViewModel = hiltViewModel(),
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val local = uiState.localProfile
    val mode by themeViewModel.mode.collectAsState()

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        
        // Profile Section
        ProfileAvatar(
            path = local.photoPath,
            bytes = local.thumbnailJpeg,
            modifier = Modifier.size(72.dp),
        )
        Text(
            text = if (local.name.isBlank()) "No name yet" else local.displayName,
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = onOpenProfile, modifier = Modifier.fillMaxWidth()) {
            Text("Profile")
        }

        // Theme Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .selectableGroup(),
            ) {
                Text("Theme", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ThemeMode.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = option == mode,
                                role = Role.RadioButton,
                                onClick = { themeViewModel.setMode(option) },
                            )
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == mode, onClick = null)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(option.label(), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = option.help(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        OutlinedButton(onClick = onOpenLanguages, modifier = Modifier.fillMaxWidth()) {
            Text("Languages")
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun ThemeMode.help(): String = when (this) {
    ThemeMode.SYSTEM -> "Match the phone light or dark setting"
    ThemeMode.LIGHT -> "Always light, even if the phone is dark"
    ThemeMode.DARK -> "Always dark, even if the phone is light"
}
