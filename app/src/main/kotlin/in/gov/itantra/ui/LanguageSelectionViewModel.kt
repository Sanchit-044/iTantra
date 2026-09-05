package `in`.gov.itantra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.lang.LanguageSelection
import `in`.gov.itantra.core.lang.LanguageSettings
import `in`.gov.itantra.core.lang.LanguageSettingsStore
import `in`.gov.itantra.core.pack.LanguagePackManager
import `in`.gov.itantra.core.pack.PackProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LanguageSelectionUiState(
    val setupDone: Boolean = false,
    val selected: Set<Language> = setOf(Language.DEFAULT),
    val current: Language = Language.DEFAULT,
    val busy: Boolean = false,
    val progress: PackProgress? = null,
    val error: String? = null,
    val finished: Boolean = false,
)

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
                    )
                }
            }
        }
    }

    fun toggle(language: Language) {
        _uiState.update { state ->
            val next = state.selected.toMutableSet()
            if (language in next) next.remove(language) else next.add(language)
            val installed = LanguageSelection.normalizeInstalled(next)
            state.copy(
                selected = installed,
                current = LanguageSelection.normalizeCurrent(state.current, installed),
                error = null,
            )
        }
    }

    fun setCurrent(language: Language) {
        _uiState.update { state ->
            val installed = if (language in state.selected) state.selected else state.selected + language
            state.copy(
                selected = LanguageSelection.normalizeInstalled(installed),
                current = language,
                error = null,
            )
        }
    }

    fun consumeFinished() {
        _uiState.update { it.copy(finished = false) }
    }

    fun confirm() {
        val selected = LanguageSelection.normalizeInstalled(_uiState.value.selected)
        val current = LanguageSelection.normalizeCurrent(_uiState.value.current, selected)
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
                    store.updateSelection(selected, current)
                } else {
                    store.completeSetup(selected, current)
                }
                _uiState.update { it.copy(busy = false, finished = true, progress = null) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(busy = false, error = e.message ?: "Could not save languages", progress = null)
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
            )
        }
    }
}
