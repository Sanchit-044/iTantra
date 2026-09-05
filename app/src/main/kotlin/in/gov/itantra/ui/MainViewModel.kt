package `in`.gov.itantra.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.gov.itantra.android.diag.AndroidDiagnosticsService
import `in`.gov.itantra.android.transport.BluetoothTransport
import `in`.gov.itantra.android.transport.LanTransport
import `in`.gov.itantra.android.transport.WifiDirectTransport
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertContent
import `in`.gov.itantra.core.alert.AlertPlayer
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.alert.IncomingAlert
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import `in`.gov.itantra.core.lang.LanguageSettingsStore
import `in`.gov.itantra.core.stt.LanguageIdEngine
import `in`.gov.itantra.core.stt.resolveSpokenLanguage
import `in`.gov.itantra.core.translate.TranslationUnavailableException
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.PairingInfo
import `in`.gov.itantra.core.transport.Transport
import `in`.gov.itantra.core.transport.TransportListener
import `in`.gov.itantra.core.usecase.ReceivePttTransmissionUseCase
import `in`.gov.itantra.core.usecase.SendAlertUseCase
import `in`.gov.itantra.core.usecase.StartPttTransmissionUseCase
import `in`.gov.itantra.core.usecase.StopPttTransmissionUseCase
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
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
    val isRequestingFloor: Boolean = false,
    val channelBusy: Boolean = false,
    val recognizedText: String = "",
    val currentLanguage: Language = Language.DEFAULT,
    val installedLanguages: Set<Language> = setOf(Language.DEFAULT),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectionMode: ConnectionMode = ConnectionMode.WIFI_DIRECT_HOST,
    val pairedDevices: List<BluetoothDeviceInfo> = emptyList(),
    val pairingInfo: PairingInfo? = null,
    val pairingConfirmed: Boolean = false,
    val selectedDeviceAddress: String? = null,
    val alertSending: Boolean = false,
    val alertStatus: String? = null,
    val error: String? = null
)

