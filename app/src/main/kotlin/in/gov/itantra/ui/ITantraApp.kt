package `in`.gov.itantra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.DisposableEffect
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
import `in`.gov.itantra.core.lang.UiStrings

private enum class MainTab { TALK, ALERT, ANALYSIS, RADAR }

@Composable
fun ITantraApp(
    appViewModel: AppViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val destination by appViewModel.destination.collectAsState()

    when (destination) {
        AppDestination.SETUP_PROFILE -> ProfileScreen(
            isSetup = true,
            onFinished = { },
        )
        AppDestination.SETUP_LANGUAGES -> LanguageSelectionScreen(
            isSetup = true,
            onFinished = { appViewModel.closeSettings() },
        )
        AppDestination.SETTINGS_HUB -> SettingsHubScreen(
            onOpenProfile = { appViewModel.openSettingsProfile() },
            onOpenLanguages = { appViewModel.openSettingsLanguages() },
            onBack = { appViewModel.closeSettings() },
            mainViewModel = mainViewModel,
        )
        AppDestination.SETTINGS_PROFILE -> ProfileScreen(
            isSetup = false,
            onFinished = { appViewModel.closeSettingsPage() },
            onBack = { appViewModel.closeSettingsPage() },
        )
        AppDestination.SETTINGS_LANGUAGES -> LanguageSelectionScreen(
            isSetup = false,
            onFinished = { appViewModel.closeSettingsPage() },
            onBack = { appViewModel.closeSettingsPage() },
        )
        AppDestination.MAIN -> MainContent(
            mainViewModel = mainViewModel,
            onOpenSettings = { appViewModel.openSettings() },
        )
    }
}

@Composable
fun MainContent(
    mainViewModel: MainViewModel,
    onOpenSettings: () -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val chrome = UiStrings.forLanguage(uiState.uiLanguage)
    val radarViewModel: RadarViewModel = hiltViewModel()
    var tab by rememberSaveable { mutableStateOf(MainTab.TALK) }

    DisposableEffect(tab) {
        if (tab == MainTab.RADAR) radarViewModel.start() else radarViewModel.stop()
        onDispose { radarViewModel.stop() }
    }

    BackHandler(enabled = tab != MainTab.TALK) { tab = MainTab.TALK }

    val pairing = uiState.pairingInfo
    if (pairing != null) {
        AlertDialog(
            onDismissRequest = { mainViewModel.dismissPairing() },
            title = { Text(chrome.pairingTitle) },
            text = { Text(chrome.pairingBody(pairing.code)) },
            confirmButton = {
                Button(onClick = { mainViewModel.confirmPairing() }) {
                    Text(chrome.pairingYes)
                }
            },
            dismissButton = {
                TextButton(onClick = { mainViewModel.dismissPairing() }) {
                    Text(chrome.pairingCancel)
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == MainTab.TALK,
                    onClick = { tab = MainTab.TALK },
                    icon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                    label = { Text(chrome.talk) },
                )
                NavigationBarItem(
                    selected = tab == MainTab.ALERT,
                    onClick = { tab = MainTab.ALERT },
                    icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
                    label = { Text(chrome.alert) },
                )
                NavigationBarItem(
                    selected = tab == MainTab.ANALYSIS,
                    onClick = { tab = MainTab.ANALYSIS },
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    label = { Text(chrome.analysis) },
                )
                NavigationBarItem(
                    selected = tab == MainTab.RADAR,
                    onClick = { tab = MainTab.RADAR },
                    icon = { Icon(Icons.Filled.Radar, contentDescription = null) },
                    label = { Text("Radar") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                MainTab.TALK -> MainScreen(viewModel = mainViewModel, onOpenSettings = onOpenSettings)
                MainTab.ALERT -> AlertScreen(viewModel = mainViewModel)
                MainTab.ANALYSIS -> DiagnosticsScreen(
                    onOpenSettings = onOpenSettings,
                    uiLanguage = uiState.uiLanguage,
                )
                MainTab.RADAR -> RadarScreen(mainViewModel = mainViewModel, radarViewModel = radarViewModel)
            }
        }
    }
}
