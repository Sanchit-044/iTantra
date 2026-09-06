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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
            onOpenProfile = { appViewModel.openSettingsProfile() },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    mainViewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
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

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
                Text(
                    text = chrome.appTitle,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                )
                androidx.compose.material3.NavigationDrawerItem(
                    label = { Text("Profile") },
                    selected = false,
                    icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onOpenProfile()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                androidx.compose.material3.NavigationDrawerItem(
                    label = { Text(chrome.settings) },
                    selected = false,
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onOpenSettings()
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (tab) {
                                MainTab.TALK -> chrome.appTitle
                                MainTab.ALERT -> chrome.alert
                                MainTab.ANALYSIS -> chrome.analysis
                                MainTab.RADAR -> "Radar"
                            },
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        if (tab == MainTab.TALK || tab == MainTab.ANALYSIS) {
                            IconButton(onClick = onOpenSettings) {
                                Icon(Icons.Filled.Settings, contentDescription = "Settings")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background
                    )
                )
            },
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
}
