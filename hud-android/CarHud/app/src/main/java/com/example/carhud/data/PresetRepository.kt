package com.example.carhud.data

import android.content.Context
import com.example.carhud.model.HudComponent
import com.example.carhud.model.LayoutPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "presets")

private val PRESET_1 = stringPreferencesKey("preset_1")
private val PRESET_2 = stringPreferencesKey("preset_2")
private val PRESET_3 = stringPreferencesKey("preset_3")

private val json = Json { ignoreUnknownKeys = true }

/** Default preset: 4×3 grid. Left: speed, obd. Center: map (2×3 full middle). Right: nav, gpsSpeed, fuel. */
fun defaultPreset(presetId: Int, name: String): LayoutPreset = LayoutPreset(
    presetId = presetId,
    name = name,
    components = listOf(
        HudComponent("speed", true, 0f, 0f, 0.25f, 1f / 3f),
        HudComponent("map", true, 0.25f, 0f, 0.5f, 1f),
        HudComponent("nav", true, 0.75f, 0f, 0.25f, 1f / 3f),
        HudComponent("obd", true, 0f, 1f / 3f, 0.25f, 1f / 3f),
        HudComponent("fuel", true, 0.75f, 2f / 3f, 0.25f, 1f / 3f),
        HudComponent("gpsSpeed", true, 0.75f, 1f / 3f, 0.25f, 1f / 3f),
    )
)

class PresetRepository(private val context: Context) {

    fun getPreset(id: Int): Flow<LayoutPreset?> = context.dataStore.data.map { prefs ->
        val key = when (id) {
            1 -> PRESET_1
            2 -> PRESET_2
            3 -> PRESET_3
            else -> return@map null
        }
        prefs[key]?.let { json.decodeFromString(LayoutPreset.serializer(), it) }
    }

    fun getAllPresets(): Flow<List<LayoutPreset?>> = context.dataStore.data.map { prefs ->
        listOf(
            prefs[PRESET_1]?.let { json.decodeFromString(LayoutPreset.serializer(), it) },
            prefs[PRESET_2]?.let { json.decodeFromString(LayoutPreset.serializer(), it) },
            prefs[PRESET_3]?.let { json.decodeFromString(LayoutPreset.serializer(), it) },
        )
    }

    suspend fun savePreset(preset: LayoutPreset) {
        context.dataStore.edit { prefs ->
            val key = when (preset.presetId) {
                1 -> PRESET_1
                2 -> PRESET_2
                3 -> PRESET_3
                else -> return@edit
            }
            prefs[key] = json.encodeToString(LayoutPreset.serializer(), preset)
        }
    }

    fun getPresetOrDefault(id: Int): Flow<LayoutPreset> = getPreset(id).map {
        it ?: defaultPreset(id, "Preset $id")
    }
}
