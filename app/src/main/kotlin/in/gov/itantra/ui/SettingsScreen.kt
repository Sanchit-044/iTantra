package `in`.gov.itantra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.core.theme.ThemeMode
import kotlin.math.roundToInt

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
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

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

    // Three distinct concerns, each its own scrollable pane -- previously all three
    // (theme + up to 10 language packs + up to 10 display-language options) were
    // stacked in a single LazyColumn, so reaching Save meant scrolling past
    // everything regardless of which section the operator actually came here for.
    val tabs = listOf(chrome.themeTab, chrome.languagesTitle, chrome.displayTab)

    uiState.pendingDelete?.let { language ->
        AlertDialog(
            onDismissRequest = { languageViewModel.cancelDelete() },
            title = { Text(chrome.deleteLanguageTitle) },
            text = { Text(chrome.deleteLanguageBody(language.englishName)) },
            confirmButton = {
                TextButton(onClick = { languageViewModel.confirmDelete() }) {
                    Text(chrome.deleteConfirm, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { languageViewModel.cancelDelete() }) {
                    Text(chrome.pairingCancel)
                }
            },
        )
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label) },
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> ThemeTab(mode = mode, onSetMode = themeViewModel::setMode, chrome = chrome)
                    1 -> LanguagePacksTab(uiState = uiState, chrome = chrome, viewModel = languageViewModel)
                    else -> DisplayLanguageTab(uiState = uiState, chrome = chrome, viewModel = languageViewModel)
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                uiState.progress?.let { progress ->
                    val fraction = progress.fraction.coerceIn(0f, 1f)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = progress.message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${(fraction * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    }
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
}

@Composable
private fun ThemeTab(mode: ThemeMode, onSetMode: (ThemeMode) -> Unit, chrome: UiStrings) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .selectableGroup(),
    ) {
        ThemeMode.entries.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == mode,
                        role = Role.RadioButton,
                        onClick = { onSetMode(option) },
                    )
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = option == mode, onClick = null)
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(option.label(chrome), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = option.help(chrome),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguagePacksTab(
    uiState: LanguageSelectionUiState,
    chrome: UiStrings,
    viewModel: LanguageSelectionViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "packs-help") {
            Spacer(Modifier.height(12.dp))
            Text(chrome.speechHelp, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }
        items(Language.entries.toList(), key = { "pack-${it.code}" }) { language ->
            PackRow(
                language = language,
                selected = language in uiState.selected,
                current = uiState.current == language,
                busy = uiState.busy,
                downloaded = language in uiState.downloaded,
                downloadingNow = uiState.progress?.language == language,
                chrome = chrome,
                onToggle = { viewModel.toggle(language) },
                onCurrent = { viewModel.setCurrent(language) },
                onDelete = { viewModel.requestDelete(language) },
            )
        }
        item(key = "packs-bottom-space") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DisplayLanguageTab(
    uiState: LanguageSelectionUiState,
    chrome: UiStrings,
    viewModel: LanguageSelectionViewModel,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "display-help") {
            Spacer(Modifier.height(12.dp))
            Text(chrome.appLanguageBody, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
        }
        items(uiState.appLanguageOptions, key = { "ui-${it.code}" }) { language ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !uiState.busy) { viewModel.setUiLanguage(language) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = uiState.uiLanguage == language,
                    onClick = { viewModel.setUiLanguage(language) },
                    enabled = !uiState.busy,
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(language.endonym, style = MaterialTheme.typography.titleMedium)
                    Text(language.englishName, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item(key = "display-bottom-space") { Spacer(Modifier.height(16.dp)) }
    }
}

private fun ThemeMode.label(chrome: UiStrings): String = when (this) {
    ThemeMode.SYSTEM -> chrome.themeSystemLabel
    ThemeMode.LIGHT -> chrome.themeLightLabel
    ThemeMode.DARK -> chrome.themeDarkLabel
}

private fun ThemeMode.help(chrome: UiStrings): String = when (this) {
    ThemeMode.SYSTEM -> chrome.themeSystemHelp
    ThemeMode.LIGHT -> chrome.themeLightHelp
    ThemeMode.DARK -> chrome.themeDarkHelp
}
