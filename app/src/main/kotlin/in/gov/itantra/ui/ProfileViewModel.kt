package `in`.gov.itantra.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.gov.itantra.core.profile.ProfileRules
import `in`.gov.itantra.profile.FileProfileStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val name: String = "",
    val photoPath: String? = null,
    val photoPresent: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    val finished: Boolean = false,
) {
    val canContinue: Boolean
        get() = ProfileRules.isComplete(name, photoPresent) && !busy
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val store: FileProfileStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        val snap = store.snapshot
        _uiState.update {
            it.copy(
                name = snap.name,
                photoPath = store.photoPath(),
                photoPresent = snap.photoPresent,
                error = null,
                finished = false,
            )
        }
    }

    fun setName(value: String) {
        _uiState.update {
            it.copy(name = value.take(ProfileRules.MAX_NAME_CHARS), error = null)
        }
    }

    fun importPhoto(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                store.writePhotoFromUri(uri)
                _uiState.update {
                    it.copy(
                        busy = false,
                        photoPath = store.photoPath(),
                        photoPresent = store.snapshot.photoPresent,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        error = e.message?.takeIf { msg -> msg.isNotBlank() } ?: "Could not save photo",
                    )
                }
            }
        }
    }

    fun consumeFinished() {
        _uiState.update { it.copy(finished = false) }
    }

    fun confirm() {
        val name = ProfileRules.normalizeName(_uiState.value.name)
        if (!ProfileRules.isComplete(name, store.snapshot.photoPresent)) {
            _uiState.update { it.copy(error = "Name and photo are required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                store.save(name)
                _uiState.update { it.copy(busy = false, finished = true, name = name) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        busy = false,
                        error = e.message?.takeIf { msg -> msg.isNotBlank() } ?: "Could not save profile",
                    )
                }
            }
        }
    }
}
