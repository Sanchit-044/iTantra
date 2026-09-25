package `in`.gov.itantra.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** Settings tab: clean and simple card layout with line-art icons and uppercase section headers. */
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
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Hero Profile Section Card (Image 2 style)
        ProfileHeroCard(state = mainState, chrome = chrome, onClick = onOpenProfile)

        // Section 1: Speech & Language Card
        SettingsGroup(chrome.sectionSpeech) {
            SettingsRow(
                icon = Icons.Outlined.RecordVoiceOver,
                title = chrome.languagesTitle,
                subtitle = "${mainState.currentLanguage.endonym} (${mainState.currentLanguage.englishName}) · ${chrome.installedCount(mainState.installedLanguages.size)}",
                onClick = onOpenLanguages,
                showDivider = false,
            )
            SettingsRow(
                icon = Icons.Outlined.Translate,
                title = chrome.appLanguageTitle,
                subtitle = "${mainState.uiLanguage.endonym} (${mainState.uiLanguage.englishName})",
                onClick = onOpenDisplay,
                showDivider = true,
            )
        }

        // Section 2: Appearance & Theme Card
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
                Spacer(Modifier.height(8.dp))
                Text(
                    text = mode.help(chrome),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 2.dp),
                )
            }
        }

        // Section 3: Diagnostics & System Metrics Card
        SettingsGroup(chrome.sectionDiagnostics) {
            SettingsRow(
                icon = Icons.Outlined.Insights,
                title = chrome.analysis,
                subtitle = "Live WER, RTF, CPU & Transport telemetry",
                onClick = onOpenAnalysis,
                showDivider = false,
            )
        }

        // Section 4: About & System Info Card
        SettingsGroup(chrome.sectionAbout) {
            AboutCardContent(chrome)
        }
    }
}

@Composable
private fun ProfileHeroCard(state: UiState, chrome: UiStrings, onClick: () -> Unit) {
    val local = state.localProfile
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                ProfileAvatar(
                    path = local.photoPath,
                    bytes = local.thumbnailJpeg,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = chrome.editProfile,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (local.name.isBlank()) chrome.noName else local.displayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            StatusPill(
                text = chrome.offlineEncrypted,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                icon = Icons.Outlined.Lock,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutCardContent(chrome: UiStrings) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = chrome.appTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = chrome.version(`in`.gov.itantra.BuildConfig.VERSION_NAME) + " · ISRO SIH PS 26173",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = chrome.aboutOffline,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StatusPill(
                text = "100% Offline",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusPill(
                text = "AES-256-GCM",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            StatusPill(
                text = "ONNX AI STT/TTS",
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        title = when (page) {
            LanguageSettingsPage.SPEECH -> chrome.languagesTitle
            LanguageSettingsPage.DISPLAY -> chrome.appLanguageTitle
        },
        onBack = onBack,
        backDescription = chrome.back,
        bottomBar = {
            if (uiState.progress != null || uiState.error != null) {
                BottomActionBar {
                    uiState.progress?.let { progress ->
                        DownloadProgressCard(message = progress.message, fraction = progress.fraction)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    uiState.error?.let { error ->
                        InlineMessage(text = error, isError = true)
                    }
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
                        current = uiState.current == language,
                        busy = uiState.busy,
                        downloaded = language in uiState.downloaded,
                        downloadingNow = uiState.progress?.language == language,
                        progressFraction = if (uiState.progress?.language == language) uiState.progress?.fraction else null,
                        chrome = chrome,
                        onSelectCurrent = { viewModel.setCurrent(language) },
                        onDownload = { viewModel.downloadLanguage(language) },
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

