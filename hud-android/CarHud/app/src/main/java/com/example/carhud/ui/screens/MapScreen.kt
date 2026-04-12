package com.example.carhud.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.location.Geocoder
import android.os.Looper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.carhud.service.ConnectionState
import com.example.carhud.service.DirectionsProvider
import com.example.carhud.service.HudConnectionHolder
import com.example.carhud.service.MapStreamManager
import com.example.carhud.service.NavigationStateHolder
import com.example.carhud.service.TripStateHolder
import com.example.carhud.ui.theme.CarHudTheme
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val STEP_COMPLETE_METERS = 50.0
private const val NAV_MIN_SPEED_MPS = 1.0f
private const val NAV_CAMERA_MOVE_MIN_METERS = 5f

private fun distanceMeters(a: LatLng, b: LatLng): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val sinDLat = sin(dLat / 2)
    val sinDLng = sin(dLng / 2)
    val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLng * sinDLng
    return 2 * r * atan2(sqrt(h), sqrt(1 - h))
}

private suspend fun getLastKnownLatLng(
    context: android.content.Context
): LatLng? = suspendCancellableCoroutine { cont ->
    val fused = LocationServices.getFusedLocationProviderClient(context)
    fused.lastLocation
        .addOnSuccessListener { loc ->
            if (cont.isActive) {
                cont.resume(loc?.let { LatLng(it.latitude, it.longitude) })
            }
        }
        .addOnFailureListener {
            if (cont.isActive) cont.resume(null)
        }
}

/** When [lastLocation] is null (cold start), ask the fused provider for a fresh fix. */
private suspend fun getCurrentLocationLatLng(
    context: android.content.Context
): LatLng? = suspendCancellableCoroutine { cont ->
    val fused = LocationServices.getFusedLocationProviderClient(context)
    val cts = CancellationTokenSource()
    cont.invokeOnCancellation { cts.cancel() }
    fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
        .addOnSuccessListener { loc ->
            if (cont.isActive) {
                cont.resume(loc?.let { LatLng(it.latitude, it.longitude) })
            }
        }
        .addOnFailureListener {
            if (cont.isActive) cont.resume(null)
        }
}

private suspend fun resolveInitialMapLatLng(context: android.content.Context): LatLng? {
    return getLastKnownLatLng(context) ?: getCurrentLocationLatLng(context)
}

