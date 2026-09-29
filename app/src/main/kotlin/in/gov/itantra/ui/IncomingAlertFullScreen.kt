package `in`.gov.itantra.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.gov.itantra.core.alert.AlertContent
import `in`.gov.itantra.core.alert.AlertTemplate
import `in`.gov.itantra.core.alert.IncomingAlert
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.ui.components.PulseRing
import `in`.gov.itantra.ui.components.StatusPill
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-screen Emergency SOS Alert Overlay with system insets protection,
 * 2-minute continuous alarm loop timer, urgent alert device vibration,
 * and live real-time proximity radar tracking matching OutboundAlertFullScreen.
 */
@Composable
fun IncomingAlertFullScreen(
    alert: IncomingAlert,
    chrome: UiStrings,
    /** Real-time RSSI-estimated distance from the ViewModel. Null if no RSSI data yet. */
    liveDistanceMeters: Float? = null,
    onDismiss: () -> Unit,
    onMuteAudio: () -> Unit = {},
    onStartTracking: () -> Unit = {},
    onStopTracking: () -> Unit = {},
) {
    val context = LocalContext.current
    var isTrackingActive by remember { mutableStateOf(false) }
    var isAudioMuted by remember { mutableStateOf(false) }

    // Device Vibrator for urgent haptic alert on receiving phone
    val vibrator = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    // Trigger urgent alert vibration on receipt
    LaunchedEffect(Unit) {
        vibrator?.let { v ->
            try {
                if (v.hasVibrator()) {
                    val pattern = longArrayOf(0, 500, 200, 500, 200, 800)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        v.vibrate(VibrationEffect.createWaveform(pattern, 0))
                    } else {
                        @Suppress("DEPRECATION")
                        v.vibrate(pattern, 0)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // Stop vibration when screen is dismissed or destroyed
    DisposableEffect(Unit) {
        onDispose {
            try { vibrator?.cancel() } catch (_: Exception) {}
        }
    }
    // Smooth distance transitions so micro RF variations glide gracefully instead of jumping
    val rawDistanceMeters = liveDistanceMeters ?: alert.distanceMeters
    val animatedDistanceMeters by animateFloatAsState(
        targetValue = rawDistanceMeters ?: 15.0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "distanceSpring"
    )
    val currentDistanceMeters = if (rawDistanceMeters != null) animatedDistanceMeters else null
    val effectiveDistanceMeters = animatedDistanceMeters

    // 2-minute (120 seconds) alarm timer countdown
    var remainingSeconds by remember { mutableIntStateOf(120) }

    LaunchedEffect(Unit) {
        while (remainingSeconds > 0 && isActive) {
            delay(1000L)
            remainingSeconds--
        }
    }

    val alertText = remember(alert.content, alert.language) {
        when (val c = alert.content) {
            is AlertContent.Template -> c.template.phrase(alert.language)
            is AlertContent.Custom -> AlertTemplate.resolveDisplayText(c.text, alert.language)
        }
    }

    val senderName = alert.senderName?.takeIf { it.isNotBlank() && it != "Unknown" && it != "Peer" } ?: "Emergency Beacon"

    // Pulsing Beacon Animation
    val transition = rememberInfiniteTransition(label = "sosPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseScale",
    )

    // Waveform Equalizer animation
    val bar1 by transition.animateFloat(
        initialValue = 10f, targetValue = 36f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1",
    )
    val bar2 by transition.animateFloat(
        initialValue = 16f, targetValue = 48f,
        animationSpec = infiniteRepeatable(tween(250, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2",
    )
    val bar3 by transition.animateFloat(
        initialValue = 8f, targetValue = 40f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3",
    )

    // Radar Sweep Line Rotation Angle
    val radarSweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radarSweep",
    )

    // Proximity Acoustic Tone Beeper (Rate scales in real-time as distance decreases)
    LaunchedEffect(isTrackingActive, currentDistanceMeters, isAudioMuted) {
        if (!isTrackingActive || currentDistanceMeters == null || isAudioMuted) return@LaunchedEffect
        var toneGen: ToneGenerator? = null
        try {
            toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 85)
            while (isActive) {
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
                // Acoustic Interval: 850ms at 30m, down to 80ms at <= 1.2m
                val intervalMs = (effectiveDistanceMeters * 26f + 50f).coerceIn(80f, 900f).toLong()
                delay(intervalMs)
            }
        } catch (_: Exception) {
        } finally {
            try { toneGen?.release() } catch (_: Exception) {}
        }
    }

    // Proximity category tokens
    val (proximityColor, proximityBandText, signalBarsCount) = when {
        currentDistanceMeters == null -> Triple(Color(0xFFFB923C), "ESTIMATING SIGNAL PROXIMITY...", 2)
        effectiveDistanceMeters <= 1.5f -> Triple(Color(0xFF10B981), "IMMEDIATE CONTACT · TARGET REACHED", 5)
        effectiveDistanceMeters <= 4.0f -> Triple(Color(0xFF34D399), "CLOSE PROXIMITY · APPROACHING SOURCE", 4)
        effectiveDistanceMeters <= 10.0f -> Triple(Color(0xFFFBBF24), "NEARBY RANGE · STRONG SIGNAL", 3)
        effectiveDistanceMeters <= 20.0f -> Triple(Color(0xFFFB923C), "MEDIUM RANGE · DETECTING BEACON", 2)
        else -> Triple(Color(0xFFEF4444), "EXTENDED RANGE · WEAK SIGNAL", 1)
    }

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
                                MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
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
                    else -> 14.dp
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .padding(horizontal = horizontalPadding, vertical = verticalPadding)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // 1. Top Header: Emergency Status & Mute/Countdown Controls
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
                                text = "EMERGENCY ALERT RECEIVED",
                                style = if (isCompactHeight) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.error,
                                letterSpacing = if (isCompactHeight) 0.5.sp else 1.0.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // Mute Audio / Stop Vibration Toggle
                            IconButton(
                                onClick = {
                                    isAudioMuted = !isAudioMuted
                                    if (isAudioMuted) {
                                        try { vibrator?.cancel() } catch (_: Exception) {}
                                        onMuteAudio()
                                    }
                                },
                                modifier = Modifier.size(if (isCompactHeight) 34.dp else 40.dp),
                            ) {
                                Icon(
                                    imageVector = if (isAudioMuted) Icons.Filled.NotificationsOff else Icons.Filled.NotificationsActive,
                                    contentDescription = if (isAudioMuted) "Muted" else "Mute Alert Audio",
                                    tint = if (isAudioMuted) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(if (isCompactHeight) 18.dp else 20.dp),
                                )
                            }

                            // 2-Minute Alarm Countdown Pill
                            val minutes = remainingSeconds / 60
                            val seconds = remainingSeconds % 60
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    if (isAudioMuted) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                                ),
                            ) {
                                Text(
                                    text = if (isAudioMuted) "MUTED" else String.format(Locale.US, "%02d:%02d", minutes, seconds),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAudioMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(sectionSpacing))

                    // 2. Alert Message & Sender Identity Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(cardCornerRadius),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.7f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(cardInnerPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            val alertFontSize = when {
                                isUltraCompactHeight -> if (alertText.length > 50) 15.sp else 17.sp
                                isCompactHeight -> if (alertText.length > 60) 16.sp else if (alertText.length > 30) 18.sp else 20.sp
                                else -> if (alertText.length > 60) 18.sp else if (alertText.length > 30) 21.sp else 24.sp
                            }
                            val alertLineHeight = when {
                                isUltraCompactHeight -> 20.sp
                                isCompactHeight -> 23.sp
                                else -> 28.sp
                            }

                            Text(
                                text = alertText,
                                fontSize = alertFontSize,
                                lineHeight = alertLineHeight,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(Modifier.height(if (isCompactHeight) 6.dp else 10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(Modifier.height(if (isCompactHeight) 6.dp else 10.dp))

                            // Sender Identity & Language
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(if (isCompactHeight) 14.dp else 16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "From: $senderName (${alert.language.endonym})",
                                    style = if (isCompactHeight) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }

                            val senderLoc = alert.senderLocation
                            if (!senderLoc.isNullOrBlank()) {
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = senderLoc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            // Responsive Acknowledgment Status Confirmation Banner
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.45f)),
                                modifier = Modifier.padding(top = if (isCompactHeight) 6.dp else 8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(
                                        horizontal = if (isSmallWidth) 8.dp else 10.dp,
                                        vertical = 4.dp
                                    ),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(if (isCompactHeight) 13.dp else 15.dp),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = "Receipt Acknowledged to Sender",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981),
                                        fontSize = if (isUltraCompactHeight) 10.sp else 11.5.sp,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(sectionSpacing))

                    // 3. Real-Time Proximity & Sonar Radar Tracking Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(cardCornerRadius),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        border = BorderStroke(
                            1.5.dp,
                            if (isTrackingActive) proximityColor.copy(alpha = 0.85f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(cardInnerPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Radar Header Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isTrackingActive) Icons.Filled.Radar else Icons.Filled.Sensors,
                                        contentDescription = null,
                                        tint = if (isTrackingActive) proximityColor else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(if (isCompactHeight) 18.dp else 20.dp),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = if (isTrackingActive) "Tactical Radar & Proximity" else "Beacon Status",
                                        style = if (isCompactHeight) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }

                                if (isTrackingActive) {
                                    IconButton(
                                        onClick = { /* Updates continuously via live RSSI flow */ },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Refresh,
                                            contentDescription = "Active Signal",
                                            tint = proximityColor,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(if (isCompactHeight) 6.dp else 10.dp))

                            if (isTrackingActive) {
                                val radarSize = when {
                                    isUltraCompactHeight -> 88.dp
                                    isCompactHeight -> 108.dp
                                    else -> 136.dp
                                }

                                // Tactical Sonar Radar Scope
                                TacticalSonarRadarView(
                                    distanceMeters = effectiveDistanceMeters,
                                    sweepAngle = radarSweepAngle,
                                    radarColor = proximityColor,
                                    modifier = Modifier
                                        .size(radarSize)
                                        .padding(vertical = 2.dp),
                                )

                                Spacer(Modifier.height(if (isCompactHeight) 6.dp else 10.dp))

                                val distanceFontSize = when {
                                    isUltraCompactHeight -> 24.sp
                                    isCompactHeight -> 28.sp
                                    else -> 34.sp
                                }

                                // Large Digital Distance Readout
                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    if (currentDistanceMeters != null) {
                                        Text(
                                            text = String.format(Locale.US, "%.1f", currentDistanceMeters),
                                            style = MaterialTheme.typography.displaySmall,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Black,
                                            color = proximityColor,
                                            fontSize = distanceFontSize,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "meters",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = if (isCompactHeight) 14.sp else 16.sp,
                                            modifier = Modifier.padding(bottom = if (isCompactHeight) 3.dp else 5.dp),
                                        )
                                    } else {
                                        Text(
                                            text = "Calculating Signal Proximity...",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = if (isCompactHeight) 14.sp else 16.sp,
                                        )
                                    }
                                }

                                Spacer(Modifier.height(if (isCompactHeight) 6.dp else 8.dp))

                                // 5-Segment Dynamic Signal Strength LED Meter
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = if (isSmallWidth) 6.dp else 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    for (i in 1..5) {
                                        val isFilled = i <= signalBarsCount
                                        val barColor = when (i) {
                                            1 -> Color(0xFFEF4444) // Red
                                            2 -> Color(0xFFFB923C) // Orange
                                            3 -> Color(0xFFFBBF24) // Yellow
                                            4 -> Color(0xFF34D399) // Light Green
                                            else -> Color(0xFF10B981) // Emerald Green
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(if (isCompactHeight) 5.dp else 7.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(if (isFilled) barColor else MaterialTheme.colorScheme.surfaceContainerHighest),
                                        )
                                    }
                                }

                                Spacer(Modifier.height(if (isCompactHeight) 6.dp else 8.dp))

                                // Proximity Band Status Label
                                Text(
                                    text = proximityBandText,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = proximityColor,
                                    textAlign = TextAlign.Center,
                                    fontSize = if (isCompactHeight) 10.sp else 11.sp,
                                )

                                if (!isAudioMuted) {
                                    Spacer(Modifier.height(3.dp))
                                    Text(
                                        text = "Acoustic sonar tightens as distance closes",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        fontSize = if (isCompactHeight) 10.sp else 11.sp,
                                    )
                                }
                            } else {
                                val beaconOuterSize = when {
                                    isUltraCompactHeight -> 68.dp
                                    isCompactHeight -> 84.dp
                                    else -> 108.dp
                                }
                                val beaconInnerSize = when {
                                    isUltraCompactHeight -> 42.dp
                                    isCompactHeight -> 52.dp
                                    else -> 64.dp
                                }
                                val beaconIconSize = when {
                                    isUltraCompactHeight -> 22.dp
                                    isCompactHeight -> 26.dp
                                    else -> 32.dp
                                }

                                // Clean Beacon Display when NOT tracking yet
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(beaconOuterSize)
                                        .scale(pulseScale)
                                        .padding(vertical = if (isCompactHeight) 2.dp else 4.dp),
                                ) {
                                    PulseRing(color = MaterialTheme.colorScheme.error, size = beaconOuterSize)
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(beaconInnerSize),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Warning,
                                                contentDescription = "Alert",
                                                tint = Color.White,
                                                modifier = Modifier.size(beaconIconSize),
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.height(if (isCompactHeight) 8.dp else 12.dp))

                                Text(
                                    text = "Emergency Signal Active",
                                    style = if (isCompactHeight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = "Tap 'Track Beacon' below to start live proximity radar and navigate to $senderName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    fontSize = if (isCompactHeight) 11.5.sp else 12.5.sp,
                                    modifier = Modifier.padding(horizontal = if (isSmallWidth) 8.dp else 14.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(sectionSpacing))

                    // 4. Responsive Bottom Action Buttons
                    val buttonContent: @Composable (Modifier, Boolean) -> Unit = { mod, isTrackBtn ->
                        if (isTrackBtn) {
                            OutlinedButton(
                                onClick = {
                                    if (!isTrackingActive) {
                                        isTrackingActive = true
                                        onStartTracking()
                                    } else {
                                        isTrackingActive = false
                                        onStopTracking()
                                    }
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = mod.height(buttonHeight),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (isTrackingActive) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                ),
                                border = BorderStroke(
                                    1.5.dp,
                                    if (isTrackingActive) Color(0xFF10B981) else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = if (isTrackingActive) Icons.Filled.Check else Icons.Filled.Explore,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (isCompactHeight) 16.dp else 18.dp),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = if (isTrackingActive) "Tracking Live" else "Track Beacon",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (isSmallWidth) 12.sp else 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    try { vibrator?.cancel() } catch (_: Exception) {}
                                    onDismiss()
                                },
                                shape = RoundedCornerShape(14.dp),
                                modifier = mod.height(buttonHeight),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = Color.White,
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Stop,
                                        contentDescription = null,
                                        modifier = Modifier.size(if (isCompactHeight) 16.dp else 18.dp),
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = when {
                                            isExtraSmallWidth -> "Stop Alarm"
                                            isSmallWidth -> "Acknowledge"
                                            else -> "Stop Alarm & ACK"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontSize = if (isSmallWidth) 12.sp else 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
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
                            buttonContent(Modifier.weight(1f), false)
                        }
                    }

                    Spacer(modifier = Modifier.height(verticalPadding))
                }
            }
        }
    }
}

/**
 * Tactical Sonar Radar Canvas with concentric distance rings, crosshairs,
 * rotating sweep line, and dynamic proximity beacon blip.
 */
@Composable
private fun TacticalSonarRadarView(
    distanceMeters: Float,
    sweepAngle: Float,
    radarColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = (minOf(size.width, size.height) / 2f) - 6f

            // Radar Scope Background
            drawCircle(
                color = Color(0xFF0C120C),
                radius = maxRadius,
                center = center,
            )

            // Concentric Range Rings (30m, 15m, 5m)
            val rings = listOf(1.0f, 0.66f, 0.33f)
            rings.forEach { fraction ->
                drawCircle(
                    color = Color(0xFF059669).copy(alpha = 0.35f),
                    radius = maxRadius * fraction,
                    center = center,
                    style = Stroke(width = 1.2f),
                )
            }

            // Crosshair Grid
            drawLine(
                color = Color(0xFF059669).copy(alpha = 0.25f),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1f,
            )
            drawLine(
                color = Color(0xFF059669).copy(alpha = 0.25f),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1f,
            )

            // Rotating Sweep Beam
            val sweepRad = (sweepAngle * PI / 180.0).toFloat()
            val sweepEnd = Offset(
                center.x + maxRadius * cos(sweepRad),
                center.y + maxRadius * sin(sweepRad),
            )
            drawLine(
                color = radarColor.copy(alpha = 0.85f),
                start = center,
                end = sweepEnd,
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )

            // Dynamic Target Blip
            // Normalized radius based on 0.5m..30m scale
            val normalizedDistance = (distanceMeters / 30f).coerceIn(0.12f, 0.92f)
            val blipAngleRad = (45.0 * PI / 180.0).toFloat() // Stable 45° bearing relative to antenna
            val blipPos = Offset(
                center.x + maxRadius * normalizedDistance * cos(blipAngleRad),
                center.y - maxRadius * normalizedDistance * sin(blipAngleRad),
            )

            // Blip Ping Glow Rings
            drawCircle(
                color = radarColor.copy(alpha = 0.35f),
                radius = 12f,
                center = blipPos,
            )
            drawCircle(
                color = radarColor,
                radius = 5.5f,
                center = blipPos,
            )
        }
    }
}
