package `in`.gov.itantra.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import `in`.gov.itantra.core.discover.NearbyPeer
import `in`.gov.itantra.core.location.GpsLocation
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real interactive Google Maps / Radar style live map.
 * 100% offline capable: uses a self-contained vector & canvas map engine,
 * pulsing blue GPS location beacon, heading cone, red peer location pins with interactive connect popups,
 * and radar distance range overlays.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealMapView(
    myLocation: GpsLocation,
    peers: List<NearbyPeer>,
    onPeerClick: (NearbyPeer) -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false,
    showRadarOverlay: Boolean = false,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isMapLoaded by remember { mutableStateOf(false) }

    val currentPeers by rememberUpdatedState(peers)
    val onPeerClickUpdated by rememberUpdatedState(onPeerClick)

    // Update Live Location on Map
    LaunchedEffect(myLocation, isMapLoaded) {
        if (isMapLoaded) {
            webViewRef?.evaluateJavascript(
                "if (window.updateLocation) { window.updateLocation(${myLocation.latitude}, ${myLocation.longitude}, ${myLocation.accuracyMeters}, ${myLocation.bearingDegrees}); }",
                null,
            )
        }
    }

    // Update Peer Pins on Map
    LaunchedEffect(peers, isMapLoaded, myLocation) {
        if (isMapLoaded) {
            val jsonArray = JSONArray()
            peers.forEach { peer ->
                val coords = peer.calculateCoordinates(myLocation)
                val obj = JSONObject().apply {
                    put("id", peer.id)
                    put("name", peer.name)
                    put("lat", coords.first)
                    put("lng", coords.second)
                    put("distance", peer.estimatedDistanceMeters())
                    put("band", peer.band.name)
                    put("radios", peer.radiosLabel)
                }
                jsonArray.put(obj)
            }
            webViewRef?.evaluateJavascript(
                "if (window.updatePeers) { window.updatePeers(${jsonArray}); }",
                null,
            )
        }
    }

    // Update Radar Overlay Mode
    LaunchedEffect(showRadarOverlay, isMapLoaded) {
        if (isMapLoaded) {
            webViewRef?.evaluateJavascript(
                "if (window.setRadarOverlay) { window.setRadarOverlay($showRadarOverlay); }",
                null,
            )
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    allowFileAccess = true
                    loadsImagesAutomatically = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isMapLoaded = true
                        view?.evaluateJavascript(
                            "if (window.initMap) { window.initMap(${myLocation.latitude}, ${myLocation.longitude}, $isDark, $showRadarOverlay); }",
                            null,
                        )
                    }
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onPeerSelected(peerId: String) {
                        val peer = currentPeers.firstOrNull { it.id == peerId }
                        peer?.let { onPeerClickUpdated(it) }
                    }
                }, "AndroidBridge")

                loadDataWithBaseURL(
                    "https://app.local/",
                    getMapHtml(myLocation.latitude, myLocation.longitude, isDark, showRadarOverlay),
                    "text/html",
                    "UTF-8",
                    null,
                )
                webViewRef = this
            }
        },
        update = { webView ->
            webViewRef = webView
        },
    )
}

private fun NearbyPeer.calculateCoordinates(myLoc: GpsLocation): Pair<Double, Double> {
    val dist = estimatedDistanceMeters()
    val angle = stableAngleDegrees()
    val lat = myLoc.latitude + (dist / 111000.0) * kotlin.math.cos(angle * Math.PI / 180.0)
    val lng = myLoc.longitude + (dist / (111000.0 * kotlin.math.cos(myLoc.latitude * Math.PI / 180.0))) * kotlin.math.sin(angle * Math.PI / 180.0)
    return lat to lng
}

private fun NearbyPeer.estimatedDistanceMeters(): Int = when (band) {
    `in`.gov.itantra.core.discover.RssiBand.NEAR -> 25
    `in`.gov.itantra.core.discover.RssiBand.MID -> 65
    `in`.gov.itantra.core.discover.RssiBand.FAR -> 120
}

