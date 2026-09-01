package `in`.gov.itantra.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.usecase.StartPttTransmissionUseCase
import `in`.gov.itantra.core.usecase.StopPttTransmissionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class UiState(
    val isSpeaking: Boolean = false,
    val recognizedText: String = "",
    val speakLanguage: Language = Language.HINDI,
    val listenLanguage: Language = Language.HINDI
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val startPttUseCase: StartPttTransmissionUseCase,
    private val stopPttUseCase: StopPttTransmissionUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun startPtt() {
        _uiState.update { it.copy(isSpeaking = true, recognizedText = "") }
        startPttUseCase.execute(language = _uiState.value.speakLanguage) { partialText ->
            _uiState.update { it.copy(recognizedText = partialText) }
        }
    }

    fun stopPtt() {
        stopPttUseCase.execute()
        _uiState.update { it.copy(isSpeaking = false) }
    }

    fun setSpeakLanguage(language: Language) {
        _uiState.update { it.copy(speakLanguage = language) }
    }

    fun setListenLanguage(language: Language) {
        _uiState.update { it.copy(listenLanguage = language) }
    }
}
