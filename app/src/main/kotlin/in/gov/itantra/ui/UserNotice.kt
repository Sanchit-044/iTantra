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

    fun format(strings: UiStrings): String = when (this) {
        is ConnectionFailed -> strings.connectionFailed(detail)
        is PairingFailed -> strings.pairingFailed(detail)
        is PlaybackError -> strings.playbackError(detail)
        is QueueSendFailed -> strings.queueSendFailed(detail)
        is GenericError -> strings.genericError(detail)
        is Raw -> detail?.takeIf { it.isNotBlank() } ?: strings.couldNotSave
        PleaseSelectDevice -> strings.pleaseSelectDevice
        AlertSent -> strings.alertSent
    }

    val isSuccess: Boolean get() = this is AlertSent
}
