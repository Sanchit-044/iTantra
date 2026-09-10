package `in`.gov.itantra.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.gov.itantra.android.alert.LanBroadcastAlertManager
import `in`.gov.itantra.android.diag.AndroidDiagnosticsService
import `in`.gov.itantra.android.notify.QueuedMessageNotifier
import `in`.gov.itantra.core.diag.AppLog
import `in`.gov.itantra.android.transport.BluetoothTransport
import `in`.gov.itantra.android.transport.LanTransport
import `in`.gov.itantra.android.transport.StreamTransport
import `in`.gov.itantra.android.transport.WifiDirectTransport
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.discover.NearbyPeer
import `in`.gov.itantra.core.alert.AlertContent
import `in`.gov.itantra.core.alert.AlertPlayer
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.alert.IncomingAlert
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import `in`.gov.itantra.core.lang.LanguageSettingsStore
import `in`.gov.itantra.core.pack.LanguagePackInstallCoordinator
import `in`.gov.itantra.core.pack.PackProgress
import `in`.gov.itantra.core.profile.OperatorProfile
import `in`.gov.itantra.core.profile.ProfileCodec
import `in`.gov.itantra.profile.FileProfileStore
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import `in`.gov.itantra.core.stt.LanguageIdEngine
import `in`.gov.itantra.core.stt.resolveSpokenLanguage
import `in`.gov.itantra.core.translate.TranslationEngine
import `in`.gov.itantra.core.translate.TranslationUnavailableException
import `in`.gov.itantra.core.translate.translateOrSame
import `in`.gov.itantra.core.queue.InboxMessage
import `in`.gov.itantra.core.queue.InboundMessageInbox
import `in`.gov.itantra.core.queue.OutboundMessage
import `in`.gov.itantra.core.queue.OutboundMessageQueue
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.PairingInfo
import `in`.gov.itantra.core.transport.Transport
import `in`.gov.itantra.core.transport.TransportListener
import `in`.gov.itantra.core.usecase.FlushQueuedMessagesUseCase
import `in`.gov.itantra.core.usecase.ReceivePttTransmissionUseCase
import `in`.gov.itantra.core.usecase.RecordAlertMessageUseCase
import `in`.gov.itantra.core.usecase.SendAlertUseCase
import `in`.gov.itantra.core.usecase.StartPttTransmissionUseCase
import `in`.gov.itantra.core.usecase.StopPttTransmissionUseCase
import `in`.gov.itantra.data.history.HistoryDao
import `in`.gov.itantra.data.history.HistoryMessage
import `in`.gov.itantra.data.history.MessageDirection
import `in`.gov.itantra.data.history.MessageStatus
import java.util.UUID
import java.util.ArrayDeque
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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

enum class AlertChannel {
    ALL, WIFI, BLUETOOTH
}

data class BluetoothDeviceInfo(val name: String, val address: String)

data class UiState(
    val isSpeaking: Boolean = false,
    val isRequestingFloor: Boolean = false,
    val channelBusy: Boolean = false,
    val recognizedText: String = "",
    val currentLanguage: Language = Language.DEFAULT,
    val uiLanguage: Language = Language.ENGLISH,
    val installedLanguages: Set<Language> = setOf(Language.DEFAULT),
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val connectionMode: ConnectionMode = ConnectionMode.WIFI_DIRECT_HOST,
    val pairedDevices: List<BluetoothDeviceInfo> = emptyList(),
    val pairingInfo: PairingInfo? = null,
    val pairingConfirmed: Boolean = false,
    val selectedDeviceAddress: String? = null,
    val alertSending: Boolean = false,
    val notice: UserNotice? = null,
    val outboundPending: Int = 0,
    val outboundFailed: Int = 0,
    val queuedOutbound: List<OutboundMessage> = emptyList(),
    val inbox: List<InboxMessage> = emptyList(),
    val playingInboxId: String? = null,
    val localProfile: OperatorProfile = OperatorProfile(),
    val peerProfile: OperatorProfile? = null,
    val radioPeerName: String? = null,
    val activeIncomingAlert: IncomingAlert? = null,
    val isWifiConnected: Boolean = false,
    val alertChannel: AlertChannel = AlertChannel.ALL,
    /** True while automatically retrying a connection that dropped unexpectedly. */
    val reconnecting: Boolean = false,
    val reconnectAttempt: Int = 0,
    /** True while recording a spoken alert message (see [MainViewModel.startAlertRecording]). */
    val isRecordingAlertMessage: Boolean = false,
    /** Live, unstable STT preview of the alert message being recorded. */
    val alertRecordingText: String = "",
    /** True while a language pack download is running in the background (see LanguagePackInstallCoordinator). */
    val packDownloadBusy: Boolean = false,
    val packDownloadProgress: PackProgress? = null,
    val packDownloadError: String? = null,
) {
    val canSendAlert: Boolean
        get() = (connectionState == ConnectionState.CONNECTED && pairingConfirmed) || isWifiConnected

    val talkingToName: String
        get() = peerProfile?.displayName
            ?: radioPeerName?.takeIf { it.isNotBlank() }
            ?: OperatorProfile.FALLBACK_NAME
}

