package `in`.gov.itantra.ui

import android.content.Context
import java.util.Locale
import java.util.UUID
import androidx.lifecycle.viewModelScope
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.alert.AlertContent
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.alert.IncomingAlert
import `in`.gov.itantra.core.diag.AppLog
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.core.transport.MessageType
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.translate.translateOrSame
import `in`.gov.itantra.data.history.HistoryMessage
import `in`.gov.itantra.data.history.MessageDirection
import `in`.gov.itantra.data.history.MessageStatus
import `in`.gov.itantra.core.queue.OutboundState
import `in`.gov.itantra.android.alert.BleAlertBroadcaster
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.pow

fun MainViewModel.sendAlertTemplate(template: AlertTemplate) {
    sendAlert(AlertContent.Template(template))
}

fun MainViewModel.sendCustomAlert(text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return
    sendAlert(AlertContent.Custom(trimmed))
}

fun MainViewModel.startAlertRecording() {
    val state = uiState.value
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
                    val recognized = text.ifBlank { uiState.value.alertRecordingText }.trim()
                    _uiState.update {
                        it.copy(
                            isRecordingAlertMessage = false,
                            alertRecordingText = ""
                        )
                    }
                    if (recognized.isNotBlank()) {
                        sendCustomAlert(recognized)
                    } else {
                        _uiState.update {
                            it.copy(notice = UserNotice.Raw("No speech detected. Please speak clearly or type your message."))
                        }
                    }
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

fun MainViewModel.stopAlertRecording() {
    recordAlertMessageUseCase.stop()
}

fun MainViewModel.cancelAlertRecording() {
    recordAlertMessageUseCase.cancel()
    _uiState.update { it.copy(isRecordingAlertMessage = false, alertRecordingText = "") }
}

fun MainViewModel.startTrackingSender(alert: IncomingAlert) {
    alertTrackingJob?.cancel()
    alertTrackingJob = viewModelScope.launch(Dispatchers.IO) {
        val alertWire = alert.content.toWirePayload()
        val payloadHash = alertWire.hashCode()
        val myName = profileStore.snapshot.name.takeIf { it.isNotBlank() } ?: uiState.value.localProfile.displayName.ifBlank { "Responder" }

        var wifiAckTick = 0
        while (isActive) {
            val loc = gpsLocationTracker.location.value
            val senderCoords = alert.senderLocation?.let { parseCoordinates(it) }
            val hasGpsLock = loc.hasRealFix && !loc.isMockOrDelhi() && senderCoords != null && !isMockOrDelhi(senderCoords.first, senderCoords.second)
            val liveGpsDist = if (hasGpsLock && senderCoords != null) {
                val results = FloatArray(1)
                android.location.Location.distanceBetween(
                    loc.latitude, loc.longitude,
                    senderCoords.first, senderCoords.second,
                    results
                )
                results[0]
            } else null

            val dist = liveGpsDist ?: uiState.value.liveAlertDistanceMeters ?: alert.distanceMeters
            if (dist != null) {
                _uiState.update { it.copy(liveAlertDistanceMeters = dist) }
            }

            val distStr = if (dist != null) " (~${String.format(Locale.US, "%.1f", dist)}m)" else ""
            val locLabel = if (loc.hasRealFix && !loc.isMockOrDelhi()) {
                "GPS: ${String.format(Locale.US, "%.4f", loc.latitude)}, ${String.format(Locale.US, "%.4f", loc.longitude)} • TRACKING LIVE$distStr"
            } else {
                "Direct RF Mesh • TRACKING LIVE$distStr"
            }

            // 1. LAN broadcast ACK (rapid zero-pairing UDP delivery < 50ms)
            lanAlertManager.sendBroadcastAck(alert.sequence, payloadHash, "$myName (Tracking)", locLabel)
            // 2. Continuous BLE ACK with isTracking = true (seamless 1-2s proximity)
            bleAlertBroadcaster.broadcastAck(payloadHash, myName, BleAlertBroadcaster.STATUS_TRACKING)
            // 3. Wi-Fi Direct DNS-SD ACK (periodically updated every ~4s)
            if (wifiAckTick % 2 == 0) {
                wifiAlertBroadcaster.broadcastAck(alert.sequence, payloadHash, "$myName (Tracking)", locLabel, durationMs = 15_000L)
            }
            wifiAckTick++
            AppLog.d("MainViewModel", "Sent tracking ACK burst for seq ${alert.sequence} from $myName ($locLabel)")
            // 4. Connected P2P transport socket (if paired & connected)
            transport?.takeIf { it.state == ConnectionState.CONNECTED }?.let { tx ->
                runCatching {
                    tx.send(Packet.text(MessageType.ACK, Language.ENGLISH, alert.sequence, "$payloadHash:$myName (Tracking):$locLabel"))
                }
            }
            delay(2000L) // 2-3 sec real-time update cadence
        }
    }
}

fun MainViewModel.stopTrackingSender() {
    alertTrackingJob?.cancel()
    alertTrackingJob = null
    _uiState.update { it.copy(liveAlertDistanceMeters = null) }
}

fun MainViewModel.dismissAlert() {
    alertTrackingJob?.cancel()
    alertTrackingJob = null
    val active = uiState.value.activeIncomingAlert
    if (active != null) {
        val alertWire = active.content.toWirePayload()
        val payloadHash = alertWire.hashCode()
        val myName = profileStore.snapshot.name.takeIf { it.isNotBlank() } ?: uiState.value.localProfile.displayName.ifBlank { "Responder" }
        val loc = gpsLocationTracker.location.value
        val dist = uiState.value.liveAlertDistanceMeters ?: active.distanceMeters
        val distStr = if (dist != null) " (~${String.format(Locale.US, "%.1f", dist)}m)" else ""
        val locLabel = "GPS: ${String.format(Locale.US, "%.4f", loc.latitude)}, ${String.format(Locale.US, "%.4f", loc.longitude)} • RECEIVED & STOPPED$distStr"

        // Broadcast ACK across all transports — use long durations and repeated bursts
        // so the sender's scanner reliably discovers our ACK
        AppLog.d("MainViewModel", "Sending dismiss ACK for seq ${active.sequence} from $myName")
        wifiAlertBroadcaster.broadcastAck(active.sequence, payloadHash, myName, locLabel, durationMs = 30_000L)
        bleAlertBroadcaster.broadcastAck(payloadHash, myName, BleAlertBroadcaster.STATUS_STOPPED)
        transport?.takeIf { it.state == ConnectionState.CONNECTED }?.let { tx ->
            runCatching {
                tx.send(Packet.text(MessageType.ACK, Language.ENGLISH, active.sequence, "$payloadHash:$myName:$locLabel"))
            }
        }
        // Send repeated LAN ACK bursts to maximize delivery chance
        viewModelScope.launch(Dispatchers.IO) {
            for (i in 1..8) {
                lanAlertManager.sendBroadcastAck(active.sequence, payloadHash, myName, locLabel)
                delay(1500L)
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val alertText = active.content.toWirePayload()
                val (playText, _) = try {
                    val currentLang = uiState.value.currentLanguage
                    if (active.language == currentLang) {
                        alertText to currentLang
                    } else {
                        translationEngine.translateOrSame(alertText, active.language, currentLang) to currentLang
                    }
                } catch (_: Exception) {
                    alertText to active.language
                }

                historyDao.insertMessage(
                    HistoryMessage(
                        id = UUID.randomUUID().toString(),
                        text = "🚨 EMERGENCY ALERT: $playText (${active.senderName})",
                        language = active.language,
                        timestampMs = active.receivedAtMs,
                        direction = MessageDirection.INBOUND,
                        status = MessageStatus.DELIVERED,
                        peerName = active.senderName,
                        isAlert = true
                    )
                )
            } catch (e: Exception) {
                AppLog.w("MainViewModel", "Failed to archive alert into history: ${e.message}")
            }
        }
    }
    alertPlayer.dismissActiveAlert()
    vibratorHelper.stopVibration()
    incomingAlertSmoother.clear()
    wifiAlertScanner.setFastScanMode(false)
    _uiState.update { it.copy(activeIncomingAlert = null, liveAlertDistanceMeters = null) }
}

fun MainViewModel.muteAlertAudio() {
    alertPlayer.dismissActiveAlert()
    vibratorHelper.stopVibration()
}

fun MainViewModel.sendAlert(content: AlertContent) {
    val currentTransport = transport
    val wifiConnected = lanAlertManager.isWifiConnected()
    _uiState.update { it.copy(isWifiConnected = wifiConnected) }

    val canSendViaP2P = currentTransport != null &&
        currentTransport.state == ConnectionState.CONNECTED &&
        uiState.value.pairingConfirmed

    val payload = content.toWirePayload()
    val lang = uiState.value.currentLanguage
    val sequence = kotlin.random.Random.nextInt(1, 255)
    val channel = uiState.value.alertChannel

    AppLog.d("MainViewModel", "Triggering broadcasters for sequence $sequence over channel $channel")
    val senderName = uiState.value.localProfile.displayName

    originatedAlertSequences[sequence] = System.currentTimeMillis()
    announcedAckPeers.clear()
    lanAlertManager.markOriginated(sequence)
    bleAlertBroadcaster.markOriginated(sequence)
    wifiAlertBroadcaster.markOriginated(sequence)
    if (!senderName.isNullOrBlank()) {
        lanAlertManager.setLocalDeviceName(senderName)
    }

    val alertPacket = Packet.text(MessageType.ALERT, lang, sequence, payload)
    relayEngine.markOriginated(alertPacket)
    alertDeliveryTracker.trackAlert(sequence, System.currentTimeMillis(), peerCount = uiState.value.pairedDevices.size)

    val outboundState = OutboundAlertState(
        sequence = sequence,
        content = content,
        language = lang,
        startedAtMs = System.currentTimeMillis(),
        durationMs = 300_000L,
        recipients = emptyList(),
        isMinimized = false,
    )
    _uiState.update { it.copy(activeOutboundAlert = outboundState, activeIncomingAlert = null, liveAlertDistanceMeters = null) }
    vibratorHelper.startBroadcastingVibration()
    wifiAlertScanner.setFastScanMode(true) // Fast-poll every 3s to catch ACKs quickly

    outboundBroadcastJob?.cancel()
    outboundBroadcastJob = viewModelScope.launch(Dispatchers.IO) {
        if (channel == AlertChannel.ALL || channel == AlertChannel.BLUETOOTH) {
            val btAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
                ?: android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (btAdapter != null && btAdapter.isEnabled) {
                try {
                    bleAlertBroadcaster.broadcastAlert(lang, content, sequence.toLong(), senderName, ttl = 3, durationMs = 300_000L)
                } catch (e: Exception) {
                    AppLog.e("MainViewModel", "BLE broadcast crashed", e)
                }
            }
        }

        val loc = gpsLocationTracker.location.value
        val locStr = if (loc.hasRealFix && !loc.isMockOrDelhi()) {
            "${String.format(Locale.US, "%.5f", loc.latitude)}, ${String.format(Locale.US, "%.5f", loc.longitude)}"
        } else null

        if (channel == AlertChannel.ALL || channel == AlertChannel.WIFI) {
            try {
                wifiAlertBroadcaster.broadcastAlert(lang, content, sequence.toLong(), senderName, durationMs = 300_000L, senderLoc = locStr)
            } catch (e: Exception) {
                AppLog.e("MainViewModel", "Wi-Fi broadcast crashed", e)
            }
        }

        var loopIteration = 0
        while (isActive) {
            val current = uiState.value.activeOutboundAlert
            if (current == null || current.sequence != sequence || current.isExpired) {
                break
            }
            if (channel == AlertChannel.ALL || channel == AlertChannel.WIFI) {
                try {
                    lanAlertManager.sendBroadcastAlert(
                        language = lang,
                        content = content,
                        sequence = sequence,
                        senderName = senderName ?: "Emergency Unit",
                        senderLoc = locStr,
                    )
                } catch (e: Exception) {
                    AppLog.w("MainViewModel", "LAN periodic burst failed: ${e.message}")
                }
            }
            loopIteration++
            // Rapid bursts (every 600ms) for first 6 seconds guarantees instant sub-second delivery
            val nextDelay = if (loopIteration < 10) 600L else 2_000L
            delay(nextDelay)
        }

        if (uiState.value.activeOutboundAlert?.sequence == sequence) {
            stopAlertBroadcast()
        }
    }

    if (!canSendViaP2P) {
        val queuedMsg = outboundQueue.enqueue(lang, payload, isAlert = true)
        if (queuedMsg != null) {
            outboundQueue.markSending(queuedMsg.id)
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
                        status = MessageStatus.SENT,
                        peerName = null,
                        isAlert = true
                    )
                )
            }
        }
        publishQueues()
        _uiState.update {
            it.copy(notice = UserNotice.Raw("Emergency Alert broadcasting across Wi-Fi Direct & mesh channels..."))
        }
        return
    }

    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(alertSending = true, notice = null) }
        try {
            val loc = gpsLocationTracker.location.value
            val locStr = if (loc.latitude != 0.0 || loc.longitude != 0.0) {
                "${String.format(Locale.US, "%.5f", loc.latitude)}, ${String.format(Locale.US, "%.5f", loc.longitude)}"
            } else null
            sendAlertUseCase.execute(
                transport = currentTransport,
                language = lang,
                content = content,
                pairingConfirmed = true,
                sequenceNum = sequence,
                senderName = senderName,
                senderLoc = locStr,
            )
            val queuedMsg = outboundQueue.enqueue(lang, payload, isAlert = true)
            if (queuedMsg != null) {
                outboundQueue.markSent(queuedMsg.id, uiState.value.talkingToName)
                val list = recentAlertIds.getOrPut(payload.hashCode()) { mutableListOf() }
                if (!list.contains(queuedMsg.id)) list.add(queuedMsg.id)
                publishQueues()
            }
            _uiState.update { it.copy(alertSending = false, notice = UserNotice.AlertSent) }
            val historyId = queuedMsg?.id ?: UUID.randomUUID().toString()
            historyDao.insertMessage(
                HistoryMessage(
                    id = historyId,
                    text = payload,
                    language = lang,
                    timestampMs = System.currentTimeMillis(),
                    direction = MessageDirection.OUTBOUND,
                    status = MessageStatus.DELIVERED,
                    peerName = uiState.value.talkingToName,
                    isAlert = true
                )
            )
        } catch (e: Exception) {
            val queuedMsg = outboundQueue.enqueue(lang, payload, isAlert = true)
            if (queuedMsg != null) {
                outboundQueue.markSending(queuedMsg.id)
                val list = recentAlertIds.getOrPut(payload.hashCode()) { mutableListOf() }
                if (!list.contains(queuedMsg.id)) list.add(queuedMsg.id)
                historyDao.insertMessage(
                    HistoryMessage(
                        id = queuedMsg.id,
                        text = payload,
                        language = lang,
                        timestampMs = queuedMsg.createdAtMs,
                        direction = MessageDirection.OUTBOUND,
                        status = MessageStatus.SENT,
                        peerName = null,
                        isAlert = true
                    )
                )
            }
            publishQueues()
            _uiState.update { it.copy(alertSending = false, notice = UserNotice.Raw("Broadcasting alert over Wi-Fi Direct & mesh (P2P stream error: ${e.message})")) }
        }
    }
}

