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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.ui.components.ProfileAvatar

private enum class MainTab { TALK, ALERT, ANALYSIS, RADAR }

@Composable
fun ITantraApp(
    appViewModel: AppViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = appViewModel.initialRoute,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(400)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(400)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(400)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(400)
            )
        }
    ) {
        composable(AppRoutes.SETUP_PROFILE) {
            ProfileScreen(
                isSetup = true,
                onFinished = { navController.navigate(AppRoutes.SETUP_LANGUAGES) { popUpTo(AppRoutes.SETUP_PROFILE) { inclusive = true } } },
            )
        }
        composable(AppRoutes.SETUP_LANGUAGES) {
            LanguageSelectionScreen(
                isSetup = true,
                onFinished = { navController.navigate(AppRoutes.MAIN) { popUpTo(0) } },
            )
        }
        composable(AppRoutes.MAIN) {
            MainContent(
                mainViewModel = mainViewModel,
                onOpenSettings = { navController.navigate(AppRoutes.SETTINGS) },
                onOpenProfile = { navController.navigate(AppRoutes.SETTINGS_PROFILE) },
                onOpenHistory = { navController.navigate(AppRoutes.HISTORY) },
            )
        }
        composable(AppRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(AppRoutes.SETTINGS_PROFILE) {
            ProfileScreen(
                isSetup = false,
                onFinished = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    mainViewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val chrome = UiStrings.forLanguage(uiState.uiLanguage)
    val radarViewModel: RadarViewModel = hiltViewModel()
    var tab by rememberSaveable { mutableStateOf(MainTab.TALK) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(mainViewModel) {
        mainViewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

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

    val activeAlert = uiState.activeIncomingAlert
    if (activeAlert != null) {
        AlertDialog(
            onDismissRequest = { /* Must be explicitly dismissed via button */ },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = androidx.compose.material3.MaterialTheme.colorScheme.error) },
            title = {
                val title = if (activeAlert.senderName.isNullOrEmpty()) {
                    "Incoming Alert"
                } else {
                    "Incoming Alert from ${activeAlert.senderName}"
                }
                Text(title)
            },
            text = { 
                Text(
                    text = when (val c = activeAlert.content) {
                        is `in`.gov.itantra.core.alert.AlertContent.Template -> c.template.phrase(activeAlert.language)
                        is `in`.gov.itantra.core.alert.AlertContent.Custom -> c.text
                    },
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                )
            },
            confirmButton = {
                Button(
                    onClick = { mainViewModel.dismissAlert() },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Okay (Stop Alarm)")
                }
            }
        )
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // Explicit width: ModalDrawerSheet with no modifier fills the screen
            // rather than the usual "overlay covering most, not all, of the
            // width" pattern -- capped at 320dp so the underlying screen stays
            // visibly peeking out at the right edge on phones, and the drawer
            // doesn't stretch edge-to-edge on tablets either.
            ModalDrawerSheet(modifier = Modifier.fillMaxWidth(0.8f).widthIn(max = 320.dp)) {
                androidx.compose.foundation.layout.Spacer(Modifier.padding(12.dp))
                
                // Drawer Header with Profile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val local = uiState.localProfile
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(
                            path = local.photoPath,
                            bytes = local.thumbnailJpeg,
                            modifier = Modifier.size(48.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
                        Text(
                            text = if (local.name.isBlank()) "No name" else local.displayName,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onOpenProfile()
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Profile", tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    }
                }
                
                androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

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
                    label = { Text("History") },
                    selected = false,
                    icon = { Icon(Icons.Default.Info, contentDescription = null) },
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onOpenHistory()
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
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
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
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
}
