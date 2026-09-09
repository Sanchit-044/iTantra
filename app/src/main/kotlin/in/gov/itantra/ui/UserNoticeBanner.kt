package `in`.gov.itantra.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import `in`.gov.itantra.core.lang.UiStrings

@Composable
fun UserNoticeBanner(
    notice: UserNotice?,
    strings: UiStrings,
    modifier: Modifier = Modifier,
) {
    if (notice == null) return
    val text = notice.format(strings)
    if (text.isBlank()) return
    Text(
        text = text,
        color = if (notice.isSuccess) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.error
        },
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier,
    )
}
