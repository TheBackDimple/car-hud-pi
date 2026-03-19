package com.example.carhud.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.abs

private val HUD_ASPECT = 1280f / 720f

/** 4×3 grid: uniform cells. Map = full center (2×3), others = 1×1. */
private const val GRID_COLS = 4
private const val GRID_ROWS = 3
private val CELL_W = 1f / GRID_COLS  // 0.25
private val CELL_H = 1f / GRID_ROWS  // ~0.333

/** Padding so blocks sit centered in cells (5% of cell per side for 1×1). */
private val BLOCK_PAD_X = 0.05f * CELL_W
private val BLOCK_PAD_Y = 0.05f * CELL_H

/** Map uses slightly more padding than blocks (8% per side) so it appears a bit smaller. */
private val MAP_PAD_X = 0.08f * CELL_W
private val MAP_PAD_Y = 0.08f * CELL_H

/** Grid size (cols, rows) per component type. Map = full center 2×3, others = 1×1. */
private fun HudComponent.gridCols(): Int = if (type == "map") 2 else 1
private fun HudComponent.gridRows(): Int = if (type == "map") 3 else 1

/** Visual rect (0–1) with padding so block is centered in its grid cell. */
private fun HudComponent.visualRect(): Rect {
    val (padX, padY) = if (type == "map") MAP_PAD_X to MAP_PAD_Y else BLOCK_PAD_X to BLOCK_PAD_Y
    return Rect(
        left = x + padX,
        top = y + padY,
        right = x + width - padX,
        bottom = y + height - padY
    )
}

/** Valid columns for 1×1: 0 (left) and 3 (right). Map occupies 1–2. */
private val VALID_1X1_COLS = listOf(0, 3)

/** Ensure map is always correctly positioned in middle 6 cells (cols 1–2, rows 0–2). */
private fun normalizeMapInPreset(preset: LayoutPreset): LayoutPreset {
    val mapIndex = preset.components.indexOfFirst { it.type == "map" }
    if (mapIndex < 0) return preset
    val map = preset.components[mapIndex]
    val correctMap = map.copy(
        x = 1f * CELL_W,  // col 1
        y = 0f,
        width = 2f * CELL_W,  // 2 cols
        height = 3f * CELL_H   // 3 rows
    )
    if (map.x == correctMap.x && map.y == correctMap.y && map.width == correctMap.width && map.height == correctMap.height) {
        return preset
    }
    val updated = preset.components.toMutableList().apply { set(mapIndex, correctMap) }
    return preset.copy(components = updated)
}

/** Snap (x, y, width, height) to 4×3 grid. Map fixed in center; 1×1 only in left/right columns. */
private fun snapTo4x3Grid(comp: HudComponent): HudComponent {
    val cols = comp.gridCols()
    val rows = comp.gridRows()
    val w = cols * CELL_W
    val h = rows * CELL_H
    val (col, row) = when (comp.type) {
        "map" -> (1 to 0)  // Map fixed in center (cols 1–2, rows 0–2)
        else -> {
            val rawCol = (comp.x / CELL_W + 0.5f).toInt().coerceIn(0, GRID_COLS - 1)
            val col = VALID_1X1_COLS.minByOrNull { abs(it - rawCol) } ?: 0
            val row = (comp.y / CELL_H + 0.5f).toInt().coerceIn(0, GRID_ROWS - 1)
            (col to row)
        }
    }
    val snappedX = col * CELL_W
    val snappedY = row * CELL_H
    return comp.copy(x = snappedX, y = snappedY, width = w, height = h)
}

/** Get grid cell (col, row) for a 1×1 component. Returns null for map. */
private fun HudComponent.cell(): Pair<Int, Int>? = if (type == "map") null else {
    val col = (x / CELL_W + 0.5f).toInt().coerceIn(0, GRID_COLS - 1)
    val row = (y / CELL_H + 0.5f).toInt().coerceIn(0, GRID_ROWS - 1)
    col to row
}

/** All valid 1×1 cells (left col 0, right col 3). */
private val ALL_1X1_CELLS = buildList {
    for (col in VALID_1X1_COLS) for (row in 0 until GRID_ROWS) add(col to row)
}

/** Find first free 1×1 cell, excluding the given cells. */
private fun findFreeCell(components: List<HudComponent>, exclude: Set<Pair<Int, Int>>): Pair<Int, Int>? {
    val occupied = components
        .filter { it.enabled && it.type != "map" }
        .mapNotNull { it.cell() }
        .toSet()
    return ALL_1X1_CELLS.firstOrNull { it !in occupied && it !in exclude }
}

