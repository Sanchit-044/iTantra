package `in`.gov.itantra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.gov.itantra.core.lang.LanguageSettingsStore
import `in`.gov.itantra.core.profile.ProfileStore
import javax.inject.Inject

object AppRoutes {
    const val SETUP_PROFILE = "setup_profile"
    const val SETUP_LANGUAGES = "setup_languages"
    const val MAIN = "main"
    const val SETTINGS = "settings"
    const val SETTINGS_PROFILE = "settings_profile"
}

@HiltViewModel
class AppViewModel @Inject constructor(
    languageStore: LanguageSettingsStore,
    profileStore: ProfileStore,
) : ViewModel() {

    val initialRoute: String = initialDestination(profileStore, languageStore)

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
