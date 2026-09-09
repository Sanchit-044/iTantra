package `in`.gov.itantra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.core.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    themeViewModel: ThemeSettingsViewModel = hiltViewModel(),
    languageViewModel: LanguageSelectionViewModel = hiltViewModel(),
) {
    val mode by themeViewModel.mode.collectAsState()
    val uiState by languageViewModel.uiState.collectAsState()
    val chrome = UiStrings.forLanguage(uiState.uiLanguage)

    BackHandler(onBack = onBack)

    LaunchedEffect(Unit) {
        languageViewModel.reloadFromStore()
    }

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            onBack()
            languageViewModel.consumeFinished()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chrome.settings) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = chrome.back)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Theme Section
                item(key = "theme-section") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .selectableGroup(),
                        ) {
                            Text(chrome.theme, style = MaterialTheme.typography.titleMedium)
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
                                        Text(chrome.themeLabel(option), style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            text = chrome.themeHelp(option),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Speech Languages Section
                item(key = "packs-title") {
                    Text(
                        text = chrome.languagesTitle,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = chrome.speechHelp,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                items(Language.entries.toList(), key = { "pack-${it.code}" }) { language ->
                    PackRow(
                        language = language,
                        selected = language in uiState.selected,
                        current = uiState.current == language,
                        busy = uiState.busy,
                        chrome = chrome,
                        onToggle = { languageViewModel.toggle(language) },
                        onCurrent = { languageViewModel.setCurrent(language) },
                    )
                }

                // App UI Language Section
                item(key = "ui-title") {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = chrome.appLanguageTitle, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = chrome.appLanguageBody, style = MaterialTheme.typography.bodyMedium)
                }

                items(uiState.appLanguageOptions, key = { "ui-${it.code}" }) { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !uiState.busy) { languageViewModel.setUiLanguage(language) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = uiState.uiLanguage == language,
                            onClick = { languageViewModel.setUiLanguage(language) },
                            enabled = !uiState.busy,
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(language.endonym, style = MaterialTheme.typography.titleMedium)
                            Text(language.englishName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            uiState.progress?.let { progress ->
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.fraction.coerceIn(0f, 1f) },
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
                onClick = { languageViewModel.confirm() },
                enabled = !uiState.busy && uiState.selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(chrome.save)
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

