package com.example.carhud.service

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class SpeedLimitProvider {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val _speedLimit = MutableStateFlow("")
    val speedLimit: StateFlow<String> = _speedLimit.asStateFlow()

    private var lastQueryMs: Long = 0L
    private var lastQueryLat: Double? = null
    private var lastQueryLng: Double? = null

    suspend fun onLocationUpdate(lat: Double, lng: Double) {
        val now = System.currentTimeMillis()
        val needByTime = now - lastQueryMs >= QUERY_INTERVAL_MS
        val needByDist = lastQueryLat == null || lastQueryLng == null ||
            distanceMeters(lat, lng, lastQueryLat!!, lastQueryLng!!) > MOVE_THRESHOLD_METERS
        if (!needByTime && !needByDist) return

        lastQueryMs = now
        lastQueryLat = lat
        lastQueryLng = lng

        val mph = getSpeedLimit(lat, lng)
        _speedLimit.value = mph?.toString() ?: ""
    }

    private suspend fun getSpeedLimit(lat: Double, lng: Double): Int? = withContext(Dispatchers.IO) {
        try {
            val query = """
                [out:json];
                way(around:30,$lat,$lng)["maxspeed"];
                out tags;
            """.trimIndent()
            val url = "https://overpass-api.de/api/interpreter?data=" +
                URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val elements = json.getJSONArray("elements")
            if (elements.length() > 0) {
                val tags = elements.getJSONObject(0).getJSONObject("tags")
                val maxspeed = tags.optString("maxspeed", "")
                parseMaxspeedToMph(maxspeed)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val r = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, r)
        return r[0]
    }

    /**
     * OSM maxspeed commonly appears as "50", "50 km/h", "30 mph", or multi-value entries
     * like "50;60". We take the first parseable segment to keep behavior deterministic.
     */
    private fun parseMaxspeedToMph(raw: String): Int? {
        if (raw.isBlank()) return null
        val segments = raw.split(';').map { it.trim() }.filter { it.isNotEmpty() }
        for (segment in segments) {
            val value = NUMBER_REGEX.find(segment)?.value?.toDoubleOrNull() ?: continue
            val normalized = segment.lowercase()
            val isMph = normalized.contains("mph") || normalized.contains("mi/h")
            val isKmh = normalized.contains("km/h") || normalized.contains("kmh") || normalized.contains("kph")
            val mph = when {
                isMph -> value
                isKmh || normalized.matches(Regex("^\\d+(\\.\\d+)?$")) -> value * KMH_TO_MPH
                else -> continue
            }
            return mph.roundToInt()
        }
        return null
    }

    companion object {
        private const val QUERY_INTERVAL_MS = 30_000L
        private const val MOVE_THRESHOLD_METERS = 200f
        private const val KMH_TO_MPH = 0.621371
        private val NUMBER_REGEX = Regex("\\d+(?:\\.\\d+)?")
    }
}
