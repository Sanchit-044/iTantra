package `in`.gov.itantra.core.discover

/**
 * Rough closeness from RSSI. Not metres and not a map.
 *
 * Thresholds are typical outdoor BLE: a phone in the same room is often stronger
 * than -55 dBm; a wall or a pocket drops into the mid band; the outer ring is
 * "still visible". Wi-Fi Direct peers often have no RSSI — those map to [MID].
 */
enum class RssiBand {
    NEAR,
    MID,
    FAR,
    ;

    val label: String
        get() = when (this) {
            NEAR -> "near"
            MID -> "mid"
            FAR -> "far"
        }

    companion object {
        const val NEAR_MIN_DBM = -55
        const val MID_MIN_DBM = -75

        fun fromRssi(rssiDbm: Int?): RssiBand {
            if (rssiDbm == null) return MID
            return when {
                rssiDbm >= NEAR_MIN_DBM -> NEAR
                rssiDbm >= MID_MIN_DBM -> MID
                else -> FAR
            }
        }
    }
}
