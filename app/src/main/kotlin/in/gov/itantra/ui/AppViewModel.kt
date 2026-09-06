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
    SETTINGS_LANGUAGES,
}

@HiltViewModel
class AppViewModel @Inject constructor(
    store: LanguageSettingsStore,
) : ViewModel() {

    private val _showSettings = MutableStateFlow(false)
    private val _languagesOpen = MutableStateFlow(false)

    val destination: StateFlow<AppDestination> = combine(
        store.settings.map { it.setupDone },
        _showSettings,
        _languagesOpen,
    ) { setupDone, showSettings, languagesOpen ->
        when {
            !setupDone -> AppDestination.SETUP
            !showSettings -> AppDestination.MAIN
            languagesOpen -> AppDestination.SETTINGS_LANGUAGES
            else -> AppDestination.SETTINGS
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        if (store.snapshot.setupDone) AppDestination.MAIN else AppDestination.SETUP,
    )

    fun openSettings() {
        _languagesOpen.value = false
        _showSettings.value = true
    }

    fun openSettingsLanguages() {
        _showSettings.value = true
        _languagesOpen.value = true
    }

    fun closeSettingsLanguages() {
        _languagesOpen.value = false
    }

    fun closeSettings() {
        _languagesOpen.value = false
        _showSettings.value = false
    }
}
