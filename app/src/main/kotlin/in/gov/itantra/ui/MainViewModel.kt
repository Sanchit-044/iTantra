package `in`.gov.itantra.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.gov.itantra.android.transport.BluetoothTransport
import `in`.gov.itantra.android.transport.WifiDirectTransport
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.PairingInfo
import `in`.gov.itantra.core.transport.Transport
import `in`.gov.itantra.core.transport.TransportListener
import `in`.gov.itantra.core.usecase.ReceivePttTransmissionUseCase
import `in`.gov.itantra.core.usecase.StartPttTransmissionUseCase
import `in`.gov.itantra.core.usecase.StopPttTransmissionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ConnectionMode {
    WIFI_DIRECT_HOST,
    WIFI_DIRECT_CLIENT,
    BLUETOOTH_HOST,
    BLUETOOTH_CLIENT
}

data class BluetoothDeviceInfo(val name: String, val address: String)

data class UiState(
    val isSpeaking: Boolean = false,
    val recognizedText: String = "",
    val speakLanguage: Language = Language.HINDI,
    val listenLanguage: Language = Language.HINDI,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectionMode: ConnectionMode = ConnectionMode.WIFI_DIRECT_HOST,
    val pairedDevices: List<BluetoothDeviceInfo> = emptyList(),
    val pairingInfo: PairingInfo? = null,
    val error: String? = null
)

@HiltViewModel
@SuppressLint("MissingPermission")
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyAgreementProvider: KeyAgreementProvider,
    private val startPttUseCase: StartPttTransmissionUseCase,
    private val stopPttUseCase: StopPttTransmissionUseCase,
    private val receivePttUseCase: ReceivePttTransmissionUseCase
) : ViewModel(), TransportListener {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var transport: Transport? = null

    init {
        loadPairedDevices()
    }

    override fun onCleared() {
        super.onCleared()
        transport?.setListener(null)
        transport?.disconnect()
    }
    
    fun setConnectionMode(mode: ConnectionMode) {
        _uiState.update { it.copy(connectionMode = mode) }
    }

    private fun loadPairedDevices() {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            if (adapter != null && adapter.isEnabled) {
                val devices = adapter.bondedDevices.map { 
                    BluetoothDeviceInfo(it.name ?: "Unknown", it.address) 
                }
                _uiState.update { it.copy(pairedDevices = devices) }
            }
        } catch (e: Exception) {
            // Ignore missing permissions if they haven't been granted yet
        }
    }

    fun refreshPairedDevices() {
        loadPairedDevices()
    }

    // --- Transport Listener ---

    override fun onStateChanged(state: ConnectionState) {
        _uiState.update { it.copy(connectionState = state) }
    }

    override fun onPairingCodeAvailable(info: PairingInfo) {
        _uiState.update { it.copy(pairingInfo = info) }
    }

    override fun onReceive(packet: Packet) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                receivePttUseCase.execute(packet)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Playback error: ${e.message}") }
            }
        }
    }

    // --- Actions ---

    fun connect(peerAddress: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                transport?.setListener(null)
                transport?.disconnect()
                
                val newTransport = when (_uiState.value.connectionMode) {
                    ConnectionMode.WIFI_DIRECT_HOST -> WifiDirectTransport(context, keyAgreementProvider, WifiDirectTransport.Role.HOST)
                    ConnectionMode.WIFI_DIRECT_CLIENT -> WifiDirectTransport(context, keyAgreementProvider, WifiDirectTransport.Role.CLIENT)
                    ConnectionMode.BLUETOOTH_HOST -> BluetoothTransport(context, keyAgreementProvider, BluetoothTransport.Role.HOST)
                    ConnectionMode.BLUETOOTH_CLIENT -> {
                        if (peerAddress == null) throw Exception("Please select a device to connect to")
                        BluetoothTransport(context, keyAgreementProvider, BluetoothTransport.Role.CLIENT, peerAddress)
                    }
                }
                
                newTransport.setListener(this@MainViewModel)
                transport = newTransport
                
                newTransport.connect()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Connection failed: ${e.message}") }
            }
        }
    }

    fun disconnect() {
        transport?.disconnect()
    }

    fun confirmPairing() {
        transport?.confirmPairing()
        _uiState.update { it.copy(pairingInfo = null) }
    }
    
    fun dismissPairing() {
        transport?.disconnect()
        _uiState.update { it.copy(pairingInfo = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun startPtt() {
        val currentTransport = transport ?: return
        
        _uiState.update { it.copy(isSpeaking = true, recognizedText = "") }
        try {
            startPttUseCase.execute(language = _uiState.value.speakLanguage, transport = currentTransport) { partialText ->
                _uiState.update { it.copy(recognizedText = partialText) }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isSpeaking = false, recognizedText = "Error: ${e.message}") }
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
