package com.example.carhud.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
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
 * Turn/distance come from [NavigationStateHolder] when turn-by-turn navigation is active.
 */
class LocationDataProvider(
    private val context: Context,
    onGpsData: (GpsDataPayload) -> Unit
) {

    private val gpsDataCallback: (GpsDataPayload) -> Unit = onGpsData

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val speedLimitProvider = SpeedLimitProvider()

    private val hasLocationPermission: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private var locationCallback: LocationCallback? = null
    private var sendJob: Job? = null
    private var lastPayload: GpsDataPayload? = null
    private var providerScope: CoroutineScope? = null

    /** Latest GPS + nav merge; used by [BleObdProvider] to combine with OBD in hud_data. */
    @Volatile
    var lastMergedGps: GpsDataPayload? = null
        private set

    fun startUpdates(scope: CoroutineScope) {
        if (locationCallback != null) return
        if (!hasLocationPermission) return

        providerScope = scope

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
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
                        maneuver = "",
                        eta = "",
                        speedLimit = "",
                        timestamp = System.currentTimeMillis()
                    )
                    providerScope?.launch(Dispatchers.Default) {
                        speedLimitProvider.onLocationUpdate(loc.latitude, loc.longitude)
                    }
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        } catch (e: SecurityException) {
            locationCallback = null
            return
        }

        // Send at 2 Hz (every 500ms) — merge with OBD on Pi; turn/distance from active navigation
        sendJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(500)
                lastPayload?.let { p ->
                    val nav = NavigationStateHolder.currentStep.value
                    val merged = GpsDataPayload(
                        gpsSpeed = p.gpsSpeed,
                        turn = nav?.instruction ?: "",
                        distance = nav?.distance ?: "",
                        maneuver = nav?.maneuver ?: "",
                        eta = NavigationStateHolder.etaText.value,
                        speedLimit = speedLimitProvider.speedLimit.value,
                        timestamp = System.currentTimeMillis()
                    )
                    lastMergedGps = merged
                    gpsDataCallback(merged)
                }
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
        lastMergedGps = null
        providerScope = null
    }
}
