package `in`.gov.itantra.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.gov.itantra.android.diag.AndroidDiagnosticsService
import `in`.gov.itantra.android.notify.QueuedMessageNotifier
import `in`.gov.itantra.core.diag.AppLog
import `in`.gov.itantra.android.transport.BluetoothTransport
import `in`.gov.itantra.android.transport.LanTransport
import `in`.gov.itantra.android.transport.StreamTransport
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
import `in`.gov.itantra.core.queue.InboxMessage
import `in`.gov.itantra.core.queue.InboundMessageInbox
import `in`.gov.itantra.core.queue.OutboundMessageQueue
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.PairingInfo
import `in`.gov.itantra.core.transport.Transport
import `in`.gov.itantra.core.transport.TransportListener
import `in`.gov.itantra.core.usecase.FlushQueuedMessagesUseCase
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
    val error: String? = null,
    val outboundPending: Int = 0,
    val outboundFailed: Int = 0,
    val inbox: List<InboxMessage> = emptyList(),
    val playingInboxId: String? = null,
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
    private val flushQueuedUseCase: FlushQueuedMessagesUseCase,
    private val outboundQueue: OutboundMessageQueue,
    private val inbox: InboundMessageInbox,
    private val notifier: QueuedMessageNotifier,
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
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        startPttUseCase.preload(snap.current)
                        receivePttUseCase.preload(snap.current)
                    } catch (e: Exception) {
                        AppLog.w("MainViewModel", "Failed to preload models: ${e.message}")
                    }
                }
            }
        }
        outboundQueue.purgeExpired()
        inbox.purgeExpired()
        publishQueues(notifyIfUnread = true)
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
        } catch (_: Exception) {
            // Permissions may not be granted yet.
        }
    }

    fun refreshPairedDevices() {
        loadPairedDevices()
    }

    // --- Transport Listener ---
    override fun onStateChanged(state: ConnectionState) {
        AppLog.d("MainViewModel", "Transport state changed to: $state")
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
        AppLog.d("MainViewModel", "Floor granted, starting PTT")
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
                        _uiState.update { it.copy(recognizedText = finalText) }
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
        AppLog.w("MainViewModel", "Floor denied: $reason")
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
        _uiState.update { it.copy(pairingInfo = info, pairingConfirmed = false) }
    }

    override fun onReceive(packet: Packet) {
        AppLog.d("MainViewModel", "Received packet: type=${packet.type}, language=${packet.language}")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (packet.type) {
                    MessageType.QUEUED -> handleQueuedInbound(packet)
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

    override fun onSendFailed(packet: Packet, reason: String) {
        if (packet.type != MessageType.QUEUED) return
        val existing = outboundQueue.snapshot().firstOrNull {
            it.text == packet.text &&
                it.language == packet.language &&
                it.createdAtMs == packet.timestampMs
        }
        if (existing != null) outboundQueue.markFailed(existing.id)
        else outboundQueue.enqueue(packet.language, packet.text)
        publishQueues()
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
                // Pass a 2-minute timeout since P2P setup involves manual user discovery and pairing
                newTransport.connect(120_000)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Connection failed: ${e.message}") }
            }
        }
    }

    fun disconnect() {
        transport?.disconnect()
        // Keep the last transport attached so session counters remain visible.
        _uiState.update { it.copy(pairingConfirmed = false, pairingInfo = null) }
    }

    fun confirmPairing() {
        val tx = transport ?: return
        try {
            tx.confirmPairing()
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Pairing failed: ${e.message}") }
            return
        }
        _uiState.update { it.copy(pairingInfo = null, pairingConfirmed = true) }
        flushQueue()
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
        val state = _uiState.value
        val isConnected = state.connectionState == ConnectionState.CONNECTED
        if (isConnected) {
            if (state.channelBusy) {
                _uiState.update { it.copy(recognizedText = "Channel busy") }
                return
            }
            if (state.isSpeaking || state.isRequestingFloor) return
            AppLog.d("MainViewModel", "startPtt: Requesting floor for live PTT")
            pttWanted.set(true)
            _uiState.update {
                it.copy(isRequestingFloor = true, recognizedText = "", error = null)
            }
            transport?.requestFloor()
        } else {
            if (state.isSpeaking) return
            AppLog.d("MainViewModel", "startPtt: Starting queued offline PTT")
            _uiState.update { it.copy(isSpeaking = true, recognizedText = "", error = null) }
            try {
                startPttUseCase.execute(
                    language = state.currentLanguage,
                    transport = null,
                    sendLive = false,
                    onPartialResult = { partial ->
                        _uiState.update { it.copy(recognizedText = partial) }
                    },
                    onFinalResult = { finalText ->
                        _uiState.update { it.copy(recognizedText = finalText) }
                    },
                    onQueued = { publishQueues() },
                    onError = { message ->
                        _uiState.update { it.copy(isSpeaking = false, recognizedText = "Error: $message") }
                    }
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(isSpeaking = false, recognizedText = "Error: ${e.message}") }
            }
        }
    }

    fun stopPtt() {
        AppLog.d("MainViewModel", "stopPtt: Stopping PTT")
        pttWanted.set(false)
        stopPttUseCase.execute(transport)
        _uiState.update {
            it.copy(isSpeaking = false, isRequestingFloor = false, recognizedText = it.recognizedText)
        }
        if (isLiveReady()) flushQueue()
    }

    fun playInbox(id: String) {
        val item = inbox.find(id) ?: return
        if (_uiState.value.playingInboxId != null) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(playingInboxId = id, error = null) }
            try {
                receivePttUseCase.execute(
                    Packet.text(MessageType.NORMAL, item.language, 0, item.text),
                    _uiState.value.currentLanguage
                )
                inbox.markRead(id)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Playback error: ${e.message}") }
            } finally {
                _uiState.update { it.copy(playingInboxId = null) }
                publishQueues()
            }
        }
    }

    fun dismissInbox(id: String) {
        inbox.discard(id)
        publishQueues()
    }
    companion object {
        private const val MAX_DEFERRED_NORMAL = 8
    }

    private fun handleQueuedInbound(packet: Packet) {
        val id = inboundId(packet)
        val stored = inbox.offer(id, packet.language, packet.text, packet.timestampMs)
        publishQueues()
        if (stored != null) {
            notifier.notifyUnread(inbox.unreadCount(), stored.text)
        }
    }

    private fun flushQueue() {
        val tx = transport ?: return
        if (!isLiveReady()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                flushQueuedUseCase.execute(tx, pairingConfirmed = true)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Queue send failed: ${e.message}") }
            } finally {
                publishQueues()
            }
        }
    }

    private fun isLiveReady(): Boolean {
        val tx = transport ?: return false
        if (tx.state != ConnectionState.CONNECTED) return false
        if (!_uiState.value.pairingConfirmed) return false
        val stream = tx as? StreamTransport
        return stream == null || stream.isPairingConfirmed
    }

    private fun publishQueues(notifyIfUnread: Boolean = false) {
        val unread = inbox.unreadCount()
        if (unread == 0) notifier.cancel()
        else if (notifyIfUnread) {
            val preview = inbox.snapshot().firstOrNull { it.unread }?.text.orEmpty()
            notifier.notifyUnread(unread, preview)
        }
        _uiState.update {
            it.copy(
                outboundPending = outboundQueue.pendingCount(),
                outboundFailed = outboundQueue.failedCount(),
                inbox = inbox.snapshot(),
            )
        }
    }

    private fun inboundId(packet: Packet): String =
        "${packet.timestampMs}:${packet.sequence}:${packet.text.hashCode()}"
}
