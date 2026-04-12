package com.example.carhud.service

import com.example.carhud.model.HudMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared connection state and service reference.
 * Updated by HudConnectionService. UI can send messages via send().
 */
object HudConnectionHolder {
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    @Volatile
    var service: HudConnectionService? = null
        private set

    fun registerService(svc: HudConnectionService) {
        service = svc
    }

    fun unregisterService() {
        service = null
    }

    fun updateState(newState: ConnectionState) {
        _state.value = newState
    }

    fun send(message: HudMessage) {
        service?.send(message)
    }
}
