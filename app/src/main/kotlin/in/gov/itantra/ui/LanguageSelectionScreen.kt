package `in`.gov.itantra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                onClick = {
                    if (setupPacksPage) viewModel.goToAppLanguage() else viewModel.confirm()
                },
                enabled = !uiState.busy && uiState.selected.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
            ) {
                Text(
                    if (isSetup) chrome.continueLabel else chrome.save,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (isSetup) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
            if (setupAppPage) {
                TextButton(
                    onClick = { viewModel.backToPacks() },
                    enabled = !uiState.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(chrome.back)
                }
            } else if (onBack != null) {
                TextButton(
                    onClick = onBack,
                    enabled = !uiState.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(chrome.back)
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
                if (highlighted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = language.endonym.take(1),
            style = MaterialTheme.typography.titleLarge,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
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
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
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
    progressFraction: Float? = null,
) {
    val isDull = !downloaded && !downloadingNow

    Surface(
        onClick = {
            if (!downloaded && !downloadingNow) {
                onDownload()
            } else {
                onSelectCurrent()
            }
        },
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDull) 0.65f else 1.0f),
        shape = MaterialTheme.shapes.medium,
        color = when {
            current -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            downloaded -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = when {
            current -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            downloaded -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanguageGlyph(language = language, highlighted = current && downloaded)

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
                    downloaded = downloaded,
                    downloadingNow = downloadingNow,
                    chrome = chrome,
                )
            }

            Spacer(Modifier.width(10.dp))

            when {
                downloadingNow -> {
                    // Circular Progress Ring showing live progress
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
                            )
                            Text(
                                text = "${(progressFraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary,
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (current) {
                            StatusPill(
                                text = chrome.active,
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                icon = Icons.Filled.Check,
                            )
                        } else {
                            Button(
                                onClick = onSelectCurrent,
                                enabled = !busy,
                                shape = MaterialTheme.shapes.small,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.height(34.dp),
                            ) {
                                Text(
                                    text = "Set as Active",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (language != Language.DEFAULT) {
                                Spacer(Modifier.width(4.dp))
                                IconButton(onClick = onDelete, enabled = !busy, modifier = Modifier.size(32.dp)) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteOutline,
                                        contentDescription = chrome.deleteLanguageAction,
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Small status line under a language's name: downloaded, downloading now, or queued to download. */
@Composable
private fun PackStatus(
    downloaded: Boolean,
    downloadingNow: Boolean,
    chrome: UiStrings,
) {
    val success = ITantraTheme.extended.success
    when {
        downloadingNow -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = chrome.downloadingLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        downloaded -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = chrome.downloaded,
                style = MaterialTheme.typography.labelSmall,
                color = success,
            )
        }
        else -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = chrome.notDownloaded,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}
