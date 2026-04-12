package com.example.carhud.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object HudThemeSettings {
    enum class HudColor(val hex: String, val label: String) {
        GREEN("#00ff78", "Green"),
        BLUE("#00bfff", "Blue"),
        WHITE("#ffffff", "White"),
        RED("#ff4444", "Red"),
        CYAN("#00ffff", "Cyan")
    }

    private val _color = MutableStateFlow(HudColor.GREEN)
    val color: StateFlow<HudColor> = _color.asStateFlow()

    fun setColor(c: HudColor) {
        _color.value = c
    }
}
