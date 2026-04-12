package com.example.carhud.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Payload for gps_data message. Sent from Android to Pi.
 * OBD data is read on the Pi; Android only provides GPS + nav.
 */
@Serializable
data class GpsDataPayload(
    val gpsSpeed: String = "",
    val turn: String = "",
    val distance: String = "",
    val maneuver: String = "",
    val eta: String = "",
    val speedLimit: String = "",
    val timestamp: Long? = null
) {
    fun toMessage(): HudMessage = HudMessage(
        type = "gps_data",
        payload = buildJsonObject {
            put("gpsSpeed", gpsSpeed)
            put("turn", turn)
            put("distance", distance)
            put("maneuver", maneuver)
            put("eta", eta)
            put("speedLimit", speedLimit)
            timestamp?.let { put("timestamp", it) }
        },
        timestamp = timestamp
    )
}
