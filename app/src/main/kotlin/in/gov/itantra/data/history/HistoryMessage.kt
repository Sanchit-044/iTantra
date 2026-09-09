package `in`.gov.itantra.data.history

import androidx.room.Entity
import androidx.room.PrimaryKey
import `in`.gov.itantra.core.Language

enum class MessageDirection {
    INBOUND, OUTBOUND
}

enum class MessageStatus {
    SENT, DELIVERED, FAILED, RECEIVED, QUEUED
}

@Entity(tableName = "history_messages")
data class HistoryMessage(
    @PrimaryKey
    val id: String,
    val text: String,
    val language: Language,
    val timestampMs: Long,
    val direction: MessageDirection,
    val status: MessageStatus,
    val peerName: String? = null,
    val isAlert: Boolean = false
)
