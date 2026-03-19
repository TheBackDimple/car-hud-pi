package com.example.carhud.service

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")
private val PI_HOST_KEY = stringPreferencesKey("pi_host")

private const val DEFAULT_PI_HOST = "carhud.local"

/**
 * Stored Pi IP address. Android 11+ randomizes USB tether subnet, so the IP
 * can change. Set this in Settings to match `ip addr show usb0` on the Pi.
 */
object PiHostSettings {

    fun getHost(context: Context): Flow<String> =
        context.settingsDataStore.data.map { prefs ->
            prefs[PI_HOST_KEY] ?: DEFAULT_PI_HOST
        }

    suspend fun setHost(context: Context, host: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[PI_HOST_KEY] = host
        }
    }
}
