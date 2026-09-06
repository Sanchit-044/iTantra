package `in`.gov.itantra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.gov.itantra.core.lang.LanguageSettingsStore
import `in`.gov.itantra.core.profile.ProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class AppDestination {
    SETUP_PROFILE,
    SETUP_LANGUAGES,
    MAIN,
    SETTINGS_HUB,
    SETTINGS_PROFILE,
    SETTINGS_LANGUAGES,
}

@HiltViewModel
class AppViewModel @Inject constructor(
    languageStore: LanguageSettingsStore,
    profileStore: ProfileStore,
) : ViewModel() {

    private val _showSettings = MutableStateFlow(false)
    private val _settingsPage = MutableStateFlow(SettingsPage.HUB)

    val destination: StateFlow<AppDestination> = combine(
        profileStore.profile,
        languageStore.settings,
        _showSettings,
        _settingsPage,
    ) { profile, languages, showSettings, settingsPage ->
        when {
            !profile.isComplete -> AppDestination.SETUP_PROFILE
            !languages.setupDone -> AppDestination.SETUP_LANGUAGES
            !showSettings -> AppDestination.MAIN
            settingsPage == SettingsPage.PROFILE -> AppDestination.SETTINGS_PROFILE
            settingsPage == SettingsPage.LANGUAGES -> AppDestination.SETTINGS_LANGUAGES
            else -> AppDestination.SETTINGS_HUB
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        initialDestination(profileStore, languageStore),
    )

    fun openSettings() {
        _settingsPage.value = SettingsPage.HUB
        _showSettings.value = true
    }

    fun openSettingsProfile() {
        _settingsPage.value = SettingsPage.PROFILE
        _showSettings.value = true
    }

    fun openSettingsLanguages() {
        _settingsPage.value = SettingsPage.LANGUAGES
        _showSettings.value = true
    }

    fun closeSettingsPage() {
        _settingsPage.value = SettingsPage.HUB
    }

    fun closeSettings() {
        _settingsPage.value = SettingsPage.HUB
        _showSettings.value = false
    }

    private enum class SettingsPage { HUB, PROFILE, LANGUAGES }

    private companion object {
        fun initialDestination(
            profileStore: ProfileStore,
            languageStore: LanguageSettingsStore,
        ): AppDestination = when {
            !profileStore.snapshot.isComplete -> AppDestination.SETUP_PROFILE
            !languageStore.snapshot.setupDone -> AppDestination.SETUP_LANGUAGES
            else -> AppDestination.MAIN
        }
    }
}
