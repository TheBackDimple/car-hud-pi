package com.example.carhud.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.carhud.ui.theme.CarHudTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdSettingsScreen(onNavigateBack: () -> Unit) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "OBD-II adapter connects to the Raspberry Pi",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "The OBD-II Bluetooth adapter is paired with the Pi (not this phone). " +
                "Vehicle data (speed, RPM, coolant temp, fuel) is read on the Pi and merged with GPS from this phone.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "On the Raspberry Pi:",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "1. Pair the OBD adapter: bluetoothctl → pair <MAC> → trust <MAC>\n" +
                "2. Bind to serial: sudo rfcomm bind 0 <MAC_ADDRESS>\n" +
                "3. Port /dev/rfcomm0 will appear; the OBD reader script uses it automatically.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth()
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
