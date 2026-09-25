package `in`.gov.itantra.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.ui.components.AppBarTitle
import `in`.gov.itantra.ui.components.AppTopBar
import `in`.gov.itantra.ui.components.BottomActionBar
import `in`.gov.itantra.ui.components.InlineMessage
import `in`.gov.itantra.ui.components.MaxWidthBox
import `in`.gov.itantra.ui.components.ProfileAvatar
import `in`.gov.itantra.ui.components.SetupHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    isSetup: Boolean,
    onFinished: () -> Unit,
    onBack: (() -> Unit)? = null,
    uiLanguage: Language = Language.ENGLISH,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // First-run setup is always English, matching the language picker that follows it.
    val chrome = UiStrings.forLanguage(if (isSetup) Language.ENGLISH else uiLanguage)

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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            if (!isSetup) {
                AppTopBar(
                    title = { AppBarTitle(chrome.editProfile) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = chrome.back)
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            BottomActionBar {
                Button(
                    onClick = { viewModel.confirm() },
                    enabled = uiState.canContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                ) {
                    if (uiState.busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(if (isSetup) chrome.continueLabel else chrome.save, style = MaterialTheme.typography.titleMedium)
                        if (isSetup) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        },
    ) { padding ->
        MaxWidthBox(modifier = Modifier.padding(padding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isSetup) {
                SetupHeader(
                    step = 1,
                    totalSteps = 3,
                    stepLabel = chrome.step(1, 3),
                    title = chrome.profileTitle,
                )
                Spacer(Modifier.height(32.dp))
            } else {
                Spacer(Modifier.height(8.dp))
            }

            // Avatar Picker with Edit Badge
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = !uiState.busy) { picker.launch("image/*") }
            ) {
                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(6.dp),
                ) {
                    ProfileAvatar(
                        path = uiState.photoPath,
                        bytes = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CameraAlt,
                        contentDescription = chrome.changePhoto,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            `in`.gov.itantra.ui.components.LabeledBasicTextField(
                value = uiState.name,
                onValueChange = viewModel::setName,
                label = chrome.nameLabel,
                leadingIcon = Icons.Filled.Person,
                singleLine = true,
                enabled = !uiState.busy,
            )

            uiState.error?.let { error ->
                Spacer(Modifier.height(8.dp))
                InlineMessage(text = error, isError = true)
            }

        }
    }
}
}
