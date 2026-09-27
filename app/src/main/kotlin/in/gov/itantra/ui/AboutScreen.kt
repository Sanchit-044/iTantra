package `in`.gov.itantra.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.gov.itantra.BuildConfig
import `in`.gov.itantra.core.Language
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.ui.components.SettingsGroup
import `in`.gov.itantra.ui.components.SettingsRow
import `in`.gov.itantra.ui.components.StatusPill
import `in`.gov.itantra.ui.components.SubScreenScaffold

/** Dedicated About screen detailing the iTantra platform architecture and specifications. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(
    uiLanguage: Language = Language.ENGLISH,
    onBack: () -> Unit,
) {
    val chrome = UiStrings.forLanguage(uiLanguage)

    SubScreenScaffold(
        title = chrome.sectionAbout,
        onBack = onBack,
        backDescription = chrome.back,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // App Hero Banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Radio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(38.dp),
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = chrome.appTitle,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_TYPE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        text = "ISRO Smart India Hackathon · PS 26173",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Spacer(Modifier.height(14.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatusPill(
                            text = "100% Offline AI",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            icon = Icons.Outlined.CheckCircle,
                        )
                        StatusPill(
                            text = "AES-256-GCM",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            icon = Icons.Outlined.Lock,
                        )
                        StatusPill(
                            text = "P2P Mesh / Direct",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            icon = Icons.Outlined.CellTower,
                        )
                    }
                }
            }

            // Architecture Section
            SettingsGroup("ARCHITECTURE & CORE ENGINES") {
                SettingsRow(
                    icon = Icons.Outlined.Mic,
                    title = "Speech-to-Text (STT)",
                    subtitle = "On-device AI4Bharat IndicWav2Vec via ONNX Runtime CPU",
                    showDivider = false,
                )
                SettingsRow(
                    icon = Icons.Outlined.Translate,
                    title = "Translation (NMT)",
                    subtitle = "IndicTrans2 320M INT8 quantized with BPE tokenization",
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Outlined.Lock,
                    title = "End-to-End Encryption",
                    subtitle = "AES-256-GCM with binary packet header as AAD",
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Outlined.CellTower,
                    title = "Offline Transports",
                    subtitle = "Wi-Fi Direct P2P, BLE discovery & LAN UDP broadcast",
                    showDivider = true,
                )
            }

            // Specifications Section
            SettingsGroup("SYSTEM SPECIFICATIONS") {
                SettingsRow(
                    icon = Icons.Outlined.Memory,
                    title = "Hardware Profile",
                    subtitle = "minSdk 24 (Android 7.0+), ~2 GB RAM, zero GPU dependency",
                    showDivider = false,
                )
                SettingsRow(
                    icon = Icons.Outlined.Language,
                    title = "Supported Languages",
                    subtitle = "10 Official Indic languages: Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, Bengali, English",
                    showDivider = true,
                )
                SettingsRow(
                    icon = Icons.Outlined.Security,
                    title = "Privacy & Telemetry",
                    subtitle = "Zero analytics, zero tracking, no cloud servers or external accounts",
                    showDivider = true,
                )
            }

            // Mission Statement Footer
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mission Objective",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "iTantra provides a fully autonomous, offline walkie-talkie communication pipeline for mission-critical operations. Speech is transcribed locally, encrypted, transmitted over peer-to-peer ad-hoc links, and synthesized on the receiver with instant language translation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
