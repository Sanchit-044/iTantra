package `in`.gov.itantra.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloseFullscreen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.gov.itantra.core.alert.AlertContent
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.ui.components.PulseRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale

/**
 * Dedicated Full-Screen Active Emergency Broadcast Screen for the Alert Sender.
 *
 * Displays:
 * 1. Live multi-radio transmission status & 5-minute countdown.
 * 2. Real-time list of nearby units that received and acknowledged the alert (with distance, RSSI, location).
 * 3. Stop Alert (exit) option and Minimize option.
 * 4. Automatic minimization on system back navigation.
 */
@Composable
fun OutboundAlertFullScreen(
    outboundAlert: OutboundAlertState,
    chrome: UiStrings,
    onStopAlert: () -> Unit,
    onMinimize: () -> Unit,
) {
    // Intercept hardware / gesture back button to auto-minimize instead of killing the emergency broadcast
    BackHandler {
        onMinimize()
    }

    var remainingSeconds by remember(outboundAlert.startedAtMs) {
        mutableIntStateOf(outboundAlert.remainingSeconds)
    }

    LaunchedEffect(outboundAlert.startedAtMs) {
        while (remainingSeconds > 0 && isActive) {
            delay(1000L)
            remainingSeconds = outboundAlert.remainingSeconds
        }
    }

    val alertText = remember(outboundAlert.content, outboundAlert.language) {
        when (val c = outboundAlert.content) {
            is AlertContent.Template -> c.template.phrase(outboundAlert.language)
            is AlertContent.Custom -> AlertTemplate.resolveDisplayText(
                c.text,
                outboundAlert.language
            )
        }
    }

    val transition = rememberInfiniteTransition(label = "outboundPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Subtle ambient emergency gradient background extends edge-to-edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.error.copy(alpha = 0.18f),
                                Color.Transparent,
                            )
                        )
                    )
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing),
                contentAlignment = Alignment.TopCenter,
            ) {
                val isCompactHeight = maxHeight < 680.dp
                val isSmallWidth = maxWidth < 380.dp
                val horizontalPadding = if (isSmallWidth) 14.dp else 20.dp
                val verticalPadding = if (isCompactHeight) 8.dp else 14.dp
                val buttonHeight = if (isCompactHeight) 46.dp else 52.dp

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                ) {
                    // 1. Top Header with Emergency Pulse Badge & Minimize Icon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f, fill = false),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .scale(pulseScale)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EMERGENCY BROADCAST ACTIVE",
                                style = if (isCompactHeight) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.error,
                                letterSpacing = if (isCompactHeight) 0.8.sp else 1.2.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        IconButton(
                            onClick = onMinimize,
                            modifier = Modifier.size(if (isCompactHeight) 36.dp else 44.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloseFullscreen,
                                contentDescription = "Minimize",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 12.dp))

                    // 2. Countdown Timer & Status Header Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(if (isCompactHeight) 16.dp else 20.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        ),
                        border = BorderStroke(
                            1.5.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(if (isCompactHeight) 12.dp else 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Transmitting Multi-Radio Beacon",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = if (isCompactHeight) 11.sp else 12.sp,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = alertText,
                                        style = if (isCompactHeight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        maxLines = if (isCompactHeight) 1 else 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                // Circular Timer Countdown
                                val minutes = remainingSeconds / 60
                                val seconds = remainingSeconds % 60
                                val progress = (remainingSeconds.toFloat() / 300f).coerceIn(0f, 1f)

                                Box(contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.size(if (isCompactHeight) 44.dp else 52.dp),
                                        color = MaterialTheme.colorScheme.error,
                                        trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                                        strokeWidth = if (isCompactHeight) 3.5.dp else 4.dp,
                                    )
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "%02d:%02d",
                                            minutes,
                                            seconds
                                        ),
                                        style = if (isCompactHeight) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 12.dp))

                            // Radio Channel Status Badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                RadioStatusChip(
                                    title = "BLE Mesh",
                                    active = true,
                                    modifier = Modifier.weight(1f)
                                )
                                RadioStatusChip(
                                    title = "Wi-Fi Direct",
                                    active = true,
                                    modifier = Modifier.weight(1f)
                                )
                                RadioStatusChip(
                                    title = "LAN UDP",
                                    active = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 14.dp))

                    // 3. Section Title: Confirmed Reached Devices
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Confirmed Reached Devices (${outboundAlert.recipients.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                        )

                        if (outboundAlert.recipients.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "LIVE",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Live Received Devices List or Radar Searching Animation
                    if (outboundAlert.recipients.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(if (isCompactHeight) 16.dp else 20.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(if (isCompactHeight) 14.dp else 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Box(
                                    modifier = Modifier.size(if (isCompactHeight) 76.dp else 96.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PulseRing(
                                        color = MaterialTheme.colorScheme.error,
                                        size = if (isCompactHeight) 68.dp else 88.dp,
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Sensors,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(if (isCompactHeight) 30.dp else 38.dp),
                                    )
                                }

                                Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 16.dp))

                                Text(
                                    text = "Broadcasting Emergency Signal...",
                                    style = if (isCompactHeight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Broadcasting continuously across BLE, Wi-Fi Direct, and LAN. Nearby devices will appear here automatically as soon as they acknowledge.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = if (isCompactHeight) 3 else 4,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 6.dp else 10.dp),
                            contentPadding = PaddingValues(bottom = 6.dp),
                        ) {
                            items(outboundAlert.recipients, key = { it.peerName }) { recipient ->
                                RecipientDeviceCard(recipient = recipient)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 10.dp else 16.dp))

                    // 5. Bottom Action Controls (Exit / Stop Broadcast & Minimize)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(if (isSmallWidth) 8.dp else 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(
                            onClick = onMinimize,
                            modifier = Modifier
                                .weight(1f)
                                .height(buttonHeight),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloseFullscreen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Minimize",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (isSmallWidth) 12.sp else 13.5.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Button(
                            onClick = onStopAlert,
                            modifier = Modifier
                                .weight(1.15f)
                                .height(buttonHeight),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Stop Broadcast",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = if (isSmallWidth) 12.sp else 13.5.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Chip displaying live status of a radio channel */
@Composable
private fun RadioStatusChip(title: String, active: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (active) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(
            1.dp,
            if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        if (active) Color(0xFF4CAF50) else Color.Gray,
                        CircleShape,
                    )
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Card representing a single device that acknowledged receipt of the emergency alert */
@Composable
private fun RecipientDeviceCard(recipient: AlertRecipient) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF4CAF50).copy(alpha = 0.15f),
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Received",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = recipient.peerName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = recipient.locationLabel ?: "Nearby Mesh Node",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Live Distance & Signal Badge
            Column(horizontalAlignment = Alignment.End) {
                if (recipient.distanceMeters != null) {
                    Text(
                        text = "~${
                            String.format(
                                Locale.US,
                                "%.1f",
                                recipient.distanceMeters
                            )
                        }m",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    Text(
                        text = "ACK",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4CAF50),
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                val rssiText = recipient.rssiDbm?.let { "$it dBm" } ?: "Online"
                Text(
                    text = rssiText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

/**
 * Minimized persistent floating banner displayed on main screens when the alert broadcast is minimized.
 */
@Composable
fun MinimizedOutboundAlertBanner(
    outboundAlert: OutboundAlertState,
    onExpand: () -> Unit,
    onStopAlert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val remainingSeconds = outboundAlert.remainingSeconds
    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onExpand() },
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(MaterialTheme.colorScheme.error, CircleShape)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "SOS Broadcasting (${
                            String.format(
                                Locale.US,
                                "%02d:%02d",
                                minutes,
                                seconds
                            )
                        })",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Text(
                        text = "${outboundAlert.recipients.size} devices confirmed reached • Tap to expand",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExpand) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                IconButton(onClick = onStopAlert) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
