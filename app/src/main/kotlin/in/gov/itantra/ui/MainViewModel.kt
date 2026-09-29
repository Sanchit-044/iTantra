package `in`.gov.itantra.ui

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import `in`.gov.itantra.android.alert.BleAlertBroadcaster
import `in`.gov.itantra.android.alert.BleAlertScanner
import `in`.gov.itantra.android.alert.LanBroadcastAlertManager
import `in`.gov.itantra.android.alert.WifiAlertBroadcaster
import `in`.gov.itantra.android.alert.WifiAlertScanner
import `in`.gov.itantra.android.diag.AndroidDiagnosticsService
import `in`.gov.itantra.android.location.GpsLocationTracker
import `in`.gov.itantra.android.notify.QueuedMessageNotifier
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertContent
import `in`.gov.itantra.core.alert.AlertDeliveryTracker
import `in`.gov.itantra.core.alert.AlertPlayer
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.alert.IncomingAlert
import `in`.gov.itantra.core.crypto.KeyAgreementProvider
import `in`.gov.itantra.core.diag.AppLog
import `in`.gov.itantra.core.discover.SignalSmoother
import `in`.gov.itantra.core.lang.LanguageSettingsStore
import `in`.gov.itantra.core.profile.OperatorProfile
import `in`.gov.itantra.core.queue.InboundMessageInbox
import `in`.gov.itantra.core.queue.OutboundMessageQueue
import `in`.gov.itantra.core.relay.RelayEngine
import `in`.gov.itantra.core.stt.LanguageIdEngine
import `in`.gov.itantra.core.stt.resolveSpokenLanguage
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.transport.PairingInfo
import `in`.gov.itantra.core.transport.Transport
import `in`.gov.itantra.core.transport.TransportListener
import `in`.gov.itantra.core.translate.TranslationEngine
import `in`.gov.itantra.core.translate.TranslationUnavailableException
import `in`.gov.itantra.core.translate.translateOrSame
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
import `in`.gov.itantra.profile.FileProfileStore
import `in`.gov.itantra.util.VibratorHelper
import java.util.ArrayDeque
import java.util.Collections
import java.util.LinkedHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class ConnectionMode {
    WIFI_DIRECT_HOST,
    WIFI_DIRECT_CLIENT,
    BLUETOOTH_HOST,
    BLUETOOTH_CLIENT
}

enum class AudioOutputDevice {
    SPEAKER,
    EARPIECE,
    BLUETOOTH
}

enum class AlertChannel {
    ALL,
    BLUETOOTH,
    WIFI
}

data class BluetoothDeviceInfo(val name: String, val address: String)

data class AlertRecipient(
    val peerName: String,
    val distanceMeters: Float? = null,
    val locationLabel: String? = null,
    val ackTimestampMs: Long = System.currentTimeMillis(),
    val rssiDbm: Int? = null,
    val isTracking: Boolean = false,
)

data class OutboundAlertState(
    val sequence: Int,
    val content: AlertContent,
    val language: Language,
    val startedAtMs: Long,
    val durationMs: Long,
    val recipients: List<AlertRecipient> = emptyList(),
    val isMinimized: Boolean = false,
) {
    val remainingMs: Long
        get() = ((startedAtMs + durationMs) - System.currentTimeMillis()).coerceAtLeast(0L)

    val remainingSeconds: Int
        get() = (remainingMs / 1000L).toInt().coerceAtLeast(0)

    val isExpired: Boolean
        get() = remainingMs <= 0L
}

data class UiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val pairingInfo: PairingInfo? = null,
    val pairingConfirmed: Boolean = false,
    val isSpeaking: Boolean = false,
    val isRequestingFloor: Boolean = false,
    val channelBusy: Boolean = false,
    val isPlayingAudio: Boolean = false,
    val receivingText: String = "",
    val recognizedText: String = "",
    val currentLanguage: Language = Language.DEFAULT,
    val uiLanguage: Language = Language.ENGLISH,
    val installedLanguages: Set<Language> = setOf(Language.DEFAULT),
    val connectionMode: ConnectionMode = ConnectionMode.WIFI_DIRECT_CLIENT,
    val pairedDevices: List<BluetoothDeviceInfo> = emptyList(),
    val selectedDeviceAddress: String? = null,
    val radioPeerName: String? = null,
    val peerProfile: OperatorProfile? = null,
    val localProfile: OperatorProfile = OperatorProfile(),
    val notice: UserNotice? = null,
    val outboundPending: Int = 0,
    val outboundFailed: Int = 0,
    val queuedOutbound: List<`in`.gov.itantra.core.queue.OutboundMessage> = emptyList(),
    val inbox: List<`in`.gov.itantra.core.queue.InboxMessage> = emptyList(),
    val playingInboxId: String? = null,
    val alertSending: Boolean = false,
    val activeIncomingAlert: IncomingAlert? = null,
    val audioOutputDevice: AudioOutputDevice = AudioOutputDevice.SPEAKER,
    val liveAlertDistanceMeters: Float? = null,
    val activeOutboundAlert: OutboundAlertState? = null,
    val isWifiConnected: Boolean = false,
    val alertChannel: AlertChannel = AlertChannel.ALL,
    val reconnecting: Boolean = false,
    val reconnectAttempt: Int = 0,
    val isRecordingAlertMessage: Boolean = false,
    val alertRecordingText: String = "",
) {
    val canSendAlert: Boolean get() = true
    val talkingToName: String
        get() = peerProfile?.displayName
            ?: radioPeerName?.takeIf { it.isNotBlank() }
            ?: OperatorProfile.FALLBACK_NAME
}

