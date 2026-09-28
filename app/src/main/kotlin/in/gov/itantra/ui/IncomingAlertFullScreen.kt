package `in`.gov.itantra.ui

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * 2-minute continuous alarm loop timer, and live real-time proximity radar tracking.
 */
@Composable
fun IncomingAlertFullScreen(
    alert: IncomingAlert,
    chrome: UiStrings,
    /** Real-time RSSI-estimated distance from the ViewModel. Null if no RSSI data yet. */
    liveDistanceMeters: Float? = null,
    onDismiss: () -> Unit,
    onMuteAudio: () -> Unit = {},
) {
    var isTrackingActive by remember { mutableStateOf(false) }
    var isAudioMuted by remember { mutableStateOf(false) }

    // Use real RSSI-based distance from ViewModel or alert's initial distance
    val currentDistanceMeters = liveDistanceMeters ?: alert.distanceMeters
    val effectiveDistanceMeters = currentDistanceMeters ?: 15.0f

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
        initialValue = 0.93f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
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
    LaunchedEffect(isTrackingActive, effectiveDistanceMeters) {
        if (!isTrackingActive) return@LaunchedEffect
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
        color = Color(0xFF0D0303), // Deep tactical black-crimson
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            val isCompactHeight = maxHeight < 680.dp
            val isSmallWidth = maxWidth < 380.dp

            val beaconOuterSize = if (isCompactHeight) 100.dp else 136.dp
            val beaconInnerSize = if (isCompactHeight) 64.dp else 82.dp
            val beaconIconSize = if (isCompactHeight) 32.dp else 44.dp
            val waveformHeight = if (isCompactHeight) 28.dp else 40.dp
            val verticalPadding = if (isCompactHeight) 8.dp else 14.dp
            val horizontalPadding = if (isSmallWidth) 12.dp else 18.dp

            // Background Radial Hazard Beacon Ambient Glow extends edge-to-edge
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFDC2626).copy(alpha = 0.32f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.25f),
                        radius = size.width * 0.85f,
                    ),
                    center = Offset(size.width / 2f, size.height * 0.25f),
                    radius = size.width * 0.85f,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 12.dp),
                ) {
                    // 1. Top Header: Priority Status & 2-Minute Countdown Loop Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusPill(
                            text = "EMERGENCY SOS",
                            containerColor = Color(0xFFDC2626),
                            contentColor = Color.White,
                            icon = Icons.Filled.Warning,
                        )

                        val minutes = remainingSeconds / 60
                        val seconds = remainingSeconds % 60
                        Surface(
                            shape = CircleShape,
                            color = if (isAudioMuted) Color(0xFF27272A) else Color(0xFF3F1414),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isAudioMuted) Color(0xFF52525B) else Color(0xFFEF4444).copy(alpha = 0.6f),
                            ),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    if (isAudioMuted) Icons.Filled.NotificationsOff else Icons.Filled.NotificationsActive,
                                    contentDescription = null,
                                    tint = if (isAudioMuted) Color(0xFFA1A1AA) else Color(0xFFFCA5A5),
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = if (isAudioMuted) "Alarm Muted" else String.format(Locale.US, "Loop %02d:%02d", minutes, seconds),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAudioMuted) Color(0xFFA1A1AA) else Color(0xFFFEF2F2),
                                )
                            }
                        }
                    }

                    // 2. Center Visual: Animated SOS Beacon or Live Radar View
                    if (!isTrackingActive) {
                        // Standard Emergency Beacon View
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(beaconOuterSize)
                                .scale(pulseScale),
                        ) {
                            PulseRing(color = Color(0xFFDC2626), size = beaconOuterSize)
                            PulseRing(color = Color(0xFFEF4444), size = beaconOuterSize, delayMillis = 400)

                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFDC2626),
                                shadowElevation = 10.dp,
                                modifier = Modifier.size(beaconInnerSize),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = "SOS",
                                        tint = Color.White,
                                        modifier = Modifier.size(beaconIconSize),
                                    )
                                }
                            }
                        }

                        // Audio Waveform Equalizer Animation (while alarm audio is active)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(waveformHeight),
                        ) {
                            val bars = listOf(bar1, bar2, bar3, bar2, bar1, bar3, bar2)
                            bars.forEach { h ->
                                Box(
                                    modifier = Modifier
                                        .width(4.5.dp)
                                        .height(if (isAudioMuted) 4.dp else h.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (isAudioMuted) Color(0xFF52525B) else Color(0xFFEF4444)),
                                )
                            }
                        }
                    } else {
                        // High-Tech Sonar Radar Compass (Signal Tracking Mode)
                        TacticalSonarRadarView(
                            distanceMeters = effectiveDistanceMeters,
                            sweepAngle = radarSweepAngle,
                            radarColor = proximityColor,
                            modifier = Modifier
                                .size(if (isCompactHeight) 130.dp else 160.dp)
                                .padding(vertical = 4.dp),
                        )
                    }

                    // 3. Alert Message Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1C1919),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFDC2626).copy(alpha = 0.8f)),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = alertText,
                                style = if (isCompactHeight) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFEF2F2),
                                textAlign = TextAlign.Center,
                            )

                            Spacer(Modifier.height(6.dp))

                            HorizontalDivider(color = Color(0xFF3F3F46).copy(alpha = 0.6f))

                            Spacer(Modifier.height(6.dp))

                            // Sender Info
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "From: $senderName (${alert.language.endonym})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFCA5A5),
                                    fontSize = 13.5.sp,
                                )
                            }
                        }
                    }

                    // 4. Tactical Real-Time Visual Proximity & Signal Gauge (NO Sliders)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF141416),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isTrackingActive) proximityColor.copy(alpha = 0.8f) else Color(0xFF27272A),
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isTrackingActive) Icons.Filled.Radar else Icons.Filled.Sensors,
                                        contentDescription = null,
                                        tint = proximityColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = if (isTrackingActive) "Real-Time Proximity Tracker" else "Estimated Beacon Proximity",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE4E4E7),
                                    )
                                }

                                // Ping / Refresh indicator
                                IconButton(
                                    onClick = { /* Distance is updated in real-time from RSSI */ },
                                    modifier = Modifier.size(28.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Refresh,
                                        contentDescription = "Refresh Signal",
                                        tint = proximityColor,
                                        modifier = Modifier.size(17.dp),
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

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
                                        fontSize = if (isCompactHeight) 32.sp else 38.sp,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "meters",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFA1A1AA),
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                } else {
                                    Text(
                                        text = "Estimating Proximity...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFA1A1AA),
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // 5-Segment Dynamic Signal Strength LED Meter
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
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
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (isFilled) barColor else Color(0xFF27272A)),
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            // Proximity Band Status Label
                            Text(
                                text = proximityBandText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = proximityColor,
                                textAlign = TextAlign.Center,
                            )

                            if (isTrackingActive) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Acoustic ping interval tightens as you move closer",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF71717A),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f, fill = false))

                    // 5. Tactical Action Buttons Row
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        // Track / Acoustic Radar Toggle Button
                        OutlinedButton(
                            onClick = {
                                if (!isTrackingActive) {
                                    isTrackingActive = true
                                    // Mute loud speech when tracking begins so acoustic sonar beeps take over
                                    if (!isAudioMuted) {
                                        isAudioMuted = true
                                        onMuteAudio()
                                    }
                                } else {
                                    isTrackingActive = false
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isCompactHeight) 44.dp else 48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (isTrackingActive) Color(0xFF34D399) else Color.White,
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isTrackingActive) Color(0xFF059669) else Color(0xFF52525B),
                            ),
                        ) {
                            Icon(
                                if (isTrackingActive) Icons.Filled.Check else Icons.Filled.Explore,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isTrackingActive) "Tracking Signal Active (Beeping...)" else "Track Signal & Proximity",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        }

                        // Stop Alarm / Mark Received Button
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isCompactHeight) 48.dp else 52.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White,
                            ),
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Stop Alarm & Mark Received",
                                style = MaterialTheme.typography.titleMedium,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(Modifier.height(if (isCompactHeight) 10.dp else 16.dp))
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
