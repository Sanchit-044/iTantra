package `in`.gov.itantra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SettingsHubScreen(
    onOpenProfile: () -> Unit,
    onOpenLanguages: () -> Unit,
    onBack: () -> Unit,
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val local = uiState.localProfile

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Change your profile or the languages on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
        OutlinedButton(onClick = onOpenLanguages, modifier = Modifier.fillMaxWidth()) {
            Text("Languages")
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back")
        }
    }
}
