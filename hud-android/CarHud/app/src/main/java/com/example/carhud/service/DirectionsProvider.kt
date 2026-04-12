package com.example.carhud.service

import com.example.carhud.BuildConfig
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class DirectionsProvider {

    private val apiKey = BuildConfig.MAPS_API_KEY
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    data class NavStep(
        val instruction: String,
        val distance: String,
        val endLat: Double,
        val endLng: Double,
        val maneuver: String = "",
        val durationSeconds: Int = 0
    )

    data class RouteInfo(
        val steps: List<NavStep>,
        val polyline: List<LatLng>,
        val durationText: String,
        val durationSeconds: Int,
        val distanceText: String
    )

    suspend fun getDirections(
        origin: LatLng,
        destination: LatLng
    ): RouteInfo? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext null
        try {
            val url = "https://maps.googleapis.com/maps/api/directions/json" +
                "?origin=${origin.latitude},${origin.longitude}" +
                "&destination=${destination.latitude},${destination.longitude}" +
                "&units=imperial" +
                "&key=$apiKey"

            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)

            if (json.getString("status") != "OK") return@withContext null

            val route = json.getJSONArray("routes").getJSONObject(0)
            val legs = route.getJSONArray("legs").getJSONObject(0)
            val durationText = legs.getJSONObject("duration").getString("text")
            val durationSeconds = legs.getJSONObject("duration").getInt("value")
            val distanceText = legs.getJSONObject("distance").getString("text")
            val steps = legs.getJSONArray("steps")

            val navSteps = mutableListOf<NavStep>()
            for (i in 0 until steps.length()) {
                val step = steps.getJSONObject(i)
                val instruction = step.getString("html_instructions")
                    .replace(Regex("<[^>]*>"), "")
                val distance = step.getJSONObject("distance").getString("text")
                val stepDurationSeconds = step.getJSONObject("duration").getInt("value")
                val endLoc = step.getJSONObject("end_location")
                val maneuver = step.optString("maneuver", "")
                navSteps.add(
                    NavStep(
                        instruction = instruction,
                        distance = distance,
                        endLat = endLoc.getDouble("lat"),
                        endLng = endLoc.getDouble("lng"),
                        maneuver = maneuver,
                        durationSeconds = stepDurationSeconds
                    )
                )
            }

            val polyline = route.getJSONObject("overview_polyline").getString("points")
            val polylinePoints = decodePolyline(polyline)

            RouteInfo(
                steps = navSteps,
                polyline = polylinePoints,
                durationText = durationText,
                durationSeconds = durationSeconds,
                distanceText = distanceText
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = mutableListOf<LatLng>()
        var index = 0
        var lat = 0
        var lng = 0
        while (index < encoded.length) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            poly.add(LatLng(lat / 1E5, lng / 1E5))
        }
        return poly
    }
}
