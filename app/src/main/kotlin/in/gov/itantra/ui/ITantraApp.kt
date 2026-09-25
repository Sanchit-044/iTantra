package `in`.gov.itantra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import `in`.gov.itantra.core.alert.AlertContent
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.ui.components.AlertFab
import `in`.gov.itantra.ui.components.AppBarTitle
import `in`.gov.itantra.ui.components.AppSnackbarHost
import `in`.gov.itantra.ui.components.AppTopBar
import `in`.gov.itantra.ui.components.MaxWidthBox
import `in`.gov.itantra.ui.components.ProfileAvatar
import `in`.gov.itantra.ui.components.SubScreenScaffold
import `in`.gov.itantra.ui.components.isWideLayout

private enum class MainTab { TALK, RADAR, ALERT, HISTORY, SETTINGS }

private const val NAV_ANIM_MS = 320

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
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(NAV_ANIM_MS), initialOffset = { it / 5 }) +
                fadeIn(tween(NAV_ANIM_MS))
        },
        exitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(NAV_ANIM_MS), targetOffset = { it / 5 }) +
                fadeOut(tween(NAV_ANIM_MS / 2))
        },
        popEnterTransition = {
            slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(NAV_ANIM_MS), initialOffset = { it / 5 }) +
                fadeIn(tween(NAV_ANIM_MS))
        },
        popExitTransition = {
            slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(NAV_ANIM_MS), targetOffset = { it / 5 }) +
                fadeOut(tween(NAV_ANIM_MS / 2))
        },
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
                onOpenProfile = { navController.navigate(AppRoutes.SETTINGS_PROFILE) },
                onOpenLanguages = { navController.navigate(AppRoutes.SETTINGS_LANGUAGES) },
                onOpenDisplay = { navController.navigate(AppRoutes.SETTINGS_DISPLAY) },
                onOpenAnalysis = { navController.navigate(AppRoutes.SETTINGS_ANALYSIS) },
            )
        }
        composable(AppRoutes.SETTINGS_PROFILE) {
            val main by mainViewModel.uiState.collectAsState()
            ProfileScreen(
                isSetup = false,
                uiLanguage = main.uiLanguage,
                onFinished = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.SETTINGS_LANGUAGES) {
            LanguageSettingsScreen(
                page = LanguageSettingsPage.SPEECH,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.SETTINGS_DISPLAY) {
            LanguageSettingsScreen(
                page = LanguageSettingsPage.DISPLAY,
                onBack = { navController.popBackStack() },
            )
        }
        composable(AppRoutes.SETTINGS_ANALYSIS) {
            val main by mainViewModel.uiState.collectAsState()
            val chrome = UiStrings.forLanguage(main.uiLanguage)
            SubScreenScaffold(
                title = chrome.analysis,
                onBack = { navController.popBackStack() },
                backDescription = chrome.back,
            ) {
                DiagnosticsScreen(uiLanguage = main.uiLanguage)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(
    mainViewModel: MainViewModel,
    onOpenProfile: () -> Unit,
    onOpenLanguages: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenAnalysis: () -> Unit,
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val chrome = UiStrings.forLanguage(uiState.uiLanguage)
    val radarViewModel: RadarViewModel = hiltViewModel()
    val historyViewModel: HistoryViewModel = hiltViewModel()
    val history by historyViewModel.uiState.collectAsState()
    var tab by rememberSaveable { mutableStateOf(MainTab.TALK) }
    var confirmClearHistory by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val wide = isWideLayout()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val unread = uiState.inbox.count { it.unread }

    LaunchedEffect(mainViewModel) {
        mainViewModel.snackbarMessage.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(uiState.notice) {
        uiState.notice?.let { notice ->
            val text = notice.format(chrome)
            if (text.isNotBlank()) {
                snackbarHostState.showSnackbar(text)
                mainViewModel.clearError()
            }
        }
    }

    DisposableEffect(tab) {
        if (tab == MainTab.RADAR) radarViewModel.start() else radarViewModel.stop()
        onDispose { radarViewModel.stop() }
    }

    BackHandler(enabled = tab != MainTab.TALK) { tab = MainTab.TALK }

    uiState.pairingInfo?.let { pairing ->
        PairingDialog(
            code = pairing.code,
            chrome = chrome,
            onConfirm = { mainViewModel.confirmPairing() },
            onDismiss = { mainViewModel.dismissPairing() },
        )
    }

    uiState.activeIncomingAlert?.let { activeAlert ->
        val text = when (val c = activeAlert.content) {
            is AlertContent.Template -> c.template.phrase(activeAlert.language)
            is AlertContent.Custom -> c.text
        }
        IncomingAlertDialog(
            senderName = activeAlert.senderName,
            chrome = chrome,
            text = text,
            onDismiss = { mainViewModel.dismissAlert() },
        )
    }

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
            iconContentColor = MaterialTheme.colorScheme.error,
            title = { Text(chrome.clearHistoryTitle) },
            text = { Text(chrome.clearHistoryBody) },
            confirmButton = {
                TextButton(onClick = {
                    historyViewModel.clearHistory()
                    confirmClearHistory = false
                }) {
                    Text(chrome.clearConfirm, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearHistory = false }) { Text(chrome.pairingCancel) }
            },
        )
    }

    Row(modifier = Modifier.fillMaxSize()) {
        if (wide) {
            MainNavRail(tab = tab, onSelect = { tab = it }, chrome = chrome, unread = unread)
        }
        Scaffold(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { AppSnackbarHost(snackbarHostState) },
            topBar = {
                AppTopBar(
                    title = {
                        when (tab) {
                            MainTab.TALK -> AppBarTitle("iTantra")
                            MainTab.RADAR -> AppBarTitle(chrome.navRadar)
                            MainTab.ALERT -> AppBarTitle(chrome.alert)
                            MainTab.HISTORY -> AppBarTitle(chrome.historyTitle)
                            MainTab.SETTINGS -> AppBarTitle(chrome.settings)
                        }
                    },
                    actions = {
                        when (tab) {
                            MainTab.TALK -> {
                                LanguageChip(label = uiState.currentLanguage.endonym, onClick = onOpenLanguages)
                                IconButton(onClick = onOpenProfile) {
                                    ProfileAvatar(
                                        path = uiState.localProfile.photoPath,
                                        bytes = uiState.localProfile.thumbnailJpeg,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                            }
                            MainTab.HISTORY -> IconButton(
                                onClick = { confirmClearHistory = true },
                                enabled = history.messages.isNotEmpty(),
                            ) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = chrome.clearHistory)
                            }
                            else -> Unit
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            },
            bottomBar = {
                if (!wide) {
                    MainNavBar(tab = tab, onSelect = { tab = it }, chrome = chrome, unread = unread)
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                label = "tab",
            ) { current ->
                MaxWidthBox {
                    when (current) {
                        MainTab.TALK -> MainScreen(viewModel = mainViewModel)
                        MainTab.RADAR -> RadarScreen(mainViewModel = mainViewModel, radarViewModel = radarViewModel)
                        MainTab.ALERT -> AlertScreen(viewModel = mainViewModel)
                        MainTab.HISTORY -> HistoryScreen(viewModel = historyViewModel, chrome = chrome)
                        MainTab.SETTINGS -> SettingsScreen(
                            mainState = uiState,
                            onOpenProfile = onOpenProfile,
                            onOpenLanguages = onOpenLanguages,
                            onOpenDisplay = onOpenDisplay,
                            onOpenAnalysis = onOpenAnalysis,
                        )
                    }
                }
            }
        }
    }
}

private data class NavDestination(
    val tab: MainTab,
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
    val badge: Int = 0,
)

private fun destinations(chrome: UiStrings, unread: Int) = listOf(
    NavDestination(MainTab.TALK, chrome.talk, Icons.Filled.Mic, Icons.Outlined.Mic, unread),
    NavDestination(MainTab.RADAR, chrome.navRadar, Icons.Filled.Radar, Icons.Outlined.Radar),
    NavDestination(MainTab.HISTORY, chrome.navHistory, Icons.Filled.History, Icons.Outlined.History),
    NavDestination(MainTab.SETTINGS, chrome.settings, Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
private fun NavIcon(dest: NavDestination, selected: Boolean) {
    BadgedBox(badge = {
        if (dest.badge > 0) Badge { Text(if (dest.badge > 9) "9+" else dest.badge.toString()) }
    }) {
        Icon(if (selected) dest.selectedIcon else dest.icon, contentDescription = null)
    }
}

/** Phone layout: Talk · Radar · [Alert FAB] · History · Settings. */
@Composable
private fun MainNavBar(tab: MainTab, onSelect: (MainTab) -> Unit, chrome: UiStrings, unread: Int) {
    val items = destinations(chrome, unread)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(106.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            items.take(2).forEach { NavBarItem(it, tab, onSelect) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    text = chrome.alert,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (tab == MainTab.ALERT) FontWeight.Bold else FontWeight.Medium,
                    color = if (tab == MainTab.ALERT) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            items.drop(2).forEach { NavBarItem(it, tab, onSelect) }
        }

        AlertFab(
            selected = tab == MainTab.ALERT,
            contentDescription = chrome.alert,
            onClick = { onSelect(MainTab.ALERT) },
            size = 52.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun RowScope.NavBarItem(dest: NavDestination, tab: MainTab, onSelect: (MainTab) -> Unit) {
    val selected = dest.tab == tab
    NavigationBarItem(
        selected = selected,
        onClick = { onSelect(dest.tab) },
        icon = { NavIcon(dest, selected) },
        label = { Text(dest.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    )
}

/** Tablet / landscape layout: navigation rail with the Alert FAB as its header. */
@Composable
private fun MainNavRail(tab: MainTab, onSelect: (MainTab) -> Unit, chrome: UiStrings, unread: Int) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        header = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(8.dp))
                AlertFab(
                    selected = tab == MainTab.ALERT,
                    contentDescription = chrome.alert,
                    onClick = { onSelect(MainTab.ALERT) },
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    chrome.alert,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    ) {
        Spacer(Modifier.weight(1f))
        destinations(chrome, unread).forEach { dest ->
            val selected = dest.tab == tab
            NavigationRailItem(
                selected = selected,
                onClick = { onSelect(dest.tab) },
                icon = { NavIcon(dest, selected) },
                label = { Text(dest.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun LanguageChip(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.padding(end = 4.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Translate, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun PairingDialog(
    code: String,
    chrome: UiStrings,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Verified, contentDescription = null) },
        title = { Text(chrome.pairingTitle, textAlign = TextAlign.Center) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = code.chunked(3).joinToString(" "),
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    chrome.pairingBody(code),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text(chrome.pairingYes) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(chrome.pairingCancel) } },
    )
}

@Composable
private fun IncomingAlertDialog(senderName: String?, chrome: UiStrings, text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Must be explicitly dismissed via button */ },
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconContentColor = MaterialTheme.colorScheme.error,
        titleContentColor = MaterialTheme.colorScheme.onErrorContainer,
        textContentColor = MaterialTheme.colorScheme.onErrorContainer,
        icon = { Icon(Icons.Filled.Warning, contentDescription = null, modifier = Modifier.size(36.dp)) },
        title = {
            Text(
                text = if (senderName.isNullOrEmpty()) chrome.incomingAlert else chrome.incomingAlertFrom(senderName),
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text(chrome.stopAlarm)
            }
        },
    )
}
