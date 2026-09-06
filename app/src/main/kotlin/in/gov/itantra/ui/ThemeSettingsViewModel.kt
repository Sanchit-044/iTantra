package `in`.gov.itantra.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.gov.itantra.core.theme.ThemeMode
import `in`.gov.itantra.core.theme.ThemeStore
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeSettingsViewModel @Inject constructor(
    private val store: ThemeStore,
) : ViewModel() {

    val mode: StateFlow<ThemeMode> = store.mode

    fun setMode(mode: ThemeMode) {
        if (mode == store.snapshot) return
        viewModelScope.launch { store.setMode(mode) }
    }
}
