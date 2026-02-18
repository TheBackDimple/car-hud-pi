package com.example.carhud.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Payload for map_frame message. Sent from Android to Pi.
 */
@Serializable
data class MapFramePayload(
    val image: String,
    val width: Int,
    val height: Int,
    val timestamp: Long
) {
    fun toMessage(): HudMessage = HudMessage(
        type = "map_frame",
        payload = buildJsonObject {
            put("image", image)
            put("width", width)
            put("height", height)
            put("timestamp", timestamp)
        },
        timestamp = timestamp
    )
}