fun MainViewModel.minimizeOutboundAlert() {
    _uiState.update { it.copy(activeOutboundAlert = it.activeOutboundAlert?.copy(isMinimized = true)) }
}

fun MainViewModel.expandOutboundAlert() {
    _uiState.update { it.copy(activeOutboundAlert = it.activeOutboundAlert?.copy(isMinimized = false)) }
}

fun MainViewModel.stopOutboundAlert() {
    stopAlertBroadcast()
}

fun MainViewModel.stopAlertBroadcast() {
    val activeSeq = _uiState.value.activeOutboundAlert?.sequence
    outboundBroadcastJob?.cancel()
    outboundBroadcastJob = null
    bleAlertBroadcaster.stopBroadcasting()
    wifiAlertBroadcaster.stopBroadcasting()
    wifiAlertScanner.setFastScanMode(false) // Return to normal 15s discovery interval
    if (activeSeq != null) {
        originatedAlertSequences.remove(activeSeq)
        lanAlertManager.clearOriginated(activeSeq)
        bleAlertBroadcaster.clearOriginated(activeSeq)
        wifiAlertBroadcaster.clearOriginated(activeSeq)
    }
    announcedAckPeers.clear()
    alertPlayer.dismissActiveAlert()
    vibratorHelper.stopVibration()
    peerSignalSmoothers.clear()
    for (item in outboundQueue.snapshot()) {
        if (item.isAlert && item.state == OutboundState.SENDING) {
            if (!item.receiverName.isNullOrBlank()) {
                outboundQueue.markSent(item.id, item.receiverName, item.distanceMeters, item.locationLabel)
            } else {
                outboundQueue.markFailed(item.id)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        historyDao.updateMessageStatus(item.id, MessageStatus.FAILED)
                    } catch (e: Exception) {
                        AppLog.w("MainViewModel", "Failed to update history status for cancelled alert: ${e.message}")
                    }
                }
            }
        }
    }
    publishQueues()
    _uiState.update { it.copy(alertSending = false, activeOutboundAlert = null, notice = null) }
}

