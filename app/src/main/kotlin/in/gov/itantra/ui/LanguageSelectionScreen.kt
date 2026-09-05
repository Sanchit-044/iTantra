package `in`.gov.itantra.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.Language

@Composable
fun LanguageSelectionScreen(
    isSetup: Boolean,
    onFinished: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: LanguageSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            onFinished()
            viewModel.consumeFinished()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
    ) {
        Text(
            text = if (isSetup) "Choose your languages" else "Languages",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The same language is used for speaking and listening. " +
                "Hindi is selected by default. Only the languages you tick are kept on this phone.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(Language.entries.toList(), key = { it.code }) { language ->
                val selected = language in uiState.selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !uiState.busy) { viewModel.toggle(language) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { viewModel.toggle(language) },
                        enabled = !uiState.busy,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = language.endonym, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = language.englishName + if (language == Language.DEFAULT) "  ·  default" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (selected) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = !uiState.busy) {
                                viewModel.setCurrent(language)
                            },
                        ) {
                            RadioButton(
                                selected = uiState.current == language,
                                onClick = { viewModel.setCurrent(language) },
                                enabled = !uiState.busy,
                            )
                            Text("Active", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        uiState.progress?.let { progress ->
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress.fraction.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(text = progress.message, style = MaterialTheme.typography.bodySmall)
        }

        uiState.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = { viewModel.confirm() },
            enabled = !uiState.busy && uiState.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isSetup) "Continue" else "Save")
        }
        if (onBack != null) {
            TextButton(
                onClick = onBack,
                enabled = !uiState.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back")
            }
        }
    }
}
