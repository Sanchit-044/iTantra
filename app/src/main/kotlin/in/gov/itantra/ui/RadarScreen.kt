package `in`.gov.itantra.ui

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import `in`.gov.itantra.core.transport.ConnectionState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {

        Text(
            text = "Discover nearby walkie-talkies. The other device must be in Host mode. Tap to connect.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = radar.filter == RadarFilter.BOTH,
                onClick = { radarViewModel.setFilter(RadarFilter.BOTH) },
                label = { Text("Both") },
            )
            FilterChip(
                selected = radar.filter == RadarFilter.WIFI,
                onClick = { radarViewModel.setFilter(RadarFilter.WIFI) },
                label = { Text("Wi-Fi") },
                leadingIcon = { Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
            FilterChip(
                selected = radar.filter == RadarFilter.BLUETOOTH,
                onClick = { radarViewModel.setFilter(RadarFilter.BLUETOOTH) },
                label = { Text("Bluetooth") },
                leadingIcon = { Icon(Icons.Filled.Bluetooth, contentDescription = null, modifier = Modifier.size(16.dp)) }
            )
        }
        
        Spacer(Modifier.height(16.dp))
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(24.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val ringColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                val youColor = MaterialTheme.colorScheme.primary
                val peerColor = MaterialTheme.colorScheme.secondary
                val sweepColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                val labelColor = MaterialTheme.colorScheme.onSurface
                
                RadarPlot(
                    peers = radar.peers,
                    ringColor = ringColor,
                    youColor = youColor,
                    peerColor = peerColor,
                    sweepColor = sweepColor,
                    labelColor = labelColor,
                    enabled = canJoin,
                    onPeerTap = { peer ->
                        if (!canJoin) return@RadarPlot
                        radarViewModel.stop()
                        
                        val myName = main.localProfile.displayName
                        val peerName = peer.name
                        val isHost = if (myName != peerName) myName > peerName else true
                        
                        val useWifi = peer.hasWifi && (radar.filter == RadarFilter.BOTH || radar.filter == RadarFilter.WIFI)
                        val useBluetooth = peer.hasBluetooth && (radar.filter == RadarFilter.BOTH || radar.filter == RadarFilter.BLUETOOTH)
                        
                        if (useWifi) {
                            // Both devices act as CLIENT and let Wi-Fi Direct negotiate the
                            // Group Owner -- but an unbiased negotiation is a coin flip that
                            // sometimes needs a retry (see WifiDirectTransport). isHost is
                            // computed identically on both handsets from the same two names,
                            // so passing it as preferGroupOwner makes exactly one side win on
                            // the first attempt instead of leaving it to chance.
                            mainViewModel.setConnectionMode(`in`.gov.itantra.ui.ConnectionMode.WIFI_DIRECT_CLIENT)
                            mainViewModel.connect(
                                peerAddress = peer.wifiAddress,
                                preferredWifiAddress = peer.wifiAddress,
                                preferGroupOwner = isHost,
                            )
                        } else if (useBluetooth) {
                            val addr = peer.bluetoothAddress ?: ""
                            if (isHost) {
                                mainViewModel.setConnectionMode(`in`.gov.itantra.ui.ConnectionMode.BLUETOOTH_HOST)
                                mainViewModel.connect()
                            } else {
                                mainViewModel.setConnectionMode(`in`.gov.itantra.ui.ConnectionMode.BLUETOOTH_CLIENT)
                                mainViewModel.selectDevice(addr)
                                mainViewModel.connect(peerAddress = addr)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                )

                when {
                    main.reconnecting -> {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Reconnecting… (attempt ${main.reconnectAttempt})",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { mainViewModel.disconnect() }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                    main.connectionState == ConnectionState.CONNECTED -> {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                "Disconnect on Talk screen before joining.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    main.connectionState == ConnectionState.DISCOVERING || main.connectionState == ConnectionState.HANDSHAKING -> {
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Connecting...",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { mainViewModel.disconnect() }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                    else -> {
                        if (radar.scanning && radar.peers.isEmpty()) {
                            Text(
                                "Scanning for peers...",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
                            )
                        }
                    }
                }
            }
        }
        
        if (radar.peers.isNotEmpty()) {
            Text("Discovered (${radar.peers.size})", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.height(140.dp).fillMaxWidth()) {
                items(radar.peers, key = { it.id }) { peer ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = canJoin) {
                                selectedPeer = peer
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp).fillMaxWidth(),
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
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(peer.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text("${peer.band.label} · ${peer.radiosLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f))
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
            title = { Text("Connect to ${peer.name}") },
            text = { Text("Choose your role for this connection.\nThe Host should wait for the Joiner to connect.") },
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
                    Text("Host")
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
                    Text("Join")
                }
            }
        )
    }
}

@Composable
private fun RadarPlot(
    peers: List<NearbyPeer>,
    ringColor: Color,
    youColor: Color,
    peerColor: Color,
    sweepColor: Color,
    labelColor: Color,
    enabled: Boolean,
    onPeerTap: (NearbyPeer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sweep = rememberInfiniteTransition(label = "radar-sweep").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    val sweepAngle = sweep.value
    val liveNow = SystemClock.elapsedRealtime()
    val layout = remember(peers, liveNow / 500) { peerLayout(peers, liveNow) }
    val labelPaint = remember(labelColor) {
        android.graphics.Paint().apply {
            color = labelColor.toArgb()
            textSize = 32f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = modifier.pointerInput(layout, enabled) {
            detectTapGestures { tap ->
                if (!enabled) return@detectTapGestures
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = min(size.width, size.height) / 2f * 0.9f
                val hit = layout.minByOrNull { placed ->
                    val pos = polar(center, maxR, placed)
                    hypot((tap.x - pos.x).toDouble(), (tap.y - pos.y).toDouble())
                } ?: return@detectTapGestures
                val pos = polar(center, maxR, hit)
                val dist = hypot((tap.x - pos.x).toDouble(), (tap.y - pos.y).toDouble())
                if (dist <= 80.0) onPeerTap(hit.peer)
            }
        },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxR = min(size.width, size.height) / 2f * 0.9f
        
        // Concentric Rings
        listOf(0.33f, 0.66f, 1f).forEach { frac ->
            drawCircle(
                color = ringColor,
                radius = maxR * frac,
                center = center,
                style = Stroke(width = 3f),
            )
        }
        
        // Radar Sweep Cone
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(Color.Transparent, sweepColor, Color.Transparent),
                center = center
            ),
            startAngle = sweepAngle - 90f - 45f,
            sweepAngle = 45f,
            useCenter = true,
            topLeft = Offset(center.x - maxR, center.y - maxR),
            size = Size(maxR * 2, maxR * 2)
        )
        
        // Sweep Line
        val sweepRad = ((sweepAngle - 90f) * PI / 180.0).toFloat()
        drawLine(
            color = youColor.copy(alpha = 0.8f),
            start = center,
            end = Offset(center.x + maxR * cos(sweepRad), center.y + maxR * sin(sweepRad)),
            strokeWidth = 4f,
        )
        
        // Center "You" dot
        drawCircle(color = youColor, radius = 20f, center = center)
        drawCircle(color = youColor.copy(alpha = 0.3f), radius = 32f, center = center) // Inner glow

        layout.forEach { placed ->
            val pos = polar(center, maxR, placed)
            val alpha = if (placed.fading) 0.35f else 1f
            
            // Peer Dot
            drawCircle(
                color = peerColor.copy(alpha = alpha),
                radius = 24f,
                center = pos,
            )
            drawCircle(
                color = peerColor.copy(alpha = alpha * 0.3f),
                radius = 36f,
                center = pos,
            ) // Peer glow
            
            drawContext.canvas.nativeCanvas.drawText(
                "${placed.peer.name}",
                pos.x,
                pos.y + 50f,
                labelPaint,
            )
        }
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
