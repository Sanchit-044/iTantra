package `in`.gov.itantra.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import `in`.gov.itantra.core.discover.NearbyPeer
import `in`.gov.itantra.core.discover.NearbyPeerBook
import `in`.gov.itantra.core.discover.RssiBand
import `in`.gov.itantra.core.lang.UiStrings
import `in`.gov.itantra.core.location.GpsLocation
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.ui.components.IconBadge
import `in`.gov.itantra.ui.components.RealMapView
import `in`.gov.itantra.ui.components.SectionHeader
import `in`.gov.itantra.ui.components.StatusPill
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

enum class RadarDisplayMode { RADAR, HYBRID, MAP }

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

    var displayMode by remember { mutableStateOf(RadarDisplayMode.RADAR) }
    var selectedPeer by remember { mutableStateOf<NearbyPeer?>(null) }
    var activeLocationPeer by remember { mutableStateOf<NearbyPeer?>(null) }

    val myLoc = radar.myLocation
    val chrome = UiStrings.forLanguage(main.uiLanguage)
    val isDark = MaterialTheme.colorScheme.background.toArgb() < -0x800000

    val infiniteTransition = rememberInfiniteTransition(label = "radarAnimation")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweepAngle",
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulseScale",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
    ) {
        // Live GPS Telemetry Status Pill
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            shape = MaterialTheme.shapes.medium,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "GPS: ${"%.4f".format(myLoc.latitude)}° N, ${"%.4f".format(myLoc.longitude)}° E",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Accuracy ±${myLoc.accuracyMeters.toInt()}m · Alt ${myLoc.altitudeMeters.toInt()}m · Provider: ${myLoc.provider}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(
                    text = "Live GPS",
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }

        // Controls Row: Radio Filter & Display Mode Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = radar.filter == RadarFilter.BOTH,
                onClick = { radarViewModel.setFilter(RadarFilter.BOTH) },
                label = { Text(chrome.filterBoth) },
                leadingIcon = if (radar.filter == RadarFilter.BOTH) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
            )
            FilterChip(
                selected = radar.filter == RadarFilter.WIFI,
                onClick = { radarViewModel.setFilter(RadarFilter.WIFI) },
                label = { Text(chrome.channelWifiLabel) },
                leadingIcon = { Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = radar.filter == RadarFilter.BLUETOOTH,
                onClick = { radarViewModel.setFilter(RadarFilter.BLUETOOTH) },
                label = { Text(chrome.channelBluetoothLabel) },
                leadingIcon = { Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )

            Spacer(Modifier.width(8.dp))

            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = displayMode == RadarDisplayMode.RADAR,
                    onClick = { displayMode = RadarDisplayMode.RADAR },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    icon = { Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(16.dp)) },
                ) {
                    Text("Radar")
                }
                SegmentedButton(
                    selected = displayMode == RadarDisplayMode.HYBRID,
                    onClick = { displayMode = RadarDisplayMode.HYBRID },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    icon = { Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(16.dp)) },
                ) {
                    Text("Hybrid")
                }
                SegmentedButton(
                    selected = displayMode == RadarDisplayMode.MAP,
                    onClick = { displayMode = RadarDisplayMode.MAP },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    icon = { Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp)) },
                ) {
                    Text("Map")
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Main Live Map / Radar Viewport
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(bottom = 12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Layer 1: Real Geolocation Map (rendered in MAP and HYBRID modes)
                if (displayMode == RadarDisplayMode.MAP || displayMode == RadarDisplayMode.HYBRID) {
                    RealMapView(
                        myLocation = myLoc,
                        peers = radar.peers,
                        onPeerClick = { peer ->
                            activeLocationPeer = peer
                            selectedPeer = peer
                        },
                        isDark = isDark,
                        showRadarOverlay = false,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Layer 2: Tactical Military Radar Sweep Canvas (rendered on top of map)
                if (displayMode != RadarDisplayMode.MAP || true) {
                    val ringColor = MaterialTheme.colorScheme.primary.copy(alpha = if (displayMode == RadarDisplayMode.MAP) 0.12f else 0.25f)
                    val youColor = MaterialTheme.colorScheme.primary
                    val peerColor = MaterialTheme.colorScheme.secondary
                    val labelColor = MaterialTheme.colorScheme.onSurface

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .pointerInput(radar.peers, canJoin) {
                                detectTapGestures { tap ->
                                    if (!canJoin) return@detectTapGestures
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val maxR = min(size.width, size.height) / 2f * 0.9f
                                    val liveNow = SystemClock.elapsedRealtime()
                                    val layout = peerLayout(radar.peers, liveNow)
                                    val hit = layout.minByOrNull { placed ->
                                        val pos = polar(center, maxR, placed)
                                        hypot((tap.x - pos.x).toDouble(), (tap.y - pos.y).toDouble())
                                    } ?: return@detectTapGestures
                                    val pos = polar(center, maxR, hit)
                                    val dist = hypot((tap.x - pos.x).toDouble(), (tap.y - pos.y).toDouble())
                                    if (dist <= 80.0) {
                                        activeLocationPeer = hit.peer
                                    }
                                }
                            },
                    ) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val maxR = min(size.width, size.height) / 2f * 0.9f

                        // 1. Background tactical glow for RADAR mode
                        if (displayMode == RadarDisplayMode.RADAR) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        ringColor.copy(alpha = 0.2f),
                                        Color.Transparent,
                                    ),
                                    center = center,
                                    radius = maxR,
                                ),
                                radius = maxR,
                                center = center,
                            )
                        }

                        // 2. Rotating Radar Sweep Beam
                        if (displayMode != RadarDisplayMode.MAP) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    0.0f to youColor.copy(alpha = 0f),
                                    0.85f to youColor.copy(alpha = 0.02f),
                                    1.0f to youColor.copy(alpha = 0.35f),
                                    center = center,
                                ),
                                startAngle = sweepAngle - 45f,
                                sweepAngle = 45f,
                                useCenter = true,
                                size = androidx.compose.ui.geometry.Size(maxR * 2, maxR * 2),
                                topLeft = Offset(center.x - maxR, center.y - maxR),
                            )

                            // Leading sweep line
                            val rad = (sweepAngle * PI / 180.0).toFloat()
                            val lineEnd = Offset(
                                center.x + maxR * cos(rad),
                                center.y + maxR * sin(rad),
                            )
                            drawLine(
                                color = youColor.copy(alpha = 0.7f),
                                start = center,
                                end = lineEnd,
                                strokeWidth = 2f,
                            )
                        }

                        // 3. Concentric Range Rings with distance markers
                        val ringSteps = listOf(
                            0.25f to "50m",
                            0.50f to "100m",
                            0.75f to "150m",
                            1.00f to "200m",
                        )
                        val ringTextPaint = android.graphics.Paint().apply {
                            color = labelColor.copy(alpha = 0.45f).toArgb()
                            textSize = 22f
                            textAlign = android.graphics.Paint.Align.LEFT
                            isAntiAlias = true
                        }

                        ringSteps.forEach { (frac, label) ->
                            val r = maxR * frac
                            drawCircle(
                                color = ringColor,
                                radius = r,
                                center = center,
                                style = Stroke(width = 1.5f),
                            )
                            if (displayMode != RadarDisplayMode.MAP) {
                                drawContext.canvas.nativeCanvas.drawText(
                                    label,
                                    center.x + 6f,
                                    center.y - r + 20f,
                                    ringTextPaint,
                                )
                            }
                        }

                        // 4. Crosshairs and Cardinal Directions
                        if (displayMode != RadarDisplayMode.MAP) {
                            // Horizontal & Vertical crosshairs
                            drawLine(
                                color = ringColor.copy(alpha = 0.4f),
                                start = Offset(center.x - maxR, center.y),
                                end = Offset(center.x + maxR, center.y),
                                strokeWidth = 1f,
                            )
                            drawLine(
                                color = ringColor.copy(alpha = 0.4f),
                                start = Offset(center.x, center.y - maxR),
                                end = Offset(center.x, center.y + maxR),
                                strokeWidth = 1f,
                            )

                            val cardinalPaint = android.graphics.Paint().apply {
                                color = youColor.toArgb()
                                textSize = 26f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isFakeBoldText = true
                                isAntiAlias = true
                            }
                            drawContext.canvas.nativeCanvas.drawText("N", center.x, center.y - maxR - 10f, cardinalPaint)
                            drawContext.canvas.nativeCanvas.drawText("S", center.x, center.y + maxR + 24f, cardinalPaint)
                            drawContext.canvas.nativeCanvas.drawText("E", center.x + maxR + 18f, center.y + 8f, cardinalPaint)
                            drawContext.canvas.nativeCanvas.drawText("W", center.x - maxR - 18f, center.y + 8f, cardinalPaint)
                        }

                        // 5. Center User Dot with Pulsing Ripple
                        drawCircle(
                            color = youColor.copy(alpha = (1f - pulseScale) * 0.35f),
                            radius = 16f + (36f * pulseScale),
                            center = center,
                        )
                        drawCircle(color = youColor.copy(alpha = 0.25f), radius = 28f, center = center)
                        drawCircle(color = youColor, radius = 14f, center = center)
                        drawCircle(color = Color.White, radius = 5f, center = center)

                        // 6. Peers
                        val liveNow = SystemClock.elapsedRealtime()
                        val layout = peerLayout(radar.peers, liveNow)

                        val labelPaint = android.graphics.Paint().apply {
                            color = labelColor.toArgb()
                            textSize = 26f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            isFakeBoldText = true
                        }
                        val badgeBgPaint = android.graphics.Paint().apply {
                            color = if (isDark) android.graphics.Color.parseColor("#1E293B") else android.graphics.Color.WHITE
                            isAntiAlias = true
                        }

                        layout.forEach { placed ->
                            val pos = polar(center, maxR, placed)
                            val alpha = if (placed.fading) 0.4f else 1f

                            // Outer pulse for peer
                            drawCircle(
                                color = peerColor.copy(alpha = alpha * 0.25f),
                                radius = 28f,
                                center = pos,
                            )
                            // Solid peer dot
                            drawCircle(
                                color = peerColor.copy(alpha = alpha),
                                radius = 16f,
                                center = pos,
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = alpha),
                                radius = 5f,
                                center = pos,
                            )

                            val distMeters = placed.peer.estimatedDistanceMeters()
                            val text = "${placed.peer.name} (${distMeters}m)"

                            // Draw small badge background pill for readability
                            val textWidth = labelPaint.measureText(text)
                            val badgeRect = android.graphics.RectF(
                                pos.x - textWidth / 2f - 12f,
                                pos.y + 24f,
                                pos.x + textWidth / 2f + 12f,
                                pos.y + 56f,
                            )
                            drawContext.canvas.nativeCanvas.drawRoundRect(badgeRect, 12f, 12f, badgeBgPaint)
                            drawContext.canvas.nativeCanvas.drawText(
                                text,
                                pos.x,
                                pos.y + 46f,
                                labelPaint,
                            )
                        }
                    }
                }
            }
        }

        // Location Detail Card when a peer pin is tapped
        activeLocationPeer?.let { peer ->
            val coords = peer.simulatedLocationCoordinates(myLoc)
            val distMeters = peer.estimatedDistanceMeters()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(
                        icon = Icons.Filled.LocationOn,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        size = 44.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(peer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Lat: ${coords.first} · Lng: ${coords.second}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${distMeters}m away · ${peer.radiosLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Button(
                        onClick = {
                            selectedPeer = peer
                            activeLocationPeer = null
                        },
                        shape = MaterialTheme.shapes.medium,
                        enabled = canJoin,
                    ) {
                        Text(chrome.connect)
                    }
                }
            }
        }

        // Discovered Devices List
        if (radar.peers.isNotEmpty()) {
            SectionHeader(chrome.discoveredTitle) {
                StatusPill(
                    text = radar.peers.size.toString(),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                radar.peers.forEach { peer ->
                    Surface(
                        onClick = {
                            activeLocationPeer = peer
                            selectedPeer = peer
                        },
                        enabled = canJoin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = peer.name.take(1).uppercase(),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(peer.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${peer.band.label} (${peer.estimatedDistanceMeters()}m) · ${peer.radiosLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (peer.hasWifi) {
                                Icon(Icons.Filled.Wifi, contentDescription = chrome.channelWifiLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            if (peer.hasBluetooth) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = chrome.channelBluetoothLabel, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    selectedPeer?.let { peer ->
        AlertDialog(
            onDismissRequest = { selectedPeer = null },
            title = { Text(chrome.connectTo(peer.name)) },
            text = { Text(chrome.chooseRoleBody) },
            confirmButton = {
                Button(
                    onClick = {
                        selectedPeer = null
                        radarViewModel.stop()
                        val useWifi = peer.hasWifi && (radar.filter == RadarFilter.BOTH || radar.filter == RadarFilter.WIFI)
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
                Button(
                    onClick = {
                        selectedPeer = null
                        radarViewModel.stop()
                        val useWifi = peer.hasWifi && (radar.filter == RadarFilter.BOTH || radar.filter == RadarFilter.WIFI)
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
            }
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
                RssiBand.NEAR -> 0.28f
                RssiBand.MID -> 0.58f
                RssiBand.FAR -> 0.88f
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

private fun NearbyPeer.simulatedLocationCoordinates(myLoc: GpsLocation): Pair<String, String> {
    val dist = estimatedDistanceMeters()
    val angle = stableAngleDegrees()
    val lat = myLoc.latitude + (dist / 111000.0) * cos(angle * PI / 180.0)
    val lng = myLoc.longitude + (dist / (111000.0 * cos(myLoc.latitude * PI / 180.0))) * sin(angle * PI / 180.0)
    return "%.4f° N".format(lat) to "%.4f° E".format(lng)
}
