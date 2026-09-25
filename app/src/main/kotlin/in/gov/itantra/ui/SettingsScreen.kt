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
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Translate
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.core.theme.ThemeMode
import `in`.gov.itantra.ui.components.BottomActionBar
import `in`.gov.itantra.ui.components.DownloadProgressCard
import `in`.gov.itantra.ui.components.IconBadge
import `in`.gov.itantra.ui.components.InlineMessage
import `in`.gov.itantra.ui.components.ProfileAvatar
import `in`.gov.itantra.ui.components.SettingsGroup
import `in`.gov.itantra.ui.components.SettingsRow
import `in`.gov.itantra.ui.components.StatusPill
import `in`.gov.itantra.ui.components.SubScreenScaffold

/** Settings tab: profile, languages, theme, diagnostics and about, as clean grouped cards. */
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
    val ext = ITantraTheme.extended

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Hero Profile Header
        ProfileHeroCard(state = mainState, chrome = chrome, onClick = onOpenProfile)

        // Section 1: Speech & Language
        SettingsGroup(chrome.sectionSpeech) {
            SettingsRow(
                icon = Icons.Outlined.RecordVoiceOver,
                title = chrome.languagesTitle,
                subtitle = "${mainState.currentLanguage.endonym} (${mainState.currentLanguage.englishName}) · ${chrome.installedCount(mainState.installedLanguages.size)}",
                onClick = onOpenLanguages,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(
                            text = mainState.currentLanguage.endonym,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
            SettingsRow(
                icon = Icons.Outlined.Translate,
                title = chrome.appLanguageTitle,
                subtitle = "${mainState.uiLanguage.endonym} (${mainState.uiLanguage.englishName})",
                onClick = onOpenDisplay,
                showDivider = true,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(
                            text = mainState.uiLanguage.endonym,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }

        // Section 2: Appearance & Theme
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
                Spacer(Modifier.height(10.dp))
                Text(
                    text = mode.help(chrome),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        // Section 3: Diagnostics & System Metrics
        SettingsGroup(chrome.sectionDiagnostics) {
            SettingsRow(
                icon = Icons.Outlined.Insights,
                title = chrome.analysis,
                subtitle = "Live WER, RTF, CPU & Transport metrics",
                onClick = onOpenAnalysis,
                iconContainerColor = ext.successContainer,
                iconContentColor = ext.onSuccessContainer,
            )
        }

        // Section 4: About & System Info
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
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                ProfileAvatar(
                    path = local.photoPath,
                    bytes = local.thumbnailJpeg,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                )
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (local.name.isBlank()) chrome.noName else local.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        text = chrome.offlineEncrypted,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        icon = Icons.Outlined.Lock,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = chrome.editProfile,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutCardContent(chrome: UiStrings) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = Icons.Outlined.Info,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                size = 36.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = chrome.appTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = chrome.version(`in`.gov.itantra.BuildConfig.VERSION_NAME) + " · ISRO SIH PS 26173",
                    style = MaterialTheme.typography.labelMedium,
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
