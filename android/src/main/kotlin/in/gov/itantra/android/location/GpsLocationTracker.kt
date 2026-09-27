package `in`.gov.itantra.android.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import `in`.gov.itantra.core.location.GpsLocation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GpsLocationTracker(
    private val context: Context,
) : LocationListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    // Default starting GPS coordinates (New Delhi reference for offline map rendering)
    private val _location = MutableStateFlow(
        GpsLocation(
            latitude = 28.613939,
            longitude = 77.209021,
            accuracyMeters = 4.5f,
            altitudeMeters = 216.0,
            provider = "GPS",
        )
    )
    val location: StateFlow<GpsLocation> = _location.asStateFlow()

    private var trackingActive = false

    @SuppressLint("MissingPermission")
    fun startTracking() {
        if (trackingActive || locationManager == null) return
        trackingActive = true

        try {
            val gpsLast = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLast = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val passiveLast = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

            val bestLast = listOfNotNull(gpsLast, netLast, passiveLast).maxByOrNull { it.time }
            bestLast?.let { updateLocation(it) }

            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2000L,
                    1f,
                    this,
                )
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    3000L,
                    2f,
                    this,
                )
            }
        } catch (e: SecurityException) {
            // Permission restricted
        } catch (e: Exception) {
            // Location exception
        }
    }

    fun stopTracking() {
        if (!trackingActive || locationManager == null) return
        trackingActive = false
        try {
            locationManager.removeUpdates(this)
        } catch (e: Exception) {
            // Ignore removal errors
        }
    }

    private fun updateLocation(loc: Location) {
        _location.value = GpsLocation(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracyMeters = if (loc.hasAccuracy()) loc.accuracy else 8f,
            altitudeMeters = if (loc.hasAltitude()) loc.altitude else 216.0,
            speedMps = if (loc.hasSpeed()) loc.speed else 0f,
            bearingDegrees = if (loc.hasBearing()) loc.bearing else 0f,
            provider = loc.provider ?: "GPS",
            timestampMs = loc.time,
        )
    }

    override fun onLocationChanged(location: Location) {
        updateLocation(location)
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
}