private fun getMapHtml(initialLat: Double, initialLng: Double, isDark: Boolean, showRadarOverlay: Boolean): String {
    val bgColor = if (isDark) "#0F172A" else "#F8FAFC"
    val cardBg = if (isDark) "#1E293B" else "#FFFFFF"
    val cardText = if (isDark) "#F8FAFC" else "#0F172A"

    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <style>
        * { box-sizing: border-box; -webkit-touch-callout: none; -webkit-user-select: none; user-select: none; }
        html, body { height: 100%; width: 100%; margin: 0; padding: 0; overflow: hidden; background: $bgColor; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        
        #map-container { position: relative; width: 100%; height: 100%; cursor: grab; }
        #map-container:active { cursor: grabbing; }
        
        canvas { display: block; width: 100%; height: 100%; }
        
        /* Peer Popup Card */
        #popup {
            position: absolute;
            display: none;
            background: $cardBg;
            color: $cardText;
            padding: 12px 16px;
            border-radius: 12px;
            box-shadow: 0 8px 24px rgba(0,0,0,0.3);
            border: 1px solid rgba(148, 163, 184, 0.25);
            z-index: 100;
            min-width: 170px;
            pointer-events: auto;
            transform: translate(-50%, -120%);
            transition: transform 0.15s ease-out;
        }
        #popup::after {
            content: '';
            position: absolute;
            bottom: -6px;
            left: 50%;
            transform: translateX(-50%) rotate(45deg);
            width: 12px;
            height: 12px;
            background: $cardBg;
            border-right: 1px solid rgba(148, 163, 184, 0.25);
            border-bottom: 1px solid rgba(148, 163, 184, 0.25);
        }
        .popup-title { font-weight: 700; font-size: 14px; margin-bottom: 4px; display: flex; align-items: center; gap: 6px; }
        .popup-sub { font-size: 11px; color: #64748B; margin-bottom: 8px; }
        .popup-btn {
            background: #2563EB;
            color: #FFFFFF;
            border: none;
            padding: 7px 12px;
            border-radius: 6px;
            font-size: 12px;
            font-weight: 600;
            width: 100%;
            cursor: pointer;
        }
        
        /* Floating Map Action Buttons */
        .controls {
            position: absolute;
            right: 16px;
            bottom: 24px;
            display: flex;
            flex-direction: column;
            gap: 10px;
            z-index: 50;
        }
        .ctrl-btn {
            width: 44px;
            height: 44px;
            border-radius: 50%;
            background: $cardBg;
            color: $cardText;
            border: 1px solid rgba(148, 163, 184, 0.3);
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 18px;
            font-weight: bold;
            cursor: pointer;
        }
        .ctrl-btn:active { transform: scale(0.92); }
        
        /* Compass Indicator */
        .compass {
            position: absolute;
            left: 16px;
            top: 16px;
            background: $cardBg;
            color: $cardText;
            padding: 6px 12px;
            border-radius: 20px;
            font-size: 11px;
            font-weight: 700;
            box-shadow: 0 2px 8px rgba(0,0,0,0.12);
            border: 1px solid rgba(148, 163, 184, 0.25);
            display: flex;
            align-items: center;
            gap: 6px;
            z-index: 50;
        }
    </style>
</head>
<body>
    <div id="map-container">
        <canvas id="mapCanvas"></canvas>
        <div class="compass" id="compassBadge">🧭 GPS ACTIVE</div>
        
        <div id="popup">
            <div class="popup-title" id="popName">Peer Device</div>
            <div class="popup-sub" id="popDetail">Distance: 45m · Wi-Fi P2P</div>
            <button class="popup-btn" id="popConnect">Connect</button>
        </div>
        
        <div class="controls">
            <div class="ctrl-btn" onclick="zoomIn()">+</div>
            <div class="ctrl-btn" onclick="zoomOut()">−</div>
            <div class="ctrl-btn" onclick="recenter()" style="color: #2563EB;">🎯</div>
        </div>
    </div>

    <script>
        var canvas = document.getElementById('mapCanvas');
        var ctx = canvas.getContext('2d');
        var popup = document.getElementById('popup');
        var popName = document.getElementById('popName');
        var popDetail = document.getElementById('popDetail');
        var popConnect = document.getElementById('popConnect');

        var userLat = $initialLat, userLng = $initialLng, userAcc = 15, userBearing = 0;
        var centerLat = $initialLat, centerLng = $initialLng;
        var zoomScale = 1.8; // Pixels per meter
        var isDarkTheme = $isDark;
        var isRadarMode = $showRadarOverlay;
        var peersList = [];
        var activePeer = null;

        var width = 0, height = 0;
        var isDragging = false, dragStart = { x: 0, y: 0 }, panOffset = { x: 0, y: 0 };
        var pulseRadius = 0;

        function drawRoundedRect(c, x, y, w, h, r) {
            if (w < 2 * r) r = w / 2;
            if (h < 2 * r) r = h / 2;
            c.beginPath();
            c.moveTo(x + r, y);
            c.arcTo(x + w, y, x + w, y + h, r);
            c.arcTo(x + w, y + h, x, y + h, r);
            c.arcTo(x, y + h, x, y, r);
            c.arcTo(x, y, x + w, y, r);
            c.closePath();
        }

        function resize() {
            width = canvas.width = window.innerWidth || document.documentElement.clientWidth || 400;
            height = canvas.height = window.innerHeight || document.documentElement.clientHeight || 400;
            render();
        }
        window.addEventListener('resize', resize);
        window.addEventListener('load', function() {
            resize();
            requestAnimationFrame(animate);
        });

        function initMap(lat, lng, dark, showRadar) {
            if (lat !== undefined && lat !== 0) { userLat = centerLat = lat; }
            if (lng !== undefined && lng !== 0) { userLng = centerLng = lng; }
            if (dark !== undefined) { isDarkTheme = dark; }
            if (showRadar !== undefined) { isRadarMode = showRadar; }
            resize();
        }

        function updateLocation(lat, lng, accuracy, bearing) {
            userLat = lat;
            userLng = lng;
            userAcc = accuracy || 15;
            userBearing = bearing || 0;
            var badge = document.getElementById('compassBadge');
            if (badge) {
                badge.innerText = '📍 ' + lat.toFixed(4) + '°, ' + lng.toFixed(4) + '°';
            }
            render();
        }

        function updatePeers(peers) {
            peersList = peers || [];
            render();
        }

        function setRadarOverlay(show) {
            isRadarMode = show;
            render();
        }

        function recenter() {
            panOffset = { x: 0, y: 0 };
            centerLat = userLat;
            centerLng = userLng;
            if (popup) popup.style.display = 'none';
            render();
        }

        function zoomIn() {
            zoomScale = Math.min(zoomScale * 1.35, 6.0);
            render();
        }

        function zoomOut() {
            zoomScale = Math.max(zoomScale / 1.35, 0.4);
            render();
        }

        // Coordinate conversion: Lat/Lng -> Screen (x, y)
        function latLngToScreen(lat, lng) {
            var metersY = (lat - centerLat) * 111000;
            var metersX = (lng - centerLng) * 111000 * Math.cos(centerLat * Math.PI / 180);
            var sx = width / 2 + panOffset.x + (metersX * zoomScale);
            var sy = height / 2 + panOffset.y - (metersY * zoomScale);
            return { x: sx, y: sy };
        }

        function drawMapGrid() {
            ctx.fillStyle = isDarkTheme ? '#0F172A' : '#F8FAFC';
            ctx.fillRect(0, 0, width, height);

            var gridStep = 50 * zoomScale; // 50m grid lines
            ctx.strokeStyle = isDarkTheme ? 'rgba(30, 41, 59, 0.9)' : 'rgba(226, 232, 240, 0.95)';
            ctx.lineWidth = 1;

            var startX = (width / 2 + panOffset.x) % gridStep;
            if (startX < 0) startX += gridStep;
            for (var x = startX; x < width; x += gridStep) {
                ctx.beginPath();
                ctx.moveTo(x, 0);
                ctx.lineTo(x, height);
                ctx.stroke();
            }

            var startY = (height / 2 + panOffset.y) % gridStep;
            if (startY < 0) startY += gridStep;
            for (var y = startY; y < height; y += gridStep) {
                ctx.beginPath();
                ctx.moveTo(0, y);
                ctx.lineTo(width, y);
                ctx.stroke();
            }

            // Stylized vector road lines
            ctx.strokeStyle = isDarkTheme ? 'rgba(51, 65, 85, 0.6)' : 'rgba(203, 213, 225, 0.8)';
            ctx.lineWidth = Math.max(4 * Math.min(zoomScale, 2.0), 3);
            ctx.beginPath();
            var cx = width / 2 + panOffset.x;
            var cy = height / 2 + panOffset.y;
            ctx.moveTo(0, cy + 80 * zoomScale);
            ctx.lineTo(width, cy - 40 * zoomScale);
            ctx.moveTo(cx - 120 * zoomScale, 0);
            ctx.lineTo(cx + 80 * zoomScale, height);
            ctx.stroke();
        }

        function drawRadarRings() {
            var userPos = latLngToScreen(userLat, userLng);
            var rings = [25, 50, 100, 150];

            rings.forEach(function(r) {
                var pxRadius = r * zoomScale;
                ctx.beginPath();
                ctx.arc(userPos.x, userPos.y, pxRadius, 0, Math.PI * 2);
                ctx.strokeStyle = isDarkTheme ? 'rgba(59, 130, 246, 0.3)' : 'rgba(37, 99, 235, 0.35)';
                ctx.lineWidth = 1.5;
                ctx.setLineDash([4, 4]);
                ctx.stroke();
                ctx.setLineDash([]);

                // Distance label
                ctx.fillStyle = isDarkTheme ? 'rgba(148, 163, 184, 0.8)' : 'rgba(100, 116, 139, 0.9)';
                ctx.font = '10px sans-serif';
                ctx.fillText(r + 'm', userPos.x + pxRadius + 4, userPos.y + 3);
            });
        }

        function drawUserLocation() {
            var pos = latLngToScreen(userLat, userLng);

            // Accuracy Halo Circle
            var accRadius = Math.max(userAcc * zoomScale, 14);
            ctx.beginPath();
            ctx.arc(pos.x, pos.y, accRadius, 0, Math.PI * 2);
            ctx.fillStyle = 'rgba(37, 99, 235, 0.12)';
            ctx.fill();
            ctx.strokeStyle = 'rgba(37, 99, 235, 0.35)';
            ctx.lineWidth = 1;
            ctx.stroke();

            // Pulsing Wave Ring
            ctx.beginPath();
            ctx.arc(pos.x, pos.y, 14 + pulseRadius, 0, Math.PI * 2);
            var alpha = Math.max(0, 1 - (pulseRadius / 25));
            ctx.strokeStyle = 'rgba(37, 99, 235, ' + (alpha * 0.7) + ')';
            ctx.lineWidth = 2;
            ctx.stroke();

            // Flashlight Heading Cone
            if (userBearing != 0) {
                var rad = (userBearing - 90) * Math.PI / 180;
                var coneLength = 36;
                ctx.beginPath();
                ctx.moveTo(pos.x, pos.y);
                ctx.arc(pos.x, pos.y, coneLength, rad - 0.35, rad + 0.35);
                ctx.closePath();
                var grad = ctx.createRadialGradient(pos.x, pos.y, 4, pos.x, pos.y, coneLength);
                grad.addColorStop(0, 'rgba(37, 99, 235, 0.5)');
                grad.addColorStop(1, 'rgba(37, 99, 235, 0.0)');
                ctx.fillStyle = grad;
                ctx.fill();
            }

            // Core Solid Blue Dot with White Outer Border (Google Maps style)
            ctx.beginPath();
            ctx.arc(pos.x, pos.y, 10, 0, Math.PI * 2);
            ctx.fillStyle = '#FFFFFF';
            ctx.fill();

            ctx.beginPath();
            ctx.arc(pos.x, pos.y, 7.5, 0, Math.PI * 2);
            ctx.fillStyle = '#2563EB';
            ctx.fill();
        }

        function drawPeers() {
            peersList.forEach(function(peer) {
                var pos = latLngToScreen(peer.lat, peer.lng);
                peer.screenX = pos.x;
                peer.screenY = pos.y;

                // Red Pin Marker
                ctx.beginPath();
                ctx.arc(pos.x, pos.y, 14, 0, Math.PI * 2);
                ctx.fillStyle = '#EF4444';
                ctx.fill();
                ctx.strokeStyle = '#FFFFFF';
                ctx.lineWidth = 2.5;
                ctx.stroke();

                // Pin Icon Dot
                ctx.beginPath();
                ctx.arc(pos.x, pos.y, 4, 0, Math.PI * 2);
                ctx.fillStyle = '#FFFFFF';
                ctx.fill();

                // Peer Name Badge Tag
                var label = peer.name + ' (' + peer.distance + 'm)';
                ctx.font = 'bold 11px sans-serif';
                var textWidth = ctx.measureText(label).width;
                var bx = pos.x - (textWidth / 2) - 8;
                var by = pos.y + 18;

                ctx.fillStyle = isDarkTheme ? '#1E293B' : '#FFFFFF';
                drawRoundedRect(ctx, bx, by, textWidth + 16, 20, 8);
                ctx.fill();
                ctx.strokeStyle = isDarkTheme ? '#334155' : '#E2E8F0';
                ctx.lineWidth = 1;
                ctx.stroke();

                ctx.fillStyle = isDarkTheme ? '#F8FAFC' : '#0F172A';
                ctx.fillText(label, bx + 8, by + 14);
            });
        }

        function render() {
            if (!width || !height) return;
            drawMapGrid();
            if (isRadarMode) drawRadarRings();
            drawUserLocation();
            drawPeers();
        }

        function animate() {
            pulseRadius = (pulseRadius + 0.4) % 25;
            render();
            requestAnimationFrame(animate);
        }

        // Touch & Drag Handling
        var startTouchDist = 0;

        canvas.addEventListener('touchstart', function(e) {
            if (e.touches.length === 1) {
                isDragging = true;
                dragStart.x = e.touches[0].clientX - panOffset.x;
                dragStart.y = e.touches[0].clientY - panOffset.y;
            } else if (e.touches.length === 2) {
                isDragging = false;
                startTouchDist = Math.hypot(
                    e.touches[0].clientX - e.touches[1].clientX,
                    e.touches[0].clientY - e.touches[1].clientY
                );
            }
        });

        canvas.addEventListener('touchmove', function(e) {
            e.preventDefault();
            if (isDragging && e.touches.length === 1) {
                panOffset.x = e.touches[0].clientX - dragStart.x;
                panOffset.y = e.touches[0].clientY - dragStart.y;
                if (popup) popup.style.display = 'none';
                render();
            } else if (e.touches.length === 2) {
                var dist = Math.hypot(
                    e.touches[0].clientX - e.touches[1].clientX,
                    e.touches[0].clientY - e.touches[1].clientY
                );
                if (startTouchDist > 0) {
                    var factor = dist / startTouchDist;
                    zoomScale = Math.min(Math.max(zoomScale * factor, 0.4), 6.0);
                    startTouchDist = dist;
                    render();
                }
            }
        });

        canvas.addEventListener('touchend', function(e) {
            isDragging = false;
            startTouchDist = 0;
        });

        // Click on Peer
        canvas.addEventListener('click', function(e) {
            var rect = canvas.getBoundingClientRect();
            var clickX = e.clientX - rect.left;
            var clickY = e.clientY - rect.top;

            var clicked = null;
            peersList.forEach(function(peer) {
                var d = Math.hypot(clickX - peer.screenX, clickY - peer.screenY);
                if (d < 30) clicked = peer;
            });

            if (clicked) {
                activePeer = clicked;
                popName.innerText = '📍 ' + clicked.name;
                popDetail.innerText = 'Distance: ' + clicked.distance + 'm · ' + clicked.radios;
                popConnect.onclick = function() {
                    if (window.AndroidBridge && window.AndroidBridge.onPeerSelected) {
                        window.AndroidBridge.onPeerSelected(clicked.id);
                    }
                };
                popup.style.left = clicked.screenX + 'px';
                popup.style.top = (clicked.screenY - 14) + 'px';
                popup.style.display = 'block';
            } else {
                if (popup) popup.style.display = 'none';
            }
        });

        resize();
        requestAnimationFrame(animate);
    </script>
</body>
</html>
    """.trimIndent()
}

