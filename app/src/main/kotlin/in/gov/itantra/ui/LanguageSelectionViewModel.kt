package `in`.gov.itantra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.lang.LanguageSelection
import `in`.gov.itantra.core.lang.LanguageSettings
import `in`.gov.itantra.core.lang.LanguageSettingsStore
import `in`.gov.itantra.core.lang.UiLanguage
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.core.pack.LanguagePackInstallCoordinator
import `in`.gov.itantra.core.pack.LanguagePackManager
import `in`.gov.itantra.core.pack.PackProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class LanguageSetupPage { PACKS, APP_LANGUAGE }

data class LanguageSelectionUiState(
    val setupDone: Boolean = false,
    val selected: Set<Language> = setOf(Language.DEFAULT),
    val current: Language = Language.DEFAULT,
    val uiLanguage: Language = Language.ENGLISH,
    val page: LanguageSetupPage = LanguageSetupPage.PACKS,
    val busy: Boolean = false,
    val progress: PackProgress? = null,
    val error: String? = null,
    val finished: Boolean = false,
    /** Languages whose files are actually present on disk right now, per [LanguagePackManager.isLanguagePackReady]. */
    val downloaded: Set<Language> = emptySet(),
    /** Language pending a delete confirmation dialog; null when no dialog is showing. */
    val pendingDelete: Language? = null,
) {
    val appLanguageOptions: List<Language>
        get() = UiLanguage.options(selected)
}

