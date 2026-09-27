package `in`.gov.itantra.core.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure offline geographic location coordinate representation with accuracy, speed, bearing, and distance math.
 */
data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float = 5f,
    val altitudeMeters: Double = 216.0,
    val speedMps: Float = 0f,
    val bearingDegrees: Float = 0f,
    val provider: String = "GPS",
    val timestampMs: Long = System.currentTimeMillis(),
) {
    /** Returns distance in meters between this location and [target] using Haversine formula. */
    fun distanceTo(target: GpsLocation): Float {
        val earthRadius = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(target.latitude - latitude)
        val dLng = Math.toRadians(target.longitude - longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(latitude)) * cos(Math.toRadians(target.latitude)) *
            sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (earthRadius * c).toFloat()
    }

    /** Returns initial bearing angle in degrees (0..360) from this location to [target]. */
    fun bearingTo(target: GpsLocation): Float {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(target.latitude)
        val dLng = Math.toRadians(target.longitude - longitude)
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        val brng = Math.toDegrees(atan2(y, x))
        return ((brng + 360) % 360).toFloat()
    }
}
