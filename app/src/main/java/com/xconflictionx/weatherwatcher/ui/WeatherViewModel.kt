package com.xconflictionx.weatherwatcher.ui

import android.app.Application
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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

    private val _hourlyForecast = MutableStateFlow(repository.getLastHourlyForecast())
    val hourlyForecast: StateFlow<List<ForecastPeriod>> = _hourlyForecast

    private val _dailyForecast = MutableStateFlow(repository.getLastDailyForecast())
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

    private val _locationUpdatesEnabled = MutableStateFlow(repository.isLocationUpdatesEnabled())
    val locationUpdatesEnabled: StateFlow<Boolean> = _locationUpdatesEnabled

    private val _regionalSafetyEnabled = MutableStateFlow(repository.isRegionalSafetyEnabled())
    val regionalSafetyEnabled: StateFlow<Boolean> = _regionalSafetyEnabled

    private val _infrastructureAlertsEnabled = MutableStateFlow(repository.isInfrastructureAlertsEnabled())
    val infrastructureAlertsEnabled: StateFlow<Boolean> = _infrastructureAlertsEnabled

    private val _localFeeds = MutableStateFlow<List<ArcgisItem>>(emptyList())
    val localFeeds: StateFlow<List<ArcgisItem>> = _localFeeds

    fun isUsLocation(): Boolean = repository.isUsLocation()

    private val _serviceStatuses = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val serviceStatuses: StateFlow<Map<String, Boolean>> = _serviceStatuses

    private val _selectedAnimationRes = MutableStateFlow<Int?>(null)
    val selectedAnimationRes: StateFlow<Int?> = _selectedAnimationRes

    val consoleLogs = ConsoleManager.logs

    private val connectivityManager = application.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private var networkChangeJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            networkChangeJob?.cancel()
            networkChangeJob = viewModelScope.launch {
                delay(3000) // Anti-flicker debounce
                Log.d("WeatherViewModel", "Network restored, refreshing status board...")
                refreshServiceStatuses()
                refreshWeather(isManual = false)
            }
        }

        override fun onLost(network: Network) {
            networkChangeJob?.cancel()
            networkChangeJob = viewModelScope.launch {
                delay(3000) // Anti-flicker debounce
                Log.d("WeatherViewModel", "Network lost, updating service statuses...")
                val offlineStatuses = _serviceStatuses.value.toMutableMap()
                offlineStatuses.keys.forEach { offlineStatuses[it] = false }
                _serviceStatuses.value = offlineStatuses.toMap()
            }
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "last_weather" || key == "last_alerts" || key == "last_hourly_forecast" || key == "last_daily_forecast") {
            Log.d("WeatherViewModel", "Cache changed for $key, syncing UI...")
            viewModelScope.launch {
                when (key) {
                    "last_weather" -> {
                        val weather = repository.getLastWeather()
                        _currentWeather.value = weather
                        updateBackgroundAnimation(weather)
                    }
                    "last_alerts" -> _activeAlerts.value = repository.getLastAlerts()
                    "last_hourly_forecast" -> {
                        val hourly = repository.getLastHourlyForecast()
                        _hourlyForecast.value = hourly
                        updateRainStatus(hourly)
                    }
                    "last_daily_forecast" -> _dailyForecast.value = repository.getLastDailyForecast()
                }
            }
        }
    }

    init {
        repository.registerListener(prefListener)
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

        refreshServiceStatuses()
        val initialWeather = repository.getLastWeather()
        updateBackgroundAnimation(initialWeather)
        refreshWeather(isManual = false)
        startWeatherWork()
        updateBatteryOptimizationStatus()
        startServiceHealer()
        if (repository.isDailyReportEnabled()) {
            com.xconflictionx.weatherwatcher.worker.DailyReportWorker.scheduleNext(context, repository)
        }
    }

    private fun updateBackgroundAnimation(weather: WeatherValues?) {
        if (weather == null) return
        val category = getWeatherCategory(weather.condition)
        val pool = getWeatherPool(category, weather.isDay)
        
        if (pool.isNotEmpty()) {
            val currentRes = _selectedAnimationRes.value
            // If the current res is already in the pool, we don't change it 
            // unless the category or time of day changed (handled by re-fetching pool).
            // Actually, the user wants "random on app load" and "change on weather change".
            // So if category is the same, we keep it.
            
            // To detect "category change", we'll check if the pool contains the current res.
            // If it doesn't, or if we have no res yet, we pick a new one.
            if (currentRes == null || !pool.contains(currentRes)) {
                val newRes = pool[kotlin.random.Random.nextInt(pool.size)]
                Log.d("WeatherViewModel", "Updating animation: Category=$category, IsDay=${weather.isDay}, Res=$newRes")
                _selectedAnimationRes.value = newRes
            }
        }
    }

    private fun updateRainStatus(hourly: List<ForecastPeriod>) {
        val now = java.time.ZonedDateTime.now()
        val futureHourly = hourly.filter { 
            try {
                java.time.ZonedDateTime.parse(it.endTime).isAfter(now)
            } catch (e: Exception) { true }
        }
        
        val isRainy = { period: ForecastPeriod ->
            val prob = period.probabilityOfPrecipitation?.value ?: 0
            val forecast = period.shortForecast ?: ""
            prob > 20 && (forecast.contains("Rain", true) || 
                         forecast.contains("Showers", true) || 
                         forecast.contains("Thunderstorm", true) ||
                         forecast.contains("Drizzle", true) ||
                         forecast.contains("Precipitation", true) ||
                         forecast.contains("Sleet", true) ||
                         forecast.contains("Snow", true))
        }

        val nextRain = futureHourly.firstOrNull { isRainy(it) }
        if (nextRain != null) {
            val startTime = java.time.ZonedDateTime.parse(nextRain.startTime)
            val prob = nextRain.probabilityOfPrecipitation?.value ?: 0
            if (startTime.isBefore(now)) {
                _isCurrentlyRaining.value = true
                _nextRainTime.value = "${nextRain.endTime}|$prob"
            } else {
                _isCurrentlyRaining.value = false
                _nextRainTime.value = "${nextRain.startTime}|$prob"
            }
        } else {
            _isCurrentlyRaining.value = false
            _nextRainTime.value = null
        }
    }

    private fun refreshServiceStatuses() {
        viewModelScope.launch {
            val statuses = _serviceStatuses.value.toMutableMap()
            coroutineScope {
                launch { statuses["NWS Forecasts"] = repository.checkServiceHealth("NWS") }
                launch { statuses["NWS Severe Alerts"] = repository.checkServiceHealth("NWS") }
                launch { statuses["Regional Safety"] = repository.checkServiceHealth("NWS") }
                launch { statuses["Open-Meteo (AQI/Sun)"] = repository.checkServiceHealth("OpenMeteo") }
                launch { statuses["Community Infrastructure"] = repository.checkServiceHealth("ArcGIS") }
            }
            _serviceStatuses.value = statuses.toMap()
        }
    }

    private fun startServiceHealer() {
        viewModelScope.launch {
            while (isActive) {
                // Sleep for 60 seconds between health pings
                delay(60_000)
                refreshServiceStatuses()
            }
        }
    }

    fun updateBatteryOptimizationStatus() {
        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        _isIgnoringBatteryOptimizations.value = powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun refreshWeather(isManual: Boolean = false) {
        updateBatteryOptimizationStatus()
        viewModelScope.launch {
            if (isManual) {
                _isRefreshing.value = true
                refreshServiceStatuses() // Ping everyone immediately if user clicks refresh
            }
            _errorMessage.value = null
            
            val statuses = _serviceStatuses.value.toMutableMap()
            
            coroutineScope {
                // 1. Current Weather & Sun/AQI (Open-Meteo)
                launch {
                    try {
                        val weather = repository.fetchCurrentWeather()
                        if (weather != null) {
                            _currentWeather.value = weather
                            repository.saveLastWeather(weather)
                            updateBackgroundAnimation(weather)
                            statuses["Open-Meteo (AQI/Sun)"] = true
                        }
                    } catch (e: Exception) {
                        Log.e("WeatherViewModel", "Open-Meteo fetch failed: ${e.message}")
                    }
                    _serviceStatuses.value = statuses.toMap()
                }

                // 2. Comprehensive Alerts Lane (NWS + Local)
                launch {
                    val finalAlerts = mutableListOf<WeatherEvent>()
                    var nwsSuccess = false
                    
                    try {
                        val nws = repository.fetchAlerts()
                        if (nws != null) {
                            finalAlerts.addAll(nws)
                            nwsSuccess = true
                        }
                    } catch (e: Exception) {
                        Log.e("WeatherViewModel", "NWS Alerts fetch failed: ${e.message}")
                    }

                    if (_infrastructureAlertsEnabled.value) {
                        try {
                            val local = repository.fetchInfrastructureAlerts()
                            if (local != null) {
                                finalAlerts.addAll(local)
                                statuses["Community Infrastructure"] = true
                            }
                        } catch (e: Exception) {
                            Log.e("WeatherViewModel", "ArcGIS fetch failed: ${e.message}")
                        }
                    }

                    if (nwsSuccess) {
                        statuses["NWS Severe Alerts"] = true
                        statuses["Regional Safety"] = true
                        
                        val cleanList = finalAlerts
                            .groupBy { it.title.lowercase().trim() }
                            .map { (_, group) ->
                                group.maxByOrNull { it.description?.length ?: 0 }!!
                            }
                            .sortedBy { it.severity }
                        
                        _activeAlerts.value = cleanList
                        repository.saveLastAlerts(cleanList)
                    }
                    _serviceStatuses.value = statuses.toMap()
                }

                // 4. Forecasts (Hourly) & Rain Logic
                launch {
                    try {
                        val hourly = repository.fetchForecast()
                        if (hourly != null) {
                            statuses["NWS Forecasts"] = true
                            val now = java.time.ZonedDateTime.now()
                            val futureHourly = hourly.filter { 
                                try {
                                    java.time.ZonedDateTime.parse(it.endTime).isAfter(now)
                                } catch (e: Exception) { true }
                            }
                            val top24 = futureHourly.take(24)
                            _hourlyForecast.value = top24
                            repository.saveLastHourlyForecast(top24)
                            updateRainStatus(top24)
                        }
                    } catch (e: Exception) {
                        Log.e("WeatherViewModel", "NWS Forecast fetch failed: ${e.message}")
                    }
                    _serviceStatuses.value = statuses.toMap()
                }

                // 5. Daily Forecast
                launch {
                    try {
                        val daily = repository.fetchDailyForecast()
                        if (daily != null) {
                            _dailyForecast.value = daily
                            repository.saveLastDailyForecast(daily)
                        }
                    } catch (e: Exception) {
                        Log.w("WeatherViewModel", "Daily forecast failed: ${e.message}")
                    }
                }

                // 6. Pollen (Non-Critical)
                launch {
                    try {
                        val pollen = repository.fetchPollenData()
                        _pollenData.value = pollen
                    } catch (e: Exception) {
                        Log.w("WeatherViewModel", "Pollen fetch failed (silently): ${e.message}")
                    }
                }
                
                // 7. Discovery (Background)
                launch {
                    if (_infrastructureAlertsEnabled.value && _localFeeds.value.isEmpty()) {
                        _localFeeds.value = repository.discoverLocalFeeds()
                    }
                }
            }
            
            // Background cleanup tasks
            if (_allHazardTypes.value.isEmpty()) {
                val hazards = repository.fetchAllAlertTypes()
                _allHazardTypes.value = hazards.filter { !it.equals("Test", ignoreCase = true) }
            }
            
            _lastSyncStatus.value = "Last Sync: " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
            _lastBackgroundSync.value = repository.getLastBackgroundSyncTime()
            _isRefreshing.value = false
        }
    }

    fun updateTheme(newTheme: String) {
        _appTheme.value = newTheme
        repository.saveTheme(newTheme)
    }

    fun updateUnits(newUnits: String) {
        _appUnits.value = newUnits
        repository.saveUnits(newUnits)
        refreshWeather(isManual = false)
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

    fun updateLocationUpdatesEnabled(enabled: Boolean) {
        _locationUpdatesEnabled.value = enabled
        repository.saveLocationUpdatesEnabled(enabled)
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
        refreshWeather(isManual = false)
    }

    fun clearAllAlerts() {
        // Use dummy flag so that fetchAlerts sees a non-empty set that matches nothing.
        val noneSet = setOf("__NONE__")
        _selectedAlerts.value = noneSet
        repository.saveSelectedAlerts(noneSet)
        refreshWeather(isManual = false)
    }

    fun selectAllAlerts() {
        // Return to optimized "Monitor Everything" state
        _selectedAlerts.value = emptySet()
        repository.saveSelectedAlerts(emptySet())
        refreshWeather(isManual = false)
    }

    fun saveSettings(locationInput: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.saveLocation(locationInput)
            _location.value = repository.getDisplayName()
            
            startWeatherWork()
            refreshWeather(isManual = false)
            
            notificationHelper.showNotification(
                title = "Weather Service Active", 
                message = "Now monitoring ${repository.getDisplayName()} for hazards and rain.",
                channelId = NotificationHelper.CHANNEL_GENERAL_ID
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
                    refreshWeather(isManual = false)
                    notificationHelper.showNotification(
                        title = "Weather Service Active", 
                        message = "Detected location: ${repository.getDisplayName()}. Now monitoring for hazards.",
                        channelId = NotificationHelper.CHANNEL_GENERAL_ID
                    )
                } else {
                    _errorMessage.value = "Unable to detect location."
                }
            } catch (e: SecurityException) {
                // Log to system log only
                Log.w("WeatherViewModel", "Location permission denied: ${e.message}")
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

    override fun onCleared() {
        super.onCleared()
        repository.unregisterListener(prefListener)
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}
