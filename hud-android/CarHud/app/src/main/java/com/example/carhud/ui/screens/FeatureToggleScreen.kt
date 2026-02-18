package com.example.carhud.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.carhud.data.PresetRepository
import com.example.carhud.model.HudComponent
import com.example.carhud.model.LayoutPreset
import com.example.carhud.service.ActivePresetHolder
import com.example.carhud.service.ConnectionState
import com.example.carhud.service.HudConnectionHolder
import com.example.carhud.ui.theme.CarHudTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Simpler alternative to the full Preset Editor.
 * Quick access to enable/disable HUD features; changes are sent immediately to the Pi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureToggleScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { PresetRepository(context) }
    val scope = rememberCoroutineScope()
    val connectionState by HudConnectionHolder.state.collectAsState()

    var selectedSlot by remember { mutableStateOf(1) }
    var preset by remember { mutableStateOf<LayoutPreset?>(null) }

    LaunchedEffect(selectedSlot) {
        preset = repository.getPresetOrDefault(selectedSlot).first()
    }

    fun sendToHudIfConnected(p: LayoutPreset) {
        if (connectionState is ConnectionState.Connected) {
            HudConnectionHolder.send(p.toLayoutConfigMessage())
            ActivePresetHolder.setActivePreset(p.name)
        }
    }

    fun updateComponent(index: Int, updater: (HudComponent) -> HudComponent) {
        preset?.let { p ->
            val updated = p.components.toMutableList().apply {
                set(index, updater(get(index)))
            }
            val newPreset = p.copy(components = updated)
            preset = newPreset
            scope.launch { repository.savePreset(newPreset) }
            sendToHudIfConnected(newPreset)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Feature Toggles") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 2, 3).forEach { slot ->
                    FilterChip(
                        selected = selectedSlot == slot,
                        onClick = { selectedSlot = slot },
                        label = { Text("Preset $slot") }
                    )
                }
            }

            Text(
                "Preset: ${preset?.name ?: "—"}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            preset?.components?.forEachIndexed { index, comp ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = HudComponent.displayName(comp.type),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Switch(
                        checked = comp.enabled,
                        onCheckedChange = { newValue ->
                            updateComponent(index) { it.copy(enabled = newValue) }
                        }
                    )
                }
            }

            Button(
                onClick = {
                    preset?.let { p ->
                        scope.launch { repository.savePreset(p) }
                        sendToHudIfConnected(p)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                enabled = connectionState is ConnectionState.Connected && preset != null
            ) {
                Text("Apply to HUD")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FeatureToggleScreenPreview() {
    CarHudTheme {
        FeatureToggleScreen(onNavigateBack = {})
    }
}
