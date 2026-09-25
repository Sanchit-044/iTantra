package `in`.gov.itantra.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
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
import `in`.gov.itantra.core.transport.ConnectionState
import `in`.gov.itantra.ui.components.IconBadge
import `in`.gov.itantra.ui.components.SectionHeader
import `in`.gov.itantra.ui.components.StatusPill
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
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

    var displayMode by remember { mutableStateOf(RadarDisplayMode.HYBRID) }
    var selectedPeer by remember { mutableStateOf<NearbyPeer?>(null) }
    var activeLocationPeer by remember { mutableStateOf<NearbyPeer?>(null) }
    val chrome = UiStrings.forLanguage(main.uiLanguage)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
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

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val ringColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                val youColor = MaterialTheme.colorScheme.primary
                val peerColor = MaterialTheme.colorScheme.secondary
                val sweepColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                val labelColor = MaterialTheme.colorScheme.onSurface
                val isDark = MaterialTheme.colorScheme.background.toArgb() < -0x800000

                // Map & Radar Drawing
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

                    // 1. Render Google Maps Vector Terrain Grid (if Hybrid or Map)
                    if (displayMode == RadarDisplayMode.HYBRID || displayMode == RadarDisplayMode.MAP) {
                        drawVectorMapBackground(
                            center = center,
                            maxR = maxR,
                            isDark = isDark,
                        )
                    }

                    // 2. Render Radar Sweep overlay (if Radar or Hybrid)
                    if (displayMode == RadarDisplayMode.RADAR || displayMode == RadarDisplayMode.HYBRID) {
                        // Concentric Rings
                        listOf(0.33f, 0.66f, 1f).forEach { frac ->
                            drawCircle(
                                color = ringColor,
                                radius = maxR * frac,
                                center = center,
                                style = Stroke(width = 2.5f),
                            )
                        }
                    }

                    // 3. Render Location Markers / Device Pins
                    val liveNow = SystemClock.elapsedRealtime()
                    val layout = peerLayout(radar.peers, liveNow)

                    // Draw connecting line if a peer is active/selected
                    activeLocationPeer?.let { active ->
                        val activePlaced = layout.firstOrNull { it.peer.id == active.id }
                        if (activePlaced != null) {
                            val targetPos = polar(center, maxR, activePlaced)
                            drawLine(
                                color = youColor,
                                start = center,
                                end = targetPos,
                                strokeWidth = 3f,
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f),
                            )
                        }
                    }

                    // Center "Your Location" pin
                    drawCircle(color = youColor.copy(alpha = 0.25f), radius = 36f, center = center)
                    drawCircle(color = youColor, radius = 16f, center = center)
                    drawCircle(color = Color.White, radius = 6f, center = center)

                    // Peer Map Pins
                    val labelPaint = android.graphics.Paint().apply {
                        color = labelColor.toArgb()
                        textSize = 28f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        isFakeBoldText = true
                    }

                    layout.forEach { placed ->
                        val pos = polar(center, maxR, placed)
                        val alpha = if (placed.fading) 0.4f else 1f

                        if (displayMode == RadarDisplayMode.MAP || displayMode == RadarDisplayMode.HYBRID) {
                            // Google Maps Pin Marker
                            val pinRadius = 18f
                            drawCircle(
                                color = peerColor.copy(alpha = alpha),
                                radius = pinRadius + 6f,
                                center = Offset(pos.x, pos.y - 10f),
                            )
                            drawCircle(
                                color = Color.White,
                                radius = pinRadius,
                                center = Offset(pos.x, pos.y - 10f),
                            )
                            drawCircle(
                                color = peerColor.copy(alpha = alpha),
                                radius = pinRadius - 6f,
                                center = Offset(pos.x, pos.y - 10f),
                            )
                        } else {
                            // Standard Radar Dot
                            drawCircle(
                                color = peerColor.copy(alpha = alpha),
                                radius = 22f,
                                center = pos,
                            )
                        }

                        // Peer Name Label
                        val distMeters = placed.peer.estimatedDistanceMeters()
                        drawContext.canvas.nativeCanvas.drawText(
                            "${placed.peer.name} (${distMeters}m)",
                            pos.x,
                            pos.y + 36f,
                            labelPaint,
                        )
                    }
                }

                // Overlay Statuses (Reconnecting, Connecting, Scanning)
                when {
                    main.reconnecting -> {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f), MaterialTheme.shapes.medium)
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    chrome.reconnecting(main.reconnectAttempt),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = { mainViewModel.disconnect() }) {
                                    Text(chrome.pairingCancel)
                                }
                            }
                        }
                    }
                    main.connectionState == ConnectionState.CONNECTED -> {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f), MaterialTheme.shapes.medium)
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Text(
                                chrome.disconnectFirst,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    main.connectionState == ConnectionState.DISCOVERING || main.connectionState == ConnectionState.HANDSHAKING -> {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f), MaterialTheme.shapes.medium)
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    chrome.connectingLabel,
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = { mainViewModel.disconnect() }) {
                                    Text(chrome.pairingCancel)
                                }
                            }
                        }
                    }
                    else -> {
                        if (radar.scanning && radar.peers.isEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(chrome.scanningPeers, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Location Detail Card when a peer pin on map is tapped
        activeLocationPeer?.let { peer ->
            val coords = peer.simulatedLocationCoordinates()
            val distMeters = peer.estimatedDistanceMeters()
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
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
            LazyColumn(
                modifier = Modifier.heightIn(max = 160.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(radar.peers, key = { it.id }) { peer ->
                    Surface(
                        onClick = {
                            activeLocationPeer = peer
                            selectedPeer = peer
                        },
                        enabled = canJoin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.secondary, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = peer.name.take(1).uppercase(),
                                    color = MaterialTheme.colorScheme.onSecondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(peer.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${peer.band.label} (${peer.estimatedDistanceMeters()}m) · ${peer.radiosLabel}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                )
                            }
                            if (peer.hasWifi) {
                                Icon(Icons.Filled.Wifi, contentDescription = chrome.channelWifiLabel, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                            }
                            if (peer.hasBluetooth) {
                                Icon(Icons.Filled.Bluetooth, contentDescription = chrome.channelBluetoothLabel, modifier = Modifier.size(18.dp))
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
                Button(onClick = {
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
                }) {
                    Text(chrome.hostRole)
                }
            },
            dismissButton = {
                Button(onClick = {
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
                }) {
                    Text(chrome.joinRole)
                }
            }
        )
    }
}

/** Vector Map Renderer: Street Grid, River Path, Terrain Blocks & Coordinates */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawVectorMapBackground(
    center: Offset,
    maxR: Float,
    isDark: Boolean,
) {
    val roadColor = if (isDark) Color(0xFF2C323B) else Color(0xFFFFFFFF)
    val roadBorderColor = if (isDark) Color(0xFF1E222A) else Color(0xFFE5E9F0)
    val riverColor = if (isDark) Color(0xFF1E3A5F) else Color(0xFFA8DADC)
    val parkColor = if (isDark) Color(0xFF1B3B2B) else Color(0xFFD8F3DC)
    val gridLineColor = if (isDark) Color(0xFF252B33) else Color(0xFFE2E8F0)

    // 1. Grid Lines (Latitude/Longitude simulation)
    val gridStep = maxR / 3f
    for (i in -3..3) {
        val x = center.x + i * gridStep
        val y = center.y + i * gridStep
        drawLine(color = gridLineColor, start = Offset(x, center.y - maxR), end = Offset(x, center.y + maxR), strokeWidth = 1f)
        drawLine(color = gridLineColor, start = Offset(center.x - maxR, y), end = Offset(center.x + maxR, y), strokeWidth = 1f)
    }

    // 2. Park / Greenery Polygon Block
    val parkPath = Path().apply {
        moveTo(center.x - maxR * 0.7f, center.y - maxR * 0.8f)
        lineTo(center.x - maxR * 0.2f, center.y - maxR * 0.9f)
        lineTo(center.x - maxR * 0.1f, center.y - maxR * 0.4f)
        lineTo(center.x - maxR * 0.6f, center.y - maxR * 0.3f)
        close()
    }
    drawPath(path = parkPath, color = parkColor)

    // 3. River / Water Path
    val riverPath = Path().apply {
        moveTo(center.x + maxR * 0.9f, center.y - maxR * 0.9f)
        cubicTo(
            center.x + maxR * 0.4f, center.y - maxR * 0.3f,
            center.x + maxR * 0.7f, center.y + maxR * 0.3f,
            center.x + maxR * 0.2f, center.y + maxR * 0.9f,
        )
    }
    drawPath(path = riverPath, color = riverColor, style = Stroke(width = 24f))

    // 4. Street / Highway Network Grid Lines
    val highwayPath = Path().apply {
        // Main Arterial Highway (Horizontal)
        moveTo(center.x - maxR, center.y + maxR * 0.2f)
        lineTo(center.x + maxR, center.y + maxR * 0.1f)

        // Main Avenue (Vertical)
        moveTo(center.x - maxR * 0.2f, center.y - maxR)
        lineTo(center.x + maxR * 0.1f, center.y + maxR)
    }
    drawPath(path = highwayPath, color = roadBorderColor, style = Stroke(width = 16f))
    drawPath(path = highwayPath, color = roadColor, style = Stroke(width = 10f))
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

private fun NearbyPeer.simulatedLocationCoordinates(): Pair<String, String> {
    val baseLat = 28.6139
    val baseLng = 77.2090
    val dist = estimatedDistanceMeters()
    val angle = stableAngleDegrees()
    val lat = baseLat + (dist / 111000.0) * cos(angle * PI / 180.0)
    val lng = baseLng + (dist / (111000.0 * cos(baseLat * PI / 180.0))) * sin(angle * PI / 180.0)
    return "%.4f° N".format(lat) to "%.4f° E".format(lng)
}
