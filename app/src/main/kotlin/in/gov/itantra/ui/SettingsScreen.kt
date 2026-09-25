package `in`.gov.itantra.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.core.theme.ThemeMode
import `in`.gov.itantra.ui.components.BottomActionBar
import `in`.gov.itantra.ui.components.DownloadProgressCard
import `in`.gov.itantra.ui.components.InlineMessage
import `in`.gov.itantra.ui.components.ProfileAvatar
import `in`.gov.itantra.ui.components.SettingsGroup
import `in`.gov.itantra.ui.components.SettingsRow
import `in`.gov.itantra.ui.components.StatusPill
import `in`.gov.itantra.ui.components.SubScreenScaffold

/** Settings tab: profile, languages, theme, diagnostics and about, as grouped rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    mainState: UiState,
    onOpenProfile: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenAnalysis: () -> Unit,
    themeViewModel: ThemeSettingsViewModel = hiltViewModel(),
) {
    val mode by themeViewModel.mode.collectAsState()
    val chrome = UiStrings.forLanguage(mainState.uiLanguage)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        ProfileCard(state = mainState, chrome = chrome, onClick = onOpenProfile)

        SettingsGroup(chrome.sectionSpeech) {
            SettingsRow(
                icon = Icons.Outlined.RecordVoiceOver,
                title = chrome.languagesTitle,
                subtitle = "${mainState.currentLanguage.endonym} · ${chrome.installedCount(mainState.installedLanguages.size)}",
                onClick = onOpenLanguages,
            )
            SettingsRow(
                icon = Icons.Outlined.Translate,
                title = chrome.appLanguageTitle,
                subtitle = mainState.uiLanguage.endonym,
                onClick = onOpenDisplay,
                showDivider = true,
            )
        }

        SettingsGroup(chrome.sectionAppearance) {
            Column(modifier = Modifier.padding(16.dp)) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == mode,
                            onClick = { themeViewModel.setMode(option) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = option == mode) {
                                    Icon(
                                        option.icon(),
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize),
                                    )
                                }
                            },
                        ) {
                            Text(option.label(chrome), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        SettingsGroup(chrome.sectionDiagnostics) {
            SettingsRow(
                icon = Icons.Outlined.Insights,
                title = chrome.analysis,
                onClick = onOpenAnalysis,
            )
        }

        SettingsGroup(chrome.sectionAbout) {
            AboutContent(chrome)
        }
    }
}

@Composable
private fun ProfileCard(state: UiState, chrome: UiStrings, onClick: () -> Unit) {
    val local = state.localProfile
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatar(
                path = local.photoPath,
                bytes = local.thumbnailJpeg,
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (local.name.isBlank()) chrome.noName else local.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = chrome.editProfile,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun AboutContent(chrome: UiStrings) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("iTantra", style = MaterialTheme.typography.titleLarge)
        Text(
            text = chrome.version(`in`.gov.itantra.BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

enum class LanguageSettingsPage { SPEECH, DISPLAY }

/** Pushed from Settings: speech language packs, or the app display language. */
@Composable
fun LanguageSettingsScreen(
    page: LanguageSettingsPage,
    onBack: () -> Unit,
    viewModel: LanguageSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val chrome = UiStrings.forLanguage(uiState.uiLanguage)

    LaunchedEffect(Unit) {
        viewModel.reloadFromStore()
    }

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            onBack()
            viewModel.consumeFinished()
        }
    }

    uiState.pendingDelete?.let { language ->
        DeleteLanguageDialog(
            language = language,
            chrome = chrome,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() },
        )
    }

    SubScreenScaffold(
        title = if (page == LanguageSettingsPage.SPEECH) chrome.languagesTitle else chrome.appLanguageTitle,
        onBack = onBack,
        backDescription = chrome.back,
        bottomBar = {
            BottomActionBar {
                uiState.progress?.let { progress ->
                    DownloadProgressCard(message = progress.message, fraction = progress.fraction)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                uiState.error?.let { error ->
                    InlineMessage(text = error, isError = true)
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Button(
                    onClick = { viewModel.confirm() },
                    enabled = !uiState.busy && uiState.selected.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    Text(chrome.save, style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (page) {
                LanguageSettingsPage.SPEECH -> items(Language.entries.toList(), key = { "pack-${it.code}" }) { language ->
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
                LanguageSettingsPage.DISPLAY -> items(uiState.appLanguageOptions, key = { "ui-${it.code}" }) { language ->
                    UiLanguageRow(
                        language = language,
                        selected = uiState.uiLanguage == language,
                        enabled = !uiState.busy,
                        onSelect = { viewModel.setUiLanguage(language) },
                    )
                }
            }
        }
    }
}

private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Filled.BrightnessAuto
    ThemeMode.LIGHT -> Icons.Filled.LightMode
    ThemeMode.DARK -> Icons.Filled.DarkMode
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
