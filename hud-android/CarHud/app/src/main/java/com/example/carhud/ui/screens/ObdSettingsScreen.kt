package com.example.carhud.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.carhud.CarHudApplication
import com.example.carhud.service.BleDeviceUi
import com.example.carhud.service.ObdBleConnectionState
import com.example.carhud.ui.theme.CarHudTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdSettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as CarHudApplication
    val ble = app.bleObdProvider
    val connectionState by ble.connectionState.collectAsState()
    val discovered by ble.discoveredList.collectAsState()
    val logLines by ble.debugLog.collectAsState()

    var permissionDeniedHint by remember { mutableStateOf<String?>(null) }
    var showLog by remember { mutableStateOf(false) }

    val requiredPermissions = remember {
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }.toTypedArray()
    }

    fun hasAllPermissions(): Boolean =
        requiredPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) {
            permissionDeniedHint = null
            ble.startScan()
        } else {
            permissionDeniedHint = "Bluetooth or location permission denied — BLE scan needs these."
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OBD-II Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = statusLabel(connectionState),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (hasAllPermissions()) {
                                permissionDeniedHint = null
                                ble.startScan()
                            } else {
                                permissionLauncher.launch(requiredPermissions)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = connectionState !is ObdBleConnectionState.Connecting
                    ) {
                        Text("Scan for OBD")
                    }
                    OutlinedButton(
                        onClick = { ble.disconnect() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Disconnect")
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = { showLog = !showLog },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showLog) "Hide Debug Log" else "Show Debug Log (${logLines.size} lines)")
                }
            }
            if (showLog) {
                item {
                    DebugLogPanel(logLines)
                }
            }
            permissionDeniedHint?.let { hint ->
                item {
                    Text(
                        text = hint,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                Text(
                    "BLE ELM327 adapters (e.g. VeePeak OBDCheck BLE): tap a device to connect. " +
                        "Vehicle data is merged with GPS from this phone when the Pi connection is active.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(
                    "Raspberry Pi (classic Bluetooth OBD):",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text(
                    "1. Pair the OBD adapter on the Pi: bluetoothctl → pair → trust\n" +
                        "2. sudo rfcomm bind 0 <MAC>  →  /dev/rfcomm0",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Text("Discovered devices", style = MaterialTheme.typography.titleSmall)
            }
            item {
                when {
                    discovered.isEmpty() && connectionState is ObdBleConnectionState.Scanning ->
                        Text("Searching…", style = MaterialTheme.typography.bodyMedium)
                    discovered.isEmpty() ->
                        Text(
                            "No devices yet — tap Scan for OBD.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
            }
            items(discovered, key = { it.address }) { item ->
                DeviceRow(
                    device = item,
                    onClick = {
                        if (hasAllPermissions()) {
                            ble.connect(item.address)
                        } else {
                            permissionLauncher.launch(requiredPermissions)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun DebugLogPanel(lines: List<String>) {
    val listState = rememberLazyListState()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(8.dp)
                .horizontalScroll(rememberScrollState())
        ) {
            items(lines.size) { idx ->
                Text(
                    text = lines[idx],
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun statusLabel(state: ObdBleConnectionState): String = when (state) {
    is ObdBleConnectionState.Disconnected -> "OBD BLE: Disconnected"
    is ObdBleConnectionState.Scanning -> "OBD BLE: Scanning…"
    is ObdBleConnectionState.Connecting -> "OBD BLE: Connecting… (${state.deviceName ?: "device"})"
    is ObdBleConnectionState.Connected -> "OBD BLE: Connected (${state.deviceName ?: "ELM327"})"
    is ObdBleConnectionState.Error -> "OBD BLE: ${state.message}"
}

@Composable
private fun DeviceRow(device: BleDeviceUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(device.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ObdSettingsScreenPreview() {
    CarHudTheme {
        ObdSettingsScreen(onNavigateBack = {})
    }
}
