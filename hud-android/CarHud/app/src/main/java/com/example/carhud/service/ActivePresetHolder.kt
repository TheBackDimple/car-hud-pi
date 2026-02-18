package com.example.carhud.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the name of the last preset applied to the HUD. */
object ActivePresetHolder {
    private val _name = MutableStateFlow("Default")
    val name: StateFlow<String> = _name.asStateFlow()

    fun setActivePreset(name: String) {
        _name.value = name
    }
}
