package com.example.carhud.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val PI_HOST_KEY = stringPreferencesKey("pi_host")
private val VOICE_NAV_ENABLED_KEY = booleanPreferencesKey("voice_navigation_enabled")
private val HUD_MIRROR_KEY = booleanPreferencesKey("hud_mirror_pi_display")

private const val DEFAULT_PI_HOST = "auto"

/**
 * Stored Pi host: "auto" to discover on local network, or IP/hostname.
 * Android 11+ randomizes USB tether subnet; "auto" probes the subnet for the Pi.
 */
object PiHostSettings {

    fun getHost(context: Context): Flow<String> =
        context.settingsDataStore.data.map { prefs ->
            prefs[PI_HOST_KEY] ?: DEFAULT_PI_HOST
        }

    suspend fun setHost(context: Context, host: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[PI_HOST_KEY] = host.trim().replace("carhud_local", "carhud.local")
        }
    }
}

/**
 * Spoken turn-by-turn prompts (TextToSpeech). Persisted; default off.
 */
object VoiceNavigationSettings {

    fun isEnabled(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[VOICE_NAV_ENABLED_KEY] ?: false
        }

    suspend fun setEnabled(context: Context, enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[VOICE_NAV_ENABLED_KEY] = enabled
        }
    }
}

/**
 * Horizontal mirror for the Pi Chromium HUD (windshield reflection vs normal monitor).
 * Default on matches Pi [start.sh] HUD_MIRROR=1. Sent over WebSocket when connected.
 */
object HudMirrorSettings {

    fun isMirrored(context: Context): Flow<Boolean> =
        context.settingsDataStore.data.map { prefs ->
            prefs[HUD_MIRROR_KEY] ?: true
        }

    suspend fun setMirrored(context: Context, mirrored: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[HUD_MIRROR_KEY] = mirrored
        }
    }
}
