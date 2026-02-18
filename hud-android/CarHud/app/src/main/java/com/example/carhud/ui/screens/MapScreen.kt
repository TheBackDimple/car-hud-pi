package com.example.carhud.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import com.example.carhud.service.ConnectionState
import com.example.carhud.service.HudConnectionHolder
import com.example.carhud.service.MapStreamManager
import com.example.carhud.service.TripStateHolder
import com.example.carhud.ui.theme.CarHudTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.rememberCameraPositionState

@OptIn(ExperimentalMaterial3Api::class, MapsComposeExperimentalApi::class)
@Composable
fun MapScreen(onNavigateBack: () -> Unit) {
    val connectionState by HudConnectionHolder.state.collectAsState()
    val isTripActive by TripStateHolder.isTripActive.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var isStreaming by remember { mutableStateOf(false) }

    // Auto-start streaming when trip is active and we're connected
    LaunchedEffect(isTripActive, connectionState) {
        if (isTripActive && connectionState is ConnectionState.Connected) {
            isStreaming = true
        }
    }
    val streamManager = remember {
        MapStreamManager(scope)
    }

    DisposableEffect(Unit) {
        onDispose {
            streamManager.stopStreaming()
        }
    }

    val defaultPosition = remember {
        CameraPosition.fromLatLngZoom(LatLng(37.7749, -122.4194), 14f)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = defaultPosition
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Map") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Text("←") }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                onMapLoaded = { }
            ) {
                MapEffect(isStreaming) { map ->
                    if (isStreaming) {
                        streamManager.startStreaming(map)
                    } else {
                        streamManager.stopStreaming()
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val canStream = connectionState is ConnectionState.Connected
                if (!canStream) {
                    Text(
                        "Connect to Pi to stream map to HUD",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
                Button(
                    onClick = {
                        val fused = LocationServices.getFusedLocationProviderClient(context)
                        fused.lastLocation.addOnSuccessListener { loc ->
                            loc?.let {
                                scope.launch {
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newLatLngZoom(
                                            LatLng(it.latitude, it.longitude), 16f
                                        ),
                                        500
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Text("My Location")
                }
                Button(
                    onClick = {
                        isStreaming = !isStreaming
                        if (!isStreaming) TripStateHolder.endTrip()
                    },
                    enabled = canStream
                ) {
                    Text(if (isStreaming) "End Trip" else "Stream to HUD")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MapScreenPreview() {
    CarHudTheme {
        MapScreen(onNavigateBack = {})
    }
}
