package com.xconflictionx.weatherwatcher

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.xconflictionx.weatherwatcher.ui.AlertsScreen
import com.xconflictionx.weatherwatcher.ui.SettingsScreen
import com.xconflictionx.weatherwatcher.ui.WeatherViewModel
import com.xconflictionx.weatherwatcher.ui.theme.WeatherWatcherTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: WeatherViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (locationGranted) {
            // Auto-detect location once permissions are granted for the first time
            viewModel.detectCurrentLocation()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            viewModel = viewModel()
            val themeMode by viewModel.appTheme.collectAsState()
            val location by viewModel.location.collectAsState()

            // Robust check for permissions on launch
            LaunchedEffect(Unit) {
                val permissionsToRequest = mutableListOf<String>()
                
                // Location check
                if (location.isBlank() || location == "New York") {
                    permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }

                // Notification check (API 33+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val status = androidx.core.content.ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                    if (status != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                if (permissionsToRequest.isNotEmpty()) {
                    requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
                }
            }

            WeatherWatcherTheme(themeMode = themeMode) {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "alerts") {
                    composable("alerts") {
                        AlertsScreen(
                            viewModel = viewModel,
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
