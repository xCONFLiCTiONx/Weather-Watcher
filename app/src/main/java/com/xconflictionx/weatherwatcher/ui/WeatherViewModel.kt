package com.xconflictionx.weatherwatcher.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.xconflictionx.weatherwatcher.data.*
import com.xconflictionx.weatherwatcher.util.ConsoleManager
import com.xconflictionx.weatherwatcher.util.NotificationHelper
import com.xconflictionx.weatherwatcher.worker.WeatherAlertWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WeatherRepository(application)
    private val workManager = WorkManager.getInstance(application)
    private val notificationHelper = NotificationHelper(application)
    private val context = application.applicationContext

    private val _location = MutableStateFlow(repository.getDisplayName())
    val location: StateFlow<String> = _location

    private val _appTheme = MutableStateFlow(repository.getTheme())
    val appTheme: StateFlow<String> = _appTheme

    private val _appUnits = MutableStateFlow(repository.getUnits())
    val appUnits: StateFlow<String> = _appUnits

    private val _selectedAlerts = MutableStateFlow(repository.getSelectedAlerts())
    val selectedAlerts: StateFlow<Set<String>> = _selectedAlerts

    private val _activeAlerts = MutableStateFlow(repository.getLastAlerts())
    val activeAlerts: StateFlow<List<WeatherEvent>> = _activeAlerts

    private val _currentWeather = MutableStateFlow(repository.getLastWeather())
    val currentWeather: StateFlow<WeatherValues?> = _currentWeather

    private val _hourlyForecast = MutableStateFlow<List<ForecastPeriod>>(emptyList())
    val hourlyForecast: StateFlow<List<ForecastPeriod>> = _hourlyForecast

    private val _dailyForecast = MutableStateFlow<List<ForecastPeriod>>(emptyList())
    val dailyForecast: StateFlow<List<ForecastPeriod>> = _dailyForecast

    private val _pollenData = MutableStateFlow<PollenData?>(null)
    val pollenData: StateFlow<PollenData?> = _pollenData

    private val _allHazardTypes = MutableStateFlow<List<String>>(emptyList())
    val allHazardTypes: StateFlow<List<String>> = _allHazardTypes

    private val _nextRainTime = MutableStateFlow<String?>(null)
    val nextRainTime: StateFlow<String?> = _nextRainTime

    private val _isCurrentlyRaining = MutableStateFlow(false)
    val isCurrentlyRaining: StateFlow<Boolean> = _isCurrentlyRaining

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _lastSyncStatus = MutableStateFlow("Never synced")
    val lastSyncStatus: StateFlow<String> = _lastSyncStatus

    private val _lastBackgroundSync = MutableStateFlow(repository.getLastBackgroundSyncTime())
    val lastBackgroundSync: StateFlow<Long> = _lastBackgroundSync

    private val _isIgnoringBatteryOptimizations = MutableStateFlow(false)
    val isIgnoringBatteryOptimizations: StateFlow<Boolean> = _isIgnoringBatteryOptimizations

    private val _dailyReportEnabled = MutableStateFlow(repository.isDailyReportEnabled())
    val dailyReportEnabled: StateFlow<Boolean> = _dailyReportEnabled

    private val _dailyReportTime = MutableStateFlow(repository.getDailyReportTime())
    val dailyReportTime: StateFlow<Pair<Int, Int>> = _dailyReportTime

    private val _rainAlertEnabled = MutableStateFlow(repository.isRainAlertEnabled())
    val rainAlertEnabled: StateFlow<Boolean> = _rainAlertEnabled

    private val _trackWeatherEnabled = MutableStateFlow(repository.isTrackWeatherEnabled())
    val trackWeatherEnabled: StateFlow<Boolean> = _trackWeatherEnabled

    private val _regionalSafetyEnabled = MutableStateFlow(repository.isRegionalSafetyEnabled())
    val regionalSafetyEnabled: StateFlow<Boolean> = _regionalSafetyEnabled

    private val _infrastructureAlertsEnabled = MutableStateFlow(repository.isInfrastructureAlertsEnabled())
    val infrastructureAlertsEnabled: StateFlow<Boolean> = _infrastructureAlertsEnabled

    private val _localFeeds = MutableStateFlow<List<ArcgisItem>>(emptyList())
    val localFeeds: StateFlow<List<ArcgisItem>> = _localFeeds

    private val _serviceStatuses = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val serviceStatuses: StateFlow<Map<String, Boolean>> = _serviceStatuses

    val consoleLogs = ConsoleManager.logs

    init {
        refreshWeather()
        startWeatherWork()
        updateBatteryOptimizationStatus()
        startServiceHealer()
        if (repository.isDailyReportEnabled()) {
            com.xconflictionx.weatherwatcher.worker.DailyReportWorker.scheduleNext(context, repository)
        }
    }

    private fun startServiceHealer() {
        viewModelScope.launch {
            while (isActive) {
                // Sleep for 60 seconds between optimal health checks
                delay(60_000)
                
                val currentStatuses = _serviceStatuses.value
                val failedServices = currentStatuses.filter { !it.value }.keys
                
                if (failedServices.isNotEmpty()) {
                    Log.d("WeatherViewModel", "Healer: Attempting to recover ${failedServices.size} failed services...")
                    
                    coroutineScope {
                        failedServices.forEach { serviceName ->
                            launch {
                                try {
                                    val success = when (serviceName) {
                                        "National Weather Service" -> {
                                            val alerts = repository.fetchAlerts()
                                            // Recovery is successful if we get any result (even empty list) without exception
                                            _activeAlerts.value = alerts
                                            true
                                        }
                                        "Open-Meteo (AQI/Sun)" -> {
                                            val weather = repository.fetchCurrentWeather()
                                            if (weather != null) {
                                                _currentWeather.value = weather
                                                true
                                            } else false
                                        }
                                        "ArcGIS Local Alerts" -> {
                                            if (_infrastructureAlertsEnabled.value) {
                                                repository.fetchInfrastructureAlerts()
                                                true
                                            } else true
                                        }
                                        else -> true
                                    }
                                    
                                    if (success) {
                                        val updated = _serviceStatuses.value.toMutableMap()
                                        updated[serviceName] = true
                                        _serviceStatuses.value = updated
                                        Log.d("WeatherViewModel", "Healer: $serviceName recovered successfully!")
                                    }
                                } catch (e: Exception) {
                                    Log.w("WeatherViewModel", "Healer: Recovery failed for $serviceName - ${e.message}")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateBatteryOptimizationStatus() {
        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        _isIgnoringBatteryOptimizations.value = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun refreshWeather() {
        updateBatteryOptimizationStatus()
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            var statusStr = ""
            val statuses = mutableMapOf<String, Boolean>()
            
            try {
                coroutineScope {
                    // Parallel Block: Fetch everything simultaneously
                    val weatherDeferred = async { repository.fetchCurrentWeather() }
                    val nwsAlertsDeferred = async { repository.fetchAlerts() }
                    val localAlertsDeferred = async { 
                        if (_infrastructureAlertsEnabled.value) repository.fetchInfrastructureAlerts() else emptyList() 
                    }
                    val hourlyDeferred = async { repository.fetchForecast() }
                    val dailyDeferred = async { repository.fetchDailyForecast() }
                    val pollenDeferred = async { repository.fetchPollenData() }

                    // Apply a hard cap on patience for the UI refresh
                    withTimeoutOrNull(8000) {
                        // 1. Weather & Open-Meteo Basics
                        val weather = try {
                            val result = weatherDeferred.await()
                            statuses["Open-Meteo (AQI/Sun)"] = result != null
                            result
                        } catch (e: Exception) {
                            statuses["Open-Meteo (AQI/Sun)"] = false
                            null
                        }

                        if (weather != null) {
                            _currentWeather.value = weather
                            repository.saveLastWeather(weather)
                            statusStr += "Weather: OK | "
                        }

                        // 2. Consolidated Alerts
                        val nwsAlerts = try {
                            val result = nwsAlertsDeferred.await()
                            statuses["National Weather Service"] = true
                            result
                        } catch (e: Exception) {
                            statuses["National Weather Service"] = false
                            emptyList()
                        }

                        val localAlerts = try {
                            val result = localAlertsDeferred.await()
                            statuses["ArcGIS Local Alerts"] = true
                            result
                        } catch (e: Exception) {
                            statuses["ArcGIS Local Alerts"] = false
                            emptyList()
                        }

                        val unifiedAlerts = (nwsAlerts + localAlerts).distinctBy { it.title.lowercase().trim() }
                        _activeAlerts.value = unifiedAlerts
                        repository.saveLastAlerts(unifiedAlerts)
                        statusStr += "Alerts: ${if (unifiedAlerts.isEmpty()) "None" else "Active (${unifiedAlerts.size})"} | "

                        // 3. Forecasts
                        val hourly = try {
                            val result = hourlyDeferred.await()
                            // Note: NWS is tracked via alerts mainly, but we can group forecast under it
                            if (statuses["National Weather Service"] != false) {
                                statuses["National Weather Service"] = result != null
                            }
                            result
                        } catch (e: Exception) {
                            statuses["National Weather Service"] = false
                            null
                        }

                        if (hourly != null) {
                            val now = java.time.ZonedDateTime.now()
                            val futureHourly = hourly.filter { 
                                try {
                                    java.time.ZonedDateTime.parse(it.endTime).isAfter(now)
                                } catch (e: Exception) { 
                                    ConsoleManager.logError("WeatherViewModel", "Hourly filter error", e)
                                    true 
                                }
                            }
                            _hourlyForecast.value = futureHourly.take(24)
                            
                            val isRainy = { period: ForecastPeriod ->
                                val prob = period.probabilityOfPrecipitation?.value ?: 0
                                val forecast = period.shortForecast ?: ""
                                prob > 20 && (forecast.contains("Rain", true) || 
                                             forecast.contains("Showers", true) || 
                                             forecast.contains("Thunderstorm", true) ||
                                             forecast.contains("Drizzle", true) ||
                                             forecast.contains("Precipitation", true))
                            }

                            val firstRainPeriod = futureHourly.firstOrNull { isRainy(it) }
                            if (firstRainPeriod != null) {
                                val startTime = java.time.ZonedDateTime.parse(firstRainPeriod.startTime)
                                if (startTime.isBefore(now)) {
                                    _isCurrentlyRaining.value = true
                                    var currentEnd = firstRainPeriod.endTime
                                    val firstIndex = futureHourly.indexOf(firstRainPeriod)
                                    if (firstIndex != -1) {
                                        for (i in firstIndex + 1 until futureHourly.size) {
                                            if (isRainy(futureHourly[i])) currentEnd = futureHourly[i].endTime else break
                                        }
                                    }
                                    _nextRainTime.value = currentEnd
                                } else {
                                    _isCurrentlyRaining.value = false
                                    _nextRainTime.value = firstRainPeriod.startTime
                                }
                            } else {
                                _isCurrentlyRaining.value = false
                                _nextRainTime.value = null
                            }
                            statusStr += "Hourly: OK | "
                        }

                        val daily = try {
                            dailyDeferred.await()
                        } catch (e: Exception) { null }
                        if (daily != null) {
                            _dailyForecast.value = daily
                            statusStr += "7-Day: OK | "
                        }

                        // 4. Pollen (Non-Critical)
                        val pollen = try {
                            pollenDeferred.await()
                        } catch (e: Exception) {
                            null
                        }
                        _pollenData.value = pollen
                        if (pollen != null) statusStr += "Pollen: OK"
                    }
                }

                // Update final statuses
                _serviceStatuses.value = statuses

                // 5. Hazard Types (Background)
                if (_allHazardTypes.value.isEmpty()) {
                    val hazards = repository.fetchAllAlertTypes()
                    _allHazardTypes.value = hazards.filter { !it.equals("Test", ignoreCase = true) }
                }
                
                // 6. Local Feed Discovery (Background)
                if (_infrastructureAlertsEnabled.value && _localFeeds.value.isEmpty()) {
                    _localFeeds.value = repository.discoverLocalFeeds()
                }
                
            } catch (e: Exception) {
                ConsoleManager.logError("WeatherViewModel", "Global weather refresh failed", e)
                _errorMessage.value = "Unable to update all data"
            } finally {
                _lastSyncStatus.value = statusStr.trimEnd(' ', '|')
                _lastBackgroundSync.value = repository.getLastBackgroundSyncTime()
                _isRefreshing.value = false
            }
        }
    }

    fun updateTheme(newTheme: String) {
        _appTheme.value = newTheme
        repository.saveTheme(newTheme)
    }

    fun updateUnits(newUnits: String) {
        _appUnits.value = newUnits
        repository.saveUnits(newUnits)
        refreshWeather()
    }

    fun updateDailyReportEnabled(enabled: Boolean) {
        _dailyReportEnabled.value = enabled
        repository.saveDailyReportEnabled(enabled)
        if (enabled) {
            com.xconflictionx.weatherwatcher.worker.DailyReportWorker.scheduleNext(context, repository)
        } else {
            workManager.cancelUniqueWork("DailyReportWork")
        }
    }

    fun updateDailyReportTime(hour: Int, minute: Int) {
        _dailyReportTime.value = hour to minute
        repository.saveDailyReportTime(hour, minute)
        if (_dailyReportEnabled.value) {
            com.xconflictionx.weatherwatcher.worker.DailyReportWorker.scheduleNext(context, repository)
        }
    }

    fun updateRainAlertEnabled(enabled: Boolean) {
        _rainAlertEnabled.value = enabled
        repository.saveRainAlertEnabled(enabled)
    }

    fun updateTrackWeatherEnabled(enabled: Boolean) {
        _trackWeatherEnabled.value = enabled
        repository.saveTrackWeatherEnabled(enabled)
    }

    fun updateRegionalSafetyEnabled(enabled: Boolean) {
        _regionalSafetyEnabled.value = enabled
        repository.saveRegionalSafetyEnabled(enabled)
    }

    fun updateInfrastructureAlertsEnabled(enabled: Boolean) {
        _infrastructureAlertsEnabled.value = enabled
        repository.saveInfrastructureAlertsEnabled(enabled)
        if (enabled) {
            viewModelScope.launch {
                _localFeeds.value = repository.discoverLocalFeeds()
            }
        }
    }

    fun toggleAlert(category: String) {
        val current = _selectedAlerts.value.toMutableSet()
        val allHazards = _allHazardTypes.value
        
        // If the current set is truly empty, it implies "All Enabled" (Default state).
        // To start custom selection, we must first explicitly enable everything.
        if (current.isEmpty()) {
            current.addAll(allHazards)
        }
        
        // Remove dummy flag if present
        current.remove("__NONE__")

        if (current.contains(category)) {
            current.remove(category)
        } else {
            current.add(category)
        }
        
        // If the resulting set has everything, clear it to return to "All Enabled" default
        if (current.size >= allHazards.size && allHazards.isNotEmpty() && current.containsAll(allHazards)) {
            current.clear()
        } else if (current.isEmpty()) {
            // If user explicitly unchecks the last item, use dummy flag to prevent 
            // the state from jumping back to "All Enabled" (empty set).
            current.add("__NONE__")
        }

        _selectedAlerts.value = current
        repository.saveSelectedAlerts(current)
        refreshWeather()
    }

    fun clearAllAlerts() {
        // Use dummy flag so that fetchAlerts sees a non-empty set that matches nothing.
        val noneSet = setOf("__NONE__")
        _selectedAlerts.value = noneSet
        repository.saveSelectedAlerts(noneSet)
        refreshWeather()
    }

    fun selectAllAlerts() {
        // Return to optimized "Monitor Everything" state
        _selectedAlerts.value = emptySet()
        repository.saveSelectedAlerts(emptySet())
        refreshWeather()
    }

    fun saveSettings(locationInput: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.saveLocation(locationInput)
            _location.value = repository.getDisplayName()
            
            startWeatherWork()
            refreshWeather()
            
            notificationHelper.showNotification(
                "Weather Service Active", 
                "Now monitoring ${repository.getDisplayName()} for hazards and rain."
            )
        }
    }

    fun detectCurrentLocation() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val fusedLocationClient = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
            try {
                val locationResult = fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
                if (locationResult != null) {
                    repository.updateLocationFromCoords(locationResult.latitude, locationResult.longitude)
                    _location.value = repository.getDisplayName()
                    startWeatherWork()
                    refreshWeather()
                    notificationHelper.showNotification(
                        "Weather Service Active", 
                        "Detected location: ${repository.getDisplayName()}. Now monitoring for hazards."
                    )
                } else {
                    _errorMessage.value = "Unable to detect location."
                }
            } catch (e: SecurityException) {
                ConsoleManager.logError("WeatherViewModel", "Location security error", e)
                _errorMessage.value = "Location permission denied."
            } catch (e: Exception) {
                ConsoleManager.logError("WeatherViewModel", "Location detection failed", e)
                _errorMessage.value = "Failed to detect location."
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun startWeatherWork() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val weatherWorkRequest = PeriodicWorkRequestBuilder<WeatherAlertWorker>(
            15, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        // Use UPDATE to ensure the worker starts immediately with new tracking logic
        workManager.enqueueUniquePeriodicWork(
            "WeatherAlertWork",
            ExistingPeriodicWorkPolicy.UPDATE,
            weatherWorkRequest
        )

        // Trigger an immediate one-time run if the background sync is stale or never happened
        val lastSync = repository.getLastBackgroundSyncTime()
        if (System.currentTimeMillis() - lastSync > 15 * 60 * 1000) {
            val oneTimeRequest = androidx.work.OneTimeWorkRequestBuilder<WeatherAlertWorker>()
                .setConstraints(constraints)
                .build()
            workManager.enqueue(oneTimeRequest)
        }
    }

    fun openBatteryOptimizationSettings(context: android.content.Context) {
        try {
            val intent = android.content.Intent().apply {
                action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = android.net.Uri.parse("package:${context.packageName}")
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            ConsoleManager.logError("WeatherViewModel", "Failed to open battery optimization settings", e)
            // Fallback to general settings list if the direct prompt fails
            val intent = android.content.Intent().apply {
                action = android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
