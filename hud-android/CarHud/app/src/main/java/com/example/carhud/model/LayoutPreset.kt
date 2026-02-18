package com.example.carhud.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * A single HUD widget in the layout.
 * Positions are 0.0–1.0 (percentage of 1280×720 canvas).
 */
@Serializable
data class HudComponent(
    val type: String,
    val enabled: Boolean = true,
    val x: Float = 0f,
    val y: Float = 0f,
    val width: Float = 0f,
    val height: Float = 0f
) {
    companion object {
        val TYPES = listOf("speed", "map", "nav", "obd", "fuel", "gpsSpeed")

        /** Human-readable labels for feature toggle UI. */
        fun displayName(type: String): String = when (type) {
            "speed" -> "Speed Display"
            "map" -> "Google Maps"
            "nav" -> "Navigation"
            "obd" -> "OBD-II Data"
            "fuel" -> "Fuel / Range"
            "gpsSpeed" -> "GPS Speed"
            else -> type
        }
    }
}

/**
 * Layout preset (up to 3 slots).
 */
@Serializable
data class LayoutPreset(
    val presetId: Int,
    val name: String,
    val components: List<HudComponent>
) {
    fun toLayoutConfigMessage(): HudMessage {
        val payload = buildJsonObject {
            put("presetId", presetId)
            put("name", name)
            putJsonArray("components") {
                components.forEach { c ->
                    add(
                        buildJsonObject {
                            put("type", c.type)
                            put("enabled", c.enabled)
                            put("x", c.x)
                            put("y", c.y)
                            put("width", c.width)
                            put("height", c.height)
                        }
                    )
                }
            }
        }
        return HudMessage(type = "layout_config", payload = payload, timestamp = System.currentTimeMillis())
    }
}
