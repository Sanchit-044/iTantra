package `in`.gov.itantra.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File

@Composable
fun ProfileScreen(
    isSetup: Boolean,
    onFinished: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(isSetup) {
        if (!isSetup) viewModel.reload()
    }

    LaunchedEffect(uiState.finished) {
        if (uiState.finished) {
            onFinished()
            viewModel.consumeFinished()
        }
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.importPhoto(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (isSetup) "Your profile" else "Edit profile",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Name and photo are required. The photo stays on this phone until you pair, then a small copy is sent to the other phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        ProfileAvatar(
            path = uiState.photoPath,
            bytes = null,
            modifier = Modifier.size(120.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { picker.launch("image/*") },
            enabled = !uiState.busy,
        ) {
            Text(if (uiState.photoPresent) "Change photo" else "Add photo")
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::setName,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Name") },
            singleLine = true,
            enabled = !uiState.busy,
        )

        uiState.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }

        Spacer(Modifier.weight(1f))
        Button(
            onClick = { viewModel.confirm() },
            enabled = uiState.canContinue,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (isSetup) "Continue" else "Save")
        }
        if (onBack != null) {
            TextButton(
                onClick = onBack,
                enabled = !uiState.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
fun ProfileAvatar(
    path: String?,
    bytes: ByteArray?,
    modifier: Modifier = Modifier,
) {
    val bitmap = when {
        !path.isNullOrBlank() && File(path).isFile ->
            BitmapFactory.decodeFile(path)
        bytes != null && bytes.isNotEmpty() ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        else -> null
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    } else {
        Spacer(
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}