@HiltViewModel
@SuppressLint("MissingPermission")
class MainViewModel @Inject constructor(
    @ApplicationContext internal val context: Context,
    internal val keyAgreementProvider: KeyAgreementProvider,
    internal val startPttUseCase: StartPttTransmissionUseCase,
    internal val stopPttUseCase: StopPttTransmissionUseCase,
    internal val receivePttUseCase: ReceivePttTransmissionUseCase,
    internal val recordAlertMessageUseCase: RecordAlertMessageUseCase,
    internal val translationEngine: TranslationEngine,
    internal val languageSettings: LanguageSettingsStore,
    internal val languageIdEngine: LanguageIdEngine,
    internal val sendAlertUseCase: SendAlertUseCase,
    internal val alertPlayer: AlertPlayer,
    internal val diagnostics: AndroidDiagnosticsService,
    internal val flushQueuedUseCase: FlushQueuedMessagesUseCase,
    internal val outboundQueue: OutboundMessageQueue,
    internal val inbox: InboundMessageInbox,
    internal val notifier: QueuedMessageNotifier,
    internal val profileStore: FileProfileStore,
    internal val historyDao: HistoryDao,
    internal val bleAlertBroadcaster: BleAlertBroadcaster,
    internal val bleAlertScanner: BleAlertScanner,
    internal val wifiAlertBroadcaster: WifiAlertBroadcaster,
    internal val wifiAlertScanner: WifiAlertScanner,
    internal val lanAlertManager: LanBroadcastAlertManager,
    internal val gpsLocationTracker: GpsLocationTracker,
    internal val vibratorHelper: VibratorHelper,
) : ViewModel(), TransportListener {

    internal val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(message)
        }
    }

    internal val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    internal var transport: Transport? = null
    internal val deferredNormals = ArrayDeque<Packet>()
    internal val pttWanted = AtomicBoolean(false)

    internal val pendingQuickChats = ArrayDeque<String>()
    internal val quickChatSequence = AtomicInteger(0)

    internal data class ConnectRequest(
        val mode: ConnectionMode,
        val peerAddress: String?,
        val preferredWifiAddress: String?,
        val peerName: String?,
        val preferGroupOwner: Boolean?,
    )

    internal var lastConnectRequest: ConnectRequest? = null
    internal var reconnectJob: Job? = null
    internal var reconnectAttempts = 0
    internal var clearRecognizedTextJob: Job? = null
    internal val userInitiatedDisconnect = AtomicBoolean(true)

    internal var hasReachedConnected = false
    internal var outboundBroadcastJob: Job? = null
    internal var alertTrackingJob: Job? = null

    internal val recentAlertIds = Collections.synchronizedMap(object : LinkedHashMap<Int, MutableList<String>>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, MutableList<String>>): Boolean = size > 50
    })

    internal val originatedAlertSequences = ConcurrentHashMap<Int, Long>()
    internal val announcedAckPeers = ConcurrentHashMap.newKeySet<String>()
    internal fun isSelfAlert(sequence: Int): Boolean {
        if (_uiState.value.activeOutboundAlert?.sequence == sequence) return true
        val origTime = originatedAlertSequences[sequence]
        if (origTime != null) {
            if (System.currentTimeMillis() - origTime < 300_000L) return true
            originatedAlertSequences.remove(sequence)
        }
        if (wifiAlertBroadcaster.isOriginated(sequence)) return true
        if (bleAlertBroadcaster.isOriginated(sequence)) return true
        if (lanAlertManager.isOriginated(sequence)) return true
        return false
    }

    internal fun parseCoordinates(text: String): Pair<Double, Double>? {
        val regex = Regex("""(-?\d+\.\d+),\s*(-?\d+\.\d+)""")
        val match = regex.find(text) ?: return null
        val lat = match.groupValues[1].toDoubleOrNull() ?: return null
        val lon = match.groupValues[2].toDoubleOrNull() ?: return null
        return lat to lon
    }

    internal fun parseEmbeddedDistance(text: String): Float? {
        val regex = Regex("""\(~(\d+(\.\d+)?)m\)""")
        val match = regex.find(text) ?: return null
        return match.groupValues[1].toFloatOrNull()
    }

    internal fun isMockOrDelhi(lat: Double, lon: Double): Boolean {
        if (lat == 0.0 && lon == 0.0) return true
        return kotlin.math.abs(lat - 28.613939) < 0.001 && kotlin.math.abs(lon - 77.209021) < 0.001
    }

    internal fun getWifiRssiDbm(): Int? {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val info = wifiManager?.connectionInfo
            val rssi = info?.rssi
            if (rssi != null && rssi != -127 && rssi != 0 && rssi < 0) {
                rssi
            } else null
        } catch (_: Exception) {
            null
        }
    }

    internal val peerSignalSmoothers = ConcurrentHashMap<String, SignalSmoother>()
    internal val incomingAlertSmoother = SignalSmoother()

    internal val alertDeliveryTracker = AlertDeliveryTracker()
    internal val relayEngine = RelayEngine(localNodeId = "local_node")
    internal val profileSequence = AtomicInteger(0)
    internal val alertSequence = AtomicInteger(0)

    init {
        for (item in outboundQueue.snapshot()) {
            if (item.isAlert) {
                val list = recentAlertIds.getOrPut(item.text.hashCode()) { mutableListOf() }
                if (!list.contains(item.id)) list.add(item.id)
            }
        }
        val snap = languageSettings.snapshot
        _uiState.update {
            it.copy(
                currentLanguage = snap.current,
                uiLanguage = snap.uiLanguage,
                installedLanguages = snap.installed,
                localProfile = profileStore.snapshot,
                isWifiConnected = lanAlertManager.isWifiConnected(),
            )
        }

        viewModelScope.launch {
            languageSettings.settings.collect { s ->
                val previousUi = _uiState.value.uiLanguage
                _uiState.update {
                    it.copy(
                        currentLanguage = s.current,
                        uiLanguage = s.uiLanguage,
                        installedLanguages = s.installed,
                    )
                }
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        startPttUseCase.preload(s.current)
                        receivePttUseCase.preload(s.current)
                    } catch (e: Exception) {
                        AppLog.w("MainViewModel", "Failed to preload models: ${e.message}")
                    }
                }
                if (s.uiLanguage != previousUi && inbox.unreadCount() > 0) {
                    publishQueues(notifyIfUnread = true)
                }
            }
        }

        viewModelScope.launch {
            profileStore.profile.collect { p ->
                _uiState.update { it.copy(localProfile = p) }
                if (p.displayName.isNotBlank()) {
                    lanAlertManager.setLocalDeviceName(p.displayName)
                }
            }
        }

        try {
            gpsLocationTracker.startTracking()
        } catch (_: Exception) {}

        lanAlertManager.startListening()
        bleAlertScanner.startScanning()
        wifiAlertScanner.startScanning()
        refreshWifiState()
        lanAlertManager.observeWifiState { isConnected ->
            _uiState.update { it.copy(isWifiConnected = isConnected) }
        }

        viewModelScope.launch {
            bleAlertScanner.alerts.collect { packet ->
                onReceive(packet)
            }
        }

        viewModelScope.launch {
            wifiAlertScanner.alerts.collect { packet ->
                onReceive(packet)
            }
        }

        viewModelScope.launch {
            lanAlertManager.alerts.collect { packet ->
                onReceive(packet)
            }
        }

        viewModelScope.launch {
            bleAlertScanner.acks.collect { ack ->
                val (payloadHash, receiverName, isTracking, ackRssi, isStopped) = ack
                val rssi = ackRssi.takeIf { it != 0 } ?: bleAlertScanner.latestRssi.value ?: getWifiRssiDbm()
                val isWifi = ackRssi == 0 && bleAlertScanner.latestRssi.value == null && rssi != null
                val distance = if (rssi != null && rssi != 0) {
                    val smoother = peerSignalSmoothers.getOrPut(receiverName) { SignalSmoother() }
                    smoother.offer(rssi, System.currentTimeMillis())
                    if (isWifi) {
                        smoother.estimateDistanceMeters(referenceDbm = -48.0, exponent = 2.7)
                    } else {
                        smoother.estimateDistanceMeters()
                    }
                } else null
                val effectiveTracking = !isStopped && isTracking
                val statusTag = when {
                    isStopped -> "• RECEIVED & STOPPED"
                    effectiveTracking -> "• TRACKING LIVE"
                    else -> "• RECEIVED"
                }
                val distStr = if (distance != null) " (~${String.format(Locale.US, "%.1f", distance)}m)" else ""
                val locLabel = "Direct RF Proximity$distStr $statusTag"
                recordAlertRecipient(receiverName, distance, rssi, locLabel, isTracking = effectiveTracking)

                val ids = recentAlertIds[payloadHash]
                val targetIds = if (!ids.isNullOrEmpty()) ids else outboundQueue.snapshot().filter { it.isAlert }.map { it.id }
                if (targetIds.isNotEmpty()) {
                    for (id in targetIds) {
                        historyDao.addPeerToMessage(id, MessageStatus.DELIVERED, receiverName, distance, locLabel)
                        outboundQueue.markSent(id, receiverName, distance, locLabel)
                    }
                    alertDeliveryTracker.onAck(payloadHash, receiverName, System.currentTimeMillis())
                    val activeOutbound = _uiState.value.activeOutboundAlert
                    if (activeOutbound != null && !effectiveTracking && !isStopped && announcedAckPeers.add(receiverName)) {
                        _snackbarMessage.emit("Alert acknowledged by $receiverName")
                    }

                    val currentNotice = _uiState.value.notice
                    if (currentNotice is UserNotice.Raw && currentNotice.detail?.contains("broadcasting nearby") == true) {
                        _uiState.update { it.copy(notice = null) }
                    }

                    publishQueues()
                }
            }
        }

        viewModelScope.launch {
            bleAlertScanner.latestRssi.collect { rssi ->
                val activeAlert = _uiState.value.activeIncomingAlert
                if (activeAlert != null && alertTrackingJob != null && rssi != null && rssi != 0) {
                    val senderCoords = activeAlert.senderLocation?.let { parseCoordinates(it) }
                    val myLoc = gpsLocationTracker.location.value
                    val hasGpsFix = myLoc.hasRealFix && !myLoc.isMockOrDelhi() && senderCoords != null && !isMockOrDelhi(senderCoords.first, senderCoords.second)
                    val gpsDist = if (hasGpsFix && senderCoords != null) {
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(
                            myLoc.latitude, myLoc.longitude,
                            senderCoords.first, senderCoords.second,
                            results
                        )
                        results[0]
                    } else null

                    incomingAlertSmoother.offer(rssi, System.currentTimeMillis())
                    val bleDist = incomingAlertSmoother.estimateDistanceMeters()
                    val dist = if (bleDist <= 35.0f || gpsDist == null) bleDist else gpsDist
                    _uiState.update { it.copy(liveAlertDistanceMeters = dist) }
                }

                // Update active tracking recipient proximity on sender screen
                val activeOutbound = _uiState.value.activeOutboundAlert
                if (activeOutbound != null && rssi != null && rssi != 0) {
                    activeOutbound.recipients.firstOrNull { it.isTracking }?.let { trackingPeer ->
                        val smoother = peerSignalSmoothers.getOrPut(trackingPeer.peerName) { SignalSmoother() }
                        smoother.offer(rssi, System.currentTimeMillis())
                        val estDist = smoother.estimateDistanceMeters()
                        recordAlertRecipient(
                            peerName = trackingPeer.peerName,
                            distanceMeters = estDist,
                            rssiDbm = rssi,
                            locationLabel = trackingPeer.locationLabel,
                            isTracking = true,
                        )
                    }
                }
            }
        }

        // Periodic proximity update loop using Wi-Fi RSSI when BLE is disabled/unavailable
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1500L)
                val bleRssi = bleAlertScanner.latestRssi.value
                val activeIncoming = _uiState.value.activeIncomingAlert
                val activeOutbound = _uiState.value.activeOutboundAlert
                if (bleRssi == null && ((activeIncoming != null && alertTrackingJob != null) || activeOutbound != null)) {
                    val wifiRssi = getWifiRssiDbm()
                    if (wifiRssi != null) {
                        if (activeIncoming != null && alertTrackingJob != null) {
                            incomingAlertSmoother.offer(wifiRssi, System.currentTimeMillis())
                            val wifiDist = incomingAlertSmoother.estimateDistanceMeters(referenceDbm = -48.0, exponent = 2.7)
                            _uiState.update { it.copy(liveAlertDistanceMeters = wifiDist) }
                        }
                        if (activeOutbound != null) {
                            activeOutbound.recipients.firstOrNull { it.isTracking }?.let { trackingPeer ->
                                val smoother = peerSignalSmoothers.getOrPut(trackingPeer.peerName) { SignalSmoother() }
                                smoother.offer(wifiRssi, System.currentTimeMillis())
                                val estDist = smoother.estimateDistanceMeters(referenceDbm = -48.0, exponent = 2.7)
                                recordAlertRecipient(
                                    peerName = trackingPeer.peerName,
                                    distanceMeters = estDist,
                                    rssiDbm = wifiRssi,
                                    locationLabel = trackingPeer.locationLabel,
                                    isTracking = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live real-time distance and proximity recalculator for sender screen (updates every 2s)
        viewModelScope.launch {
            while (isActive) {
                val activeOutbound = _uiState.value.activeOutboundAlert
                if (activeOutbound != null && activeOutbound.recipients.isNotEmpty()) {
                    val senderLoc = gpsLocationTracker.location.value
                    if (senderLoc.hasRealFix && !senderLoc.isMockOrDelhi()) {
                        for (recipient in activeOutbound.recipients) {
                            val coords = recipient.locationLabel?.let { parseCoordinates(it) }
                            if (coords != null && !isMockOrDelhi(coords.first, coords.second)) {
                                val results = FloatArray(1)
                                android.location.Location.distanceBetween(
                                    senderLoc.latitude, senderLoc.longitude,
                                    coords.first, coords.second,
                                    results
                                )
                                val newGpsDist = results[0]
                                // Only prioritize GPS if BLE RSSI is not available or if GPS distance indicates beyond BLE range (> 35m)
                                if (recipient.rssiDbm == null || newGpsDist > 35.0f) {
                                    if (recipient.distanceMeters == null || kotlin.math.abs(recipient.distanceMeters - newGpsDist) > 0.5f) {
                                        val distStr = " (~${String.format(Locale.US, "%.1f", newGpsDist)}m)"
                                        val isStopped = recipient.locationLabel?.contains("STOPPED", ignoreCase = true) == true
                                        val statusTag = when {
                                            isStopped -> "• RECEIVED & STOPPED"
                                            recipient.isTracking -> "• TRACKING LIVE"
                                            else -> "• RECEIVED"
                                        }
                                        val baseLoc = "GPS: ${String.format(Locale.US, "%.4f", coords.first)}, ${String.format(Locale.US, "%.4f", coords.second)}"
                                        recordAlertRecipient(
                                            peerName = recipient.peerName,
                                            distanceMeters = newGpsDist,
                                            rssiDbm = recipient.rssiDbm,
                                            locationLabel = "$baseLoc $statusTag$distStr",
                                            isTracking = recipient.isTracking,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                delay(2000L)
            }
        }

        loadPairedDevices()
        publishQueues()
    }

    override fun onCleared() {
        super.onCleared()
        userInitiatedDisconnect.set(true)
        reconnectJob?.cancel()
        outboundBroadcastJob?.cancel()
        alertTrackingJob?.cancel()
        bleAlertBroadcaster.stopBroadcasting()
        bleAlertScanner.stopScanning()
        wifiAlertBroadcaster.stopBroadcasting()
        wifiAlertScanner.stopScanning()
        lanAlertManager.stopListening()
        transport?.setListener(null)
        transport?.disconnect()
        transport = null
        if (diagnostics.attachedTransport === transport) {
            diagnostics.attachedTransport = null
        }
    }

    // --- TransportListener Implementation ---

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
            `in`.gov.itantra.service.ConnectionService.notifyTransportConnected(context)
        } else if (state == ConnectionState.DISCONNECTED || state == ConnectionState.FAILED) {
            `in`.gov.itantra.service.ConnectionService.notifyTransportDisconnected(context)
            stopPtt()
            _uiState.update {
                it.copy(channelBusy = false, isRequestingFloor = false, isSpeaking = false)
            }
            if (hasReachedConnected && !userInitiatedDisconnect.get()) {
                scheduleReconnect()
            }
        }
    }

    override fun onChannelBusyChanged(busy: Boolean) {
        _uiState.update { it.copy(channelBusy = busy) }
    }

    override fun onFloorGranted() {
        val nextText = synchronized(pendingQuickChats) { pendingQuickChats.pollFirst() }
        if (nextText != null) {
            sendQuickChatLive(nextText)
            return
        }
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
                    onPartialResult = { partial ->
                        clearRecognizedTextJob?.cancel()
                        _uiState.update { it.copy(recognizedText = partial) }
                    },
                    onFinalResult = { text ->
                        _uiState.update { it.copy(recognizedText = text) }
                        scheduleRecognizedTextClear()
                        if (text.isNotBlank()) {
                            viewModelScope.launch(Dispatchers.IO) {
                                historyDao.insertMessage(
                                    HistoryMessage(
                                        id = UUID.randomUUID().toString(),
                                        text = text,
                                        language = spoken,
                                        timestampMs = System.currentTimeMillis(),
                                        direction = MessageDirection.OUTBOUND,
                                        status = MessageStatus.DELIVERED,
                                        peerName = _uiState.value.talkingToName,
                                        isAlert = false
                                    )
                                )
                            }
                        }
                    },
                    onError = { err ->
                        _uiState.update {
                            it.copy(
                                isSpeaking = false,
                                isRequestingFloor = false,
                                notice = UserNotice.GenericError(err),
                            )
                        }
                    },
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSpeaking = false,
                        isRequestingFloor = false,
                        notice = UserNotice.GenericError(e.message ?: "Failed to start speech recognition"),
                    )
                }
            }
        }
    }

    override fun onFloorDenied(reason: String) {
        val nextText = synchronized(pendingQuickChats) { pendingQuickChats.pollFirst() }
        if (nextText != null) {
            queueQuickChat(nextText)
            drainNextQuickChat()
            return
        }
        AppLog.d("MainViewModel", "Floor denied: $reason")
        pttWanted.set(false)
        _uiState.update {
            it.copy(
                isRequestingFloor = false,
                isSpeaking = false,
                notice = UserNotice.FloorDenied(reason),
            )
        }
    }

    override fun onPairingCodeAvailable(info: PairingInfo) {
        AppLog.d("MainViewModel", "Pairing code available: ${info.code}")
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

                        val split = packet.text.split("\u001F", limit = 3)
                        val senderName = if (split.size >= 2) split[0].takeIf { it.isNotBlank() } else null
                        val wirePayload = if (split.size >= 2) split[1] else packet.text
                        val senderLocationStr = if (split.size >= 3) split[2].takeIf { it.isNotBlank() } else null

                        val localName = profileStore.snapshot.name.takeIf { it.isNotBlank() } ?: _uiState.value.localProfile.displayName
                        if (isSelfAlert(packet.sequence)) {
                            AppLog.d("MainViewModel", "Ignoring self-broadcast alert loopback (seq=${packet.sequence}) from $senderName")
                            return@launch
                        }

                        val content = AlertTemplate.fromWirePayload(wirePayload)
                        val coords = senderLocationStr?.let { parseCoordinates(it) }
                        val myLoc = gpsLocationTracker.location.value
                        val hasGpsFix = myLoc.hasRealFix && !myLoc.isMockOrDelhi() && coords != null && !isMockOrDelhi(coords.first, coords.second)
                        val initialGpsDistance = if (hasGpsFix && coords != null) {
                            val results = FloatArray(1)
                            android.location.Location.distanceBetween(
                                myLoc.latitude, myLoc.longitude,
                                coords.first, coords.second,
                                results
                            )
                            results[0]
                        } else null
                        val latestRssi = bleAlertScanner.latestRssi.value ?: getWifiRssiDbm()
                        val isWifi = bleAlertScanner.latestRssi.value == null && latestRssi != null
                        val initialBleDist = if (latestRssi != null && latestRssi != 0) {
                            incomingAlertSmoother.offer(latestRssi, System.currentTimeMillis())
                            if (isWifi) {
                                incomingAlertSmoother.estimateDistanceMeters(referenceDbm = -48.0, exponent = 2.7)
                            } else {
                                incomingAlertSmoother.estimateDistanceMeters()
                            }
                        } else null
                        val initialDistance = when {
                            initialBleDist != null && initialBleDist <= 35.0f -> initialBleDist
                            initialGpsDistance != null -> initialGpsDistance
                            else -> initialBleDist
                        }

                        if (initialDistance != null) {
                            _uiState.update { it.copy(liveAlertDistanceMeters = initialDistance) }
                        }

                        // Send ACK immediately to sender over all active channels so sender screen updates in real time
                        val payloadHash = wirePayload.hashCode()
                        val myName = localName.ifBlank { "Responder" }
                        val distStr = if (initialDistance != null) " (~${String.format(Locale.US, "%.1f", initialDistance)}m)" else ""
                        val locLabel = if (myLoc.hasRealFix && !myLoc.isMockOrDelhi()) {
                            "GPS: ${String.format(Locale.US, "%.4f", myLoc.latitude)}, ${String.format(Locale.US, "%.4f", myLoc.longitude)} • RECEIVED$distStr"
                        } else {
                            "Direct RF Mesh • RECEIVED$distStr"
                        }
                        AppLog.d("MainViewModel", "Sending auto-ACK for seq ${packet.sequence} from $myName ($locLabel)")
                        lanAlertManager.sendBroadcastAck(packet.sequence, payloadHash, myName, locLabel)
                        bleAlertBroadcaster.broadcastAck(payloadHash, myName)
                        wifiAlertBroadcaster.broadcastAck(packet.sequence, payloadHash, myName, locLabel, durationMs = 30_000L)
                        transport?.takeIf { it.state == ConnectionState.CONNECTED }?.let { tx ->
                            runCatching {
                                tx.send(Packet.text(MessageType.ACK, Language.ENGLISH, packet.sequence, "$payloadHash:$myName:$locLabel"))
                            }
                        }
                        // Send repeated LAN ACK bursts to maximize delivery probability
                        viewModelScope.launch(Dispatchers.IO) {
                            for (i in 1..8) {
                                delay(2000L)
                                lanAlertManager.sendBroadcastAck(packet.sequence, payloadHash, myName, locLabel)
                            }
                        }
                        val alertToPlay = when (content) {
                            is AlertContent.Template -> IncomingAlert(
                                content = content,
                                language = currentLang,
                                sequence = packet.sequence,
                                receivedAtMs = System.currentTimeMillis(),
                                senderName = senderName,
                                distanceMeters = initialDistance,
                                senderLocation = senderLocationStr,
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
                                    distanceMeters = initialDistance,
                                    senderLocation = senderLocationStr,
                                )
                            }
                        }

                        val alertText = when (val c = alertToPlay.content) {
                            is AlertContent.Template -> c.template.phrase(currentLang)
                            is AlertContent.Custom -> c.text
                        }
                        val alertId = "alert_${alertToPlay.sequence}_${alertToPlay.receivedAtMs}"
                        val effectiveSender = senderName ?: _uiState.value.talkingToName
                        val distance = initialDistance
                        val location = "Emergency Beacon"
                        inbox.offer(
                            id = alertId,
                            language = alertToPlay.language,
                            text = alertText,
                            receivedAtMs = alertToPlay.receivedAtMs,
                            senderName = effectiveSender,
                            isAlert = true,
                            locationLabel = location,
                            distanceMeters = distance,
                        )
                        historyDao.insertMessage(
                            HistoryMessage(
                                id = alertId,
                                text = alertText,
                                language = alertToPlay.language,
                                timestampMs = alertToPlay.receivedAtMs,
                                direction = MessageDirection.INBOUND,
                                status = MessageStatus.RECEIVED,
                                peerName = effectiveSender,
                                isAlert = true,
                                locationLabel = location,
                                distanceMeters = distance,
                            )
                        )
                        _uiState.update { it.copy(activeIncomingAlert = alertToPlay, liveAlertDistanceMeters = initialDistance) }
                        publishQueues()
                        wifiAlertScanner.setFastScanMode(true)
                        startTrackingSender(alertToPlay)
                        vibratorHelper.startAlertVibration()

                        // Multi-Hop Relay Hopping
                        val currentTtl = if (packet.flags > 0) packet.flags else 3
                        val relayDecision = relayEngine.consider(packet, senderNodeId = senderName, ttl = currentTtl)
                        if (relayDecision is RelayEngine.Decision.Forward) {
                            viewModelScope.launch(Dispatchers.IO) {
                                delay(relayDecision.delayMillis)
                                val nextTtl = currentTtl - 1
                                if (nextTtl > 0) {
                                    AppLog.d("MainViewModel", "Relaying emergency alert multi-hop (nextTtl=$nextTtl)")
                                    bleAlertBroadcaster.broadcastAlert(
                                        language = alertToPlay.language,
                                        content = alertToPlay.content,
                                        sequence = alertToPlay.sequence.toLong(),
                                        senderName = senderName,
                                        ttl = nextTtl,
                                    )
                                    if (lanAlertManager.isWifiConnected()) {
                                        lanAlertManager.sendBroadcastAlert(
                                            language = alertToPlay.language,
                                            content = alertToPlay.content,
                                            sequence = alertToPlay.sequence,
                                            senderName = senderName ?: "Relayed Peer",
                                            ttl = nextTtl,
                                        )
                                    }
                                }
                            }
                        }

                        viewModelScope.launch(Dispatchers.IO) {
                            alertPlayer.play(alertToPlay)
                            drainDeferredNormals()
                        }
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
                        AppLog.d("MainViewModel", ">>> ACK RECEIVED: seq=${packet.sequence}, text='${packet.text}'")
                        val parts = packet.text.split(":", limit = 3)
                        val payloadHash = parts.getOrNull(0)?.toIntOrNull() ?: return@launch
                        val rawReceiverName = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Responder"
                        val locationInfo = parts.getOrNull(2)?.takeIf { it.isNotBlank() }

                        val isStopped = locationInfo?.contains("STOPPED", ignoreCase = true) == true
                        val isTracking = !isStopped && (locationInfo?.contains("TRACKING", ignoreCase = true) == true || rawReceiverName.contains("Tracking", ignoreCase = true))
                        val cleanName = rawReceiverName.replace(" (Tracking)", "").trim()
                        AppLog.d("MainViewModel", ">>> ACK parsed: hash=$payloadHash, name='$cleanName', tracking=$isTracking, stopped=$isStopped, loc='$locationInfo'")

                        val coords = locationInfo?.let { parseCoordinates(it) }
                        val senderLoc = gpsLocationTracker.location.value
                        val hasGpsFix = senderLoc.hasRealFix && !senderLoc.isMockOrDelhi() && coords != null && !isMockOrDelhi(coords.first, coords.second)
                        val gpsDistance = if (hasGpsFix && coords != null) {
                            val results = FloatArray(1)
                            android.location.Location.distanceBetween(
                                senderLoc.latitude, senderLoc.longitude,
                                coords.first, coords.second,
                                results
                            )
                            results[0]
                        } else null

                        val rssi = bleAlertScanner.latestRssi.value ?: getWifiRssiDbm()
                        val isWifi = bleAlertScanner.latestRssi.value == null && rssi != null
                        val bleDist = if (rssi != null && rssi != 0) {
                            val smoother = peerSignalSmoothers.getOrPut(cleanName) { SignalSmoother() }
                            smoother.offer(rssi, System.currentTimeMillis())
                            if (isWifi) {
                                smoother.estimateDistanceMeters(referenceDbm = -48.0, exponent = 2.7)
                            } else {
                                smoother.estimateDistanceMeters()
                            }
                        } else null

                        val embeddedDistance = locationInfo?.let { parseEmbeddedDistance(it) }
                        val distance = when {
                            bleDist != null && bleDist <= 35.0f -> bleDist
                            gpsDistance != null -> gpsDistance
                            embeddedDistance != null -> embeddedDistance
                            else -> bleDist
                        }
                        val statusTag = when {
                            isStopped -> "• RECEIVED & STOPPED"
                            isTracking -> "• TRACKING LIVE"
                            else -> "• RECEIVED"
                        }
                        val locLabel = locationInfo ?: (if (distance != null) "Direct RF Proximity (~${String.format(Locale.US, "%.1f", distance)}m) $statusTag" else "Nearby Radio Mesh $statusTag")
                        AppLog.d("MainViewModel", ">>> ACK recording recipient: name='$cleanName', dist=$distance, tracking=$isTracking, activeOutbound=${_uiState.value.activeOutboundAlert?.sequence}")
                        recordAlertRecipient(cleanName, distance, rssi, locLabel, isTracking = isTracking)

                        val ids = recentAlertIds[payloadHash]
                        val targetIds = if (!ids.isNullOrEmpty()) ids else outboundQueue.snapshot().filter { it.isAlert }.map { it.id }
                        if (targetIds.isNotEmpty()) {
                            for (id in targetIds) {
                                historyDao.addPeerToMessage(id, MessageStatus.DELIVERED, cleanName, distance, locLabel)
                                outboundQueue.markSent(id, cleanName, distance, locLabel)
                            }
                            alertDeliveryTracker.onAck(payloadHash, cleanName, System.currentTimeMillis())
                            val activeOutbound = _uiState.value.activeOutboundAlert
                            if (activeOutbound != null && !isTracking && !isStopped && announcedAckPeers.add(cleanName)) {
                                _snackbarMessage.emit("Alert acknowledged by $cleanName")
                            }
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

    internal suspend fun playNormalOrDefer(packet: Packet) {
        val accepted = alertPlayer.tryPlayNormal(packet.sequence) { }
        if (accepted) {
            _uiState.update { it.copy(isPlayingAudio = true, receivingText = packet.text) }
            try {
                receivePttUseCase.execute(packet, _uiState.value.currentLanguage)
            } finally {
                _uiState.update { it.copy(isPlayingAudio = false, receivingText = "") }
            }
        } else {
            synchronized(deferredNormals) {
                if (deferredNormals.size < MAX_DEFERRED_NORMAL) {
                    deferredNormals.addLast(packet)
                }
            }
        }
    }

    internal suspend fun drainDeferredNormals() {
        while (true) {
            val next = synchronized(deferredNormals) { deferredNormals.pollFirst() } ?: break
            val accepted = alertPlayer.tryPlayNormal(next.sequence) { }
            if (!accepted) {
                synchronized(deferredNormals) { deferredNormals.addFirst(next) }
                break
            }
            _uiState.update { it.copy(isPlayingAudio = true, receivingText = next.text) }
            try {
                receivePttUseCase.execute(next, _uiState.value.currentLanguage)
            } finally {
                _uiState.update { it.copy(isPlayingAudio = false, receivingText = "") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(notice = null) }
    }

    companion object {
        internal const val MAX_DEFERRED_NORMAL = 8
        internal const val PEER_THUMB_FILE = "peer-avatar.jpg"
        internal const val MAX_RECONNECT_ATTEMPTS = 5
        internal const val RECONNECT_BASE_DELAY_MS = 2_000L
        internal const val RECONNECT_MAX_DELAY_MS = 30_000L
    }
}