@HiltViewModel
@SuppressLint("MissingPermission")
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyAgreementProvider: KeyAgreementProvider,
    private val startPttUseCase: StartPttTransmissionUseCase,
    private val stopPttUseCase: StopPttTransmissionUseCase,
    private val receivePttUseCase: ReceivePttTransmissionUseCase,
    private val languageSettings: LanguageSettingsStore,
    private val languageIdEngine: LanguageIdEngine,
    private val sendAlertUseCase: SendAlertUseCase,
    private val alertPlayer: AlertPlayer,
    private val diagnostics: AndroidDiagnosticsService,
) : ViewModel(), TransportListener {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var transport: Transport? = null
    private val deferredNormals = ArrayDeque<Packet>()
    private val pttWanted = AtomicBoolean(false)

    init {
        loadPairedDevices()
        viewModelScope.launch {
            languageSettings.settings.collect { snap ->
                _uiState.update {
                    it.copy(
                        currentLanguage = snap.current,
                        installedLanguages = snap.installed,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        transport?.setListener(null)
        transport?.disconnect()
        if (diagnostics.attachedTransport === transport) {
            diagnostics.attachedTransport = null
        }
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
        _uiState.update {
            val stillLinked = state == ConnectionState.CONNECTED || state == ConnectionState.HANDSHAKING
            it.copy(
                connectionState = state,
                pairingConfirmed = if (stillLinked) it.pairingConfirmed else false,
                pairingInfo = if (stillLinked) it.pairingInfo else null,
            )
        }
        if (state == ConnectionState.DISCONNECTED || state == ConnectionState.FAILED) {
            stopPtt()
            _uiState.update {
                it.copy(channelBusy = false, isRequestingFloor = false, isSpeaking = false)
            }
        }
    }

    override fun onChannelBusyChanged(busy: Boolean) {
        _uiState.update { it.copy(channelBusy = busy) }
    }

    override fun onFloorGranted() {
        viewModelScope.launch(Dispatchers.Main) {
            val currentTransport = transport ?: return@launch
            if (!pttWanted.get()) {
                currentTransport.releaseFloor()
                return@launch
            }

            val snap = _uiState.value
            val spoken = languageIdEngine.resolveSpokenLanguage(
                installed = snap.installedLanguages,
                current = snap.currentLanguage,
            )
            if (spoken != snap.currentLanguage) {
                viewModelScope.launch { languageSettings.setCurrentLanguage(spoken) }
            }

            diagnostics.currentLanguage = spoken

            _uiState.update {
                it.copy(
                    isSpeaking = true,
                    isRequestingFloor = false,
                    channelBusy = false,
                    recognizedText = "",
                    currentLanguage = spoken,
                    error = null,
                )
            }
            try {
                startPttUseCase.execute(
                    language = spoken,
                    transport = currentTransport,
                    onPartialResult = { partialText ->
                        _uiState.update { it.copy(recognizedText = partialText) }
                    },
                    onFinalResult = { finalText ->
                        val refined = languageIdEngine.detectFromText(finalText, snap.installedLanguages)
                        if (refined != null && refined != spoken) {
                            viewModelScope.launch { languageSettings.setCurrentLanguage(refined) }
                        }
                    },
                )
            } catch (e: Exception) {
                stopPttUseCase.execute(currentTransport)
                _uiState.update {
                    it.copy(isSpeaking = false, recognizedText = "Error: ${e.message}")
                }
            }
        }
    }

    override fun onFloorDenied(reason: String) {
        pttWanted.set(false)
        _uiState.update {
            it.copy(
                isSpeaking = false,
                isRequestingFloor = false,
                channelBusy = reason.contains("busy", ignoreCase = true),
                recognizedText = if (reason.contains("busy", ignoreCase = true)) {
                    "Channel busy"
                } else {
                    ""
                },
                error = null,
            )
        }
    }

    override fun onPairingCodeAvailable(info: PairingInfo) {
        _uiState.update { it.copy(pairingInfo = info) }
    }

    override fun onReceive(packet: Packet) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (packet.type) {
                    MessageType.ALERT -> {
                        alertPlayer.play(
                            IncomingAlert(
                                content = AlertTemplate.fromWirePayload(packet.text),
                                language = packet.language,
                                sequence = packet.sequence,
                                receivedAtMs = System.currentTimeMillis(),
                            )
                        )
                        drainDeferredNormals()
                    }
                    MessageType.NORMAL -> playNormalOrDefer(packet)
                    else -> Unit
                }
            } catch (e: TranslationUnavailableException) {
                _uiState.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Playback error: ${e.message}") }
            }
        }
    }

    private suspend fun playNormalOrDefer(packet: Packet) {
        val accepted = alertPlayer.tryPlayNormal(packet.sequence) { }
        if (accepted) {
            receivePttUseCase.execute(packet, _uiState.value.currentLanguage)
        } else {
            synchronized(deferredNormals) {
                if (deferredNormals.size < MAX_DEFERRED_NORMAL) {
                    deferredNormals.addLast(packet)
                }
            }
        }
    }

    private suspend fun drainDeferredNormals() {
        while (true) {
            val next = synchronized(deferredNormals) { deferredNormals.pollFirst() } ?: break
            val accepted = alertPlayer.tryPlayNormal(next.sequence) { }
            if (!accepted) {
                synchronized(deferredNormals) { deferredNormals.addFirst(next) }
                break
            }
            receivePttUseCase.execute(next, _uiState.value.currentLanguage)
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
                        val address = peerAddress ?: _uiState.value.selectedDeviceAddress
                        if (address == null) throw Exception("Please select a device to connect to")
                        BluetoothTransport(context, keyAgreementProvider, BluetoothTransport.Role.CLIENT, address)
                    }

                }

                newTransport.setListener(this@MainViewModel)
                transport = newTransport
                _uiState.update { it.copy(pairingConfirmed = false, error = null) }
                diagnostics.attachedTransport = newTransport
                diagnostics.currentLanguage = _uiState.value.currentLanguage
                newTransport.connect()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Connection failed: ${e.message}") }
            }
        }
    }

    fun disconnect() {
        transport?.disconnect()
        // Keep the last transport attached so session counters remain visible.
    }

    fun confirmPairing() {
        transport?.confirmPairing()
        _uiState.update { it.copy(pairingInfo = null, pairingConfirmed = true) }
    }

    fun dismissPairing() {
        transport?.disconnect()
        _uiState.update { it.copy(pairingInfo = null, pairingConfirmed = false) }
    }

    fun selectDevice(address: String) {
        _uiState.update { it.copy(selectedDeviceAddress = address) }
    }

    fun sendAlertTemplate(template: AlertTemplate) {
        sendAlert(AlertContent.Template(template))
    }

    fun sendCustomAlert(text: String) {
        sendAlert(AlertContent.Custom(text))
    }

    private fun sendAlert(content: AlertContent) {
        val currentTransport = transport ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(alertSending = true, error = null, alertStatus = null) }
            try {
                sendAlertUseCase.execute(
                    transport = currentTransport,
                    language = _uiState.value.currentLanguage,
                    content = content,
                    pairingConfirmed = _uiState.value.pairingConfirmed,
                )
                _uiState.update { it.copy(alertSending = false, alertStatus = "Alert sent") }
            } catch (e: Exception) {
                _uiState.update { it.copy(alertSending = false, error = e.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun startPtt() {
        val currentTransport = transport ?: return
        val state = _uiState.value
        if (state.connectionState != ConnectionState.CONNECTED) return
        if (state.channelBusy) {
            _uiState.update { it.copy(recognizedText = "Channel busy") }
            return
        }
        if (state.isSpeaking || state.isRequestingFloor) return
        pttWanted.set(true)
        _uiState.update {
            it.copy(isRequestingFloor = true, recognizedText = "", error = null)
        }
        currentTransport.requestFloor()
    }

    fun stopPtt() {
        pttWanted.set(false)
        stopPttUseCase.execute(transport)
        _uiState.update {
            it.copy(isSpeaking = false, isRequestingFloor = false, recognizedText = it.recognizedText)
        }
    }
    companion object {
        private const val MAX_DEFERRED_NORMAL = 8
    }
}
