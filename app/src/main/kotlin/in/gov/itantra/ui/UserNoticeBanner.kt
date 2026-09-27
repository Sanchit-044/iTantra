package `in`.gov.itantra.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.ui.components.InlineMessage

@Composable
fun UserNoticeBanner(
    notice: UserNotice?,
    strings: UiStrings,
    modifier: Modifier = Modifier,
) {
    if (notice == null) return
    val text = notice.format(strings)
    if (text.isBlank()) return
    InlineMessage(
        text = text,
        isError = !notice.isSuccess,
        icon = if (notice.isSuccess) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
        modifier = modifier,
    )
}
