package `in`.gov.itantra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.gov.itantra.core.lang.LanguageSettingsStore
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.core.profile.ProfileStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

object AppRoutes {
    const val SETUP_PROFILE = "setup_profile"
    const val SETUP_LANGUAGES = "setup_languages"
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val SETTINGS_PROFILE = "settings_profile"
    const val HISTORY = "history"
}

@HiltViewModel
class AppViewModel @Inject constructor(
    languageStore: LanguageSettingsStore,
    profileStore: ProfileStore,
) : ViewModel() {

    val initialRoute: String = initialDestination(profileStore, languageStore)

    /**
     * App chrome for the whole NavHost. Screens outside [MainContent] -- History and
     * Profile -- have no MainViewModel to read the UI language from, and hardcoding
     * their text was why they stayed English while the tabs beside them translated.
     */
    val strings: StateFlow<UiStrings> = languageStore.settings
        .map { UiStrings.forLanguage(it.normalized.uiLanguage) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiStrings.forLanguage(languageStore.snapshot.normalized.uiLanguage),
        )

    private companion object {
        fun initialDestination(
            profileStore: ProfileStore,
            languageStore: LanguageSettingsStore,
        ): String = when {
            !profileStore.snapshot.isComplete -> AppRoutes.SETUP_PROFILE
            !languageStore.snapshot.setupDone -> AppRoutes.SETUP_LANGUAGES
            else -> AppRoutes.MAIN
        }
    }
}
