package `in`.gov.itantra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel

private enum class MainTab { TALK, ANALYSIS }

@Composable
fun ITantraApp(
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by mainViewModel.uiState.collectAsState()
    var tab by rememberSaveable { mutableStateOf(MainTab.TALK) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = !settingsOpen && tab == MainTab.ANALYSIS) {
        tab = MainTab.TALK
    }

    if (uiState.pairingInfo != null) {
        AlertDialog(
            onDismissRequest = { mainViewModel.dismissPairing() },
            title = { Text("Confirm Pairing") },
            text = {
                Text("Do you see the same code on the other device?\n\nCode: ${uiState.pairingInfo?.code}")
            },
            confirmButton = {
                Button(onClick = { mainViewModel.confirmPairing() }) {
                    Text("Yes, Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { mainViewModel.dismissPairing() }) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!settingsOpen) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == MainTab.TALK,
                        onClick = { tab = MainTab.TALK },
                        icon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        label = { Text("Talk") },
                    )
                    NavigationBarItem(
                        selected = tab == MainTab.ANALYSIS,
                        onClick = { tab = MainTab.ANALYSIS },
                        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        label = { Text("Analysis") },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                settingsOpen -> SettingsScreen(
                    currentLanguage = uiState.speakLanguage,
                    speaking = uiState.isSpeaking,
                    onLanguageSelected = { language ->
                        mainViewModel.setSpeakLanguage(language)
                        mainViewModel.setListenLanguage(language)
                    },
                    onBack = { settingsOpen = false },
                )
                tab == MainTab.ANALYSIS -> DiagnosticsScreen(
                    onOpenSettings = { settingsOpen = true },
                )
                else -> MainScreen(
                    onOpenSettings = { settingsOpen = true },
                    viewModel = mainViewModel,
                )
            }
        }
    }
}
