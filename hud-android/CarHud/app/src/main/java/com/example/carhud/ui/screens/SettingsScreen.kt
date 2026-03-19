package com.example.carhud.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.carhud.service.MapStreamSettings
import com.example.carhud.ui.theme.CarHudTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val quality by MapStreamSettings.quality.collectAsState()
    val fps by MapStreamSettings.fps.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            Text(
                "Pi IP: 192.168.254.2 (USB tether)",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                "Map streaming quality",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 24.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapStreamSettings.Quality.entries.forEach { q ->
                    val selected = quality == q
                    androidx.compose.material3.FilterChip(
                        selected = selected,
                        onClick = { MapStreamSettings.setQuality(q) },
                        label = { Text(q.label) }
                    )
                }
            }

            Text(
                "Map streaming FPS",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapStreamSettings.Fps.entries.forEach { f ->
                    val selected = fps == f
                    androidx.compose.material3.FilterChip(
                        selected = selected,
                        onClick = { MapStreamSettings.setFps(f) },
                        label = { Text(f.label) }
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    CarHudTheme {
        SettingsScreen(onNavigateBack = {})
    }
}
