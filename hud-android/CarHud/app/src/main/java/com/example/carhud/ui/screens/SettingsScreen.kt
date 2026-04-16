package com.example.carhud.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.carhud.model.HudMessage
import com.example.carhud.service.HudConnectionHolder
import com.example.carhud.service.HudThemeSettings
import com.example.carhud.service.MapStreamSettings
import com.example.carhud.service.PiDiscovery
import com.example.carhud.service.PiHostSettings
import com.example.carhud.service.VoiceNavigationSettings
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.example.carhud.ui.theme.CarHudTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val quality by MapStreamSettings.quality.collectAsState()
    val fps by MapStreamSettings.fps.collectAsState()
    val hudColor by HudThemeSettings.color.collectAsState()
    val piHostStored by PiHostSettings.getHost(context).collectAsState(initial = "auto")
    var piHostDraft by remember { mutableStateOf(piHostStored) }
    var piHostFocused by remember { mutableStateOf(false) }
    LaunchedEffect(piHostStored) {
        if (!piHostFocused) piHostDraft = piHostStored
    }
    val voiceNavEnabled by VoiceNavigationSettings.isEnabled(context).collectAsState(initial = false)

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
                "Pi host: \"auto\" discovers on USB subnet, or enter IP (e.g. 192.168.171.140)",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            OutlinedTextField(
                value = piHostDraft,
                onValueChange = { new ->
                    val normalized = new.replace("carhud_local", "carhud.local")
                    piHostDraft = normalized
                    scope.launch { PiHostSettings.setHost(context, normalized.trim()) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { piHostFocused = it.isFocused },
                singleLine = true,
                placeholder = { Text("auto or 192.168.171.140") }
            )
            TextButton(
                onClick = {
                    val clip = PiDiscovery.lastDiscoveryReport.ifBlank { "(Run Connect with Pi host \"auto\" once, then copy again.)" }
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Pi discovery debug", clip))
                    Toast.makeText(context, "Copied Pi discovery debug log", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Copy last Pi discovery debug log")
            }
            Text(
                "After a failed \"auto\" connect, tap above and paste into a note or chat. Logcat: adb logcat -s CarHudPiDiscovery",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        "Voice navigation prompts",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        "Speak turn-by-turn directions while Start Navigation is active on the map.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Switch(
                    checked = voiceNavEnabled,
                    onCheckedChange = { on ->
                        scope.launch { VoiceNavigationSettings.setEnabled(context, on) }
                    }
                )
            }

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

            Text(
                "HUD Color Theme",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 24.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HudThemeSettings.HudColor.entries.forEach { c ->
                    val selected = hudColor == c
                    val dotColor = Color(android.graphics.Color.parseColor(c.hex))
                    androidx.compose.material3.FilterChip(
                        selected = selected,
                        onClick = {
                            HudThemeSettings.setColor(c)
                            val themeMessage = HudMessage(
                                type = "theme_config",
                                payload = buildJsonObject {
                                    put("color", c.hex)
                                },
                                timestamp = System.currentTimeMillis()
                            )
                            HudConnectionHolder.send(themeMessage)
                        },
                        label = { Text(c.label) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(dotColor, CircleShape)
                                    .then(
                                        if (c == HudThemeSettings.HudColor.WHITE) {
                                            Modifier.border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline,
                                                CircleShape
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                            )
                        }
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
