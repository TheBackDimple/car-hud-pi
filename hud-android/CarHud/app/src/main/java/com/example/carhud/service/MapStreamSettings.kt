package com.example.carhud.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Map streaming quality and FPS settings.
 * Updated from Settings screen; read by MapStreamManager.
 */
object MapStreamSettings {
    enum class Quality(val jpegQuality: Int, val label: String) {
        LOW(40, "Low"),
        MEDIUM(60, "Medium"),
        HIGH(80, "High")
    }

    enum class Fps(val intervalMs: Long, val label: String) {
        FPS_2(500L, "2 FPS"),
        FPS_3(333L, "3 FPS")
    }

    private val _quality = MutableStateFlow(Quality.MEDIUM)
    val quality: StateFlow<Quality> = _quality.asStateFlow()

    private val _fps = MutableStateFlow(Fps.FPS_2)
    val fps: StateFlow<Fps> = _fps.asStateFlow()

    fun setQuality(q: Quality) {
        _quality.value = q
    }

    fun setFps(f: Fps) {
        _fps.value = f
    }
}
