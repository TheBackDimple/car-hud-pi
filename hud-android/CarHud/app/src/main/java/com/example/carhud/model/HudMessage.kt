package com.example.carhud.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * WebSocket message envelope. Matches backend schema:
 * { "type": "<message_type>", "payload": { ... }, "timestamp": <ms> }
 */
@Serializable
data class HudMessage(
    val type: String,
    val payload: JsonObject = buildJsonObject { },
    val timestamp: Long? = null
) {
    fun toJson(): String = Json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(jsonString: String): HudMessage? = runCatching {
            json.decodeFromString<HudMessage>(jsonString)
        }.getOrNull()
    }
}
