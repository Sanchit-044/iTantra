package `in`.gov.itantra.ui

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
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

@Composable
fun RadarScreen(
    mainViewModel: MainViewModel,
    radarViewModel: RadarViewModel = hiltViewModel(),
) {
    val radar by radarViewModel.uiState.collectAsState()
    val main by mainViewModel.uiState.collectAsState()
    val canJoin = main.connectionState == ConnectionState.DISCONNECTED ||
        main.connectionState == ConnectionState.FAILED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text("Radar", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Signal rings around you — not a map, not true north. " +
                "Other phone must Host on Talk. This tab only joins.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
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
            )
            FilterChip(
                selected = radar.filter == RadarFilter.BLUETOOTH,
                onClick = { radarViewModel.setFilter(RadarFilter.BLUETOOTH) },
                label = { Text("Bluetooth") },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = when {
                !canJoin -> "Disconnect on Talk before joining another phone."
                radar.scanning && radar.peers.isEmpty() -> "Scanning… near / mid / far from signal, not feet."
                radar.peers.isEmpty() -> "No visible iTantra peers."
                else -> "${radar.peers.size} nearby — tap a dot to connect"
            },
            style = MaterialTheme.typography.bodySmall,
        )
        if (radar.scanError != null) {
            Text(
                radar.scanError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (main.error != null) {
            Text(
                main.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(8.dp))
        val ringColor = MaterialTheme.colorScheme.outline
        val youColor = MaterialTheme.colorScheme.primary
        val peerColor = MaterialTheme.colorScheme.error
        val sweepColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        val labelColor = MaterialTheme.colorScheme.onSurface
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
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
                    mainViewModel.connectToNearbyPeer(peer)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (radar.peers.isNotEmpty()) {
            LazyColumn(modifier = Modifier.height(120.dp).fillMaxWidth()) {
                items(radar.peers, key = { it.id }) { peer ->
                    TextButton(
                        onClick = {
                            if (!canJoin) return@TextButton
                            radarViewModel.stop()
                            mainViewModel.connectToNearbyPeer(peer)
                        },
                        enabled = canJoin,
                    ) {
                        Text("${peer.name} · ${peer.band.label} · ${peer.radiosLabel}")
                    }
                }
            }
        }
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
            animation = tween(durationMillis = 4000, easing = LinearEasing),
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
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    Canvas(
        modifier = modifier.pointerInput(layout, enabled) {
            detectTapGestures { tap ->
                if (!enabled) return@detectTapGestures
                val center = Offset(size.width / 2f, size.height / 2f)
                val maxR = min(size.width, size.height) / 2f * 0.88f
                val hit = layout.minByOrNull { placed ->
                    val pos = polar(center, maxR, placed)
                    hypot((tap.x - pos.x).toDouble(), (tap.y - pos.y).toDouble())
                } ?: return@detectTapGestures
                val pos = polar(center, maxR, hit)
                val dist = hypot((tap.x - pos.x).toDouble(), (tap.y - pos.y).toDouble())
                if (dist <= 64.0) onPeerTap(hit.peer)
            }
        },
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxR = min(size.width, size.height) / 2f * 0.88f
        listOf(0.33f, 0.66f, 1f).forEach { frac ->
            drawCircle(
                color = ringColor,
                radius = maxR * frac,
                center = center,
                style = Stroke(width = 2f),
            )
        }
        val sweepRad = ((sweepAngle - 90f) * PI / 180.0).toFloat()
        drawLine(
            color = sweepColor,
            start = center,
            end = Offset(center.x + maxR * cos(sweepRad), center.y + maxR * sin(sweepRad)),
            strokeWidth = 6f,
        )
        drawCircle(color = youColor, radius = 14f, center = center)

        layout.forEach { placed ->
            val pos = polar(center, maxR, placed)
            val alpha = if (placed.fading) 0.35f else 1f
            drawCircle(
                color = peerColor.copy(alpha = alpha),
                radius = 16f,
                center = pos,
            )
            drawContext.canvas.nativeCanvas.drawText(
                "${placed.peer.name} · ${placed.peer.band.label}",
                pos.x,
                pos.y + 32f,
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