@HiltViewModel
class LanguageSelectionViewModel @Inject constructor(
    private val store: LanguageSettingsStore,
    private val packs: LanguagePackManager,
    private val installCoordinator: LanguagePackInstallCoordinator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LanguageSelectionUiState())
    val uiState: StateFlow<LanguageSelectionUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val snap = store.settings.first()
            if (snap.setupDone) {
                apply(snap)
            } else {
                _uiState.update {
                    it.copy(
                        setupDone = false,
                        selected = setOf(Language.DEFAULT),
                        current = Language.DEFAULT,
                        uiLanguage = Language.ENGLISH,
                        page = LanguageSetupPage.PACKS,
                    )
                }
            }
            refreshDownloaded()
        }
        // Installation runs in installCoordinator's own process-lifetime scope, not
        // this ViewModel's -- it keeps going after confirm() navigates away and this
        // ViewModel is cleared. Mirror its state here so this screen still shows
        // live progress if the operator stays on it (or comes back to it) while a
        // download -- possibly kicked off from a previous visit -- is still running.
        viewModelScope.launch {
            installCoordinator.state.collect { install ->
                _uiState.update {
                    it.copy(busy = install.busy, progress = install.progress, error = install.error ?: it.error)
                }
                if (!install.busy) refreshDownloaded()
            }
        }
    }

    /** Re-checks disk state for every language; cheap (file existence checks) but still off the main thread. */
    private suspend fun refreshDownloaded() {
        val ready = withContext(Dispatchers.IO) {
            Language.entries.filter { packs.isLanguagePackReady(it) }.toSet()
        }
        _uiState.update { it.copy(downloaded = ready) }
    }

    fun toggle(language: Language) {
        _uiState.update { state ->
            val next = state.selected.toMutableSet()
            if (language in next) {
                if (next.size == 1) return@update state
                next.remove(language)
            } else {
                next.add(language)
            }
            val installed = LanguageSelection.normalizeInstalled(next)
            state.copy(
                selected = installed,
                current = LanguageSelection.normalizeCurrent(state.current, installed),
                uiLanguage = UiLanguage.normalize(state.uiLanguage, installed),
                error = null,
            )
        }
    }

    fun setCurrent(language: Language) {
        if (language !in _uiState.value.selected) return
        _uiState.update { state ->
            state.copy(
                current = LanguageSelection.normalizeCurrent(language, state.selected),
                error = null,
            )
        }
    }

    fun setUiLanguage(language: Language) {
        _uiState.update { state ->
            if (language !in state.appLanguageOptions) return@update state
            state.copy(uiLanguage = language, error = null)
        }
    }

    fun goToAppLanguage() {
        val selected = LanguageSelection.normalizeInstalled(_uiState.value.selected)
        if (selected.isEmpty()) return
        _uiState.update {
            it.copy(
                selected = selected,
                current = LanguageSelection.normalizeCurrent(it.current, selected),
                uiLanguage = UiLanguage.normalize(it.uiLanguage, selected),
                page = LanguageSetupPage.APP_LANGUAGE,
                error = null,
            )
        }
    }

    fun backToPacks() {
        _uiState.update { it.copy(page = LanguageSetupPage.PACKS, error = null) }
    }

    fun consumeFinished() {
        _uiState.update { it.copy(finished = false) }
    }

    fun reloadFromStore() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            val snap = store.settings.first()
            if (snap.setupDone) apply(snap)
            refreshDownloaded()
        }
    }

    /**
     * Persists the selection immediately and returns (via [LanguageSelectionUiState.finished])
     * right away -- the actual file download continues in [installCoordinator]'s
     * background scope, so the operator lands in the main app instead of waiting on
     * a blocking spinner. A pack the operator picked but that hasn't finished
     * downloading yet just isn't [LanguageSelectionUiState.downloaded] until it has;
     * the rest of the app already tolerates a selected-but-not-ready language (see
     * LocalLanguagePackManager's `.ready` marker and MainViewModel's language-id
     * fallback), so this is not a new failure mode, just a longer window for it.
     */
    fun confirm() {
        val selected = LanguageSelection.normalizeInstalled(_uiState.value.selected)
        val current = LanguageSelection.normalizeCurrent(_uiState.value.current, selected)
        val uiLanguage = UiLanguage.normalize(_uiState.value.uiLanguage, selected)
        viewModelScope.launch {
            try {
                val previous = store.settings.first().installed
                val removed = previous - selected
                if (_uiState.value.setupDone) {
                    store.updateSelection(selected, current, uiLanguage)
                } else {
                    store.completeSetup(selected, current, uiLanguage)
                }
                installCoordinator.install(selected, removed, includeTranslation = true)
                _uiState.update { it.copy(finished = true) }
            } catch (e: Exception) {
                val chromeLang = if (_uiState.value.setupDone) {
                    _uiState.value.uiLanguage
                } else {
                    Language.ENGLISH
                }
                _uiState.update {
                    it.copy(
                        error = e.message?.takeIf { msg -> msg.isNotBlank() }
                            ?: UiStrings.forLanguage(chromeLang).couldNotSave,
                    )
                }
            }
        }
    }

    /** Opens the delete-confirmation dialog for [language]; only meaningful for a downloaded pack. */
    fun requestDelete(language: Language) {
        _uiState.update { it.copy(pendingDelete = language) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(pendingDelete = null) }
    }

    /** Deletes the pending language's files from disk and drops it from the selection. */
    fun confirmDelete() {
        val language = _uiState.value.pendingDelete ?: return
        _uiState.update { it.copy(pendingDelete = null) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { packs.uninstall(language) }
            val updated = _uiState.updateAndGet { state ->
                val next = state.selected.toMutableSet()
                if (next.size > 1) next.remove(language)
                val normalized = LanguageSelection.normalizeInstalled(next)
                val current = LanguageSelection.normalizeCurrent(state.current, normalized)
                val uiLanguage = UiLanguage.normalize(state.uiLanguage, normalized)
                state.copy(selected = normalized, current = current, uiLanguage = uiLanguage)
            }
            if (updated.setupDone) {
                store.updateSelection(updated.selected, updated.current, updated.uiLanguage)
            }
            refreshDownloaded()
        }
    }

    private fun apply(snap: LanguageSettings) {
        val normalized = snap.normalized
        _uiState.update {
            it.copy(
                setupDone = normalized.setupDone,
                selected = normalized.installed,
                current = normalized.current,
                uiLanguage = normalized.uiLanguage,
                page = LanguageSetupPage.PACKS,
            )
        }
    }
}
