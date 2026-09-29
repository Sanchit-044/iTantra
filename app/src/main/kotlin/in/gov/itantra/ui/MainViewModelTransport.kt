package `in`.gov.itantra.ui

import android.bluetooth.BluetoothAdapter
import androidx.lifecycle.viewModelScope
import `in`.gov.itantra.android.transport.BluetoothTransport
import `in`.gov.itantra.android.transport.StreamTransport
import `in`.gov.itantra.android.transport.WifiDirectTransport
import `in`.gov.itantra.core.diag.AppLog
import `in`.gov.itantra.core.profile.ProfileCodec
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun MainViewModel.setConnectionMode(mode: ConnectionMode) {
    _uiState.update { it.copy(connectionMode = mode) }
    if (mode == ConnectionMode.BLUETOOTH_HOST || mode == ConnectionMode.BLUETOOTH_CLIENT) {
        refreshPairedDevices()
    }
}

fun MainViewModel.loadPairedDevices() {
    refreshPairedDevices()
}

fun MainViewModel.refreshPairedDevices() {
    val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
    try {
        val devices = adapter.bondedDevices?.map {
            BluetoothDeviceInfo(it.name ?: "Unknown Device", it.address)
        } ?: emptyList()
        _uiState.update { it.copy(pairedDevices = devices) }
    } catch (e: Exception) {
        AppLog.w("MainViewModel", "Failed to query paired Bluetooth devices: ${e.message}")
    }
}

fun MainViewModel.connect(
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
    val request = MainViewModel.ConnectRequest(
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

internal fun MainViewModel.performConnect(request: MainViewModel.ConnectRequest) {
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

            newTransport.setListener(this@performConnect)
            transport = newTransport
            _uiState.update {
                it.copy(pairingConfirmed = false, notice = null, peerProfile = null)
            }
            diagnostics.attachedTransport = newTransport
            diagnostics.currentLanguage = _uiState.value.currentLanguage
            newTransport.connect(120_000)
        } catch (e: Exception) {
            _uiState.update { it.copy(notice = UserNotice.ConnectionFailed(e.message), reconnecting = false) }
        }
    }
}

internal fun MainViewModel.scheduleReconnect() {
    val request = lastConnectRequest ?: return
    reconnectAttempts++
    if (reconnectAttempts > MainViewModel.MAX_RECONNECT_ATTEMPTS) {
        AppLog.w("MainViewModel", "Giving up reconnecting after ${MainViewModel.MAX_RECONNECT_ATTEMPTS} attempts")
        _uiState.update { it.copy(reconnecting = false) }
        return
    }
    val delayMs = (MainViewModel.RECONNECT_BASE_DELAY_MS shl (reconnectAttempts - 1).coerceAtMost(4))
        .coerceAtMost(MainViewModel.RECONNECT_MAX_DELAY_MS)
    AppLog.d("MainViewModel", "Connection dropped; reconnect attempt $reconnectAttempts in ${delayMs}ms")
    _uiState.update { it.copy(reconnecting = true, reconnectAttempt = reconnectAttempts) }
    reconnectJob = viewModelScope.launch {
        delay(delayMs)
        if (userInitiatedDisconnect.get()) return@launch
        performConnect(request)
    }
}

fun MainViewModel.disconnect() {
    userInitiatedDisconnect.set(true)
    reconnectJob?.cancel()
    reconnectJob = null
    transport?.disconnect()
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

fun MainViewModel.confirmPairing() {
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

fun MainViewModel.dismissPairing() {
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

fun MainViewModel.selectDevice(address: String) {
    _uiState.update { it.copy(selectedDeviceAddress = address) }
}

fun MainViewModel.isLiveReady(): Boolean {
    val tx = transport ?: return false
    if (tx.state != ConnectionState.CONNECTED) return false
    if (!_uiState.value.pairingConfirmed) return false
    val stream = tx as? StreamTransport
    return stream == null || stream.isPairingConfirmed
}

fun MainViewModel.sendLocalProfile() {
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

internal fun MainViewModel.handlePeerProfile(packet: Packet) {
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

internal fun MainViewModel.writePeerThumbnail(jpeg: ByteArray?): String? {
    if (jpeg == null || jpeg.isEmpty()) return null
    return try {
        val file = File(context.cacheDir, MainViewModel.PEER_THUMB_FILE)
        file.writeBytes(jpeg)
        file.absolutePath
    } catch (_: Exception) {
        null
    }
}
