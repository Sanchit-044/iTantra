package `in`.gov.itantra.ui

import java.util.UUID
import androidx.lifecycle.viewModelScope
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.diag.AppLog
import `in`.gov.itantra.core.transport.Packet
import `in`.gov.itantra.core.translate.translateOrSame
import `in`.gov.itantra.data.history.HistoryMessage
import `in`.gov.itantra.data.history.MessageDirection
import `in`.gov.itantra.data.history.MessageStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

fun MainViewModel.sendQuickChat(text: String) {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return
    val state = uiState.value
    if (isLiveReady()) {
        if (!state.isSpeaking && !state.isRequestingFloor && !state.channelBusy) {
            sendQuickChatLive(trimmed)
            return
        }
        synchronized(pendingQuickChats) { pendingQuickChats.addLast(trimmed) }
        transport?.requestFloor()
    } else {
        queueQuickChat(trimmed)
    }
}

fun MainViewModel.sendQuickChatLive(text: String) {
    val currentTransport = transport ?: return
    val lang = uiState.value.currentLanguage
    try {
        val packet = Packet.text(
            type = `in`.gov.itantra.core.transport.MessageType.NORMAL,
            language = lang,
            sequence = quickChatSequence.incrementAndGet(),
            text = text,
        )
        currentTransport.send(packet)
        val queuedMsg = outboundQueue.enqueue(lang, text, isAlert = false)
        if (queuedMsg != null) {
            outboundQueue.markSent(queuedMsg.id, uiState.value.talkingToName)
            publishQueues()
        }
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.insertMessage(
                HistoryMessage(
                    id = queuedMsg?.id ?: UUID.randomUUID().toString(),
                    text = text,
                    language = lang,
                    timestampMs = packet.timestampMs,
                    direction = MessageDirection.OUTBOUND,
                    status = MessageStatus.DELIVERED,
                    peerName = uiState.value.talkingToName,
                    isAlert = false,
                )
            )
        }
    } catch (e: Exception) {
        AppLog.w("MainViewModel", "Failed to send quick chat live: ${e.message}, queuing it instead")
        queueQuickChat(text)
    } finally {
        currentTransport.releaseFloor()
        drainNextQuickChat()
    }
}

fun MainViewModel.drainNextQuickChat() {
    val hasMore = synchronized(pendingQuickChats) { pendingQuickChats.isNotEmpty() }
    if (hasMore) transport?.requestFloor()
}

fun MainViewModel.queueQuickChat(text: String) {
    val lang = uiState.value.currentLanguage
    val queuedMsg = outboundQueue.enqueue(lang, text, isAlert = false)
    publishQueues()
    if (queuedMsg != null) {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.insertMessage(
                HistoryMessage(
                    id = queuedMsg.id,
                    text = text,
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

fun MainViewModel.handleQueuedInbound(packet: Packet) {
    val id = inboundId(packet)
    val stored = inbox.offer(id, packet.language, packet.text, packet.timestampMs)
    publishQueues()
    if (stored != null) {
        notifier.notifyUnread(inbox.unreadCount(), stored.text, uiState.value.uiLanguage)
    }
}

fun MainViewModel.flushQueue() {
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

fun MainViewModel.dismissInbox(id: String) {
    inbox.discard(id)
    viewModelScope.launch(Dispatchers.IO) {
        try {
            historyDao.deleteMessage(id)
        } catch (e: Exception) {
            AppLog.w("MainViewModel", "Failed to delete history for inbox item: ${e.message}")
        }
    }
    publishQueues()
}

fun MainViewModel.deleteQueuedMessage(id: String) {
    val queued = outboundQueue.snapshot().firstOrNull { it.id == id }
    if (queued != null && queued.isAlert) {
        bleAlertBroadcaster.stopBroadcasting()
        wifiAlertBroadcaster.stopBroadcasting()
        recentAlertIds.remove(queued.text.hashCode())
    }
    outboundQueue.discard(id)
    viewModelScope.launch(Dispatchers.IO) {
        try {
            historyDao.deleteMessage(id)
        } catch (e: Exception) {
            AppLog.w("MainViewModel", "Failed to delete history for queued item: ${e.message}")
        }
    }
    publishQueues()
}

fun MainViewModel.retryQueuedMessage(id: String) {
    val queued = outboundQueue.snapshot().firstOrNull { it.id == id }
    if (queued != null && queued.isAlert) {
        val content = `in`.gov.itantra.core.alert.AlertTemplate.fromWirePayload(queued.text)
        outboundQueue.retry(id)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                historyDao.updateMessageStatus(id, MessageStatus.QUEUED)
            } catch (e: Exception) {
                AppLog.w("MainViewModel", "Failed to update history status for queued alert: ${e.message}")
            }
        }
        sendAlert(content)
        return
    }

    outboundQueue.retry(id)
    viewModelScope.launch(Dispatchers.IO) {
        try {
            historyDao.updateMessageStatus(id, MessageStatus.QUEUED)
        } catch (e: Exception) {
            AppLog.w("MainViewModel", "Failed to update history status for queued item: ${e.message}")
        }
    }
    publishQueues()
    if (isLiveReady()) flushQueue()
}

fun MainViewModel.deleteHistoryMessage(id: String) {
    val queued = outboundQueue.snapshot().firstOrNull { it.id == id }
    if (queued != null && queued.isAlert) {
        bleAlertBroadcaster.stopBroadcasting()
        wifiAlertBroadcaster.stopBroadcasting()
        recentAlertIds.remove(queued.text.hashCode())
    }
    outboundQueue.discard(id)
    inbox.discard(id)
    viewModelScope.launch(Dispatchers.IO) {
        try {
            historyDao.deleteMessage(id)
        } catch (e: Exception) {
            AppLog.w("MainViewModel", "Failed to delete history message: ${e.message}")
        }
    }
    publishQueues()
}

fun MainViewModel.playInbox(id: String) {
    val item = inbox.find(id) ?: return
    if (uiState.value.playingInboxId != null) return
    viewModelScope.launch(Dispatchers.IO) {
        _uiState.update { it.copy(playingInboxId = id, isPlayingAudio = true, receivingText = item.text, notice = null) }
        try {
            val currentLang = uiState.value.currentLanguage
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
            _uiState.update { it.copy(playingInboxId = null, isPlayingAudio = false, receivingText = "") }
            publishQueues()
        }
    }
}

fun MainViewModel.publishQueues(notifyIfUnread: Boolean = false) {
    val unread = inbox.unreadCount()
    if (unread == 0) notifier.cancel()
    else if (notifyIfUnread) {
        val preview = inbox.snapshot().firstOrNull { it.unread }?.text.orEmpty()
        notifier.notifyUnread(unread, preview, uiState.value.uiLanguage)
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

fun MainViewModel.inboundId(packet: Packet): String =
    "${packet.timestampMs}:${packet.sequence}:${packet.text.hashCode()}"
