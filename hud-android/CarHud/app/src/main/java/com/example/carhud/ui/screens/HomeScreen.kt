package com.example.carhud.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.carhud.CarHudApplication
import com.example.carhud.service.ConnectionState
import com.example.carhud.service.HudConnectionHolder
import com.example.carhud.service.HudConnectionService
import com.example.carhud.service.ActivePresetHolder
import com.example.carhud.service.ObdBleConnectionState
import com.example.carhud.service.ObdLiveData
import com.example.carhud.service.PiHostSettings
import com.example.carhud.ui.theme.CarHudTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPresets: () -> Unit,
    onNavigateToFeatureToggles: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToObd: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val connectionState by HudConnectionHolder.state.collectAsState()
    val activePresetName by ActivePresetHolder.name.collectAsState()
    val context = LocalContext.current
    val piHost by PiHostSettings.getHost(context).collectAsState(initial = "auto")

    val ble = (context.applicationContext as CarHudApplication).bleObdProvider
    val obdState by ble.connectionState.collectAsState()
    val obdLive by ble.latestObd.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            HudConnectionService.startConnect(context, piHost)
        }
    }

    val startTripPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.any { it }) {
            if (connectionState is ConnectionState.Disconnected ||
                connectionState is ConnectionState.Error
            ) {
                HudConnectionService.startConnect(context, piHost)
            }
        }
        onNavigateToMap()
    }

    fun onConnectClick() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ->
                HudConnectionService.startConnect(context, piHost)
            else -> permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Car HUD") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Text("⚙", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ConnectionStatusCard(connectionState)

            ObdLiveCard(obdState, obdLive)

            Button(
                onClick = {
                    val hasLocation =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                    if (!hasLocation) {
                        startTripPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    } else {
                        if (connectionState is ConnectionState.Disconnected ||
                            connectionState is ConnectionState.Error
                        ) {
                            onConnectClick()
                        }
                        onNavigateToMap()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Trip")
            }

            Text(
                "Active preset: $activePresetName",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onConnectClick() },
                    modifier = Modifier.weight(1f),
                    enabled = connectionState is ConnectionState.Disconnected ||
                        connectionState is ConnectionState.Error
                ) {
                    Text("Connect")
                }
                Button(
                    onClick = { HudConnectionService.stopService(context) },
                    modifier = Modifier.weight(1f),
                    enabled = connectionState is ConnectionState.Connected ||
                        connectionState is ConnectionState.Connecting ||
                        connectionState is ConnectionState.Error
                ) {
                    Text("Disconnect")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavButton("Presets", onNavigateToPresets)
                NavButton("Toggles", onNavigateToFeatureToggles)
                NavButton("Map", onNavigateToMap)
                NavButton("OBD-II", onNavigateToObd)
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(state: ConnectionState) {
    val (statusText, color) = when (state) {
        is ConnectionState.Disconnected -> "Disconnected" to Color.Gray
        is ConnectionState.Connecting -> "Connecting…" to Color(0xFFFFA726)
        is ConnectionState.Connected -> "Connected" to Color(0xFF66BB6A)
        is ConnectionState.Error -> "Error: ${state.message}" to Color(0xFFEF5350)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = CardDefaults.shape
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(12.dp),
                onDraw = {
                    drawCircle(color = color)
                }
            )
            Spacer(modifier = Modifier.size(12.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun ObdLiveCard(obdState: ObdBleConnectionState, data: ObdLiveData?) {
    val isConnected = obdState is ObdBleConnectionState.Connected

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = if (isConnected) 1f else 0.5f
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "OBD-II Live",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    when (obdState) {
                        is ObdBleConnectionState.Connected -> "Connected"
                        is ObdBleConnectionState.Connecting -> "Connecting…"
                        is ObdBleConnectionState.Scanning -> "Scanning…"
                        else -> "Not connected"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isConnected) Color(0xFF66BB6A) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isConnected && data != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ObdGauge("MPH", data.speedMph)
                    ObdGauge("RPM", data.rpm)
                    ObdGauge("Coolant", data.coolantTemp)
                    ObdGauge("MPG", data.mpg)
                    ObdGauge("Fuel", data.fuelLevel)
                }
            } else if (!isConnected) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Go to OBD-II to connect your adapter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ObdGauge(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.ifBlank { "—" },
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NavButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(label)
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    CarHudTheme {
        HomeScreen(
            onNavigateToPresets = {},
            onNavigateToFeatureToggles = {},
            onNavigateToMap = {},
            onNavigateToObd = {},
            onNavigateToSettings = {}
        )
    }
}