/** Apply drag-end: snap dragged component; if target has another 1×1, ALWAYS swap (never overlay). */
private fun applyDragEnd(
    preset: LayoutPreset,
    dragIndex: Int,
    fromCell: Pair<Int, Int>?,
    presetAtDragStart: LayoutPreset?
): LayoutPreset {
    val dragged = preset.components[dragIndex]
    if (dragged.type == "map") {
        val snapped = snapTo4x3Grid(dragged)
        return preset.copy(components = preset.components.toMutableList().apply { set(dragIndex, snapped) })
    }
    val snapped = snapTo4x3Grid(dragged)
    val targetCell = snapped.cell() ?: return preset.copy(components = preset.components.toMutableList().apply { set(dragIndex, snapped) })
    val otherIndex = preset.components.indexOfFirst { i, c ->
        i != dragIndex && c.enabled && c.type != "map" && c.cell() == targetCell
    }
    if (otherIndex < 0) {
        return preset.copy(components = preset.components.toMutableList().apply { set(dragIndex, snapped) })
    }
    // Target is occupied: ALWAYS swap. Use presetAtDragStart for reliable fromCell (avoids recomposition bugs).
    val other = preset.components[otherIndex]
    val reliableFromCell = presetAtDragStart?.components?.getOrNull(dragIndex)?.cell() ?: fromCell
    val destCellForOther = when {
        reliableFromCell != null && reliableFromCell != targetCell -> reliableFromCell  // Swap: other goes where dragged came from
        else -> findFreeCell(preset.components, setOf(targetCell))  // Fallback only when fromCell unknown
    }
    val otherSnapped = if (destCellForOther != null) {
        other.copy(
            x = destCellForOther.first * CELL_W,
            y = destCellForOther.second * CELL_H,
            width = CELL_W,
            height = CELL_H
        )
    } else {
        // No free cell: revert entire preset to state before drag to prevent overlay
        return presetAtDragStart ?: preset
    }
    return preset.copy(components = preset.components.toMutableList().apply {
        set(dragIndex, snapped)
        set(otherIndex, otherSnapped)
    })
}

private fun <T> List<T>.indexOfFirst(predicate: (Int, T) -> Boolean): Int {
    for (i in indices) if (predicate(i, get(i))) return i
    return -1
}

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
    var dragStartCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragStartPreset by remember { mutableStateOf<LayoutPreset?>(null) }

    LaunchedEffect(selectedSlot) {
        preset = repository.getPresetOrDefault(selectedSlot).first().let(::normalizeMapInPreset)
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
                // Draw 4×3 grid overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val gridColor = Color.Gray.copy(alpha = 0.4f)
                    val strokeWidth = 1f
                    for (c in 1 until GRID_COLS) {
                        val x = size.width * c / GRID_COLS
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth)
                    }
                    for (r in 1 until GRID_ROWS) {
                        val y = size.height * r / GRID_ROWS
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth)
                    }
                }
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
                            },
                            onDragStart = {
                                dragStartCell = comp.cell()
                                preset?.let { dragStartPreset = it }
                            },
                            onDragEnd = {
                                preset?.let { p ->
                                    preset = applyDragEnd(p, index, dragStartCell, dragStartPreset)
                                    dragStartCell = null
                                    dragStartPreset = null
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
    onPositionChange: (dx: Float, dy: Float) -> Unit,
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {}
) {
    val density = LocalDensity.current
    val canvasWidthPx = with(density) { canvasWidth.toPx() }.coerceAtLeast(1f)
    val canvasHeightPx = with(density) { canvasHeight.toPx() }.coerceAtLeast(1f)
    val rect = component.visualRect()

    Box(
        modifier = modifier
            .offset(
                x = (rect.left * canvasWidth.value).dp,
                y = (rect.top * canvasHeight.value).dp
            )
            .size(
                width = (rect.width * canvasWidth.value).dp,
                height = (rect.height * canvasHeight.value).dp
            )
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(component.type) {
                    detectDragGestures(
                        onDragStart = { onDragStart() },
                        onDragEnd = onDragEnd,
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onPositionChange(
                                dragAmount.x / canvasWidthPx,
                                dragAmount.y / canvasHeightPx
                            )
                        }
                    )
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
