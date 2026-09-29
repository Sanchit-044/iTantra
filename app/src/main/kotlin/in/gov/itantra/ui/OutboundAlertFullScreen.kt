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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
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
import androidx.compose.ui.graphics.lerp
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
                val isUltraCompactHeight = maxHeight < 620.dp
                val isCompactHeight = maxHeight < 740.dp
                val isSmallWidth = maxWidth < 380.dp
                val isExtraSmallWidth = maxWidth < 330.dp

                val horizontalPadding = when {
                    isExtraSmallWidth -> 10.dp
                    isSmallWidth -> 14.dp
                    else -> 20.dp
                }
                val verticalPadding = when {
                    isUltraCompactHeight -> 6.dp
                    isCompactHeight -> 10.dp
                    else -> 14.dp
                }
                val buttonHeight = when {
                    isUltraCompactHeight -> 42.dp
                    isCompactHeight -> 46.dp
                    else -> 50.dp
                }
                val cardCornerRadius = if (isCompactHeight) 16.dp else 22.dp
                val cardInnerPadding = when {
                    isUltraCompactHeight -> 10.dp
                    isCompactHeight -> 14.dp
                    else -> 18.dp
                }
                val sectionSpacing = when {
                    isUltraCompactHeight -> 6.dp
                    isCompactHeight -> 10.dp
                    else -> 12.dp
                }

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
                                    .size(if (isCompactHeight) 10.dp else 12.dp)
                                    .scale(pulseScale)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(if (isCompactHeight) 6.dp else 8.dp))
                            Text(
                                text = "EMERGENCY BROADCAST ACTIVE",
                                style = if (isCompactHeight) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.error,
                                letterSpacing = if (isCompactHeight) 0.5.sp else 1.0.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        IconButton(
                            onClick = onMinimize,
                            modifier = Modifier.size(if (isCompactHeight) 34.dp else 42.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloseFullscreen,
                                contentDescription = "Minimize",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(if (isCompactHeight) 18.dp else 22.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(sectionSpacing))

                    // 2. Modern Glassmorphic Countdown Timer & Status Header Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(cardCornerRadius),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(cardInnerPadding),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                        modifier = Modifier.padding(bottom = if (isCompactHeight) 4.dp else 6.dp),
                                    ) {
                                        Text(
                                            text = "BEACON ACTIVE",
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            fontSize = if (isCompactHeight) 8.5.sp else 9.sp,
                                            letterSpacing = 0.8.sp,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                    val alertFontSize = when {
                                        isUltraCompactHeight -> 13.sp
                                        isCompactHeight -> 14.sp
                                        else -> 16.sp
                                    }
                                    Text(
                                        text = alertText,
                                        style = if (isCompactHeight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = alertFontSize,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = if (isUltraCompactHeight) 1 else 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Broadcasting on multi-radio mesh",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = if (isCompactHeight) 10.5.sp else 12.sp,
                                    )
                                }

                                // Modern Circular Timer Ring Widget with Padding
                                val minutes = remainingSeconds / 60
                                val seconds = remainingSeconds % 60
                                val progress = (remainingSeconds.toFloat() / 300f).coerceIn(0f, 1f)

                                ModernCircularTimerRing(
                                    progress = progress,
                                    minutes = minutes,
                                    seconds = seconds,
                                    isCompactHeight = isCompactHeight,
                                    isUltraCompactHeight = isUltraCompactHeight,
                                    modifier = Modifier.padding(start = 2.dp, end = 0.dp),
                                )
                            }

                            Spacer(modifier = Modifier.height(if (isCompactHeight) 8.dp else 14.dp))

                            // Radio Channel Status Badges
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(if (isExtraSmallWidth) 4.dp else 6.dp),
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

                    Spacer(modifier = Modifier.height(sectionSpacing))

                    // 3. Section Title: Confirmed Reached Devices
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (isExtraSmallWidth) "Confirmed Devices (${outboundAlert.recipients.size})" else "Confirmed Reached Devices (${outboundAlert.recipients.size})",
                            style = if (isCompactHeight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isSmallWidth) 13.sp else if (isCompactHeight) 14.sp else 16.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (outboundAlert.recipients.isNotEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = "LIVE",
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = if (isCompactHeight) 9.sp else 10.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(if (isCompactHeight) 6.dp else 8.dp))

                    // 4. Live Received Devices List or Radar Searching Animation
                    if (outboundAlert.recipients.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(if (isCompactHeight) 14.dp else 18.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .padding(if (isCompactHeight) 10.dp else 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                val pulseSize = when {
                                    isUltraCompactHeight -> 52.dp
                                    isCompactHeight -> 64.dp
                                    else -> 84.dp
                                }
                                val iconSize = when {
                                    isUltraCompactHeight -> 22.dp
                                    isCompactHeight -> 26.dp
                                    else -> 34.dp
                                }
                                Box(
                                    modifier = Modifier.size(pulseSize + 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PulseRing(
                                        color = MaterialTheme.colorScheme.error,
                                        size = pulseSize,
                                    )
                                    Icon(
                                        imageVector = Icons.Default.Sensors,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(iconSize),
                                    )
                                }

                                Spacer(modifier = Modifier.height(if (isCompactHeight) 6.dp else 10.dp))

                                Text(
                                    text = "Broadcasting Emergency Signal...",
                                    style = if (isCompactHeight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = if (isCompactHeight) 13.5.sp else 15.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Broadcasting continuously across BLE, Wi-Fi Direct, and LAN. Nearby devices will appear here automatically as soon as they acknowledge.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    fontSize = if (isCompactHeight) 11.sp else 12.sp,
                                    maxLines = if (isUltraCompactHeight) 2 else if (isCompactHeight) 3 else 4,
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
                            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 6.dp else 8.dp),
                            contentPadding = PaddingValues(bottom = 6.dp),
                        ) {
                            items(outboundAlert.recipients, key = { it.peerName }) { recipient ->
                                RecipientDeviceCard(
                                    recipient = recipient,
                                    isCompactHeight = isCompactHeight,
                                    isSmallWidth = isSmallWidth,
                                    isExtraSmallWidth = isExtraSmallWidth,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(sectionSpacing))

                    // 5. Bottom Action Controls (Exit / Stop Broadcast & Minimize)
                    val buttonContent: @Composable (Modifier, Boolean) -> Unit = { mod, isMinBtn ->
                        if (isMinBtn) {
                            OutlinedButton(
                                onClick = onMinimize,
                                modifier = mod.height(buttonHeight),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloseFullscreen,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (isCompactHeight) 15.dp else 16.dp),
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
                        } else {
                            Button(
                                onClick = onStopAlert,
                                modifier = mod.height(buttonHeight),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (isCompactHeight) 16.dp else 18.dp),
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

                    if (isExtraSmallWidth) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            buttonContent(Modifier.fillMaxWidth(), true)
                            buttonContent(Modifier.fillMaxWidth(), false)
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(if (isSmallWidth) 8.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            buttonContent(Modifier.weight(1f), true)
                            buttonContent(Modifier.weight(1.15f), false)
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
private fun RecipientDeviceCard(
    recipient: AlertRecipient,
    isCompactHeight: Boolean = false,
    isSmallWidth: Boolean = false,
    isExtraSmallWidth: Boolean = false,
) {
    val isStopped = recipient.locationLabel?.contains("STOPPED", ignoreCase = true) == true
    val isTracking = !isStopped && (recipient.isTracking || recipient.locationLabel?.contains("TRACKING") == true || recipient.peerName.contains("Tracking"))
    val isReceived = !isTracking && !isStopped

    val cleanName = recipient.peerName.replace(" (Tracking)", "").trim()

    val cardBorder = when {
        isTracking -> BorderStroke(1.5.dp, Color(0xFF10B981).copy(alpha = 0.8f))
        isStopped -> BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.7f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }

    val containerColor = when {
        isTracking -> Color(0xFF064E3B).copy(alpha = 0.22f)
        isStopped -> Color(0xFF78350F).copy(alpha = 0.15f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isCompactHeight) 14.dp else 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
        ),
        border = cardBorder,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = if (isExtraSmallWidth) 8.dp else if (isSmallWidth) 10.dp else 12.dp,
                    vertical = if (isCompactHeight) 8.dp else 11.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                val iconColor = when {
                    isTracking -> Color(0xFF10B981)
                    isStopped -> Color(0xFFF59E0B)
                    else -> Color(0xFF4CAF50)
                }
                Surface(
                    shape = CircleShape,
                    color = iconColor.copy(alpha = 0.18f),
                    modifier = Modifier.size(if (isExtraSmallWidth) 30.dp else if (isSmallWidth) 36.dp else 40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                isTracking -> Icons.Default.Radar
                                isStopped -> Icons.Default.TaskAlt
                                else -> Icons.Default.CheckCircle
                            },
                            contentDescription = when {
                                isTracking -> "Tracking Live"
                                isStopped -> "Alarm Stopped"
                                else -> "Received"
                            },
                            tint = iconColor,
                            modifier = Modifier.size(if (isExtraSmallWidth) 17.dp else if (isSmallWidth) 20.dp else 22.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(if (isExtraSmallWidth) 6.dp else if (isSmallWidth) 8.dp else 10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = cleanName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isExtraSmallWidth) 12.5.sp else if (isSmallWidth) 13.5.sp else 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        if (isTracking) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, Color(0xFF10B981)),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .background(Color(0xFF34D399), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "TRACKING LIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF34D399),
                                        fontSize = 8.sp,
                                    )
                                }
                            }
                        } else if (isStopped) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, Color(0xFFF59E0B)),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .background(Color(0xFFF59E0B), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "STOPPED",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFBBF24),
                                        fontSize = 8.sp,
                                    )
                                }
                            }
                        } else if (isReceived) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                                border = BorderStroke(0.5.dp, Color(0xFF60A5FA)),
                            ) {
                                Text(
                                    text = "RECEIVED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF93C5FD),
                                    fontSize = 8.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    val parsedLoc = recipient.locationLabel?.let {
                        it.replace("• TRACKING LIVE", "")
                            .replace("• RECEIVED & STOPPED", "")
                            .replace("• RECEIVED", "")
                            .replace(Regex("""\(~\d+(\.\d+)?m\)"""), "")
                            .trim()
                    }?.takeIf { it.isNotBlank() }

                    val cleanLoc = when {
                        parsedLoc != null && parsedLoc != "Direct RF Proximity" && parsedLoc != "Direct RF Mesh" -> parsedLoc
                        isTracking -> "Tracking Active · Live RF"
                        isStopped -> "Alarm Dismissed"
                        else -> "Receipt Confirmed · Standby"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = when {
                                isTracking -> Color(0xFF34D399)
                                isStopped -> Color(0xFFFBBF24)
                                else -> MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(if (isSmallWidth) 11.dp else 12.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = cleanLoc,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = if (isExtraSmallWidth) 9.5.sp else if (isSmallWidth) 10.5.sp else 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(if (isSmallWidth) 6.dp else 10.dp))

            // Live Distance & Signal Badge
            Column(horizontalAlignment = Alignment.End) {
                if (recipient.distanceMeters != null) {
                    val distColor = when {
                        isTracking -> {
                            when {
                                recipient.distanceMeters <= 1.5f -> Color(0xFF10B981) // Emerald
                                recipient.distanceMeters <= 4.0f -> Color(0xFF34D399) // Mint Green
                                recipient.distanceMeters <= 10.0f -> Color(0xFFFBBF24) // Amber
                                recipient.distanceMeters <= 25.0f -> Color(0xFFFB923C) // Orange
                                else -> Color(0xFFEF4444) // Red
                            }
                        }
                        isStopped -> Color(0xFFF59E0B) // Amber
                        else -> Color(0xFF3B82F6) // Blue
                    }
                    Text(
                        text = "~${String.format(Locale.US, "%.1f", recipient.distanceMeters)}m",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (isSmallWidth) 13.5.sp else 15.sp,
                        color = distColor,
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    val proximityBand = when {
                        recipient.distanceMeters <= 1.5f -> "<1.5m"
                        recipient.distanceMeters <= 4.0f -> "1–4m"
                        recipient.distanceMeters <= 10.0f -> "4–10m"
                        recipient.distanceMeters <= 25.0f -> "10–25m"
                        else -> ">25m"
                    }
                    val rssiSuffix = recipient.rssiDbm?.let { " • ${it}dBm" } ?: ""
                    Text(
                        text = "$proximityBand$rssiSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (isSmallWidth) 9.sp else 10.sp,
                        color = distColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        text = when {
                            isStopped -> "STOPPED"
                            isTracking -> "TRACKING..."
                            else -> "DELIVERED"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isSmallWidth) 11.sp else 12.5.sp,
                        color = when {
                            isStopped -> Color(0xFFF59E0B)
                            isTracking -> Color(0xFF10B981)
                            else -> Color(0xFF3B82F6)
                        },
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = when {
                            isStopped -> "Dismissed"
                            isTracking -> "Syncing..."
                            else -> "Standby"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = if (isSmallWidth) 8.5.sp else 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
    var remainingSeconds by remember(outboundAlert.startedAtMs) {
        mutableIntStateOf(outboundAlert.remainingSeconds)
    }

    LaunchedEffect(outboundAlert.startedAtMs) {
        while (remainingSeconds > 0 && isActive) {
            delay(1000L)
            remainingSeconds = outboundAlert.remainingSeconds
        }
    }

    val minutes = remainingSeconds / 60
    val seconds = remainingSeconds % 60
    val progress = (remainingSeconds.toFloat() / 300f).coerceIn(0f, 1f)

    val dynamicRed = lerp(
        start = Color(0xFF5B0E0E),
        stop = Color(0xFFEF4444),
        fraction = progress,
    )

    val transition = rememberInfiniteTransition(label = "minimizedPulse")
    val pulseAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    val pulseScale by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onExpand() },
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.75f),
        ),
        shadowElevation = 8.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val isSmallWidth = maxWidth < 380.dp
            val hPadding = if (isSmallWidth) 10.dp else 14.dp
            val vPadding = if (isSmallWidth) 8.dp else 10.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPadding, vertical = vPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    // Live Pulsing Emergency Beacon Dot
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(if (isSmallWidth) 16.dp else 20.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isSmallWidth) 14.dp else 18.dp)
                                .scale(pulseScale)
                                .background(
                                    MaterialTheme.colorScheme.error.copy(alpha = pulseAlpha * 0.35f),
                                    CircleShape,
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(if (isSmallWidth) 8.dp else 10.dp)
                                .background(MaterialTheme.colorScheme.error, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.width(if (isSmallWidth) 6.dp else 10.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "EMERGENCY",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = if (isSmallWidth) 8.5.sp else 9.5.sp,
                                letterSpacing = 0.6.sp,
                                maxLines = 1,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            // Monospace Countdown Pill
                            Surface(
                                shape = CircleShape,
                                color = dynamicRed.copy(alpha = 0.18f),
                                border = BorderStroke(0.8.dp, dynamicRed.copy(alpha = 0.5f)),
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%02d:%02d", minutes, seconds),
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = if (isSmallWidth) 9.5.sp else 10.5.sp,
                                    color = dynamicRed,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(1.dp))

                        Text(
                            text = if (outboundAlert.recipients.isEmpty()) {
                                "Broadcasting on multi-radio mesh"
                            } else {
                                "✓ ${outboundAlert.recipients.size} device(s) confirmed reached"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = if (isSmallWidth) 10.5.sp else 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Action Buttons (Expand & Stop)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    IconButton(
                        onClick = onExpand,
                        modifier = Modifier.size(if (isSmallWidth) 32.dp else 36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(if (isSmallWidth) 16.dp else 18.dp),
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(if (isSmallWidth) 30.dp else 34.dp),
                    ) {
                        IconButton(
                            onClick = onStopAlert,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Broadcast",
                                tint = MaterialTheme.colorScheme.onError,
                                modifier = Modifier.size(if (isSmallWidth) 16.dp else 18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern Custom Animated Circular Timer Ring for Outbound Emergency Broadcast screen.
 */
@Composable
private fun ModernCircularTimerRing(
    progress: Float,
    minutes: Int,
    seconds: Int,
    isCompactHeight: Boolean,
    isUltraCompactHeight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val size = if (isUltraCompactHeight) 62.dp else if (isCompactHeight) 72.dp else 82.dp
    val strokeWidth = if (isUltraCompactHeight) 3.5.dp else if (isCompactHeight) 4.5.dp else 5.5.dp
    val dynamicRed = lerp(
        start = Color(0xFF5B0E0E), // Dark deep crimson when time is almost 0
        stop = Color(0xFFEF4444),  // Bright vivid crimson when full 5:00 minutes
        fraction = progress.coerceIn(0f, 1f)
    )
    val unfilledTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(strokeWidth / 2f + if (isUltraCompactHeight) 5.dp else 8.dp)
        ) {
            val strokeWidthPx = strokeWidth.toPx()
            val arcSize = this.size

            // Unfilled Background Track (clearly visible neutral ring)
            drawArc(
                color = unfilledTrackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset.Zero,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            // Active Progress Ring Arc - darkens smoothly as time decreases
            drawArc(
                color = dynamicRed,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                topLeft = Offset.Zero,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = String.format(Locale.US, "%02d:%02d", minutes, seconds),
                style = if (isCompactHeight) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                fontSize = if (isUltraCompactHeight) 10.sp else if (isCompactHeight) 11.sp else 12.5.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "LEFT",
                style = MaterialTheme.typography.labelSmall,
                fontSize = if (isUltraCompactHeight) 6.5.sp else 7.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                color = dynamicRed,
            )
        }
    }
}
