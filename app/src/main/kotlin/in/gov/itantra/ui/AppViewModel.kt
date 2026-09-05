package `in`.gov.itantra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.gov.itantra.core.lang.LanguageSettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class AppDestination {
    SETUP,
    MAIN,
    SETTINGS,
}

@HiltViewModel
class AppViewModel @Inject constructor(
    store: LanguageSettingsStore,
) : ViewModel() {

    private val _showSettings = MutableStateFlow(false)

    val destination: StateFlow<AppDestination> = combine(
        store.settings.map { it.setupDone },
        _showSettings,
    ) { setupDone, showSettings ->
        when {
            !setupDone -> AppDestination.SETUP
            showSettings -> AppDestination.SETTINGS
            else -> AppDestination.MAIN
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        if (store.snapshot.setupDone) AppDestination.MAIN else AppDestination.SETUP,
    )

    fun openSettings() {
        _showSettings.value = true
    }

    fun closeSettings() {
        _showSettings.value = false
    }
}
