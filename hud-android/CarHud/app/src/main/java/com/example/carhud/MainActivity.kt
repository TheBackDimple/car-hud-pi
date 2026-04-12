package com.example.carhud

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.example.carhud.BuildConfig
import com.google.android.libraries.places.api.Places
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.carhud.navigation.NavRoutes
import com.example.carhud.ui.screens.HomeScreen
import com.example.carhud.ui.screens.MapScreen
import com.example.carhud.ui.screens.ObdSettingsScreen
import com.example.carhud.ui.screens.FeatureToggleScreen
import com.example.carhud.ui.screens.PresetEditorScreen
import com.example.carhud.ui.screens.SettingsScreen
import com.example.carhud.ui.theme.CarHudTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!Places.isInitialized() && BuildConfig.MAPS_API_KEY.isNotBlank()) {
            Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        }
        setContent {
            CarHudTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = NavRoutes.HOME,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(NavRoutes.HOME) {
                            HomeScreen(
                                onNavigateToPresets = {
                                    navController.navigate(NavRoutes.PRESETS)
                                },
                                onNavigateToFeatureToggles = {
                                    navController.navigate(NavRoutes.FEATURE_TOGGLES)
                                },
                                onNavigateToMap = {
                                    navController.navigate(NavRoutes.MAP)
                                },
                                onNavigateToObd = {
                                    navController.navigate(NavRoutes.OBD)
                                },
                                onNavigateToSettings = {
                                    navController.navigate(NavRoutes.SETTINGS)
                                }
                            )
                        }
                        composable(NavRoutes.PRESETS) {
                            PresetEditorScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(NavRoutes.FEATURE_TOGGLES) {
                            FeatureToggleScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(NavRoutes.MAP) {
                            MapScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(NavRoutes.OBD) {
                            ObdSettingsScreen(onNavigateBack = { navController.popBackStack() })
                        }
                        composable(NavRoutes.SETTINGS) {
                            SettingsScreen(onNavigateBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