fun MainViewModel.refreshWifiState() {
    _uiState.update { it.copy(isWifiConnected = lanAlertManager.isWifiConnected()) }
}

fun MainViewModel.setAlertChannel(channel: AlertChannel) {
    _uiState.update { it.copy(alertChannel = channel) }
}

fun MainViewModel.recordAlertRecipient(
    peerName: String,
    distanceMeters: Float?,
    rssiDbm: Int? = null,
    locationLabel: String? = null,
    isTracking: Boolean = false,
) {
    _uiState.update { current ->
        val active = current.activeOutboundAlert ?: return@update current
        val cleanName = peerName.replace(" (Tracking)", "").trim()

        fun isGeneric(name: String): Boolean {
            val n = name.replace(" (Tracking)", "").trim()
            return n.isBlank() ||
                n.equals("Responder", ignoreCase = true) ||
                n.startsWith("Responder", ignoreCase = true) ||
                n.equals("Peer", ignoreCase = true) ||
                n.startsWith("Peer", ignoreCase = true) ||
                n.equals("Nearby Peer", ignoreCase = true) ||
                n.equals("Wi-Fi Peer", ignoreCase = true)
        }

        val incomingIsGeneric = isGeneric(cleanName)

        var matchIndex = active.recipients.indexOfFirst {
            it.peerName.replace(" (Tracking)", "").trim().equals(cleanName, ignoreCase = true)
        }

        if (matchIndex < 0) {
            if (!incomingIsGeneric) {
                // If incoming is a concrete name (e.g. "Suman"), replace ANY existing generic entry
                val genericIdx = active.recipients.indexOfFirst { isGeneric(it.peerName) }
                if (genericIdx >= 0) {
                    matchIndex = genericIdx
                }
            } else {
                // If incoming is generic (e.g. "Responder"), merge into existing entry so we NEVER create a 2nd responder
                if (active.recipients.isNotEmpty()) {
                    matchIndex = 0
                }
            }
        }

        val existingItem = if (matchIndex >= 0) active.recipients[matchIndex] else null
        val resolvedName = when {
            matchIndex >= 0 && incomingIsGeneric -> existingItem?.peerName ?: cleanName
            !incomingIsGeneric -> cleanName
            else -> cleanName.ifBlank { "Responder" }
        }

        val isExplicitStopped = locationLabel?.contains("STOPPED", ignoreCase = true) == true
        val effectiveTracking = !isExplicitStopped && (
            isTracking ||
            (locationLabel?.contains("TRACKING", ignoreCase = true) == true) ||
            peerName.contains("Tracking", ignoreCase = true)
        )

        // Retain and update distance and location for ALL states (Tracking, Acknowledged, Stopped)
        val extractedDist = distanceMeters ?: locationLabel?.let { parseEmbeddedDistance(it) }
        val effectiveDist = extractedDist ?: existingItem?.distanceMeters

        val effectiveLocation = when {
            locationLabel?.isNotBlank() == true -> locationLabel
            existingItem?.locationLabel?.isNotBlank() == true -> existingItem.locationLabel
            effectiveTracking -> "Direct RF Proximity • TRACKING LIVE"
            isExplicitStopped -> "Direct RF Proximity • RECEIVED & STOPPED"
            else -> "Direct RF Proximity • RECEIVED"
        }

        val updatedRecipient = AlertRecipient(
            peerName = resolvedName,
            distanceMeters = effectiveDist,
            locationLabel = effectiveLocation,
            ackTimestampMs = System.currentTimeMillis(),
            rssiDbm = rssiDbm ?: existingItem?.rssiDbm,
            isTracking = effectiveTracking,
        )
        val updatedList = if (matchIndex >= 0) {
            active.recipients.toMutableList().apply { set(matchIndex, updatedRecipient) }
        } else {
            active.recipients + updatedRecipient
        }

        // Clean out any generic "Responder" duplicates if a real name is now known
        val hasConcrete = updatedList.any { !isGeneric(it.peerName) }
        val finalList = if (hasConcrete) {
            updatedList.filter { !isGeneric(it.peerName) }
        } else {
            updatedList.take(1)
        }

        current.copy(
            activeOutboundAlert = active.copy(
                recipients = finalList
            )
        )
    }
}

fun MainViewModel.estimateDistanceMeters(rssi: Int?, isWifi: Boolean = false): Float? {
    if (rssi == null || rssi == 0 || rssi == -127) return null
    val txPower = if (isWifi) -48.0 else -59.0
    val pathLossExponent = if (isWifi) 2.7 else 2.6
    val dist = 10.0.pow((txPower - rssi) / (10.0 * pathLossExponent)).toFloat()
    return dist.coerceIn(0.5f, 60.0f)
}
