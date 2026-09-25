package `in`.gov.itantra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.ui.components.BottomActionBar
import `in`.gov.itantra.ui.components.DownloadProgressCard
import `in`.gov.itantra.ui.components.MaxContentWidth
import `in`.gov.itantra.ui.components.InlineMessage
import `in`.gov.itantra.ui.components.SetupHeader
import `in`.gov.itantra.ui.components.StatusPill

@Composable
fun LanguageSelectionScreen(
    isSetup: Boolean,
    onFinished: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: LanguageSelectionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val chrome = if (isSetup || !uiState.setupDone) {
        UiStrings.forLanguage(Language.ENGLISH)
    } else {
        UiStrings.forLanguage(uiState.uiLanguage)
    }

    LaunchedEffect(isSetup) {
        if (!isSetup) viewModel.reloadFromStore()
    }

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            onFinished()
            viewModel.consumeFinished()
        }
    }

    val setupAppPage = isSetup && uiState.page == LanguageSetupPage.APP_LANGUAGE
    val setupPacksPage = isSetup && uiState.page == LanguageSetupPage.PACKS

    BackHandler(enabled = setupAppPage && !uiState.busy) {
        viewModel.backToPacks()
    }

    uiState.pendingDelete?.let { language ->
        DeleteLanguageDialog(
            language = language,
            chrome = chrome,
            onConfirm = { viewModel.confirmDelete() },
            onDismiss = { viewModel.cancelDelete() },
        )
    }

    val snackbarHostState = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            if (error.isNotBlank()) {
                snackbarHostState.showSnackbar(error)
            }
        }
    }

    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { `in`.gov.itantra.ui.components.AppSnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isSetup) {
                BottomActionBar {
                    Button(
                        onClick = {
                            if (setupPacksPage) viewModel.goToAppLanguage() else viewModel.confirm()
                        },
                        enabled = !uiState.busy && uiState.selected.isNotEmpty(),
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text(
                            chrome.continueLabel,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                    }
                    if (setupAppPage) {
                        TextButton(
                            onClick = { viewModel.backToPacks() },
                            enabled = !uiState.busy,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(chrome.back)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .statusBarsPadding(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!isSetup || setupPacksPage) {
                    item(key = "packs-title") {
                        if (isSetup) {
                            SetupHeader(
                                step = 2,
                                totalSteps = 3,
                                stepLabel = chrome.step(2, 3),
                                title = chrome.chooseLanguages,
                                body = chrome.chooseLanguagesBody,
                            )
                        } else {
                            Column {
                                Text(chrome.languagesTitle, style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    chrome.speechHelp,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    items(Language.entries.toList(), key = { "pack-${it.code}" }) { language ->
                        val isDownloading = language in uiState.activeDownloads || uiState.progress?.language == language
                        val isCurrentProgress = uiState.progress?.language == language
                        val fraction = if (isCurrentProgress) uiState.progress?.fraction else null
                        PackRow(
                            language = language,
                            current = uiState.current == language,
                            busy = false,
                            downloaded = language in uiState.downloaded,
                            downloadingNow = isDownloading,
                            progressFraction = fraction,
                            chrome = chrome,
                            onSelectCurrent = { viewModel.setCurrent(language) },
                            onDownload = { viewModel.downloadLanguage(language) },
                            onDelete = { viewModel.requestDelete(language) },
                            onPause = { viewModel.pauseDownload(language) },
                        )
                    }
                }

                if (!isSetup || setupAppPage) {
                    item(key = "ui-title") {
                        if (isSetup) {
                            SetupHeader(
                                step = 3,
                                totalSteps = 3,
                                stepLabel = chrome.step(3, 3),
                                title = chrome.appLanguageTitle,
                                body = chrome.appLanguageBody,
                            )
                        } else {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(chrome.appLanguageTitle, style = MaterialTheme.typography.headlineSmall)
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    chrome.appLanguageBody,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    items(uiState.appLanguageOptions, key = { "ui-${it.code}" }) { language ->
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
}

@Composable
internal fun DeleteLanguageDialog(
    language: Language,
    chrome: UiStrings,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
        iconContentColor = MaterialTheme.colorScheme.error,
        title = { Text(chrome.deleteLanguageTitle) },
        text = { Text(chrome.deleteLanguageBody(language.englishName)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(chrome.deleteConfirm, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(chrome.pairingCancel)
            }
        },
    )
}

/** Round glyph showing the first letter of the language in its own script. */
@Composable
private fun LanguageGlyph(language: Language, highlighted: Boolean) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (highlighted) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = language.endonym.take(1),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun UiLanguageRow(
    language: Language,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanguageGlyph(language, highlighted = selected)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(language.endonym, style = MaterialTheme.typography.titleMedium)
                Text(
                    language.englishName,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        }
    }
}

@Composable
fun PackRow(
    language: Language,
    current: Boolean,
    busy: Boolean,
    downloaded: Boolean,
    downloadingNow: Boolean,
    chrome: UiStrings,
    onSelectCurrent: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onPause: () -> Unit = {},
    progressFraction: Float? = null,
) {
    val isDull = !downloaded && !downloadingNow

    Surface(
        onClick = {
            if (downloaded) {
                onSelectCurrent()
            } else if (!downloadingNow) {
                onDownload()
            }
        },
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDull) 0.7f else 1.0f),
        shape = MaterialTheme.shapes.medium,
        color = when {
            current -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        contentColor = when {
            current -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        border = when {
            current -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
            downloaded -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanguageGlyph(language = language, highlighted = current)

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = language.endonym,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = language.englishName + if (language == Language.DEFAULT) "  ·  ${chrome.defaultHint}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(2.dp))

                PackStatus(
                    language = language,
                    current = current,
                    downloaded = downloaded,
                    downloadingNow = downloadingNow,
                    chrome = chrome,
                    progressFraction = progressFraction,
                )
            }

            Spacer(Modifier.width(10.dp))

            when {
                downloadingNow -> {
                    // Circular Progress Ring with pause button in the center
                    Box(
                        modifier = Modifier.size(38.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (progressFraction != null && progressFraction > 0f) {
                            CircularProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            )
                        }
                        IconButton(
                            onClick = onPause,
                            modifier = Modifier.size(38.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Pause,
                                contentDescription = "Pause download",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                !downloaded -> {
                    // Download Icon Button
                    IconButton(
                        onClick = onDownload,
                        enabled = !busy,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudDownload,
                            contentDescription = chrome.downloadingLabel,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                downloaded -> {
                    // Delete Button
                    IconButton(
                        onClick = onDelete,
                        enabled = !busy,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = chrome.deleteLanguageAction,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

val Language.packSize: String
    get() = when (this) {
        Language.HINDI -> "395 MB"
        Language.TAMIL -> "395 MB"
        Language.BENGALI -> "395 MB"
        Language.GUJARATI -> "395 MB"
        Language.MARATHI -> "395 MB"
        Language.KANNADA -> "395 MB"
        Language.MALAYALAM -> "395 MB"
        Language.TELUGU -> "395 MB"
        Language.ODIA -> "395 MB"
        Language.ENGLISH -> "390 MB"
    }

/** Small status line under a language's name: size, downloaded, downloading percentage, or not downloaded. */
@Composable
private fun PackStatus(
    language: Language,
    current: Boolean,
    downloaded: Boolean,
    downloadingNow: Boolean,
    chrome: UiStrings,
    progressFraction: Float? = null,
) {
    val sizeText = language.packSize
    val success = ITantraTheme.extended.success
    when {
        downloadingNow -> Row(verticalAlignment = Alignment.CenterVertically) {
            val pctText = if (progressFraction != null && progressFraction > 0f) {
                "${(progressFraction * 100).toInt()}%"
            } else {
                "0%"
            }
            Text(
                text = "$sizeText · $pctText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        downloaded -> Row(verticalAlignment = Alignment.CenterVertically) {
            val statusLabel = if (current) "${chrome.downloaded} · ${chrome.active}" else chrome.downloaded
            Text(
                text = "$sizeText · $statusLabel",
                style = MaterialTheme.typography.labelSmall,
                color = if (current) MaterialTheme.colorScheme.primary else success,
                fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
            )
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$sizeText · ${chrome.notDownloaded}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
