package `in`.gov.itantra.core.pack

import `in`.gov.itantra.core.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LanguageInstallState(
    val busy: Boolean = false,
    val progress: PackProgress? = null,
    val error: String? = null,
)

/**
 * Runs language-pack installation in a process-lifetime scope rather than a
 * screen's ViewModel scope, so the operator can hit Save on Language Setup or
 * Settings and immediately start using Talk / Alert / Radar while the actual
 * files keep downloading in the background. A `viewModelScope` coroutine would be
 * cancelled the instant its screen leaves the back stack -- LanguageSelectionScreen
 * and SettingsScreen both navigate away right after persisting the selection now,
 * so something with a longer lifetime has to own the download itself.
 *
 * [LanguageSelectionViewModel] and [in.gov.itantra.ui.MainViewModel] both observe
 * [state]: the former to show progress if the operator stays on that screen, the
 * latter to show a small persistent banner from anywhere else in the app.
 */
class LanguagePackInstallCoordinator(
    private val packs: LanguagePackManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(LanguageInstallState())
    val state: StateFlow<LanguageInstallState> = _state.asStateFlow()

    @Volatile
    private var job: Job? = null

    /**
     * Installs [languages] and uninstalls [remove] in the background; returns
     * immediately without waiting for either to finish. A call made while a
     * previous one is still running cancels it first -- the operator's latest
     * selection wins rather than racing with an older one.
     */
    fun install(languages: Set<Language>, remove: Set<Language>, includeTranslation: Boolean = true) {
        job?.cancel()
        job = scope.launch {
            _state.value = LanguageInstallState(busy = true)
            try {
                packs.install(languages, includeTranslation) { progress ->
                    _state.update { it.copy(progress = progress) }
                }
                for (language in remove) {
                    packs.uninstall(language)
                }
                _state.value = LanguageInstallState(busy = false)
            } catch (e: Exception) {
                _state.value = LanguageInstallState(
                    busy = false,
                    error = e.message?.takeIf { msg -> msg.isNotBlank() } ?: "Could not download language packs",
                )
            }
        }
    }

    /** Dismisses a past failure without starting a new install. */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
