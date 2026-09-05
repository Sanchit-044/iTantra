package `in`.gov.itantra.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ITantraApp(appViewModel: AppViewModel = hiltViewModel()) {
    val destination by appViewModel.destination.collectAsState()
    when (destination) {
        AppDestination.SETUP -> LanguageSelectionScreen(
            isSetup = true,
            onFinished = { appViewModel.closeSettings() },
        )
        AppDestination.SETTINGS -> LanguageSelectionScreen(
            isSetup = false,
            onFinished = { appViewModel.closeSettings() },
            onBack = { appViewModel.closeSettings() },
        )
        AppDestination.MAIN -> MainScreen(
            onOpenSettings = { appViewModel.openSettings() },
        )
    }
}
