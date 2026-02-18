package com.example.carhud.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.example.carhud.model.GpsDataPayload
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Provides GPS location updates. Used by HudConnectionService to send gps_data.
 * Turn/distance come from navigation (Phase 6); for now they are placeholders.
 */
class LocationDataProvider(
    private val context: Context,
    onGpsData: (GpsDataPayload) -> Unit
) {

    private val gpsDataCallback: (GpsDataPayload) -> Unit = onGpsData

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val hasLocationPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private var locationCallback: LocationCallback? = null
    private var sendJob: Job? = null
    private var lastPayload: GpsDataPayload? = null

    fun startUpdates(scope: CoroutineScope) {
        if (locationCallback != null) return
        if (!hasLocationPermission) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdates(0)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val speedMps = loc.speed
                    val speedMph = if (speedMps >= 0) (speedMps * 2.237).toInt().toString() else ""
                    lastPayload = GpsDataPayload(
                        gpsSpeed = speedMph,
                        turn = "",
                        distance = "",
                        timestamp = System.currentTimeMillis()
                    )
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback!!, null)
        } catch (e: SecurityException) {
            locationCallback = null
            return
        }

        // Send at 2 Hz (every 500ms) — merge with OBD on Pi
        sendJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(500)
                lastPayload?.let { gpsDataCallback(it) }
            }
        }
    }

    fun stopUpdates() {
        sendJob?.cancel()
        sendJob = null
        locationCallback?.let {
            fusedClient.removeLocationUpdates(it)
            locationCallback = null
        }
        lastPayload = null
    }
}