/** Immediate follow-me view when navigation starts (location callback alone may not move camera until moving). */
private fun centerMapOnUserForNavigationStart(
    context: android.content.Context,
    scope: CoroutineScope,
    cameraPositionState: CameraPositionState,
    navBearingRef: FloatArray,
    userLocationHint: LatLng?,
    onLastNavCameraLatLng: (LatLng) -> Unit,
) {
    val fused = LocationServices.getFusedLocationProviderClient(context)
    fused.lastLocation.addOnSuccessListener { loc ->
        if (loc != null) {
            onLastNavCameraLatLng(LatLng(loc.latitude, loc.longitude))
            val spd = loc.speed
            if (spd >= NAV_MIN_SPEED_MPS && loc.hasBearing()) {
                navBearingRef[0] = loc.bearing
            }
            val bearing = if (spd >= NAV_MIN_SPEED_MPS && loc.hasBearing()) {
                navBearingRef[0]
            } else {
                cameraPositionState.position.bearing
            }
            scope.launch {
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(LatLng(loc.latitude, loc.longitude))
                            .zoom(17.5f)
                            .tilt(45f)
                            .bearing(bearing)
                            .build()
                    ),
                    500
                )
            }
        } else {
            scope.launch {
                val ll = userLocationHint ?: getCurrentLocationLatLng(context) ?: return@launch
                onLastNavCameraLatLng(ll)
                cameraPositionState.animate(
                    CameraUpdateFactory.newCameraPosition(
                        CameraPosition.Builder()
                            .target(ll)
                            .zoom(17.5f)
                            .tilt(45f)
                            .bearing(cameraPositionState.position.bearing)
                            .build()
                    ),
                    500
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationStepCard(
    step: DirectionsProvider.NavStep,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Navigation",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                step.instruction,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                step.distance,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    MapsComposeExperimentalApi::class,
    FlowPreview::class,
    ExperimentalComposeUiApi::class
)
@Composable
fun MapScreen(onNavigateBack: () -> Unit) {
    val connectionState by HudConnectionHolder.state.collectAsState()
    val isTripActive by TripStateHolder.isTripActive.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    var isStreaming by remember { mutableStateOf(false) }
    var isNavigating by remember { mutableStateOf(false) }
    var needsRecenter by remember { mutableStateOf(false) }
    val navBearingRef = remember { floatArrayOf(0f) }
    var searchQuery by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf<LatLng?>(null) }

    var autocompletePredictions by remember { mutableStateOf<List<AutocompletePrediction>>(emptyList()) }
    var navSteps by remember { mutableStateOf<List<DirectionsProvider.NavStep>>(emptyList()) }
    var routeInfo by remember { mutableStateOf<DirectionsProvider.RouteInfo?>(null) }
    var routePoints by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var currentStepIndex by remember { mutableIntStateOf(0) }

    val latestSteps = rememberUpdatedState(navSteps)
    val latestIndex = rememberUpdatedState(currentStepIndex)
    val latestIsNavigating = rememberUpdatedState(isNavigating)
    val latestDestination = rememberUpdatedState(destination)
    val arrivalHandled = remember { AtomicBoolean(false) }

    var userLocation by remember { mutableStateOf<LatLng?>(null) }
    var lastNavCameraLatLng by remember { mutableStateOf<LatLng?>(null) }
    var prevConnectionState by remember { mutableStateOf<ConnectionState?>(null) }

    val placesClient: PlacesClient? = remember {
        if (Places.isInitialized()) Places.createClient(context) else null
    }
    val directionsProvider = remember { DirectionsProvider() }

    LaunchedEffect(isTripActive, connectionState) {
        if (isTripActive && connectionState is ConnectionState.Connected) {
            isStreaming = true
        }
    }

    LaunchedEffect(connectionState) {
        if (connectionState !is ConnectionState.Connected) {
            isStreaming = false
        }
        val prev = prevConnectionState
        prevConnectionState = connectionState
        val wasActive =
            prev is ConnectionState.Connected || prev is ConnectionState.Connecting
        val nowOff =
            connectionState is ConnectionState.Disconnected || connectionState is ConnectionState.Error
        if (wasActive && nowOff) {
            snackbarHostState.showSnackbar("Disconnected From Pi")
        }
    }

    LaunchedEffect(isNavigating) {
        if (isNavigating) {
            lastNavCameraLatLng = null
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

    LaunchedEffect(navSteps, currentStepIndex, isNavigating, routeInfo) {
        NavigationStateHolder.syncNavigation(
            route = routeInfo,
            navSteps = navSteps,
            currentStepIndex = currentStepIndex,
            isNavigating = isNavigating
        )
        if (isNavigating) {
            NavigationStateHolder.updateStep(navSteps.getOrNull(currentStepIndex))
        } else {
            NavigationStateHolder.updateStep(null)
        }
    }

    val defaultPosition = remember {
        CameraPosition.fromLatLngZoom(LatLng(37.7749, -122.4194), 14f)
    }
    val cameraPositionState = rememberCameraPositionState {
        position = defaultPosition
    }

    var resumeTick by remember { mutableIntStateOf(0) }
    var mapCenterApplied by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val activity = context as? ComponentActivity ?: return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTick++
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumeTick) {
        if (mapCenterApplied) return@LaunchedEffect
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return@LaunchedEffect
        val latLng = withContext(Dispatchers.IO) {
            resolveInitialMapLatLng(context)
        }
        latLng?.let {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(it, 14f),
                600
            )
            mapCenterApplied = true
        }
    }

    fun hasFineLocation(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun endNavigationTrip() {
        scope.launch {
            if (!isNavigating) return@launch
            TripStateHolder.prepareTripEndedHudNotice()
            TripStateHolder.endTrip()
            isStreaming = false
            isNavigating = false
            needsRecenter = false
            val points = routePoints.toList()
            if (points.size >= 2) {
                val builder = LatLngBounds.Builder()
                points.forEach { builder.include(it) }
                destination?.let { builder.include(it) }
                val bounds = builder.build()
                val pad = (48 * context.resources.displayMetrics.density).toInt()
                cameraPositionState.animate(
                    CameraUpdateFactory.newLatLngBounds(bounds, pad),
                    800
                )
            }
            navSteps = emptyList()
            routeInfo = null
            routePoints = emptyList()
            currentStepIndex = 0
            destination = null
            lastNavCameraLatLng = null
            NavigationStateHolder.updateStep(null)
        }
    }

    fun startNavigation() {
        if (!hasFineLocation()) {
            scope.launch {
                snackbarHostState.showSnackbar("Location Permission Required for Navigation")
            }
            return
        }
        needsRecenter = false
        TripStateHolder.startTrip()
        isNavigating = true
        centerMapOnUserForNavigationStart(
            context = context,
            scope = scope,
            cameraPositionState = cameraPositionState,
            navBearingRef = navBearingRef,
            userLocationHint = userLocation,
            onLastNavCameraLatLng = { lastNavCameraLatLng = it }
        )
    }

    val hasFineLocationPermission =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    DisposableEffect(hasFineLocationPermission) {
        if (!hasFineLocationPermission) {
            return@DisposableEffect onDispose { }
        }
        val fused = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val here = LatLng(loc.latitude, loc.longitude)
                userLocation = here
                val speed = loc.speed
                val s = latestSteps.value
                val i = latestIndex.value
                if (s.isNotEmpty() && i < s.size) {
                    val step = s[i]
                    val end = LatLng(step.endLat, step.endLng)
                    if (distanceMeters(here, end) < STEP_COMPLETE_METERS) {
                        if (i < s.size - 1) {
                            currentStepIndex = i + 1
                        }
                    }
                }
                if (latestIsNavigating.value && speed >= NAV_MIN_SPEED_MPS) {
                    if (loc.hasBearing()) {
                        navBearingRef[0] = loc.bearing
                    }
                    val lastCam = lastNavCameraLatLng
                    val shouldMoveCamera = lastCam == null || run {
                        val dist = FloatArray(1)
                        Location.distanceBetween(
                            lastCam.latitude,
                            lastCam.longitude,
                            here.latitude,
                            here.longitude,
                            dist
                        )
                        dist[0] > NAV_CAMERA_MOVE_MIN_METERS
                    }
                    if (shouldMoveCamera) {
                        lastNavCameraLatLng = here
                        val cameraPosition = CameraPosition.Builder()
                            .target(here)
                            .zoom(17.5f)
                            .tilt(45f)
                            .bearing(navBearingRef[0])
                            .build()
                        scope.launch {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newCameraPosition(cameraPosition),
                                1000
                            )
                        }
                    }
                }
                if (latestIsNavigating.value && s.isNotEmpty() && i == s.size - 1) {
                    val dest = latestDestination.value
                    if (dest != null && distanceMeters(here, dest) < STEP_COMPLETE_METERS) {
                        if (arrivalHandled.compareAndSet(false, true)) {
                            scope.launch { endNavigationTrip() }
                        }
                    }
                }
            }
        }
        var registered = false
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            registered = true
        } catch (_: SecurityException) {
        }
        onDispose {
            if (registered) {
                fused.removeLocationUpdates(callback)
            }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { searchQuery }
            .debounce(300)
            .collect { query ->
                val client = placesClient ?: return@collect
                val q = query.trim()
                if (q.length < 2) {
                    autocompletePredictions = emptyList()
                    return@collect
                }
                val request = FindAutocompletePredictionsRequest.builder()
                    .setQuery(q)
                    .build()
                client.findAutocompletePredictions(request)
                    .addOnSuccessListener { response ->
                        autocompletePredictions = response.autocompletePredictions
                    }
                    .addOnFailureListener {
                        autocompletePredictions = emptyList()
                    }
            }
    }

    fun fetchDirections(origin: LatLng, dest: LatLng) {
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                directionsProvider.getDirections(origin, dest)
            }
            if (result == null) {
                snackbarHostState.showSnackbar("Could Not Load Directions")
                routePoints = emptyList()
                navSteps = emptyList()
                routeInfo = null
                currentStepIndex = 0
                isNavigating = false
                arrivalHandled.set(false)
                return@launch
            }
            navSteps = result.steps
            routePoints = result.polyline
            routeInfo = result
            currentStepIndex = 0
            isNavigating = false
            arrivalHandled.set(false)
        }
    }

    fun applyDestination(latLng: LatLng, title: String) {
        destination = latLng
        searchQuery = title
        autocompletePredictions = emptyList()
        scope.launch {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(latLng, 16f),
                500
            )
            val origin = getLastKnownLatLng(context)
            if (origin == null) {
                snackbarHostState.showSnackbar("Current Location Unavailable for Directions")
                routePoints = emptyList()
                navSteps = emptyList()
                routeInfo = null
                currentStepIndex = 0
                isNavigating = false
                arrivalHandled.set(false)
                NavigationStateHolder.updateStep(null)
                return@launch
            }
            fetchDirections(origin, latLng)
        }
    }

    fun performDestinationSearch() {
        val query = searchQuery.trim()
        if (query.isEmpty()) return
        scope.launch {
            val latLng = withContext(Dispatchers.IO) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocationName(query, 1)
                if (!results.isNullOrEmpty()) {
                    val loc = results[0]
                    LatLng(loc.latitude, loc.longitude)
                } else {
                    null
                }
            }
            if (latLng != null) {
                applyDestination(latLng, query)
            } else {
                snackbarHostState.showSnackbar("Location Not Found")
            }
        }
    }

    fun onSuggestionSelected(prediction: AutocompletePrediction) {
        keyboardController?.hide()
        focusManager.clearFocus()
        val client = placesClient ?: return
        val placeFields = listOf(
            Place.Field.LAT_LNG,
            Place.Field.NAME,
            Place.Field.ADDRESS
        )
        val fetchRequest = FetchPlaceRequest.newInstance(prediction.placeId, placeFields)
        client.fetchPlace(fetchRequest)
            .addOnSuccessListener { response ->
                val place = response.place
                val latLng = place.latLng ?: return@addOnSuccessListener
                val title = place.name ?: prediction.getPrimaryText(null).toString()
                applyDestination(latLng, title)
            }
            .addOnFailureListener {
                scope.launch {
                    snackbarHostState.showSnackbar("Could Not Load Place")
                }
            }
    }

    val connectionLabel = when (connectionState) {
        is ConnectionState.Connected -> "Connected"
        is ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.Disconnected -> "Disconnected"
        is ConnectionState.Error -> "Disconnected"
    }
    val connectionChipColor =
        if (connectionState is ConnectionState.Connected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }

    val currentNavStep = navSteps.getOrNull(currentStepIndex)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                MapEffect(isNavigating) { map ->
                    map.setOnCameraMoveStartedListener { reason ->
                        if (latestIsNavigating.value &&
                            reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE
                        ) {
                            needsRecenter = true
                        }
                    }
                }
                userLocation?.let { center ->
                    Circle(
                        center = center,
                        radius = 8.0,
                        fillColor = Color(0x400000FF),
                        strokeColor = Color(0xFF0000FF),
                        strokeWidth = 2f
                    )
                    Circle(
                        center = center,
                        radius = 3.0,
                        fillColor = Color(0xFF0000FF),
                        strokeColor = Color(0xFF0000FF),
                        strokeWidth = 0f
                    )
                }
                if (routePoints.size >= 2) {
                    Polyline(
                        points = routePoints,
                        color = Color(0xFF1565C0),
                        width = 8f
                    )
                }
                destination?.let { pos ->
                    key(pos) {
                        Marker(
                            state = rememberMarkerState(position = pos),
                            title = "Destination"
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(1f)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isNavigating) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Enter Destination...") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null
                                )
                            },
                            trailingIcon = {
                                TextButton(onClick = { performDestinationSearch() }) {
                                    Text("Go")
                                }
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { performDestinationSearch() }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                    }

                    if (autocompletePredictions.isNotEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(autocompletePredictions, key = { it.placeId }) { prediction ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onSuggestionSelected(prediction) }
                                            .padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = prediction.getPrimaryText(null).toString(),
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = prediction.getSecondaryText(null).toString(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = connectionChipColor,
                    shadowElevation = 2.dp
                ) {
                    Text(
                        connectionLabel,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isNavigating && currentNavStep != null) {
                    NavigationStepCard(
                        step = currentNavStep,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            val hasRoute = routePoints.size >= 2

            if (isNavigating && needsRecenter) {
                FloatingActionButton(
                    onClick = {
                        if (!hasFineLocation()) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Location Permission Required")
                            }
                            return@FloatingActionButton
                        }
                        val fused = LocationServices.getFusedLocationProviderClient(context)
                        fused.lastLocation.addOnSuccessListener { loc ->
                            loc?.let {
                                val spd = it.speed
                                if (spd >= NAV_MIN_SPEED_MPS && it.hasBearing()) {
                                    navBearingRef[0] = it.bearing
                                }
                                val bearing = if (spd >= NAV_MIN_SPEED_MPS && it.hasBearing()) {
                                    navBearingRef[0]
                                } else {
                                    cameraPositionState.position.bearing
                                }
                                scope.launch {
                                    val cameraPosition = CameraPosition.Builder()
                                        .target(LatLng(it.latitude, it.longitude))
                                        .zoom(17.5f)
                                        .tilt(45f)
                                        .bearing(bearing)
                                        .build()
                                    cameraPositionState.animate(
                                        CameraUpdateFactory.newCameraPosition(cameraPosition),
                                        500
                                    )
                                    needsRecenter = false
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 160.dp, end = 20.dp)
                        .zIndex(1f),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(
                        imageVector = Icons.Filled.CenterFocusStrong,
                        contentDescription = "Re-Center"
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val canStream = connectionState is ConnectionState.Connected
                if (!canStream) {
                    Text(
                        "Connect to Pi to Stream Map to HUD",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FloatingActionButton(
                        onClick = {
                            val fused = LocationServices.getFusedLocationProviderClient(context)
                            fused.lastLocation.addOnSuccessListener { loc ->
                                loc?.let {
                                    scope.launch {
                                        if (isNavigating) {
                                            val spd = it.speed
                                            if (spd >= NAV_MIN_SPEED_MPS && it.hasBearing()) {
                                                navBearingRef[0] = it.bearing
                                            }
                                            val bearing = if (spd >= NAV_MIN_SPEED_MPS && it.hasBearing()) {
                                                navBearingRef[0]
                                            } else {
                                                cameraPositionState.position.bearing
                                            }
                                            val cameraPosition = CameraPosition.Builder()
                                                .target(LatLng(it.latitude, it.longitude))
                                                .zoom(17.5f)
                                                .tilt(45f)
                                                .bearing(bearing)
                                                .build()
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newCameraPosition(cameraPosition),
                                                500
                                            )
                                            needsRecenter = false
                                        } else {
                                            cameraPositionState.animate(
                                                CameraUpdateFactory.newLatLngZoom(
                                                    LatLng(it.latitude, it.longitude),
                                                    16f
                                                ),
                                                500
                                            )
                                        }
                                    }
                                }
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MyLocation,
                            contentDescription = "My Location"
                        )
                    }
                    Button(
                        onClick = {
                            if (hasRoute) {
                                if (isNavigating) {
                                    endNavigationTrip()
                                } else {
                                    startNavigation()
                                }
                            } else {
                                if (isStreaming) {
                                    TripStateHolder.endTrip()
                                }
                                isStreaming = !isStreaming
                            }
                        },
                        enabled = if (hasRoute) true else canStream,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            when {
                                isNavigating -> "End Trip"
                                hasRoute -> "Start Navigation"
                                isStreaming -> "Stop Streaming"
                                else -> "Stream to HUD"
                            }
                        )
                    }
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
