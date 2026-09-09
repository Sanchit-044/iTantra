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
import `in`.gov.itantra.core.pack.LanguagePackManager
import `in`.gov.itantra.core.pack.PackState
import `in`.gov.itantra.core.pack.PackProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    /**
     * Selected languages whose weights are not on disk. Their selection is saved,
     * but STT and TTS cannot run for them, and the screen says so rather than
     * letting the first PTT press fail with a decoder error.
     */
    val placeholders: Set<Language> = emptySet(),
) {
    val appLanguageOptions: List<Language>
        get() = UiLanguage.options(selected)
}

@HiltViewModel
class LanguageSelectionViewModel @Inject constructor(
    private val store: LanguageSettingsStore,
    private val packs: LanguagePackManager,
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
        }
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
        }
    }

    fun confirm() {
        val selected = LanguageSelection.normalizeInstalled(_uiState.value.selected)
        val current = LanguageSelection.normalizeCurrent(_uiState.value.current, selected)
        val uiLanguage = UiLanguage.normalize(_uiState.value.uiLanguage, selected)
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                val previous = store.settings.first().installed
                val removed = previous - selected
                packs.install(selected, includeTranslation = true) { progress ->
                    _uiState.update { it.copy(progress = progress) }
                }
                for (language in removed) {
                    packs.uninstall(language)
                }
                if (_uiState.value.setupDone) {
                    store.updateSelection(selected, current, uiLanguage)
                } else {
                    store.completeSetup(selected, current, uiLanguage)
                }
                _uiState.update {
                    it.copy(
                        busy = false,
                        finished = true,
                        progress = null,
                        placeholders = placeholdersAmong(selected),
                    )
                }
            } catch (e: Exception) {
                // Match whatever the screen is currently rendering in.
                val chromeLang = if (_uiState.value.setupDone) {
                    _uiState.value.uiLanguage
                } else {
                    _uiState.value.current
                }
                _uiState.update {
                    it.copy(
                        busy = false,
                        error = e.message?.takeIf { msg -> msg.isNotBlank() }
                            ?: UiStrings.forLanguage(chromeLang).couldNotSave,
                        progress = null,
                    )
                }
            }
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
                placeholders = placeholdersAmong(normalized.installed),
            )
        }
    }

    /** Selected languages the pack manager reports as recorded but not usable. */
    private fun placeholdersAmong(selected: Set<Language>): Set<Language> =
        selected.filterTo(mutableSetOf()) { packs.packState(it) == PackState.PLACEHOLDER }
}
