package com.example.carhud.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds "trip active" state. When true, map streaming auto-starts on Map screen.
 */
object TripStateHolder {
    private val _isTripActive = MutableStateFlow(false)
    val isTripActive: StateFlow<Boolean> = _isTripActive.asStateFlow()

    fun startTrip() {
        _isTripActive.value = true
    }

    fun endTrip() {
        _isTripActive.value = false
    }
}
