package `in`.gov.itantra.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import `in`.gov.itantra.core.discover.NearbyPeer
import `in`.gov.itantra.core.discover.RssiBand
import `in`.gov.itantra.core.location.GpsLocation
import `in`.gov.itantra.ui.ITantraTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * 100% Native Jetpack Compose Tactical & Geolocation Interactive Map.
 * Renders instant vector GPS maps, terrain grids, pulsing location beacons,
 * interactive peer pins, and tactical radar sweeps with zero WebView dependencies.
 */
@Composable
fun RealMapView(
    myLocation: GpsLocation,
    peers: List<NearbyPeer>,
    onPeerClick: (NearbyPeer) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
    showRadarOverlay: Boolean = false,
) {
    // Zoom scale: pixels per meter (default: 2.2 px/m = ~150m viewport)
    var pixelsPerMeter by remember { mutableFloatStateOf(2.2f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Pulsing Animation for Live GPS Beacon
    val transition = rememberInfiniteTransition(label = "mapPulse")
    val pulseFraction by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseFraction",
    )

    // Rotating Radar Sweep for Hybrid Mode
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "radarSweep",
    )

    // Theme Color Definitions
    val mapBgColor = if (isDark) Color(0xFF0F172A) else Color(0xFFF1F5F9)
    val gridColor = if (isDark) Color(0xFF1E293B) else Color(0xFFE2E8F0)
    val majorGridColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1)
    val roadColor = if (isDark) Color(0xFF1E293B).copy(alpha = 0.9f) else Color(0xFFFFFFFF)
    val roadBorderColor = if (isDark) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFCBD5E1)
    val primaryColor = MaterialTheme.colorScheme.primary
    val beaconHaloColor = primaryColor.copy(alpha = 0.15f)
    val peerColor = Color(0xFFEF4444)
    val peerBadgeBg = if (isDark) Color(0xFF1E293B) else Color(0xFFFFFFFF)
    val peerTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(mapBgColor)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    panOffset += pan
                    pixelsPerMeter = (pixelsPerMeter * zoom).coerceIn(0.6f, 8.0f)
                }
            }
            .pointerInput(peers, panOffset, pixelsPerMeter, myLocation) {
                detectTapGestures { tap ->
                    val center = Offset(size.width / 2f, size.height / 2f) + panOffset
                    // Find if any peer pin was tapped
                    val clickedPeer = peers.firstOrNull { peer ->
                        val offsetMeters = peer.calculateRelativeOffsetMeters()
                        val pinPos = Offset(
                            center.x + (offsetMeters.x * pixelsPerMeter),
                            center.y - (offsetMeters.y * pixelsPerMeter),
                        )
                        hypot((tap.x - pinPos.x).toDouble(), (tap.y - pinPos.y).toDouble()) <= 45.0
                    }
                    clickedPeer?.let { onPeerClick(it) }
                }
            },
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f) + panOffset

            // 1. Draw Tactical Urban Map Grid & City Blocks
            drawTacticalMapBase(
                center = center,
                ppm = pixelsPerMeter,
                gridColor = gridColor,
                majorGridColor = majorGridColor,
                roadColor = roadColor,
                roadBorderColor = roadBorderColor,
                isDark = isDark,
            )

            // 2. Draw Concentric Distance Range Rings (25m, 50m, 100m, 150m, 200m)
            val ringSteps = listOf(25, 50, 100, 150, 200)
            val ringPaint = Paint().apply {
                color = (if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)).copy(alpha = 0.5f).toArgb()
                textSize = 22f
                isAntiAlias = true
                isFakeBoldText = true
            }

            ringSteps.forEach { meters ->
                val radiusPx = meters * pixelsPerMeter
                drawCircle(
                    color = if (isDark) Color(0xFF3B82F6).copy(alpha = 0.22f) else Color(0xFF2563EB).copy(alpha = 0.20f),
                    radius = radiusPx,
                    center = center,
                    style = Stroke(width = 1.2f),
                )
                // Distance Text Label
                drawContext.canvas.nativeCanvas.drawText(
                    "${meters}m",
                    center.x + radiusPx + 6f,
                    center.y + 6f,
                    ringPaint,
                )
            }

            // 3. Draw Hybrid Mode Rotating Radar Beam
            if (showRadarOverlay) {
                val maxRadarR = 200f * pixelsPerMeter
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to primaryColor.copy(alpha = 0f),
                        0.85f to primaryColor.copy(alpha = 0.02f),
                        1.0f to primaryColor.copy(alpha = 0.30f),
                        center = center,
                    ),
                    startAngle = sweepAngle - 45f,
                    sweepAngle = 45f,
                    useCenter = true,
                    size = Size(maxRadarR * 2, maxRadarR * 2),
                    topLeft = Offset(center.x - maxRadarR, center.y - maxRadarR),
                )
                val sweepRad = (sweepAngle * PI / 180.0).toFloat()
                drawLine(
                    color = primaryColor.copy(alpha = 0.6f),
                    start = center,
                    end = Offset(center.x + maxRadarR * cos(sweepRad), center.y + maxRadarR * sin(sweepRad)),
                    strokeWidth = 1.5f,
                )
            }

            // 4. Draw Peer Markers on Map
            val peerLabelPaint = Paint().apply {
                color = peerTextColor.toArgb()
                textSize = 24f
                isAntiAlias = true
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
            val peerBadgePaint = Paint().apply {
                color = peerBadgeBg.toArgb()
                isAntiAlias = true
            }

            peers.forEach { peer ->
                val offsetMeters = peer.calculateRelativeOffsetMeters()
                val pinPos = Offset(
                    center.x + (offsetMeters.x * pixelsPerMeter),
                    center.y - (offsetMeters.y * pixelsPerMeter),
                )

                // Peer Accuracy Halo
                drawCircle(
                    color = peerColor.copy(alpha = 0.2f),
                    radius = 24f,
                    center = pinPos,
                )

                // Solid Red Pin Head
                drawCircle(
                    color = Color.White,
                    radius = 12f,
                    center = pinPos,
                )
                drawCircle(
                    color = peerColor,
                    radius = 9f,
                    center = pinPos,
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5f,
                    center = pinPos,
                )

                // Peer Name Tag Pill
                val dist = peer.estimatedDistanceMeters()
                val label = "${peer.name} (${dist}m)"
                val textW = peerLabelPaint.measureText(label)
                val badgeRect = android.graphics.RectF(
                    pinPos.x - textW / 2f - 14f,
                    pinPos.y + 16f,
                    pinPos.x + textW / 2f + 14f,
                    pinPos.y + 48f,
                )
                drawContext.canvas.nativeCanvas.drawRoundRect(badgeRect, 10f, 10f, peerBadgePaint)
                drawContext.canvas.nativeCanvas.drawText(label, pinPos.x, pinPos.y + 39f, peerLabelPaint)
            }

            // 5. Draw Live GPS User Beacon
            val accRadius = (myLocation.accuracyMeters * pixelsPerMeter).coerceIn(16f, 120f)
            // Accuracy Area Halo
            drawCircle(
                color = beaconHaloColor,
                radius = accRadius,
                center = center,
            )
            drawCircle(
                color = primaryColor.copy(alpha = 0.35f),
                radius = accRadius,
                center = center,
                style = Stroke(width = 1.2f),
            )

            // Pulsing Wave Ring
            val waveRadius = 14f + (36f * pulseFraction)
            drawCircle(
                color = primaryColor.copy(alpha = (1f - pulseFraction) * 0.4f),
                radius = waveRadius,
                center = center,
            )

            // Flashlight Heading Cone
            if (myLocation.bearingDegrees.toDouble() != 0.0) {
                val rad = ((myLocation.bearingDegrees - 90.0) * PI / 180.0).toFloat()
                val coneLength = 55f
                val conePath = Path().apply {
                    moveTo(center.x, center.y)
                    arcTo(
                        rect = androidx.compose.ui.geometry.Rect(
                            center.x - coneLength,
                            center.y - coneLength,
                            center.x + coneLength,
                            center.y + coneLength,
                        ),
                        startAngleDegrees = myLocation.bearingDegrees.toFloat() - 90f - 22f,
                        sweepAngleDegrees = 44f,
                        forceMoveTo = false,
                    )
                    close()
                }
                drawPath(
                    path = conePath,
                    brush = Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.45f), Color.Transparent),
                        center = center,
                        radius = coneLength,
                    ),
                )
            }

            // Solid Blue Location Beacon (Google Maps Style)
            drawCircle(color = Color.White, radius = 13f, center = center)
            drawCircle(color = primaryColor, radius = 9.5f, center = center)
            drawCircle(color = Color.White, radius = 3.5f, center = center)
        }

        // Top-Left Floating GPS Coordinate Pill
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            color = if (isDark) Color(0xFF1E293B).copy(alpha = 0.92f) else Color(0xFFFFFFFF).copy(alpha = 0.95f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(color = ITantraTheme.extended.success, pulsing = true, size = 8.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${"%.4f".format(myLocation.latitude)}° N, ${"%.4f".format(myLocation.longitude)}° E",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // Bottom-Right Floating Map Controls (+ / - / Recenter)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp),
        ) {
            FilledIconButton(
                onClick = { pixelsPerMeter = (pixelsPerMeter * 1.35f).coerceAtMost(8.0f) },
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Zoom in", modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            FilledIconButton(
                onClick = { pixelsPerMeter = (pixelsPerMeter / 1.35f).coerceAtLeast(0.6f) },
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Zoom out", modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            FilledIconButton(
                onClick = {
                    panOffset = Offset.Zero
                    pixelsPerMeter = 2.2f
                },
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "Recenter", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Draws procedural street grids, blocks, and arterial roads for tactical offline navigation. */
private fun DrawScope.drawTacticalMapBase(
    center: Offset,
    ppm: Float,
    gridColor: Color,
    majorGridColor: Color,
    roadColor: Color,
    roadBorderColor: Color,
    isDark: Boolean,
) {
    val step = 40f * ppm // 40 meter grid spacing

    // Fine grid
    val startX = (center.x % step + step) % step
    var x = startX
    while (x < size.width) {
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f,
        )
        x += step
    }

    val startY = (center.y % step + step) % step
    var y = startY
    while (y < size.height) {
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
        y += step
    }

    // Stylized Arterial Roads & Intersection
    val roadWidth = (14f * (ppm / 2f)).coerceIn(8f, 26f)

    // Horizontal Main Road
    val hRoadY = center.y + 70f * ppm
    drawLine(
        color = roadBorderColor,
        start = Offset(0f, hRoadY),
        end = Offset(size.width, hRoadY),
        strokeWidth = roadWidth + 3f,
    )
    drawLine(
        color = roadColor,
        start = Offset(0f, hRoadY),
        end = Offset(size.width, hRoadY),
        strokeWidth = roadWidth,
    )

    // Diagonal Cross Street
    val vRoadX = center.x - 60f * ppm
    drawLine(
        color = roadBorderColor,
        start = Offset(vRoadX, 0f),
        end = Offset(vRoadX + 80f * ppm, size.height),
        strokeWidth = roadWidth + 3f,
    )
    drawLine(
        color = roadColor,
        start = Offset(vRoadX, 0f),
        end = Offset(vRoadX + 80f * ppm, size.height),
        strokeWidth = roadWidth,
    )
}

/** Relative X/Y offset in meters from local device based on RSSI distance and stable angle. */
private fun NearbyPeer.calculateRelativeOffsetMeters(): Offset {
    val dist = estimatedDistanceMeters().toFloat()
    val angleRad = (stableAngleDegrees() * PI / 180.0).toFloat()
    val x = dist * sin(angleRad)
    val y = dist * cos(angleRad)
    return Offset(x, y)
}

private fun NearbyPeer.estimatedDistanceMeters(): Int = when (band) {
    RssiBand.NEAR -> 25
    RssiBand.MID -> 65
    RssiBand.FAR -> 120
}
