package com.example.carhud.ui.screens

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
import androidx.compose.ui.unit.dp
import com.example.carhud.service.ConnectionState
import com.example.carhud.service.HudConnectionHolder
import com.example.carhud.service.HudConnectionService

@Composable
fun HomeScreen(
    onNavigateToPresets: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToObd: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val connectionState by HudConnectionHolder.state.collectAsState()
    val context = LocalContext.current

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

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Active preset: Default",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { HudConnectionService.startConnect(context) },
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
                        connectionState is ConnectionState.Connecting
                ) {
                    Text("Disconnect")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                NavButton("Presets", onNavigateToPresets)
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
private fun NavButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(label)
    }
}
