package com.example.carhud.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds "navigation trip" state. When true and the Pi is connected, map streaming is enabled.
 * Set when the user starts navigation on the map; cleared when navigation ends or streaming stops.
 */
object TripStateHolder {
    private val _isTripActive = MutableStateFlow(false)
    val isTripActive: StateFlow<Boolean> = _isTripActive.asStateFlow()

    /** Next [MapStreamManager.stopStreaming] should tell the Pi to show "Trip Ended" on the HUD. */
    @Volatile
    private var pendingTripEndedHudNotice = false

    fun startTrip() {
        _isTripActive.value = true
        pendingTripEndedHudNotice = false
    }

    fun endTrip() {
        _isTripActive.value = false
    }

    fun prepareTripEndedHudNotice() {
        pendingTripEndedHudNotice = true
    }

    fun consumeTripEndedHudNoticeOnMapClear(): Boolean {
        if (!pendingTripEndedHudNotice) return false
        pendingTripEndedHudNotice = false
        return true
    }
}
