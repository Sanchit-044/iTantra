package `in`.gov.itantra.ui

import android.os.SystemClock
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.discover.NearbyPeer
import `in`.gov.itantra.core.discover.NearbyPeerBook
import `in`.gov.itantra.core.discover.RssiBand
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.ui.components.IconBadge
import `in`.gov.itantra.ui.components.SectionHeader
import `in`.gov.itantra.ui.components.StatusPill
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Clean, consistent Radar Screen adhering strictly to Material 3 design tokens.
 *
 * Provides a minimal, harmonious radar visualizer with concentric range rings,
 * a single sweep beam, nearest peer indicator, and discovered peer cards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadarScreen(
    mainViewModel: MainViewModel,
    radarViewModel: RadarViewModel = hiltViewModel(),
) {
    val radar by radarViewModel.uiState.collectAsState()
    val main by mainViewModel.uiState.collectAsState()
    val canJoin = (main.connectionState == ConnectionState.DISCONNECTED ||
        main.connectionState == ConnectionState.FAILED) && !main.reconnecting

    var selectedPeer by remember { mutableStateOf<NearbyPeer?>(null) }
    val myLoc = radar.myLocation
    val chrome = UiStrings.forLanguage(main.uiLanguage)

    // Calculate nearest peer based on RSSI distance
    val nearestPeer = remember(radar.peers) {
        radar.peers.minByOrNull { it.estimatedDistanceMeters() }
    }

    // Sweep animation
    val infiniteTransition = rememberInfiniteTransition(label = "radarSweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweepAngle",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Minimized Telemetry & Location Share Bar
        var locationShared by remember { mutableStateOf(false) }
        LaunchedEffect(locationShared) {
            if (locationShared) {
                delay(2000)
                locationShared = false
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.medium,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${"%.4f".format(myLoc.latitude)}° N, ${"%.4f".format(myLoc.longitude)}° E",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${radar.peers.size} nearby · ±${myLoc.accuracyMeters.toInt()}m",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(
                    onClick = {
                        val locText = "GPS: ${"%.4f".format(myLoc.latitude)}° N, ${"%.4f".format(myLoc.longitude)}° E (±${myLoc.accuracyMeters.toInt()}m)"
                        mainViewModel.sendQuickChat(locText)
                        locationShared = true
                    },
                    shape = MaterialTheme.shapes.small,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = if (locationShared) {
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    } else {
                        ButtonDefaults.filledTonalButtonColors()
                    },
                ) {
                    Icon(
                        imageVector = if (locationShared) Icons.Filled.Check else Icons.Filled.Share,
                        contentDescription = "Share Location",
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (locationShared) "Shared" else "Share",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }

        // Radar Canvas Viewport
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            ),
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val tertiaryColor = MaterialTheme.colorScheme.tertiary
            val ringColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            val textColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
            val peerTextColor = MaterialTheme.colorScheme.onSurface.toArgb()

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .pointerInput(radar.peers, canJoin) {
                        detectTapGestures { tap ->
                            if (!canJoin) return@detectTapGestures
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val maxR = min(size.width, size.height) / 2f * 0.88f
                            val liveNow = SystemClock.elapsedRealtime()
                            val layout = peerLayout(radar.peers, liveNow)
                            val hit = layout.minByOrNull { placed ->
                                val pos = polar(center, maxR, placed)
                                hypot((tap.x - pos.x).toDouble(), (tap.y - pos.y).toDouble())
                            } ?: return@detectTapGestures
                            val pos = polar(center, maxR, hit)
                            val dist = hypot((tap.x - pos.x).toDouble(), (tap.y - pos.y).toDouble())
                            if (dist <= 60.0) {
                                selectedPeer = hit.peer
                            }
                        }
                    },
            ) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = min(size.width, size.height) / 2f * 0.88f

                // Concentric range rings
                val rings = listOf(0.33f to "25m", 0.66f to "65m", 1.0f to "120m")
                val textPaint = android.graphics.Paint().apply {
                    color = textColor
                    textSize = 20f
                    textAlign = android.graphics.Paint.Align.LEFT
                    isAntiAlias = true
                }

                rings.forEach { (frac, label) ->
                    val r = maxR * frac
                    drawCircle(
                        color = ringColor,
                        radius = r,
                        center = center,
                        style = Stroke(width = 1f),
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        center.x + 6f,
                        center.y - r + 16f,
                        textPaint,
                    )
                }

                // Crosshairs
                drawLine(
                    color = ringColor.copy(alpha = 0.35f),
                    start = Offset(center.x - maxR, center.y),
                    end = Offset(center.x + maxR, center.y),
                    strokeWidth = 1f,
                )
                drawLine(
                    color = ringColor.copy(alpha = 0.35f),
                    start = Offset(center.x, center.y - maxR),
                    end = Offset(center.x, center.y + maxR),
                    strokeWidth = 1f,
                )

                // Rotating Sweep Sector & Leading Line
                rotate(degrees = sweepAngle, pivot = center) {
                    // 45-degree trailing scanning sector wedge
                    drawArc(
                        brush = Brush.sweepGradient(
                            0.875f to Color.Transparent,
                            1.0f to primaryColor.copy(alpha = 0.28f),
                            center = center,
                        ),
                        startAngle = -45f,
                        sweepAngle = 45f,
                        useCenter = true,
                        size = Size(maxR * 2, maxR * 2),
                        topLeft = Offset(center.x - maxR, center.y - maxR),
                    )

                    // Crisp leading edge scan line
                    drawLine(
                        color = primaryColor.copy(alpha = 0.85f),
                        start = center,
                        end = Offset(center.x + maxR, center.y),
                        strokeWidth = 2f,
                    )
                }

                // Center User Position
                drawCircle(color = primaryColor.copy(alpha = 0.2f), radius = 16f, center = center)
                drawCircle(color = primaryColor, radius = 7f, center = center)
                drawCircle(color = Color.White, radius = 2.5f, center = center)

                // Discovered Peer Blips
                val liveNow = SystemClock.elapsedRealtime()
                val layout = peerLayout(radar.peers, liveNow)

                val peerNamePaint = android.graphics.Paint().apply {
                    color = peerTextColor
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    isFakeBoldText = true
                }

                layout.forEach { placed ->
                    val isNearest = placed.peer.id == nearestPeer?.id
                    val pos = polar(center, maxR, placed)
                    val blipColor = if (isNearest) tertiaryColor else primaryColor
                    val alpha = if (placed.fading) 0.4f else 1f

                    // Outer dot halo
                    drawCircle(
                        color = blipColor.copy(alpha = 0.2f * alpha),
                        radius = if (isNearest) 18f else 14f,
                        center = pos,
                    )
                    // Core dot
                    drawCircle(
                        color = blipColor.copy(alpha = alpha),
                        radius = if (isNearest) 8f else 6f,
                        center = pos,
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = 2.5f,
                        center = pos,
                    )

                    // Clean label
                    val label = placed.peer.name
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        pos.x,
                        pos.y + 26f,
                        peerNamePaint,
                    )
                }
            }
        }

        // Nearest Device Highlight Card (Clean & Consistent)
        nearestPeer?.let { nearest ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f),
                ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        icon = Icons.Filled.NearMe,
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        size = 38.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NEAREST PEER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Text(
                            text = nearest.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "${nearest.estimatedDistanceMeters()}m · ${nearest.radiosLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    FilledTonalButton(
                        onClick = { selectedPeer = nearest },
                        enabled = canJoin,
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(chrome.connect, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Discovered Devices List
        if (radar.peers.isNotEmpty()) {
            SectionHeader(chrome.discoveredTitle) {
                Text(
                    text = "${radar.peers.size} nearby",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                radar.peers.forEach { peer ->
                    val isNearest = peer.id == nearestPeer?.id
                    Surface(
                        onClick = { selectedPeer = peer },
                        enabled = canJoin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isNearest) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(
                                        if (isNearest) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        CircleShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = peer.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isNearest) MaterialTheme.colorScheme.onSecondaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = peer.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (isNearest) {
                                        Spacer(Modifier.width(6.dp))
                                        StatusPill(
                                            text = "Nearest",
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                                Text(
                                    text = "${peer.estimatedDistanceMeters()}m · ${peer.radiosLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (peer.hasWifi) {
                                Icon(
                                    Icons.Filled.Wifi,
                                    contentDescription = chrome.channelWifiLabel,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            if (peer.hasBluetooth) {
                                Icon(
                                    Icons.Filled.Bluetooth,
                                    contentDescription = chrome.channelBluetoothLabel,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Connect / Role Selection Modal
    selectedPeer?.let { peer ->
        AlertDialog(
            onDismissRequest = { selectedPeer = null },
            title = { Text(chrome.connectTo(peer.name), style = MaterialTheme.typography.titleLarge) },
            text = { Text(chrome.chooseRoleBody, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(
                    onClick = {
                        selectedPeer = null
                        radarViewModel.stop()
                        val useWifi = peer.hasWifi
                        if (useWifi) {
                            mainViewModel.setConnectionMode(`in`.gov.itantra.ui.ConnectionMode.WIFI_DIRECT_HOST)
                            mainViewModel.connect()
                        } else {
                            mainViewModel.setConnectionMode(`in`.gov.itantra.ui.ConnectionMode.BLUETOOTH_HOST)
                            mainViewModel.connect()
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(chrome.hostRole)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        selectedPeer = null
                        radarViewModel.stop()
                        val useWifi = peer.hasWifi
                        if (useWifi) {
                            mainViewModel.setConnectionMode(`in`.gov.itantra.ui.ConnectionMode.WIFI_DIRECT_CLIENT)
                            mainViewModel.connect(peerAddress = peer.wifiAddress, preferredWifiAddress = peer.wifiAddress)
                        } else {
                            val addr = peer.bluetoothAddress ?: ""
                            mainViewModel.setConnectionMode(`in`.gov.itantra.ui.ConnectionMode.BLUETOOTH_CLIENT)
                            mainViewModel.selectDevice(addr)
                            mainViewModel.connect(peerAddress = addr, peerName = peer.name)
                        }
                    },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(chrome.joinRole)
                }
            },
        )
    }
}

private data class PlacedPeer(
    val peer: NearbyPeer,
    val radiusFrac: Float,
    val angleDeg: Float,
    val fading: Boolean,
)

private fun peerLayout(peers: List<NearbyPeer>, nowMs: Long): List<PlacedPeer> =
    peers.map { peer ->
        PlacedPeer(
            peer = peer,
            radiusFrac = when (peer.band) {
                RssiBand.NEAR -> 0.30f
                RssiBand.MID -> 0.62f
                RssiBand.FAR -> 0.90f
            },
            angleDeg = peer.stableAngleDegrees(),
            fading = NearbyPeerBook.fading(peer, nowMs),
        )
    }

private fun polar(center: Offset, maxR: Float, placed: PlacedPeer): Offset {
    val rad = ((placed.angleDeg - 90f) * PI / 180.0)
    val r = maxR * placed.radiusFrac
    return Offset(
        center.x + (r * cos(rad)).toFloat(),
        center.y + (r * sin(rad)).toFloat(),
    )
}

private fun NearbyPeer.estimatedDistanceMeters(): Int = when (band) {
    RssiBand.NEAR -> 25
    RssiBand.MID -> 65
    RssiBand.FAR -> 120
}
