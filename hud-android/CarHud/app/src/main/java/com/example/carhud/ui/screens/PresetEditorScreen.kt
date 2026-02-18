package com.example.carhud.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.carhud.data.PresetRepository
import com.example.carhud.data.defaultPreset
import com.example.carhud.model.HudComponent
import com.example.carhud.model.LayoutPreset
import com.example.carhud.service.ActivePresetHolder
import com.example.carhud.service.ConnectionState
import com.example.carhud.service.HudConnectionHolder
import com.example.carhud.ui.theme.CarHudTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
private val HUD_ASPECT = 1280f / 720f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditorScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val repository = remember { PresetRepository(context) }
    val scope = rememberCoroutineScope()
    val connectionState by HudConnectionHolder.state.collectAsState()

    var selectedSlot by remember { mutableStateOf(1) }
    var preset by remember { mutableStateOf<LayoutPreset?>(null) }
    var presetName by remember { mutableStateOf("") }

    LaunchedEffect(selectedSlot) {
        preset = repository.getPresetOrDefault(selectedSlot).first()
        presetName = preset?.name ?: "Preset $selectedSlot"
    }

    LaunchedEffect(preset) {
        preset?.let { presetName = it.name }
    }

    fun updatePreset(updater: (LayoutPreset) -> LayoutPreset) {
        preset?.let { preset = updater(it) }
    }

    fun updateComponent(index: Int, updater: (HudComponent) -> HudComponent) {
        preset?.let { p ->
            val updated = p.components.toMutableList().apply {
                set(index, updater(get(index)))
            }
            preset = p.copy(components = updated)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preset Editor") },
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
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 2, 3).forEach { slot ->
                    FilterChip(
                        selected = selectedSlot == slot,
                        onClick = {
                            scope.launch {
                                preset?.let { repository.savePreset(it) }
                            }
                            selectedSlot = slot
                        },
                        label = { Text("Preset $slot") }
                    )
                }
            }

            OutlinedTextField(
                value = presetName,
                onValueChange = {
                    presetName = it
                    preset?.let { p -> preset = p.copy(name = it) }
                },
                label = { Text("Preset name") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            Text(
                "HUD canvas (1280×720)",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(HUD_ASPECT)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val canvasW = maxWidth
                val canvasH = maxHeight
                preset?.components?.forEachIndexed { index, comp ->
                    if (comp.enabled && comp.width > 0 && comp.height > 0) {
                        DraggableComponent(
                            modifier = Modifier.align(Alignment.TopStart),
                            component = comp,
                            canvasWidth = canvasW,
                            canvasHeight = canvasH,
                            onPositionChange = { dx, dy ->
                                updateComponent(index) { c ->
                                    val newX = (c.x + dx).coerceIn(0f, 1f - c.width)
                                    val newY = (c.y + dy).coerceIn(0f, 1f - c.height)
                                    c.copy(x = newX, y = newY)
                                }
                            }
                        )
                    }
                }
            }

            Text(
                "Feature toggles",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            preset?.components?.forEachIndexed { index, comp ->
                FeatureToggleRow(
                    label = HudComponent.displayName(comp.type),
                    enabled = comp.enabled,
                    onCheckedChange = { newValue ->
                        preset?.let { p ->
                            val updated = p.components.toMutableList().apply {
                                set(index, get(index).copy(enabled = newValue))
                            }
                            val updatedPreset = p.copy(components = updated)
                            preset = updatedPreset
                            if (connectionState is ConnectionState.Connected) {
                                HudConnectionHolder.send(updatedPreset.toLayoutConfigMessage())
                                ActivePresetHolder.setActivePreset(updatedPreset.name)
                            }
                        }
                    }
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            preset?.let { repository.savePreset(it) }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
                Button(
                    onClick = {
                        preset?.let { p ->
                            HudConnectionHolder.send(p.toLayoutConfigMessage())
                            ActivePresetHolder.setActivePreset(p.name)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = connectionState is ConnectionState.Connected
                ) {
                    Text("Apply to HUD")
                }
            }
        }
    }
}

@Composable
private fun FeatureToggleRow(
    label: String,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(
            checked = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun DraggableComponent(
    modifier: Modifier = Modifier,
    component: HudComponent,
    canvasWidth: Dp,
    canvasHeight: Dp,
    onPositionChange: (dx: Float, dy: Float) -> Unit
) {
    Box(
        modifier = modifier
            .offset(
                x = (component.x * canvasWidth.value).dp,
                y = (component.y * canvasHeight.value).dp
            )
            .size(
                width = (component.width * canvasWidth.value).dp,
                height = (component.height * canvasHeight.value).dp
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(component) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onPositionChange(
                            dragAmount.x / size.width.toFloat(),
                            dragAmount.y / size.height.toFloat()
                        )
                    }
                },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    component.type,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PresetEditorScreenPreview() {
    CarHudTheme {
        PresetEditorScreen(onNavigateBack = {})
    }
}
