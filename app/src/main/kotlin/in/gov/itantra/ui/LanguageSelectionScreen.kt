package `in`.gov.itantra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.lang.UiStrings
import kotlin.math.roundToInt

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
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text(chrome.deleteLanguageTitle) },
            text = { Text(chrome.deleteLanguageBody(language.englishName)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text(chrome.deleteConfirm, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) {
                    Text(chrome.pairingCancel)
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!isSetup || setupPacksPage) {
                item(key = "packs-title") {
                    Text(
                        text = if (isSetup) chrome.chooseLanguages else chrome.languagesTitle,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isSetup) chrome.chooseLanguagesBody else chrome.speechHelp,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
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
            }

            if (!isSetup || setupAppPage) {
                item(key = "ui-title") {
                    if (!isSetup) Spacer(modifier = Modifier.height(20.dp))
                    Text(text = chrome.appLanguageTitle, style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = chrome.appLanguageBody, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
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
                        Column {
                            Text(language.endonym, style = MaterialTheme.typography.titleMedium)
                            Text(language.englishName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

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
            onClick = {
                if (setupPacksPage) viewModel.goToAppLanguage() else viewModel.confirm()
            },
            enabled = !uiState.busy && uiState.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isSetup) chrome.continueLabel else chrome.save)
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

@Composable
fun PackRow(
    language: Language,
    selected: Boolean,
    current: Boolean,
    busy: Boolean,
    downloaded: Boolean,
    downloadingNow: Boolean,
    chrome: UiStrings,
    onToggle: () -> Unit,
    onCurrent: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !busy, onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = { onToggle() }, enabled = !busy)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = language.endonym, style = MaterialTheme.typography.titleMedium)
            Text(
                text = language.englishName + if (language == Language.DEFAULT) "  ·  ${chrome.defaultHint}" else "",
                style = MaterialTheme.typography.bodySmall,
            )
            PackStatus(
                downloaded = downloaded,
                downloadingNow = downloadingNow,
                selected = selected,
                chrome = chrome,
            )
        }
        if (downloaded && !downloadingNow) {
            IconButton(onClick = onDelete, enabled = !busy) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = chrome.deleteLanguageAction,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (selected) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(enabled = !busy, onClick = onCurrent),
            ) {
                RadioButton(selected = current, onClick = onCurrent, enabled = !busy)
                Text(chrome.active, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private val DownloadedGreen = Color(0xFF2E7D32)

/** Small status line under a language's name: downloaded, downloading now, or queued to download. */
@Composable
private fun PackStatus(
    downloaded: Boolean,
    downloadingNow: Boolean,
    selected: Boolean,
    chrome: UiStrings,
) {
    when {
        downloadingNow -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = chrome.downloadingLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        downloaded -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = DownloadedGreen,
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = chrome.downloaded, style = MaterialTheme.typography.labelSmall, color = DownloadedGreen)
        }
        selected -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = chrome.notDownloaded,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
