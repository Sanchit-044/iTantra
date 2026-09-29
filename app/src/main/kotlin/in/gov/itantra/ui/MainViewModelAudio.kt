package `in`.gov.itantra.ui

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.lifecycle.viewModelScope
import `in`.gov.itantra.core.diag.AppLog
import `in`.gov.itantra.core.transport.ConnectionState
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

fun MainViewModel.setAudioOutputDevice(device: AudioOutputDevice) {
    _uiState.update { it.copy(audioOutputDevice = device) }
    try {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (device) {
            AudioOutputDevice.SPEAKER -> {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                    audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }?.let {
                        audioManager.setCommunicationDevice(it)
                    }
                }
            }
            AudioOutputDevice.EARPIECE -> {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                    audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }?.let {
                        audioManager.setCommunicationDevice(it)
                    }
                }
            }
            AudioOutputDevice.BLUETOOTH -> {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.clearCommunicationDevice()
                    audioManager.availableCommunicationDevices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }?.let {
                        audioManager.setCommunicationDevice(it)
                    }
                }
            }
        }
    } catch (e: Exception) {
        AppLog.w("MainViewModel", "Could not route audio: ${e.message}")
    }
}

fun MainViewModel.toggleAudioOutputDevice() {
    val current = uiState.value.audioOutputDevice
    val next = when (current) {
        AudioOutputDevice.SPEAKER -> AudioOutputDevice.EARPIECE
        AudioOutputDevice.EARPIECE -> AudioOutputDevice.SPEAKER
        AudioOutputDevice.BLUETOOTH -> AudioOutputDevice.SPEAKER
    }
    setAudioOutputDevice(next)
}

fun MainViewModel.scheduleRecognizedTextClear(delayMs: Long = 4_000L) {
    clearRecognizedTextJob?.cancel()
    clearRecognizedTextJob = viewModelScope.launch {
        delay(delayMs)
        _uiState.update { current ->
            if (!current.isSpeaking && !current.isRequestingFloor) {
                current.copy(recognizedText = "")
            } else {
                current
            }
        }
    }
}

fun MainViewModel.startPtt() {
    val state = uiState.value
    if (state.isRecordingAlertMessage) return
    val isConnected = state.connectionState == ConnectionState.CONNECTED
    if (isConnected) {
        if (state.channelBusy) {
            return
        }
        if (state.isSpeaking || state.isRequestingFloor) return
        AppLog.d("MainViewModel", "startPtt: Requesting floor for live PTT")
        clearRecognizedTextJob?.cancel()
        pttWanted.set(true)
        _uiState.update {
            it.copy(isRequestingFloor = true, recognizedText = "", notice = null)
        }
        transport?.requestFloor()
    } else {
        if (state.isSpeaking) return
        AppLog.d("MainViewModel", "startPtt: Starting queued offline PTT")
        clearRecognizedTextJob?.cancel()
        _uiState.update { it.copy(isSpeaking = true, recognizedText = "", notice = null) }
        try {
            startPttUseCase.execute(
                language = state.currentLanguage,
                transport = null,
                sendLive = false,
                onPartialResult = { partial ->
                    clearRecognizedTextJob?.cancel()
                    _uiState.update { it.copy(recognizedText = partial) }
                },
                onFinalResult = { finalText ->
                    _uiState.update { it.copy(recognizedText = finalText) }
                    scheduleRecognizedTextClear()
                },
                onQueued = { publishQueues() },
                onError = { message ->
                    _uiState.update {
                        it.copy(isSpeaking = false, notice = UserNotice.GenericError(message))
                    }
                }
            )
        } catch (e: Exception) {
            _uiState.update {
                it.copy(isSpeaking = false, notice = UserNotice.GenericError(e.message))
            }
        }
    }
}

fun MainViewModel.stopPtt() {
    AppLog.d("MainViewModel", "stopPtt: Stopping PTT")
    pttWanted.set(false)
    stopPttUseCase.execute(transport)
    _uiState.update {
        it.copy(isSpeaking = false, isRequestingFloor = false, recognizedText = it.recognizedText)
    }
    scheduleRecognizedTextClear()
    if (isLiveReady()) flushQueue()
}
