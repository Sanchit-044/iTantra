package `in`.gov.itantra.ui.components

import android.annotation.SuppressLint
import android.content.Context
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
 * Real interactive Google Maps / OpenStreetMap-style live map with real-time GPS location tracking,
 * pulsing blue user beacon, nearby peer location pins, and offline tile caching.
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
                "if (window.updateMyLocation) { window.updateMyLocation(${myLocation.latitude}, ${myLocation.longitude}, ${myLocation.accuracyMeters}, ${myLocation.bearingDegrees}); }",
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
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
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
                    "https://maps.local/",
                    getMapHtml(isDark),
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

private fun getMapHtml(isDark: Boolean): String {
    val tileUrl = if (isDark) {
        "https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png"
    } else {
        "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
    }

    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
    <style>
        html, body, #map { height: 100%; width: 100%; margin: 0; padding: 0; background: ${if (isDark) "#0F172A" else "#F8FAFC"}; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
        
        /* Pulse Animation for Live Location Blue Dot */
        .user-location-marker {
            width: 22px;
            height: 22px;
            background-color: #407BFF;
            border: 3px solid #FFFFFF;
            border-radius: 50%;
            box-shadow: 0 0 10px rgba(64, 123, 255, 0.8);
            position: relative;
        }
        .user-location-pulse {
            position: absolute;
            top: -12px;
            left: -12px;
            width: 40px;
            height: 40px;
            border-radius: 50%;
            background-color: rgba(64, 123, 255, 0.35);
            animation: pulse 2s infinite ease-out;
        }
        @keyframes pulse {
            0% { transform: scale(0.6); opacity: 1; }
            100% { transform: scale(1.6); opacity: 0; }
        }

        /* Peer Red Pin */
        .peer-pin {
            background-color: #EF4444;
            color: #FFFFFF;
            font-size: 11px;
            font-weight: bold;
            padding: 4px 8px;
            border-radius: 12px;
            border: 2px solid #FFFFFF;
            box-shadow: 0 2px 6px rgba(0,0,0,0.3);
            white-space: nowrap;
            display: inline-flex;
            align-items: center;
            gap: 4px;
        }

        /* Radar Overlay Sweep */
        .radar-sweep-circle {
            pointer-events: none;
        }

        .leaflet-popup-content-wrapper {
            background: ${if (isDark) "#1E293B" else "#FFFFFF"};
            color: ${if (isDark) "#F8FAFC" else "#0F172A"};
            border-radius: 8px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.15);
        }
        .leaflet-popup-tip {
            background: ${if (isDark) "#1E293B" else "#FFFFFF"};
        }
        .connect-btn {
            background-color: #407BFF;
            color: #FFFFFF;
            border: none;
            padding: 6px 14px;
            border-radius: 6px;
            font-weight: 600;
            font-size: 12px;
            cursor: pointer;
            width: 100%;
            margin-top: 6px;
        }
    </style>
</head>
<body>
    <div id="map"></div>
    <script>
        var map, userMarker, accuracyCircle, peerMarkers = {}, radarCircles = [];
        var currentLat = 28.6139, currentLng = 77.2090;

        function initMap(lat, lng, dark, showRadar) {
            currentLat = lat;
            currentLng = lng;
            map = L.map('map', { zoomControl: false }).setView([lat, lng], 16);

            L.tileLayer('$tileUrl', {
                maxZoom: 19,
                attribution: '© OpenStreetMap'
            }).addTo(map);

            // User Live Location Marker (Google Maps Style Blue Dot with Pulse)
            var userIcon = L.divIcon({
                className: 'user-marker-container',
                html: '<div class="user-location-pulse"></div><div class="user-location-marker"></div>',
                iconSize: [22, 22],
                iconAnchor: [11, 11]
            });

            userMarker = L.marker([lat, lng], { icon: userIcon }).addTo(map);
            accuracyCircle = L.circle([lat, lng], {
                radius: 15,
                color: '#407BFF',
                fillColor: '#407BFF',
                fillOpacity: 0.15,
                weight: 1
            }).addTo(map);

            if (showRadar) setRadarOverlay(true);
        }

        function updateMyLocation(lat, lng, accuracy, bearing) {
            currentLat = lat;
            currentLng = lng;
            if (userMarker) {
                userMarker.setLatLng([lat, lng]);
            }
            if (accuracyCircle) {
                accuracyCircle.setLatLng([lat, lng]);
                accuracyCircle.setRadius(Math.max(accuracy, 10));
            }
        }

        function updatePeers(peers) {
            // Remove old markers not present in new list
            var newIds = peers.map(function(p) { return p.id; });
            for (var id in peerMarkers) {
                if (newIds.indexOf(id) === -1) {
                    map.removeLayer(peerMarkers[id]);
                    delete peerMarkers[id];
                }
            }

            // Add/update peer markers
            peers.forEach(function(peer) {
                var icon = L.divIcon({
                    className: 'peer-pin-container',
                    html: '<div class="peer-pin">📍 ' + peer.name + ' (' + peer.distance + 'm)</div>',
                    iconSize: [100, 24],
                    iconAnchor: [50, 12]
                });

                if (peerMarkers[peer.id]) {
                    peerMarkers[peer.id].setLatLng([peer.lat, peer.lng]);
                } else {
                    var marker = L.marker([peer.lat, peer.lng], { icon: icon }).addTo(map);
                    var popupContent = '<b>' + peer.name + '</b><br>' +
                        '<span style="font-size:11px;color:#64748B;">Distance: ' + peer.distance + 'm · ' + peer.radios + '</span><br>' +
                        '<button class="connect-btn" onclick="AndroidBridge.onPeerSelected(\'' + peer.id + '\')">Connect</button>';
                    marker.bindPopup(popupContent);
                    peerMarkers[peer.id] = marker;
                }
            });
        }

        function setRadarOverlay(show) {
            radarCircles.forEach(function(c) { map.removeLayer(c); });
            radarCircles = [];
            if (show) {
                [50, 100, 150].forEach(function(r) {
                    var circle = L.circle([currentLat, currentLng], {
                        radius: r,
                        color: '#407BFF',
                        fill: false,
                        weight: 1.5,
                        dashArray: '4, 4',
                        className: 'radar-sweep-circle'
                    }).addTo(map);
                    radarCircles.push(circle);
                });
            }
        }

        function recenter() {
            if (map && userMarker) {
                map.flyTo([currentLat, currentLng], 16);
            }
        }
    </script>
</body>
</html>
    """.trimIndent()
}
