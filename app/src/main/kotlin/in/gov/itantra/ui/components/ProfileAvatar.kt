package `in`.gov.itantra.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import java.io.File

@Composable
fun ProfileAvatar(
    path: String?,
    bytes: ByteArray?,
    modifier: Modifier = Modifier,
) {
    // Keyed on the file's mtime too so a re-picked photo at the same path refreshes.
    val stamp = path?.let { File(it).lastModified() } ?: 0L
    val bitmap = remember(path, stamp, bytes) {
        when {
            !path.isNullOrBlank() && File(path).isFile ->
                BitmapFactory.decodeFile(path)
            bytes != null && bytes.isNotEmpty() ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            else -> null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        BoxWithConstraints(
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(minOf(maxWidth, maxHeight) * 0.6f),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}
