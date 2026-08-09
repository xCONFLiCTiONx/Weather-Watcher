package com.xconflictionx.weatherwatcher.data

import android.content.Context
import android.content.SharedPreferences
import android.location.Geocoder
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.xconflictionx.weatherwatcher.util.ConsoleManager
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

class WeatherRepository(private val context: Context) {

    private val sharedPrefs: SharedPreferences by lazy {
        context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)
    }

    private val json = Json { ignoreUnknownKeys = true }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    
    private val nwsApiService: NwsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(NwsApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(NwsApiService::class.java)
    }

    private val openMeteoApiService: OpenMeteoApiService by lazy {
        Retrofit.Builder()
            .baseUrl(OpenMeteoApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenMeteoApiService::class.java)
    }

    private val arcgisApiService: ArcgisApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ArcgisApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ArcgisApiService::class.java)
    }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private suspend fun <T> retryIO(
        times: Int = 2,
        initialDelay: Long = 2000,
        tag: String,
        message: String,
        block: suspend () -> T
    ): T? {
        repeat(times - 1) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                Log.w(tag, "$message (Attempt ${attempt + 1} failed, retrying in ${initialDelay}ms): ${e.message}")
            }
            delay(initialDelay)
        }
        return try {
            block()
        } catch (e: Exception) {
            // No longer silencing timeouts. Report everything to Console for debugging.
            ConsoleManager.logError(tag, message, e)
            null
        }
    }

    suspend fun saveLocation(input: String) = withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocationName(input, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val lat = String.format(Locale.US, "%.4f", address.latitude)
                val lon = String.format(Locale.US, "%.4f", address.longitude)
                val name = address.locality ?: address.subAdminArea ?: address.adminArea ?: input
                val fullName = if (address.adminArea != null) "$name, ${address.adminArea}" else name

                sharedPrefs.edit()
                    .putString("location_input", input)
                    .putString("latitude", lat)
                    .putString("longitude", lon)
                    .putString("display_name", fullName)
                    .remove("nws_forecast_url")
                    .remove("nws_daily_url")
                    .remove("nws_obs_url")
                    .apply()
            } else {
                sharedPrefs.edit()
                    .putString("location_input", input)
                    .putString("display_name", input)
                    .apply()
            }
        } catch (e: Exception) {
            ConsoleManager.logError("WeatherRepository", "Failed to save location", e)
            sharedPrefs.edit()
                .putString("location_input", input)
                .putString("display_name", input)
                .apply()
        }
    }

    fun getLocationInput(): String {
        return sharedPrefs.getString("location_input", "New York") ?: "New York"
    }

    fun getDisplayName(): String {
        return sharedPrefs.getString("display_name", "New York") ?: "New York"
    }

    fun getCoordinates(): String? {
        val lat = sharedPrefs.getString("latitude", null)
        val lon = sharedPrefs.getString("longitude", null)
        return if (lat != null && lon != null) "$lat,$lon" else null
    }

    fun isUsLocation(): Boolean {
        val coords = getCoordinates() ?: return false
        val parts = coords.split(",").mapNotNull { it.toDoubleOrNull() }
        if (parts.size != 2) return false
        val lat = parts[0]
        val lon = parts[1]
        // Approx bounding box for USA (Conterminous + AK + HI)
        return (lat in 18.0..72.0) && (lon in -170.0..-66.0)
    }

    suspend fun resolveCityName(input: String): String = withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocationName(input, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val name = address.locality ?: address.subAdminArea ?: address.adminArea ?: input
                if (address.adminArea != null) "$name, ${address.adminArea}" else name
            } else input
        } catch (e: Exception) {
            ConsoleManager.logError("WeatherRepository", "Failed to resolve city name", e)
            input
        }
    }

    suspend fun updateLocationFromCoords(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        val geocoder = Geocoder(context, Locale.getDefault())
        val latStr = String.format(Locale.US, "%.4f", lat)
        val lonStr = String.format(Locale.US, "%.4f", lon)
        
        var fullName = "$latStr, $lonStr"
        var stateName: String? = null
        try {
            val addresses = geocoder.getFromLocation(lat, lon, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val name = address.locality ?: address.subAdminArea ?: address.adminArea ?: "Selected Point"
                fullName = if (address.adminArea != null) "$name, ${address.adminArea}" else name
                stateName = address.adminArea
            }
        } catch (e: Exception) {
            ConsoleManager.logError("WeatherRepository", "Failed to update location from coords", e)
        }

        sharedPrefs.edit()
            .putString("location_input", fullName)
            .putString("latitude", latStr)
            .putString("longitude", lonStr)
            .putString("display_name", fullName)
            .putString("state_name", stateName)
            .remove("nws_forecast_url")
            .remove("nws_daily_url")
            .remove("nws_obs_url")
            .apply()
        
        fullName
    }

    suspend fun refreshLocationFromGps(): String? = withContext(Dispatchers.IO) {
        val currentCoords = getCurrentLocationCoords() ?: return@withContext null
        val parts = currentCoords.split(",").mapNotNull { it.toDoubleOrNull() }
        if (parts.size != 2) return@withContext null

        val newLat = parts[0]
        val newLon = parts[1]

        val oldLat = sharedPrefs.getString("latitude", "0.0")?.toDoubleOrNull() ?: 0.0
        val oldLon = sharedPrefs.getString("longitude", "0.0")?.toDoubleOrNull() ?: 0.0

        // Calculate distance (very rough, approx 0.05 degrees is ~3-5 miles)
        val threshold = 0.05
        val moved = Math.abs(newLat - oldLat) > threshold || Math.abs(newLon - oldLon) > threshold

        if (moved) {
            val newName = updateLocationFromCoords(newLat, newLon)
            Log.d("WeatherRepository", "Location moved significantly. Now at: $newName")
            return@withContext newName
        }
        null
    }

    suspend fun getCurrentLocationCoords(): String? = withContext(Dispatchers.IO) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        try {
            // Priority 1: Recent cached location (within 10 minutes)
            val lastLocation = fusedLocationClient.lastLocation.await()
            if (lastLocation != null && (System.currentTimeMillis() - lastLocation.time) < 600_000) {
                return@withContext "${lastLocation.latitude},${lastLocation.longitude}"
            }

            // Priority 2: Force fresh fetch with high accuracy
            withTimeoutOrNull(15_000) {
                val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                if (location != null) {
                    "${location.latitude},${location.longitude}"
                } else null
            }
        } catch (e: SecurityException) {
            // Log to system log only, don't flood user console with expected permission issues
            Log.w("WeatherRepository", "GPS permission not yet granted: ${e.message}")
            null
        } catch (e: Exception) {
            ConsoleManager.logError("WeatherRepository", "Failed to get current GPS coords", e)
            null
        }
    }

    private suspend fun getGridMetadata(): NwsPointProperties? {
        val cachedForecast = sharedPrefs.getString("nws_forecast_url", null)
        val cachedDaily = sharedPrefs.getString("nws_daily_url", null)
        val cachedObs = sharedPrefs.getString("nws_obs_url", null)
        
        if (cachedForecast != null && cachedObs != null && cachedDaily != null) {
            return NwsPointProperties(cachedForecast, cachedObs, cachedDaily)
        }

        val coords = getCoordinates() ?: return null
        val parts = coords.split(",")
        return retryIO(tag = "WeatherRepository", message = "Failed to fetch grid metadata") {
            val response = nwsApiService.getPointData(parts[0], parts[1])
            sharedPrefs.edit()
                .putString("nws_forecast_url", response.properties.forecastHourly)
                .putString("nws_daily_url", response.properties.forecast)
                .putString("nws_obs_url", response.properties.observationStations)
                .apply()
            response.properties
        }
    }

    fun saveSelectedAlerts(alerts: Set<String>) {
        sharedPrefs.edit().putStringSet("selected_alerts", alerts).apply()
    }

    fun getSelectedAlerts(): Set<String> {
        return sharedPrefs.getStringSet("selected_alerts", null) ?: emptySet() 
    }

    fun saveTheme(theme: String) {
        sharedPrefs.edit().putString("app_theme", theme).apply()
    }

    fun getTheme(): String {
        return sharedPrefs.getString("app_theme", "system") ?: "system"
    }

    fun saveUnits(units: String) {
        sharedPrefs.edit().putString("app_units", units).apply()
    }

    fun getUnits(): String {
        return sharedPrefs.getString("app_units", "imperial") ?: "imperial"
    }

    fun saveDailyReportEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("daily_report_enabled", enabled).apply()
    }

    fun isDailyReportEnabled(): Boolean {
        return sharedPrefs.getBoolean("daily_report_enabled", true)
    }

    fun saveRainAlertEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("rain_alerts_enabled", enabled).apply()
    }

    fun isRainAlertEnabled(): Boolean {
        return sharedPrefs.getBoolean("rain_alerts_enabled", true)
    }

    fun saveTrackWeatherEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("track_weather_enabled", enabled).apply()
    }

    fun isTrackWeatherEnabled(): Boolean {
        return sharedPrefs.getBoolean("track_weather_enabled", false)
    }

    fun saveRegionalSafetyEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("regional_safety_enabled", enabled).apply()
    }

    fun isRegionalSafetyEnabled(): Boolean {
        return sharedPrefs.getBoolean("regional_safety_enabled", true)
    }

    fun saveInfrastructureAlertsEnabled(enabled: Boolean) {
        sharedPrefs.edit().putBoolean("infrastructure_alerts_enabled", enabled).apply()
    }

    fun isInfrastructureAlertsEnabled(): Boolean {
        return sharedPrefs.getBoolean("infrastructure_alerts_enabled", true)
    }

    fun saveDailyReportTime(hour: Int, minute: Int) {
        val totalMinutes = hour * 60 + minute
        sharedPrefs.edit().putInt("daily_report_time", totalMinutes).apply()
    }

    fun getDailyReportTime(): Pair<Int, Int> {
        val totalMinutes = sharedPrefs.getInt("daily_report_time", 480)
        return (totalMinutes / 60) to (totalMinutes % 60)
    }

    suspend fun fetchAllAlertTypes(): List<String> {
        return retryIO(tag = "WeatherRepository", message = "Failed to fetch active alerts from NWS") {
            val response = nwsApiService.getAlertTypes()
            response.eventTypes
        } ?: emptyList()
    }

    fun saveLastAlerts(alerts: List<WeatherEvent>) {
        if (alerts.isNotEmpty()) {
            val jsonString = json.encodeToString(alerts)
            sharedPrefs.edit().putString("last_alerts", jsonString).apply()
        }
    }

    fun getLastAlerts(): List<WeatherEvent> {
        val jsonString = sharedPrefs.getString("last_alerts", null) ?: return emptyList()
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLastWeather(weather: WeatherValues?) {
        if (weather != null) {
            val jsonString = json.encodeToString(weather)
            sharedPrefs.edit().putString("last_weather", jsonString).apply()
        }
    }

    fun saveLastHourlyForecast(periods: List<ForecastPeriod>) {
        if (periods.isNotEmpty()) {
            val jsonString = json.encodeToString(periods)
            sharedPrefs.edit().putString("last_hourly_forecast", jsonString).apply()
        }
    }

    fun getLastHourlyForecast(): List<ForecastPeriod> {
        val jsonString = sharedPrefs.getString("last_hourly_forecast", null) ?: return emptyList()
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLastDailyForecast(periods: List<ForecastPeriod>) {
        if (periods.isNotEmpty()) {
            val jsonString = json.encodeToString(periods)
            sharedPrefs.edit().putString("last_daily_forecast", jsonString).apply()
        }
    }

    fun getLastDailyForecast(): List<ForecastPeriod> {
        val jsonString = sharedPrefs.getString("last_daily_forecast", null) ?: return emptyList()
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveLastBackgroundSyncTime(time: Long) {
        sharedPrefs.edit().putLong("last_background_sync_time", time).apply()
    }

    fun getLastBackgroundSyncTime(): Long {
        return sharedPrefs.getLong("last_background_sync_time", 0L)
    }

    fun shouldNotifyForNwsAlert(event: WeatherEvent): Boolean {
        val key = "notified_nws_${event.id}"
        val currentHash = event.hash
        val savedHash = sharedPrefs.getInt(key, 0)
        
        if (savedHash != currentHash) {
            sharedPrefs.edit().putInt(key, currentHash).apply()
            return true
        }
        return false
    }

    fun shouldNotifyForRainPeriod(startTime: String): Boolean {
        val now = System.currentTimeMillis()
        val lastRainStop = sharedPrefs.getLong("last_rain_stop_time", 0L)
        
        // Rule: At least 3 hours must pass since the last rain stopped
        val threeHoursMillis = 3 * 60 * 60 * 1000L
        val isCooldownOver = (now - lastRainStop) > threeHoursMillis

        val key = "notified_rain_at_$startTime"
        val notifiedForThisPeriod = sharedPrefs.getBoolean(key, false)

        if (!notifiedForThisPeriod && isCooldownOver) {
            sharedPrefs.edit().putBoolean(key, true).apply()
            return true
        }
        return false
    }

    fun saveLastRainStopTime(timeMillis: Long) {
        sharedPrefs.edit().putLong("last_rain_stop_time", timeMillis).apply()
    }

    fun getLastWeather(): WeatherValues? {
        val jsonString = sharedPrefs.getString("last_weather", null) ?: return null
        return try {
            json.decodeFromString(jsonString)
        } catch (e: Exception) {
            ConsoleManager.logError("WeatherRepository", "Failed to decode last weather", e)
            null
        }
    }

    suspend fun fetchAlerts(): List<WeatherEvent> {
        val coords = getCoordinates() ?: return emptyList()
        val selected = getSelectedAlerts()
        val regionalSafetyEnabled = isRegionalSafetyEnabled()
        
        return retryIO(tag = "WeatherRepository", message = "Failed to fetch active alerts from NWS") {
            val response = nwsApiService.getActiveAlerts(coords)
            response.features
                .filter { feature ->
                    val event = feature.properties.event ?: return@filter false
                    
                    // 1. Check if it matches user's specific hazard selection
                    val isSelected = selected.isEmpty() || selected.contains(event)
                    if (!isSelected) return@filter false
                    
                    // 2. Check Regional Safety filter for Non-Weather Emergency Messages (NWEM)
                    if (!regionalSafetyEnabled) {
                        val isRegionalSafety = event.contains("Danger", true) ||
                                              event.contains("Emergency", true) ||
                                              event.contains("Immediate", true) ||
                                              event.contains("Amber", true) ||
                                              event.contains("Civil", true) ||
                                              event.contains("Evacuation", true) ||
                                              event.contains("Law Enforcement", true) ||
                                              event.contains("911", true)
                        if (isRegionalSafety) return@filter false
                    }
                    
                    true
                }
                .map { feature ->
                    val id = feature.properties.id ?: feature.properties.event ?: "alert_${System.currentTimeMillis()}"
                    WeatherEvent(
                        id = id,
                        title = feature.properties.event ?: "Alert",
                        severity = feature.properties.severity ?: "Unknown",
                        description = feature.properties.description,
                        instruction = feature.properties.instruction,
                        hash = feature.properties.hashCode()
                    )
                }
        } ?: emptyList()
    }

    suspend fun fetchCurrentWeather(): WeatherValues? = coroutineScope {
        val meta = getGridMetadata() ?: return@coroutineScope null
        val coords = getCoordinates() ?: return@coroutineScope null
        val parts = coords.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        if (parts.size != 2) return@coroutineScope null

        // Parallel internal fetches with retry
        val obsDeferred = async {
            retryIO(tag = "WeatherRepository", message = "Failed to fetch NWS observation") {
                val stationsResponse = nwsApiService.getStations(meta.observationStations)
                val firstStationUrl = stationsResponse.features.firstOrNull()?.id ?: return@retryIO null
                nwsApiService.getLatestObservations("$firstStationUrl/observations/latest")
            }
        }
        
        val sunDeferred = async {
            retryIO(tag = "WeatherRepository", message = "Failed to fetch sun times") {
                openMeteoApiService.getSunTimes(parts[0], parts[1])
            }
        }
        
        val aqiDeferred = async {
            retryIO(tag = "WeatherRepository", message = "Failed to fetch AQI") {
                openMeteoApiService.getAqi(parts[0], parts[1])
            }
        }

        val obsResponse = obsDeferred.await() ?: return@coroutineScope null
        val sunResponse = sunDeferred.await()
        val aqiResponse = aqiDeferred.await()

        // 1. Temperature & Basic Obs
        val tempC = obsResponse.properties.temperature?.value ?: return@coroutineScope null
        val isImperial = getUnits() == "imperial"
        val temp = if (isImperial) (tempC * 9/5) + 32 else tempC
        val lastUpdated = java.time.ZonedDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
        
        // 2. Sun Times & Rain Probability
        var sunrise: String? = null
        var sunset: String? = null
        var rainProb: Int? = null
        sunResponse?.daily?.let { daily ->
            sunrise = daily.sunrise.firstOrNull()?.let { 
                java.time.ZonedDateTime.parse(it + "Z").format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
            }
            sunset = daily.sunset.firstOrNull()?.let { 
                java.time.ZonedDateTime.parse(it + "Z").format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
            }
        }
        sunResponse?.hourly?.let { hourly ->
            rainProb = hourly.precipitationProbability.firstOrNull()
        }

        // 3. AQI Processing
        var aqiValue: Int? = null
        val aqiForecast = mutableListOf<AqiDayInfo>()
        aqiResponse?.hourly?.let { hourlyData ->
            aqiValue = hourlyData.aqi.firstOrNull()
            val times = hourlyData.time
            val aqiList = hourlyData.aqi
            val pm25List = hourlyData.pm2_5
            val pm10List = hourlyData.pm10
            val ozoneList = hourlyData.ozone
            val no2List = hourlyData.no2
            val coList = hourlyData.co
            val so2List = hourlyData.so2
            
            val dailyDataMap = mutableMapOf<String, AqiDayInfo>()
            for (i in times.indices) {
                val date = times[i].split("T")[0]
                val currentAqi = aqiList.getOrNull(i) ?: 0
                val existing = dailyDataMap[date]
                if (existing == null || currentAqi > existing.maxAqi) {
                    dailyDataMap[date] = AqiDayInfo(
                        date = date,
                        maxAqi = currentAqi,
                        label = getAqiLabel(currentAqi),
                        pm2_5 = pm25List.getOrNull(i),
                        pm10 = pm10List.getOrNull(i),
                        ozone = ozoneList.getOrNull(i),
                        no2 = no2List.getOrNull(i),
                        co = coList.getOrNull(i),
                        so2 = so2List.getOrNull(i)
                    )
                }
            }
            aqiForecast.addAll(dailyDataMap.values.filter { it.maxAqi > 0 }.sortedBy { it.date })
        }

        // 4. Day/Night Calculation (Fix: Using LocalTime to avoid UTC offset bugs)
        val now = java.time.LocalDateTime.now()
        val isDayResult = sunResponse?.daily?.let { daily ->
            val sr = daily.sunrise.firstOrNull()?.let { java.time.LocalDateTime.parse(it) }
            val ss = daily.sunset.firstOrNull()?.let { java.time.LocalDateTime.parse(it) }
            if (sr != null && ss != null) {
                now.isAfter(sr) && now.isBefore(ss)
            } else true
        } ?: true

        WeatherValues(
            temperature = temp,
            condition = obsResponse.properties.textDescription ?: "Unknown",
            icon = obsResponse.properties.textDescription,
            lastUpdated = lastUpdated,
            humidity = obsResponse.properties.relativeHumidity?.value,
            windSpeed = obsResponse.properties.windSpeed?.value,
            rainProbability = rainProb,
            sunrise = sunrise,
            sunset = sunset,
            isDay = isDayResult,
            aqi = aqiValue,
            aqiForecast = aqiForecast.sortedBy { it.date }
        )
    }

    suspend fun fetchForecast(): List<ForecastPeriod>? = withContext(Dispatchers.IO) {
        val meta = getGridMetadata() ?: return@withContext null
        retryIO(tag = "WeatherRepository", message = "Forecast fetch timed out or failed") {
            // Strict 4s timeout for rain/hourly data to prevent dashboard hanging
            withTimeout(4000) {
                val response = nwsApiService.getHourlyForecast(meta.forecastHourly)
                response.properties.periods
            }
        }
    }

    suspend fun fetchDailyForecast(): List<ForecastPeriod>? = withContext(Dispatchers.IO) {
        val meta = getGridMetadata() ?: return@withContext null
        retryIO(tag = "WeatherRepository", message = "Daily forecast fetch timed out or failed") {
            // Strict 4s timeout for 7-day outlook
            withTimeout(4000) {
                val response = nwsApiService.getDailyForecast(meta.forecast)
                response.properties.periods
            }
        }
    }

    suspend fun fetchPollenData(): PollenData? {
        val coords = getCoordinates() ?: return null
        val parts = coords.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        if (parts.size != 2) return null
        
        // Open-Meteo Pollen is currently Europe-only. 
        // We skip for US locations to avoid HTTP 400 Bad Request.
        val lat = parts[0]
        val lon = parts[1]
        
        val isUsLocation = (lat in 24.0..50.0) && (lon in -125.0..-66.0)
        val state = sharedPrefs.getString("state_name", "") ?: ""
        
        if (isUsLocation || state.isNotBlank()) {
            Log.d("WeatherRepository", "Skipping Pollen fetch (Europe-only source detected by region)")
            return null
        }

        return try {
            val response = openMeteoApiService.getPollen(
                lat = lat, 
                lon = lon
            )
            val daily = response.daily ?: return null
            
            fun getLevel(value: Float?): String {
                if (value == null) return "N/A"
                return when {
                    value < 1f -> "None"
                    value < 10f -> "Low"
                    value < 50f -> "Moderate"
                    value < 100f -> "High"
                    else -> "Very High"
                }
            }

            val dailyInfo = daily.time.indices.map { i ->
                PollenDayInfo(
                    date = daily.time[i],
                    grassLevel = getLevel(daily.grass.getOrNull(i)),
                    ragweedLevel = getLevel(daily.ragweed.getOrNull(i)),
                    birchLevel = getLevel(daily.birch.getOrNull(i)),
                    alderLevel = getLevel(daily.alder.getOrNull(i)),
                    mugwortLevel = getLevel(daily.mugwort.getOrNull(i)),
                    oliveLevel = getLevel(daily.olive.getOrNull(i))
                )
            }

            PollenData(
                dailyPollen = dailyInfo,
                isAvailable = true
            )
        } catch (e: Exception) {
            if (e is retrofit2.HttpException && e.code() == 400) {
                // Known issue: Open-Meteo Pollen is Europe-only. 
                // We log as a warning to Logcat but SILENCE it from the in-app ConsoleManager.
                Log.w("WeatherRepository", "Pollen fetch skipped: Service does not support this region.")
            } else {
                ConsoleManager.logError("WeatherRepository", "Failed to fetch pollen data", e)
            }
            null
        }
    }

    suspend fun discoverLocalFeeds(): List<ArcgisItem> = withContext(Dispatchers.IO) {
        val coords = getCoordinates() ?: return@withContext emptyList()
        val parts = coords.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        if (parts.size != 2) return@withContext emptyList()

        val lat = parts[0]
        val lon = parts[1]
        // Search radius: +/- 0.4 degrees (~30 miles)
        val bbox = "${lon - 0.4},${lat - 0.4},${lon + 0.4},${lat + 0.4}"
        
        // Fix: Add explicit logging to verify coordinate order (lat vs lon)
        Log.d("WeatherRepository", "ArcGIS Search - Parsed Lat: $lat, Lon: $lon, Bbox: $bbox")
        
        val state = sharedPrefs.getString("state_name", "") ?: ""
        val stateQuery = if (state.isNotBlank()) " AND $state" else ""
        
        try {
            // Search query locked to user's state and general tags
            val query = "tags:(\"Emergency Alerts\" OR \"Public Information\" OR \"Road Closures\" OR \"Utilities\") AND type:\"Feature Service\"$stateQuery"
            
            // Log query for troubleshooting
            Log.d("WeatherRepository", "ArcGIS Portal Query: $query")
            
            val response = arcgisApiService.searchPortal(query = query, bbox = bbox)
            
            // Fix: Filter for trusted domains, relevant titles (Arkansas/AR), and Global Noise
            val filteredResults = response.results.filter { item ->
                val url = item.url ?: ""
                val title = item.title
                
                val isTrusted = isTrustedFeedDomain(url)
                val isNotGlobalNoise = !isGlobalNoise(title)
                
                val isRelevant = if (state.isNotBlank()) {
                    // Strict word matching for state/abbreviation
                    val stateRegex = Regex("\\b${Regex.escape(state)}\\b|\\bAR\\b", RegexOption.IGNORE_CASE)
                    title.contains(stateRegex)
                } else true
                
                isTrusted && isNotGlobalNoise && isRelevant
            }

            // Clean up titles
            val cleanedResults = filteredResults.map { item ->
                item.copy(title = cleanTechnicalTitle(item.title))
            }

            val feedUrls = cleanedResults.mapNotNull { it.url }.toSet()
            sharedPrefs.edit().putStringSet("discovered_arcgis_urls", feedUrls).apply()
            
            cleanedResults
        } catch (e: Exception) {
            ConsoleManager.logError("WeatherRepository", "ArcGIS discovery failed", e)
            emptyList()
        }
    }

    private fun isTrustedFeedDomain(url: String): Boolean {
        if (url.isBlank()) return false
        val lowUrl = url.lowercase()
        // Allow common ArcGIS domains and trusted US govt/state extensions
        return lowUrl.contains("arcgis.com") || 
               lowUrl.contains(".gov") || 
               lowUrl.contains(".us") || 
               lowUrl.contains("state.ar.us")
    }

    private fun isGlobalNoise(title: String): Boolean {
        if (title.isBlank()) return false
        val lowTitle = title.lowercase()
        val blacklist = listOf(
            "esri_", "_eu", "europe", "global", "world", 
            "contributors", "basemap", "national", "continental",
            "federal", "usa_", "united states"
        )
        return blacklist.any { lowTitle.contains(it) }
    }

    private fun cleanTechnicalTitle(title: String): String {
        return title.split("_", "-")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
            .replace("Application Form", "Updates", ignoreCase = true)
            .replace("Row Permit", "Road Work Permits", ignoreCase = true)
            .replace("ROW", "Right-of-Way")
            .trim()
    }

    fun getDiscoveredFeeds(): Set<String> {
        return sharedPrefs.getStringSet("discovered_arcgis_urls", emptySet()) ?: emptySet()
    }

    suspend fun fetchInfrastructureAlerts(): List<WeatherEvent> = withContext(Dispatchers.IO) {
        if (!isInfrastructureAlertsEnabled()) return@withContext emptyList()
        
        val coords = getCoordinates() ?: return@withContext emptyList()
        val parts = coords.split(",").mapNotNull { it.trim().toDoubleOrNull() }
        if (parts.size != 2) return@withContext emptyList()

        val lat = parts[0]
        val lon = parts[1]
        
        // Fix: Add explicit logging to verify coordinate order (lat vs lon)
        Log.d("WeatherRepository", "Infrastructure Fetch - Parsed Lat: $lat, Lon: $lon")

        suspend fun fetchWithRadius(radius: Double): List<WeatherEvent> = coroutineScope {
            val bboxJson = "{\"xmin\":${lon - radius},\"ymin\":${lat - radius},\"xmax\":${lon + radius},\"ymax\":${lat + radius},\"spatialReference\":{\"wkid\":4326}}"
            val urls = getDiscoveredFeeds()
            
            val deferredAlerts = urls.map { url ->
                async {
                    try {
                        // Tightened timeout to 3.5s to prevent dashboard hangs on distant/slow servers
                        withTimeout(3500) {
                            val response = arcgisApiService.queryFeatureService(
                                url = url + "/0/query",
                                geometry = bboxJson,
                                geometryType = "esriGeometryEnvelope",
                                spatialRel = "esriSpatialRelIntersects",
                                inSR = "4326"
                            )
                            val feedAlerts = mutableListOf<WeatherEvent>()
                            
                            response.features.forEach { feature ->
                                fun getString(key: String): String? = feature.attributes[key]?.let {
                                    val str = if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString()
                                    if (str.isBlank() || str.equals("null", true)) null else str
                                }

                                val rawTitle = getString("INCIDENT") ?: getString("TYPE") ?: getString("SUBTYPE") ?: 
                                            getString("EVENT_TYPE") ?: getString("STREET") ?: getString("TITLE") ?: 
                                            getString("EVENT") ?: getString("Description") ?: getString("Name") ?:
                                            getString("ROAD_NAME") ?: getString("LOCATION") ?: getString("SUBTYPE_TEXT")
                                
                                val title = rawTitle ?: url.split("/services/").getOrNull(1)?.split("/")?.firstOrNull()?.replace("_", " ") ?: "Local Update"
                                
                                val details = getString("DESCRIPTION") ?: getString("MESSAGE") ?: getString("NOTES") ?: 
                                              getString("REMARKS") ?: getString("DETAILS") ?: getString("COMMENTS") ?: 
                                              getString("REASON") ?: getString("FULL_DESC") ?: getString("summary") ?:
                                              getString("FULL_DESCRIPTION") ?: getString("INCIDENT_DESCRIPTION") ?:
                                              getString("MESSAGE_TEXT") ?: getString("COMMENT_") ?: getString("STATUS_DESC")

                                val id = getString("OBJECTID") ?: getString("GUID") ?: "infra_${System.currentTimeMillis()}_${url.hashCode()}"
                                
                                val finalDetails = details ?: feature.attributes.entries
                                    .filter { it.key != "OBJECTID" && it.key != "GUID" }
                                    .joinToString("\n") { "${it.key}: ${it.value}" }
                                    .ifBlank { "Information provided by local municipal feed." }

                                feedAlerts.add(
                                    WeatherEvent(
                                        id = id,
                                        title = title.split("_", "-").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
                                        severity = "Information",
                                        description = finalDetails,
                                        instruction = getString("ACTION") ?: getString("DETOUR") ?: "",
                                        hash = feature.hashCode()
                                    )
                                )
                            }
                            feedAlerts
                        }
                    } catch (e: Exception) {
                        // Silent log for individual feed failures to prevent Console flooding
                        // Details are still available in Logcat for developers
                        Log.w("WeatherRepository", "Skipping slow/failed feed: $url - ${e.message}")
                        emptyList<WeatherEvent>()
                    }
                }
            }
            deferredAlerts.awaitAll().flatten()
        }

        // Tier 1: Strict local (15 miles)
        var results = fetchWithRadius(0.2)
        
        // Tier 2: Fallback to regional (40 miles) if nothing found locally
        if (results.isEmpty()) {
            results = fetchWithRadius(0.5)
        }

        results.distinctBy { it.title.lowercase().trim() + (it.description ?: "").lowercase().trim() }
    }

    fun getAqiLabel(aqi: Int): String {
        return when (aqi) {
            in 0..50 -> "Good"
            in 51..100 -> "Moderate"
            in 101..150 -> "Unhealthy for Sensitive Groups"
            in 151..200 -> "Unhealthy"
            in 201..300 -> "Very Unhealthy"
            else -> "Hazardous"
        }
    }

    fun getWeatherDescription(code: Int): String {
        return when (code) {
            1000 -> "Clear"
            1100 -> "Mostly Clear"
            1101 -> "Partly Cloudy"
            1102 -> "Mostly Cloudy"
            1001 -> "Cloudy"
            4000 -> "Drizzle"
            4001 -> "Rain"
            4200 -> "Light Rain"
            4201 -> "Heavy Rain"
            5000 -> "Snow"
            5001 -> "Flurries"
            5100 -> "Light Snow"
            5101 -> "Heavy Snow"
            6000 -> "Freezing Drizzle"
            6001 -> "Freezing Rain"
            6200 -> "Light Freezing Rain"
            6201 -> "Heavy Freezing Rain"
            7000 -> "Ice Pellets"
            7101 -> "Heavy Ice Pellets"
            7102 -> "Light Ice Pellets"
            8000 -> "Thunderstorm"
            else -> "Unknown"
        }
    }
}
