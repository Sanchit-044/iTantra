package `in`.gov.itantra.ui

import `in`.gov.itantra.core.lang.UiStrings

sealed class UserNotice {
    data class ConnectionFailed(val detail: String?) : UserNotice()
    data class PairingFailed(val detail: String?) : UserNotice()
    data class PlaybackError(val detail: String?) : UserNotice()
    data class QueueSendFailed(val detail: String?) : UserNotice()
    data class GenericError(val detail: String?) : UserNotice()
    data class Raw(val detail: String?) : UserNotice()
    data object PleaseSelectDevice : UserNotice()
    data object AlertSent : UserNotice()

    fun format(strings: UiStrings): String {
        fun clean(msg: String?): String? {
            if (msg.isNullOrBlank()) return null
            val line = msg.lines().firstOrNull()?.trim() ?: return null
            val stripped = line.substringAfterLast(": ").trim()
            val text = if (stripped.isNotBlank()) stripped else line
            return text.take(60)
        }

        return when (this) {
            is ConnectionFailed -> strings.connectionFailed(clean(detail))
            is PairingFailed -> strings.pairingFailed(clean(detail))
            is PlaybackError -> strings.playbackError(clean(detail))
            is QueueSendFailed -> strings.queueSendFailed(clean(detail))
            is GenericError -> strings.genericError(clean(detail))
            is Raw -> clean(detail) ?: strings.couldNotSave
            PleaseSelectDevice -> strings.pleaseSelectDevice
            AlertSent -> strings.alertSent
        }
    }

    val isSuccess: Boolean get() = this is AlertSent
}
