package com.example.carhud.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Payload for hud_data message. Matches backend HudDataPayload.
 */
@Serializable
data class HudDataPayload(
    val speed: String = "",
    val gpsSpeed: String = "",
    val rpm: String = "",
    val coolantTemp: String = "",
    val mpg: String = "",
    val range: String = "",
    val fuelLevel: String = "",
    val turn: String = "",
    val distance: String = "",
    val timestamp: Long? = null
) {
    fun toMessage(): HudMessage = HudMessage(
        type = "hud_data",
        payload = buildJsonObject {
            put("speed", speed)
            put("gpsSpeed", gpsSpeed)
            put("rpm", rpm)
            put("coolantTemp", coolantTemp)
            put("mpg", mpg)
            put("range", range)
            put("fuelLevel", fuelLevel)
            put("turn", turn)
            put("distance", distance)
            timestamp?.let { put("timestamp", it) }
        },
        timestamp = timestamp
    )
}
