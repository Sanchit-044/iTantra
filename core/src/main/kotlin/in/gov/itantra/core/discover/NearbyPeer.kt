package `in`.gov.itantra.core.discover

import java.util.Locale

data class NearbyPeer(
    val id: String,
    val name: String,
    val wifiAddress: String? = null,
    val bluetoothAddress: String? = null,
    val rssiDbm: Int? = null,
    val smoothedRssiDbm: Double? = null,
    val distanceMeters: Float = 3.5f,
    val trend: SignalTrend = SignalTrend.STEADY,
    val lastSeenMs: Long,
) {
    val hasWifi: Boolean get() = !wifiAddress.isNullOrBlank()
    val hasBluetooth: Boolean get() = !bluetoothAddress.isNullOrBlank()
    val effectiveRssi: Int? get() = smoothedRssiDbm?.toInt() ?: rssiDbm
    val band: RssiBand get() = RssiBand.fromRssi(effectiveRssi)
    val radiosLabel: String = when {
        hasWifi && hasBluetooth -> "Wi-Fi + BT"
        hasWifi -> "Wi-Fi"
        hasBluetooth -> "Bluetooth"
        else -> "Unknown"
    }

    fun formattedDistance(): String {
        return String.format(Locale.US, "%.1fm", distanceMeters)
    }

    /** Stable 0–360 placement. Same id stays put; it is not a real heading. */
    fun stableAngleDegrees(): Float {
        var h = id.hashCode()
        if (h == Int.MIN_VALUE) h = 0
        return (kotlin.math.abs(h) % 360).toFloat()
    }
}

enum class NearbyRadio { WIFI, BLUETOOTH }

object NearbyPeerBook {
    const val STALE_MS = 10_000L
    const val FADE_MS = 6_000L

    fun sighting(
        name: String,
        radio: NearbyRadio,
        address: String?,
        rssiDbm: Int?,
        nowMs: Long,
        smoothedRssiDbm: Double? = null,
        distanceMeters: Float = 3.5f,
        trend: SignalTrend = SignalTrend.STEADY,
    ): NearbyPeer {
        val cleanedName = name.trim().ifBlank { "Unknown" }
        val mac = address?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val id = mac?.let { "mac:$it" } ?: "name:${cleanedName.lowercase()}"
        return NearbyPeer(
            id = id,
            name = cleanedName,
            wifiAddress = if (radio == NearbyRadio.WIFI) mac else null,
            bluetoothAddress = if (radio == NearbyRadio.BLUETOOTH) mac else null,
            rssiDbm = rssiDbm,
            smoothedRssiDbm = smoothedRssiDbm ?: rssiDbm?.toDouble(),
            distanceMeters = distanceMeters,
            trend = trend,
            lastSeenMs = nowMs,
        )
    }

    fun upsert(existing: List<NearbyPeer>, incoming: NearbyPeer): List<NearbyPeer> {
        val match = existing.firstOrNull { sharesIdentity(it, incoming) }
            ?: return existing + incoming
        return existing.map { if (it.id == match.id) merge(it, incoming) else it }
    }

    fun prune(peers: List<NearbyPeer>, nowMs: Long, staleMs: Long = STALE_MS): List<NearbyPeer> =
        peers.filter { nowMs - it.lastSeenMs < staleMs }

    fun fading(peer: NearbyPeer, nowMs: Long, fadeAfterMs: Long = FADE_MS): Boolean =
        nowMs - peer.lastSeenMs >= fadeAfterMs

    internal fun sharesIdentity(a: NearbyPeer, b: NearbyPeer): Boolean {
        val macsA = macsOf(a)
        val macsB = macsOf(b)
        if (macsA.isNotEmpty() && macsB.isNotEmpty() && macsA.any { it in macsB }) return true
        val na = a.name.trim().lowercase()
        val nb = b.name.trim().lowercase()
        if (na.isBlank() || nb.isBlank()) return false
        if (isGenericName(na) || isGenericName(nb)) return false
        return na == nb
    }

    private fun merge(old: NearbyPeer, incoming: NearbyPeer): NearbyPeer {
        val rssi = incoming.rssiDbm ?: old.rssiDbm
        val smoothed = incoming.smoothedRssiDbm ?: old.smoothedRssiDbm
        val dist = if (incoming.smoothedRssiDbm != null || incoming.rssiDbm != null) incoming.distanceMeters else old.distanceMeters
        return NearbyPeer(
            id = old.id,
            name = incoming.name.takeIf { it.isNotBlank() && !isGenericName(it.lowercase()) } ?: old.name,
            wifiAddress = incoming.wifiAddress ?: old.wifiAddress,
            bluetoothAddress = incoming.bluetoothAddress ?: old.bluetoothAddress,
            rssiDbm = rssi,
            smoothedRssiDbm = smoothed,
            distanceMeters = dist,
            trend = incoming.trend,
            lastSeenMs = incoming.lastSeenMs,
        )
    }

    private fun macsOf(peer: NearbyPeer): Set<String> = buildSet {
        peer.wifiAddress?.lowercase()?.let { add(it) }
        peer.bluetoothAddress?.lowercase()?.let { add(it) }
    }

    private fun isGenericName(lower: String): Boolean =
        lower == "unknown" || lower == "android" || lower == "wlan0"
}
