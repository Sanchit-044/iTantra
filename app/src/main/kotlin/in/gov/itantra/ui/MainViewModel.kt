package `in`.gov.itantra.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.gov.itantra.android.notify.QueuedMessageNotifier
import `in`.gov.itantra.android.transport.BluetoothTransport
import `in`.gov.itantra.android.transport.StreamTransport
import `in`.gov.itantra.android.transport.WifiDirectTransport
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
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
    val pairingConfirmed: Boolean = false,
    val outboundPending: Int = 0,
    val outboundFailed: Int = 0,
    val inbox: List<InboxMessage> = emptyList(),
    val playingInboxId: String? = null,
    val error: String? = null,
)

@HiltViewModel
@SuppressLint("MissingPermission")
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyAgreementProvider: KeyAgreementProvider,
    private val startPttUseCase: StartPttTransmissionUseCase,
    private val stopPttUseCase: StopPttTransmissionUseCase,
    private val receivePttUseCase: ReceivePttTransmissionUseCase,
    private val flushQueuedUseCase: FlushQueuedMessagesUseCase,
    private val outboundQueue: OutboundMessageQueue,
    private val inbox: InboundMessageInbox,
    private val notifier: QueuedMessageNotifier,
) : ViewModel(), TransportListener {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var transport: Transport? = null

    init {
        loadPairedDevices()
        outboundQueue.purgeExpired()
        inbox.purgeExpired()
        publishQueues(notifyIfUnread = true)
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
        } catch (_: Exception) {
            // Permissions may not be granted yet.
        }
    }

    fun refreshPairedDevices() {
        loadPairedDevices()
    }

    override fun onStateChanged(state: ConnectionState) {
        _uiState.update {
            it.copy(
                connectionState = state,
                pairingConfirmed = if (state == ConnectionState.CONNECTED) it.pairingConfirmed else false,
            )
        }
        if (state != ConnectionState.CONNECTED) {
            // Do not flush; leftover items stay queued for the next confirmed session.
        }
    }

    override fun onPairingCodeAvailable(info: PairingInfo) {
        _uiState.update { it.copy(pairingInfo = info, pairingConfirmed = false) }
    }

    override fun onReceive(packet: Packet) {
        when (packet.type) {
            MessageType.QUEUED -> handleQueuedInbound(packet)
            MessageType.NORMAL -> viewModelScope.launch(Dispatchers.IO) {
                try {
                    receivePttUseCase.execute(packet)
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = "Playback error: ${e.message}") }
                }
            }
            else -> Unit
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
                _uiState.update { it.copy(pairingConfirmed = false, error = null) }
                newTransport.connect()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Connection failed: ${e.message}") }
            }
        }
    }

    fun disconnect() {
        transport?.disconnect()
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

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun startPtt() {
        if (_uiState.value.isSpeaking) return
        val sendLive = isLiveReady()
        _uiState.update { it.copy(isSpeaking = true, recognizedText = "") }
        try {
            startPttUseCase.execute(
                language = _uiState.value.speakLanguage,
                transport = transport,
                sendLive = sendLive,
                onPartialResult = { partial ->
                    _uiState.update { it.copy(recognizedText = partial) }
                },
                onQueued = { publishQueues() },
                onError = { message ->
                    _uiState.update { it.copy(isSpeaking = false, recognizedText = "Error: $message") }
                },
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(isSpeaking = false, recognizedText = "Error: ${e.message}") }
        }
    }

    fun stopPtt() {
        stopPttUseCase.execute()
        _uiState.update { it.copy(isSpeaking = false) }
        if (isLiveReady()) flushQueue()
    }

    fun playInbox(id: String) {
        val item = inbox.find(id) ?: return
        if (_uiState.value.playingInboxId != null) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(playingInboxId = id, error = null) }
            try {
                receivePttUseCase.playText(item.text, item.language)
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

    fun setSpeakLanguage(language: Language) {
        _uiState.update { it.copy(speakLanguage = language) }
    }

    fun setListenLanguage(language: Language) {
        _uiState.update { it.copy(listenLanguage = language) }
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