@HiltViewModel
@SuppressLint("MissingPermission")
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyAgreementProvider: KeyAgreementProvider,
    private val startPttUseCase: StartPttTransmissionUseCase,
    private val stopPttUseCase: StopPttTransmissionUseCase,
    private val receivePttUseCase: ReceivePttTransmissionUseCase,
    private val recordAlertMessageUseCase: RecordAlertMessageUseCase,
    private val translationEngine: TranslationEngine,
    private val languageSettings: LanguageSettingsStore,
    private val languagePackInstallCoordinator: LanguagePackInstallCoordinator,
    private val languageIdEngine: LanguageIdEngine,
    private val sendAlertUseCase: SendAlertUseCase,
    private val alertPlayer: AlertPlayer,
    private val diagnostics: AndroidDiagnosticsService,
    private val flushQueuedUseCase: FlushQueuedMessagesUseCase,
    private val outboundQueue: OutboundMessageQueue,
    private val inbox: InboundMessageInbox,
    private val notifier: QueuedMessageNotifier,
    private val profileStore: FileProfileStore,
    private val historyDao: HistoryDao,
    private val bleAlertBroadcaster: `in`.gov.itantra.android.alert.BleAlertBroadcaster,
    private val bleAlertScanner: `in`.gov.itantra.android.alert.BleAlertScanner,
    private val wifiAlertBroadcaster: `in`.gov.itantra.android.alert.WifiAlertBroadcaster,
    private val lanAlertManager: LanBroadcastAlertManager,
) : ViewModel(), TransportListener {

    private val _snackbarMessage = kotlinx.coroutines.flow.MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var transport: Transport? = null
    private val deferredNormals = ArrayDeque<Packet>()
    private val pttWanted = AtomicBoolean(false)

    /** What [connect] was last asked for, so an unexpected drop can retry the same target. */
    private data class ConnectRequest(
        val mode: ConnectionMode,
        val peerAddress: String?,
        val preferredWifiAddress: String?,
        val peerName: String?,
        val preferGroupOwner: Boolean?,
    )

    private var lastConnectRequest: ConnectRequest? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val userInitiatedDisconnect = AtomicBoolean(true)
    /** True once this session has reached CONNECTED at least once, so a later drop is a reconnect case rather than an initial-attempt failure the transport's own retry loop already gave up on. */
    private var hasReachedConnected = false
    private var lastInsertedAlertKey = ""
    private var lastInsertedAlertHistoryId = ""
    private val recentAlertIds = object : java.util.LinkedHashMap<Int, MutableList<String>>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, MutableList<String>>): Boolean {
            return size > 50
        }
    }

    init {
        for (item in outboundQueue.snapshot()) {
            if (item.isAlert) {
                val list = recentAlertIds.getOrPut(item.text.hashCode()) { mutableListOf() }
                if (!list.contains(item.id)) list.add(item.id)
            }
        }
        loadPairedDevices()
        viewModelScope.launch {
            alertPlayer.activeAlertState.collect { alert ->
                if (alert != null) {
                    _uiState.update { it.copy(activeIncomingAlert = alert) }
                    
                    val alertKey = "${alert.sequence}:${alert.content.toWirePayload().hashCode()}"
                    if (alertKey != lastInsertedAlertKey) {
                        lastInsertedAlertKey = alertKey
                        lastInsertedAlertHistoryId = java.util.UUID.randomUUID().toString()
                        try {
                            historyDao.insertMessage(
                                HistoryMessage(
                                    id = lastInsertedAlertHistoryId,
                                    text = alert.content.toWirePayload(),
                                    language = alert.language,
                                    timestampMs = alert.receivedAtMs,
                                    direction = MessageDirection.INBOUND,
                                    status = MessageStatus.RECEIVED,
                                    peerName = alert.senderName ?: _uiState.value.talkingToName,
                                    isAlert = true
                                )
                            )
                            if (lanAlertManager.isWifiConnected()) {
                                lanAlertManager.sendBroadcastAck(
                                    sequence = alert.sequence,
                                    payloadHash = alert.content.toWirePayload().hashCode(),
                                    receiverName = _uiState.value.localProfile.displayName
                                )
                            }
                            // Always send BLE ACK for fully offline cases
                            bleAlertBroadcaster.broadcastAck(
                                payloadHash = alert.content.toWirePayload().hashCode(),
                                receiverName = _uiState.value.localProfile.displayName
                            )
                        } catch (e: Exception) {
                            AppLog.w("MainViewModel", "Failed to save alert history: ${e.message}")
                        }
                    } else if (alert.senderName != null) {
                        try {
                            historyDao.updateMessageStatusAndPeer(
                                id = lastInsertedAlertHistoryId,
                                status = MessageStatus.RECEIVED,
                                peerName = alert.senderName
                            )
                        } catch (e: Exception) {
                            AppLog.w("MainViewModel", "Failed to update alert peer name: ${e.message}")
                        }
                    }
                } else {
                    _uiState.update { it.copy(activeIncomingAlert = null) }
                }
            }
        }
        viewModelScope.launch {
            languageSettings.settings.collect { snap ->
                val previousUi = _uiState.value.uiLanguage
                _uiState.update {
                    it.copy(
                        currentLanguage = snap.current,
                        uiLanguage = snap.uiLanguage,
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
                if (snap.uiLanguage != previousUi && inbox.unreadCount() > 0) {
                    publishQueues(notifyIfUnread = true)
                }
            }
        }
        viewModelScope.launch {
            languagePackInstallCoordinator.state.collect { install ->
                _uiState.update {
                    it.copy(
                        packDownloadBusy = install.busy,
                        packDownloadProgress = install.progress,
                        packDownloadError = install.error,
                    )
                }
            }
        }
        viewModelScope.launch {
            profileStore.profile.collect { snap ->
                _uiState.update { it.copy(localProfile = snap) }
            }
        }
        outboundQueue.purgeExpired()
        inbox.purgeExpired()
        publishQueues(notifyIfUnread = true)
        lanAlertManager.startListening { packet ->
            onReceive(packet)
        }
        refreshWifiState()
        lanAlertManager.observeWifiState { isConnected ->
            _uiState.update { it.copy(isWifiConnected = isConnected) }
        }
        
        viewModelScope.launch {
            bleAlertScanner.acks.collect { (payloadHash, receiverName) ->
                val ids = recentAlertIds[payloadHash]
                if (!ids.isNullOrEmpty()) {
                    for (id in ids) {
                        historyDao.addPeerToMessage(id, MessageStatus.DELIVERED, receiverName)
                        outboundQueue.discard(id)
                    }
                    _snackbarMessage.emit("Alert received by $receiverName (via BLE)")
                    
                    val currentNotice = _uiState.value.notice
                    if (currentNotice is UserNotice.Raw && currentNotice.detail?.contains("broadcasting nearby") == true) {
                        _uiState.update { it.copy(notice = null) }
                    }
                    
                    publishQueues()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        userInitiatedDisconnect.set(true)
        reconnectJob?.cancel()
        lanAlertManager.stopListening()
        transport?.setListener(null)
        transport?.disconnect()
        if (diagnostics.attachedTransport === transport) {
            diagnostics.attachedTransport = null
        }
    }

    fun setConnectionMode(mode: ConnectionMode) {
        _uiState.update { it.copy(connectionMode = mode) }
    }

    fun setAlertChannel(channel: AlertChannel) {
        _uiState.update { it.copy(alertChannel = channel) }
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
                peerProfile = if (stillLinked) it.peerProfile else null,
                radioPeerName = if (stillLinked) it.radioPeerName else null,
            )
        }
        
        if (state == ConnectionState.CONNECTED || state == ConnectionState.HANDSHAKING) {
            `in`.gov.itantra.service.ConnectionService.start(context)
        }
        if (state == ConnectionState.CONNECTED) {
            hasReachedConnected = true
            reconnectAttempts = 0
            _uiState.update { it.copy(reconnecting = false, reconnectAttempt = 0) }
            // Protect the socket/reader thread from Doze and Wi-Fi power-save for as
            // long as this session is live -- released the moment it stops being CONNECTED.
            `in`.gov.itantra.service.ConnectionService.notifyTransportConnected(context)
        } else if (state == ConnectionState.DISCONNECTED || state == ConnectionState.FAILED) {
            `in`.gov.itantra.service.ConnectionService.notifyTransportDisconnected(context)
            // Do not stop the service here, so that background alert scanning continues
            stopPtt()
            _uiState.update {
                it.copy(channelBusy = false, isRequestingFloor = false, isSpeaking = false)
            }
            // A radio drop mid-session (out of range, interference) is worth retrying
            // automatically; an initial connection attempt that never got there has
            // already exhausted the transport's own retry budget (see openLink()), and
            // an operator-pressed Disconnect must never be second-guessed.
            if (hasReachedConnected && !userInitiatedDisconnect.get()) {
                scheduleReconnect()
            } else {
                _uiState.update { it.copy(reconnecting = false, reconnectAttempt = 0) }
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
                    notice = null,
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
                    onSentLive = { text, timestampMs ->
                        viewModelScope.launch(Dispatchers.IO) {
                            historyDao.insertMessage(
                                HistoryMessage(
                                    id = UUID.randomUUID().toString(),
                                    text = text,
                                    language = spoken,
                                    timestampMs = timestampMs,
                                    direction = MessageDirection.OUTBOUND,
                                    status = MessageStatus.DELIVERED,
                                    peerName = _uiState.value.talkingToName,
                                    isAlert = false
                                )
                            )
                        }
                    },
                    onQueued = { msg ->
                        publishQueues()
                        viewModelScope.launch(Dispatchers.IO) {
                            historyDao.insertMessage(
                                HistoryMessage(
                                    id = msg.id,
                                    text = msg.text,
                                    language = msg.language,
                                    timestampMs = msg.createdAtMs,
                                    direction = MessageDirection.OUTBOUND,
                                    status = MessageStatus.QUEUED,
                                    peerName = null,
                                    isAlert = msg.isAlert
                                )
                            )
                        }
                    },
                )
            } catch (e: Exception) {
                stopPttUseCase.execute(currentTransport)
                _uiState.update {
                    it.copy(isSpeaking = false, notice = UserNotice.GenericError(e.message))
                }
            }
        }
    }

    override fun onFloorDenied(reason: String) {
        AppLog.w("MainViewModel", "Floor denied: $reason")
        pttWanted.set(false)
        // FloorController's callback runs on whichever thread produced the denial --
        // the transport's read thread for a remote FLOOR_DENY, the scheduler thread
        // for a local grant-timeout. Hop to Main so this can never interleave with
        // onFloorGranted's own Main-dispatched state updates.
        viewModelScope.launch(Dispatchers.Main) {
            _uiState.update {
                it.copy(
                    isSpeaking = false,
                    isRequestingFloor = false,
                    // Do not re-derive channelBusy from the reason string: onChannelBusyChanged
                    // already ran with the transport's actual peer-holds state moments earlier
                    // (see StreamTransport's FloorListener.onDenied), and a substring match here
                    // ("channel busy" vs. e.g. "no floor grant") can disagree with it -- showing
                    // this device as busy when it is not, or vice versa.
                    recognizedText = "",
                    notice = null,
                )
            }
        }
    }

    override fun onPairingCodeAvailable(info: PairingInfo) {
        _uiState.update {
            it.copy(
                pairingInfo = info,
                pairingConfirmed = false,
                radioPeerName = info.peerName,
                peerProfile = null,
            )
        }
    }

    override fun onReceive(packet: Packet) {
        AppLog.d("MainViewModel", "Received packet: type=${packet.type}, language=${packet.language}")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                when (packet.type) {
                    MessageType.QUEUED -> {
                        handleQueuedInbound(packet)
                        historyDao.insertMessage(
                            HistoryMessage(
                                id = UUID.randomUUID().toString(),
                                text = packet.text,
                                language = packet.language,
                                timestampMs = packet.timestampMs,
                                direction = MessageDirection.INBOUND,
                                status = MessageStatus.QUEUED,
                                peerName = _uiState.value.talkingToName,
                                isAlert = false
                            )
                        )
                    }
                    MessageType.ALERT -> {
                        val currentLang = _uiState.value.currentLanguage
                        
                        val split = packet.text.split("\u001F", limit = 2)
                        val senderName = if (split.size == 2) split[0].takeIf { it.isNotBlank() } else null
                        val wirePayload = if (split.size == 2) split[1] else packet.text
                        
                        val content = AlertTemplate.fromWirePayload(wirePayload)
                        val alertToPlay = when (content) {
                            is AlertContent.Template -> IncomingAlert(
                                content = content,
                                language = currentLang,
                                sequence = packet.sequence,
                                receivedAtMs = System.currentTimeMillis(),
                                senderName = senderName,
                            )
                            is AlertContent.Custom -> {
                                val (translatedText, playLang) = try {
                                    val translated = translationEngine.translateOrSame(
                                        content.text,
                                        packet.language,
                                        currentLang,
                                    )
                                    translated to currentLang
                                } catch (e: Exception) {
                                    AppLog.w(
                                        "MainViewModel",
                                        "Alert translation failed (${e.message}) — playing original in ${packet.language.code}",
                                    )
                                    content.text to packet.language
                                }
                                IncomingAlert(
                                    content = AlertContent.Custom(translatedText),
                                    language = playLang,
                                    sequence = packet.sequence,
                                    receivedAtMs = System.currentTimeMillis(),
                                    senderName = senderName,
                                )
                            }
                        }
                        alertPlayer.play(alertToPlay)
                        drainDeferredNormals()
                    }
                    MessageType.NORMAL -> {
                        playNormalOrDefer(packet)
                        historyDao.insertMessage(
                            HistoryMessage(
                                id = UUID.randomUUID().toString(),
                                text = packet.text,
                                language = packet.language,
                                timestampMs = packet.timestampMs,
                                direction = MessageDirection.INBOUND,
                                status = MessageStatus.RECEIVED,
                                peerName = _uiState.value.talkingToName,
                                isAlert = false
                            )
                        )
                    }
                    MessageType.PROFILE -> handlePeerProfile(packet)
                    MessageType.ACK -> {
                        val parts = packet.text.split(":")
                        val payloadHash = parts.getOrNull(0)?.toIntOrNull() ?: return@launch
                        val receiverName = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Peer (LAN)"
                        
                        val ids = recentAlertIds[payloadHash]
                        if (!ids.isNullOrEmpty()) {
                            for (id in ids) {
                                historyDao.addPeerToMessage(id, MessageStatus.DELIVERED, receiverName)
                                outboundQueue.discard(id)
                            }
                            _snackbarMessage.emit("Alert received by $receiverName")
                            val currentNotice = _uiState.value.notice
                            if (currentNotice is UserNotice.Raw && currentNotice.detail?.contains("broadcasting nearby") == true) {
                                _uiState.update { it.copy(notice = null) }
                            }
                            publishQueues()
                        }
                    }
                    else -> Unit
                }
            } catch (e: TranslationUnavailableException) {
                _uiState.update { it.copy(notice = UserNotice.Raw(e.message)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(notice = UserNotice.PlaybackError(e.message)) }
            }
        }
    }

    override fun onSendFailed(packet: Packet, reason: String) {
        if (packet.type != MessageType.QUEUED && packet.type != MessageType.ALERT) return
        val existing = outboundQueue.snapshot().firstOrNull {
            it.text == packet.text &&
                it.language == packet.language &&
                it.createdAtMs == packet.timestampMs
        }
        if (existing != null) {
            outboundQueue.markFailed(existing.id)
            viewModelScope.launch(Dispatchers.IO) {
                historyDao.insertMessage(
                    HistoryMessage(
                        id = existing.id,
                        text = packet.text,
                        language = packet.language,
                        timestampMs = packet.timestampMs,
                        direction = MessageDirection.OUTBOUND,
                        status = MessageStatus.FAILED,
                        peerName = _uiState.value.talkingToName,
                        isAlert = packet.type == MessageType.ALERT
                    )
                )
            }
        } else {
            val queuedMsg = outboundQueue.enqueue(packet.language, packet.text, isAlert = packet.type == MessageType.ALERT)
            if (queuedMsg != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    historyDao.insertMessage(
                        HistoryMessage(
                            id = queuedMsg.id,
                            text = packet.text,
                            language = packet.language,
                            timestampMs = queuedMsg.createdAtMs,
                            direction = MessageDirection.OUTBOUND,
                            status = MessageStatus.QUEUED,
                            peerName = null,
                            isAlert = packet.type == MessageType.ALERT
                        )
                    )
                }
            }
        }
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


    fun connect(
        peerAddress: String? = null,
        preferredWifiAddress: String? = null,
        peerName: String? = null,
        preferGroupOwner: Boolean? = null,
    ) {
        userInitiatedDisconnect.set(false)
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempts = 0
        hasReachedConnected = false
        val request = ConnectRequest(
            mode = _uiState.value.connectionMode,
            peerAddress = peerAddress,
            preferredWifiAddress = preferredWifiAddress,
            peerName = peerName,
            preferGroupOwner = preferGroupOwner,
        )
        lastConnectRequest = request
        _uiState.update { it.copy(reconnecting = false, reconnectAttempt = 0) }
        performConnect(request)
    }

    /** The actual connect attempt, shared by a fresh [connect] call and an automatic reconnect. */
    private fun performConnect(request: ConnectRequest) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                transport?.setListener(null)
                transport?.disconnect()

                val newTransport = when (request.mode) {
                    ConnectionMode.WIFI_DIRECT_HOST -> WifiDirectTransport(context, keyAgreementProvider, WifiDirectTransport.Role.HOST)
                    ConnectionMode.WIFI_DIRECT_CLIENT -> WifiDirectTransport(
                        context,
                        keyAgreementProvider,
                        WifiDirectTransport.Role.CLIENT,
                        preferredPeerAddress = request.preferredWifiAddress ?: request.peerAddress,
                        groupOwnerIntent = when (request.preferGroupOwner) {
                            true -> WifiDirectTransport.GROUP_OWNER_INTENT
                            false -> 0
                            null -> -1
                        },
                    )
                    ConnectionMode.BLUETOOTH_HOST -> BluetoothTransport(context, keyAgreementProvider, BluetoothTransport.Role.HOST)
                    ConnectionMode.BLUETOOTH_CLIENT -> {
                        val address = request.peerAddress ?: _uiState.value.selectedDeviceAddress
                        if (address.isNullOrBlank()) {
                            _uiState.update { it.copy(notice = UserNotice.PleaseSelectDevice, reconnecting = false) }
                            return@launch
                        }
                        BluetoothTransport(context, keyAgreementProvider, BluetoothTransport.Role.CLIENT, address, request.peerName)
                    }

                }

                newTransport.setListener(this@MainViewModel)
                transport = newTransport
                _uiState.update {
                    it.copy(pairingConfirmed = false, notice = null, peerProfile = null)
                }
                diagnostics.attachedTransport = newTransport
                diagnostics.currentLanguage = _uiState.value.currentLanguage
                // Pass a 2-minute timeout since P2P setup involves manual user discovery and pairing
                newTransport.connect(120_000)
            } catch (e: Exception) {
                _uiState.update { it.copy(notice = UserNotice.ConnectionFailed(e.message), reconnecting = false) }
            }
        }
    }

    /**
     * Retries [lastConnectRequest] with backoff after the transport drops CONNECTED
     * without the operator asking to disconnect. Not used for an initial connection
     * attempt that never reached CONNECTED -- the transport's own openLink() retry loop
     * already spends its whole timeout budget on that, so a second layer of retries on
     * top would just repeat the same failure. See [WifiDirectTransport] / [BluetoothTransport].
     */
    private fun scheduleReconnect() {
        val request = lastConnectRequest ?: return
        reconnectAttempts++
        if (reconnectAttempts > MAX_RECONNECT_ATTEMPTS) {
            AppLog.w("MainViewModel", "Giving up reconnecting after $MAX_RECONNECT_ATTEMPTS attempts")
            _uiState.update { it.copy(reconnecting = false) }
            return
        }
        val delayMs = (RECONNECT_BASE_DELAY_MS shl (reconnectAttempts - 1).coerceAtMost(4))
            .coerceAtMost(RECONNECT_MAX_DELAY_MS)
        AppLog.d("MainViewModel", "Connection dropped; reconnect attempt $reconnectAttempts in ${delayMs}ms")
        _uiState.update { it.copy(reconnecting = true, reconnectAttempt = reconnectAttempts) }
        reconnectJob = viewModelScope.launch {
            delay(delayMs)
            if (userInitiatedDisconnect.get()) return@launch
            performConnect(request)
        }
    }

    fun disconnect() {
        userInitiatedDisconnect.set(true)
        reconnectJob?.cancel()
        reconnectJob = null
        transport?.disconnect()
        // Keep the last transport attached so session counters remain visible.
        _uiState.update {
            it.copy(
                pairingConfirmed = false,
                pairingInfo = null,
                peerProfile = null,
                radioPeerName = null,
                reconnecting = false,
                reconnectAttempt = 0,
            )
        }
    }

    fun confirmPairing() {
        val tx = transport ?: return
        try {
            tx.confirmPairing()
        } catch (e: Exception) {
            _uiState.update { it.copy(notice = UserNotice.PairingFailed(e.message)) }
            return
        }
        _uiState.update { it.copy(pairingInfo = null, pairingConfirmed = true) }
        sendLocalProfile()
        flushQueue()
    }

    fun dismissPairing() {
        userInitiatedDisconnect.set(true)
        reconnectJob?.cancel()
        reconnectJob = null
        transport?.disconnect()
        _uiState.update {
            it.copy(
                pairingInfo = null,
                pairingConfirmed = false,
                peerProfile = null,
                radioPeerName = null,
                reconnecting = false,
                reconnectAttempt = 0,
            )
        }
    }

    fun selectDevice(address: String) {
        _uiState.update { it.copy(selectedDeviceAddress = address) }
    }

    fun sendAlertTemplate(template: AlertTemplate) {
        sendAlert(AlertContent.Template(template))
    }

    fun sendCustomAlert(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        sendAlert(AlertContent.Custom(trimmed))
    }

    /**
     * Records a spoken alert message via STT and sends it exactly like a typed custom
     * alert once recognized -- same delivery path, same translate-then-TTS on the
     * receiving phone. Mirrors [startPtt]'s offline STT capture but without any floor
     * request or transport coupling: alerts have their own delivery path.
     */
    fun startAlertRecording() {
        val state = _uiState.value
        if (state.isRecordingAlertMessage || state.isSpeaking || state.isRequestingFloor) return
        val language = state.currentLanguage
        _uiState.update {
            it.copy(isRecordingAlertMessage = true, alertRecordingText = "", notice = null)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                recordAlertMessageUseCase.start(
                    language = language,
                    onPartialResult = { partial ->
                        _uiState.update { it.copy(alertRecordingText = partial) }
                    },
                    onFinalResult = { text ->
                        _uiState.update { it.copy(isRecordingAlertMessage = false, alertRecordingText = "") }
                        if (text.isNotBlank()) sendCustomAlert(text)
                    },
                    onError = { message ->
                        _uiState.update {
                            it.copy(
                                isRecordingAlertMessage = false,
                                alertRecordingText = "",
                                notice = UserNotice.GenericError(message),
                            )
                        }
                    },
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRecordingAlertMessage = false,
                        alertRecordingText = "",
                        notice = UserNotice.GenericError(e.message),
                    )
                }
            }
        }
    }

    /** Ends the recording now and sends whatever was recognized, same as releasing PTT. */
    fun stopAlertRecording() {
        recordAlertMessageUseCase.stop()
    }

    /** Abandons the recording; nothing is sent. */
    fun cancelAlertRecording() {
        recordAlertMessageUseCase.cancel()
        _uiState.update { it.copy(isRecordingAlertMessage = false, alertRecordingText = "") }
    }

    fun sendQuickChat(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val lang = _uiState.value.currentLanguage
        val queuedMsg = outboundQueue.enqueue(lang, trimmed, isAlert = false)
        publishQueues()
        if (queuedMsg != null) {
            viewModelScope.launch(Dispatchers.IO) {
                historyDao.insertMessage(
                    HistoryMessage(
                        id = queuedMsg.id,
                        text = trimmed,
                        language = lang,
                        timestampMs = queuedMsg.createdAtMs,
                        direction = MessageDirection.OUTBOUND,
                        status = MessageStatus.QUEUED,
                        peerName = null,
                        isAlert = false
                    )
                )
            }
        }
        flushQueue()
    }

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(message)
        }
    }

    fun deleteQueuedMessage(id: String) {
        outboundQueue.discard(id)
        publishQueues()
    }

    fun dismissAlert() {
        alertPlayer.dismissActiveAlert()
    }

    private val alertSequence = AtomicInteger(0)

    fun refreshWifiState() {
        _uiState.update { it.copy(isWifiConnected = lanAlertManager.isWifiConnected()) }
    }

    private fun sendAlert(content: AlertContent) {
        val currentTransport = transport
        val wifiConnected = lanAlertManager.isWifiConnected()
        _uiState.update { it.copy(isWifiConnected = wifiConnected) }

        val canSendViaP2P = currentTransport != null &&
            currentTransport.state == ConnectionState.CONNECTED &&
            _uiState.value.pairingConfirmed

        val payload = content.toWirePayload()
        val lang = _uiState.value.currentLanguage
        val sequence = alertSequence.incrementAndGet()
        val channel = _uiState.value.alertChannel

        AppLog.d("MainViewModel", "Triggering broadcasters for sequence $sequence over channel $channel")
        val senderName = _uiState.value.localProfile.displayName

        // 1. Connectionless BLE (If ALL or BLUETOOTH)
        if (channel == AlertChannel.ALL || channel == AlertChannel.BLUETOOTH) {
            try {
                bleAlertBroadcaster.broadcastAlert(lang, content, sequence.toLong(), senderName)
            } catch (e: Exception) {
                AppLog.e("MainViewModel", "BLE broadcast crashed", e)
            }
        }

        // 2. Wi-Fi Direct and LAN (If ALL or WIFI)
        if (channel == AlertChannel.ALL || channel == AlertChannel.WIFI) {
            try {
                wifiAlertBroadcaster.broadcastAlert(lang, content, sequence.toLong(), senderName)
            } catch (e: Exception) {
                AppLog.e("MainViewModel", "Wi-Fi broadcast crashed", e)
            }

            if (wifiConnected) {
                try {
                    lanAlertManager.sendBroadcastAlert(
                        language = lang,
                        content = content,
                        sequence = sequence,
                        senderName = senderName
                    )
                } catch (e: Exception) {
                    AppLog.w("MainViewModel", "LAN broadcast alert send failed: ${e.message}")
                }
            }
        }

        // 3. P2P Direct Send or Queue
        if (!canSendViaP2P) {
            val queuedMsg = outboundQueue.enqueue(lang, payload, isAlert = true)
            publishQueues()
            _uiState.update { 
                it.copy(notice = UserNotice.Raw("Alert broadcasting nearby. Queued for P2P delivery.")) 
            }
            if (queuedMsg != null) {
                val list = recentAlertIds.getOrPut(payload.hashCode()) { mutableListOf() }
                if (!list.contains(queuedMsg.id)) list.add(queuedMsg.id)
                viewModelScope.launch(Dispatchers.IO) {
                    historyDao.insertMessage(
                        HistoryMessage(
                            id = queuedMsg.id,
                            text = payload,
                            language = lang,
                            timestampMs = queuedMsg.createdAtMs,
                            direction = MessageDirection.OUTBOUND,
                            status = MessageStatus.QUEUED,
                            peerName = null,
                            isAlert = true
                        )
                    )
                }
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(alertSending = true, notice = null) }
            try {
                sendAlertUseCase.execute(
                    transport = currentTransport,
                    language = lang,
                    content = content,
                    pairingConfirmed = true,
                )
                _uiState.update { it.copy(alertSending = false, notice = UserNotice.AlertSent) }
                val historyId = UUID.randomUUID().toString()
                val list = recentAlertIds.getOrPut(payload.hashCode()) { mutableListOf() }
                if (!list.contains(historyId)) list.add(historyId)
                historyDao.insertMessage(
                    HistoryMessage(
                        id = historyId,
                        text = payload,
                        language = lang,
                        timestampMs = System.currentTimeMillis(),
                        direction = MessageDirection.OUTBOUND,
                        status = MessageStatus.DELIVERED,
                        peerName = _uiState.value.talkingToName,
                        isAlert = true
                    )
                )
            } catch (e: Exception) {
                val queuedMsg = outboundQueue.enqueue(lang, payload, isAlert = true)
                publishQueues()
                _uiState.update { it.copy(alertSending = false, notice = UserNotice.Raw("Alert queued: ${e.message}")) }
                if (queuedMsg != null) {
                    historyDao.insertMessage(
                        HistoryMessage(
                            id = queuedMsg.id,
                            text = payload,
                            language = lang,
                            timestampMs = queuedMsg.createdAtMs,
                            direction = MessageDirection.OUTBOUND,
                            status = MessageStatus.QUEUED,
                            peerName = null,
                            isAlert = true
                        )
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(notice = null) }
    }

    fun dismissPackDownloadError() {
        languagePackInstallCoordinator.clearError()
    }

    fun startPtt() {
        val state = _uiState.value
        // The STT engine holds exactly one resident model/session; an alert recording
        // in progress must finish (or be cancelled) before PTT can claim it.
        if (state.isRecordingAlertMessage) return
        val isConnected = state.connectionState == ConnectionState.CONNECTED
        if (isConnected) {
            if (state.channelBusy) {
                return
            }
            if (state.isSpeaking || state.isRequestingFloor) return
            AppLog.d("MainViewModel", "startPtt: Requesting floor for live PTT")
            pttWanted.set(true)
            _uiState.update {
                it.copy(isRequestingFloor = true, recognizedText = "", notice = null)
            }
            transport?.requestFloor()
        } else {
            if (state.isSpeaking) return
            AppLog.d("MainViewModel", "startPtt: Starting queued offline PTT")
            _uiState.update { it.copy(isSpeaking = true, recognizedText = "", notice = null) }
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
            _uiState.update { it.copy(playingInboxId = id, notice = null) }
            try {
                val currentLang = _uiState.value.currentLanguage
                val (playText, playLang) = if (item.language == currentLang) {
                    item.text to currentLang
                } else {
                    try {
                        val translated = translationEngine.translateOrSame(
                            item.text, item.language, currentLang,
                        )
                        translated to currentLang
                    } catch (e: Exception) {
                        AppLog.w(
                            "MainViewModel",
                            "Inbox translation failed (${e.message}) — playing original in ${item.language.code}",
                        )
                        item.text to item.language
                    }
                }
                receivePttUseCase.playText(playText, playLang)
                inbox.markRead(id)
            } catch (e: Exception) {
                _uiState.update { it.copy(notice = UserNotice.PlaybackError(e.message)) }
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
    private val profileSequence = AtomicInteger(0)

    private fun sendLocalProfile() {
        val tx = transport ?: return
        if (!isLiveReady()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val local = profileStore.snapshot
                val payload = ProfileCodec.encode(local.name, profileStore.thumbnailJpeg())
                tx.send(
                    Packet(
                        type = MessageType.PROFILE,
                        language = _uiState.value.currentLanguage,
                        sequence = profileSequence.incrementAndGet(),
                        timestampMs = System.currentTimeMillis(),
                        payload = payload,
                    )
                )
            } catch (e: Exception) {
                AppLog.w("MainViewModel", "PROFILE send failed: ${e.message}")
            }
        }
    }

    private fun handlePeerProfile(packet: Packet) {
        val decoded = ProfileCodec.decode(packet.payload) ?: return
        val cachedPath = writePeerThumbnail(decoded.thumbnailJpeg)
        _uiState.update {
            it.copy(
                peerProfile = decoded.copy(
                    photoPath = cachedPath,
                    photoPresent = cachedPath != null || decoded.photoPresent,
                ),
            )
        }
    }

    private fun writePeerThumbnail(jpeg: ByteArray?): String? {
        if (jpeg == null || jpeg.isEmpty()) return null
        return try {
            val file = File(context.cacheDir, PEER_THUMB_FILE)
            file.writeBytes(jpeg)
            file.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val MAX_DEFERRED_NORMAL = 8
        private const val PEER_THUMB_FILE = "peer-avatar.jpg"

        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RECONNECT_BASE_DELAY_MS = 2_000L
        private const val RECONNECT_MAX_DELAY_MS = 30_000L
    }

    private fun handleQueuedInbound(packet: Packet) {
        val id = inboundId(packet)
        val stored = inbox.offer(id, packet.language, packet.text, packet.timestampMs)
        publishQueues()
        if (stored != null) {
            notifier.notifyUnread(inbox.unreadCount(), stored.text, _uiState.value.uiLanguage)
        }
    }

    private fun flushQueue() {
        val tx = transport ?: return
        if (!isLiveReady()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = flushQueuedUseCase.execute(tx, pairingConfirmed = true, receiverName = uiState.value.talkingToName)
                if (result.sentIds.isNotEmpty()) {
                    result.sentIds.forEach { id ->
                        historyDao.updateMessageStatusAndPeer(id, MessageStatus.DELIVERED, uiState.value.talkingToName)
                        outboundQueue.discard(id)
                    }
                    _snackbarMessage.emit("${result.sentIds.size} message(s) delivered")
                }
                if (result.failedIds.isNotEmpty()) {
                    result.failedIds.forEach { id ->
                        historyDao.updateMessageStatus(id, MessageStatus.FAILED)
                    }
                    _snackbarMessage.emit("${result.failedIds.size} message(s) failed to send")
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(notice = UserNotice.QueueSendFailed(e.message)) }
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
            notifier.notifyUnread(unread, preview, _uiState.value.uiLanguage)
        }
        _uiState.update {
            it.copy(
                outboundPending = outboundQueue.pendingCount(),
                outboundFailed = outboundQueue.failedCount(),
                queuedOutbound = outboundQueue.snapshot(),
                inbox = inbox.snapshot(),
            )
        }
    }

    private fun inboundId(packet: Packet): String =
        "${packet.timestampMs}:${packet.sequence}:${packet.text.hashCode()}"
}
