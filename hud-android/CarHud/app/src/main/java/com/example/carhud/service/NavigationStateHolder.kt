package com.example.carhud.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object NavigationStateHolder {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _steps = MutableStateFlow<List<DirectionsProvider.NavStep>>(emptyList())
    private val _currentStepIndex = MutableStateFlow(0)
    private val _totalDistance = MutableStateFlow("")

    private val _currentStep = MutableStateFlow<DirectionsProvider.NavStep?>(null)
    val currentStep: StateFlow<DirectionsProvider.NavStep?> = _currentStep.asStateFlow()

    private val routeRemainingSeconds: StateFlow<Int> =
        combine(_steps, _currentStepIndex) { steps, idx ->
            steps.drop(idx).sumOf { it.durationSeconds }
        }.stateIn(scope, SharingStarted.Eagerly, 0)

    private val secondTick = flow {
        emit(Unit)
        while (true) {
            delay(1000)
            emit(Unit)
        }
    }

    val etaText: StateFlow<String> =
        combine(routeRemainingSeconds, secondTick) { rem, _ ->
            formatEta(rem)
        }.stateIn(scope, SharingStarted.Eagerly, "")

    val totalDistance: StateFlow<String> = _totalDistance.asStateFlow()

    /** Directions API maneuver id for the current step (e.g. `turn-left`), or empty if none. */
    val maneuver: String
        get() = _currentStep.value?.maneuver.orEmpty()

    fun updateStep(step: DirectionsProvider.NavStep?) {
        _currentStep.value = step
    }

    /**
     * Sync active route progress for ETA / total distance. Call when [isNavigating] is true with
     * a loaded [route]; clears when navigation stops.
     */
    fun syncNavigation(
        route: DirectionsProvider.RouteInfo?,
        navSteps: List<DirectionsProvider.NavStep>,
        currentStepIndex: Int,
        isNavigating: Boolean
    ) {
        if (isNavigating && route != null && navSteps.isNotEmpty()) {
            _totalDistance.value = route.distanceText
            _steps.value = navSteps
            _currentStepIndex.value =
                currentStepIndex.coerceIn(0, maxOf(0, navSteps.size - 1))
        } else {
            _totalDistance.value = ""
            _steps.value = emptyList()
            _currentStepIndex.value = 0
        }
    }

    private fun formatEta(remainingSeconds: Int): String {
        if (remainingSeconds <= 0) return ""
        val arrival = Instant.now().plusSeconds(remainingSeconds.toLong())
        val zdt = arrival.atZone(ZoneId.systemDefault())
        val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
        return "ETA ${fmt.format(zdt)}"
    }
}
