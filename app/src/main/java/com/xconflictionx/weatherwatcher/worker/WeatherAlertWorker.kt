package com.xconflictionx.weatherwatcher.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.xconflictionx.weatherwatcher.data.WeatherRepository
import com.xconflictionx.weatherwatcher.util.ConsoleManager
import com.xconflictionx.weatherwatcher.util.NotificationHelper
import kotlinx.coroutines.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class WeatherAlertWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val repository = WeatherRepository(applicationContext)
        val notificationHelper = NotificationHelper(applicationContext)
        
        repository.saveLastBackgroundSyncTime(System.currentTimeMillis())

        // 1. Every check starts with a Location Refresh
        try {
            val newLocation = repository.refreshLocationFromGps()
            if (newLocation != null) {
                // User has traveled to a new area
                notificationHelper.showNotification(
                    title = "📍 Tracking active",
                    message = "Location updated to $newLocation. Monitoring local hazards.",
                    isEmergency = false
                )
            }
        } catch (e: Exception) {
            ConsoleManager.logError("WeatherAlertWorker", "Background location tracking failed", e)
        }

        // 2. Parallel Background Fetch
        val (currentWeather, periods) = coroutineScope {
            val weatherDef = async { repository.fetchCurrentWeather() }
            val forecastDef = async { repository.fetchForecast() }
            weatherDef.await() to forecastDef.await()
        }

        // 3. Condition Change Detection
        if (currentWeather != null) {
            // If Track is ON, check for condition changes and notify
            if (repository.isTrackWeatherEnabled()) {
                val lastWeather = repository.getLastWeather()
                if (lastWeather != null && currentWeather.condition != lastWeather.condition) {
                    val condition = currentWeather.condition
                    val verb = if (condition.contains("Showers", true) || 
                                 condition.contains("Thunderstorms", true) ||
                                 condition.contains("Clouds", true)) "are" else "is"
                    
                    notificationHelper.showNotification(
                        title = "Weather Update",
                        message = "Current conditions: $condition $verb currently in progress.",
                        isEmergency = false
                    )
                }
            }
            // Always update cache to keep the dashboard fresh in the background
            repository.saveLastWeather(currentWeather)
        }

        // 4. Proactive Rain Detection
        if (repository.isRainAlertEnabled() && periods != null) {
            try {
                // Find first rain period in the future
                val nextRain = periods.firstOrNull { 
                    val prob = it.probabilityOfPrecipitation?.value ?: 0
                    val isRain = it.shortForecast?.contains("Rain", ignoreCase = true) == true ||
                                 it.shortForecast?.contains("Showers", ignoreCase = true) == true ||
                                 it.shortForecast?.contains("Drizzle", ignoreCase = true) == true ||
                                 it.shortForecast?.contains("Thunderstorm", ignoreCase = true) == true
                    
                    val endTime = ZonedDateTime.parse(it.endTime)
                    prob > 20 && isRain && endTime.isAfter(ZonedDateTime.now())
                }

                if (nextRain != null) {
                    val startTime = ZonedDateTime.parse(nextRain.startTime)
                    val now = ZonedDateTime.now()
                    val diffMinutes = java.time.Duration.between(now, startTime).toMinutes()

                    // Window: Starts within next 75 mins OR started within last 15 mins
                    if (diffMinutes in 0..75 || diffMinutes in -15..-1) {
                        if (repository.shouldNotifyForRainPeriod(nextRain.startTime)) {
                            val justStarted = diffMinutes < 0
                            val timeLabel = startTime.format(DateTimeFormatter.ofPattern("h:mm a"))
                            val intensity = nextRain.shortForecast ?: "Rain"
                            val title = if (justStarted) "🌧️ Rain has started" else "🌧️ Rain starting at $timeLabel"
                            
                            val verb = if (intensity.contains("Showers", true) || 
                                         intensity.contains("Thunderstorms", true) ||
                                         intensity.contains("Clouds", true)) "are" else "is"
                            
                            val message = if (justStarted) {
                                "$intensity $verb currently in progress. Expected start was around $timeLabel."
                            } else {
                                "$intensity $verb forecasted. Current probability is ${nextRain.probabilityOfPrecipitation?.value}%."
                            }
                            
                            notificationHelper.showNotification(
                                title = title,
                                message = message,
                                isEmergency = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                ConsoleManager.logError("WeatherAlertWorker", "Rain processing failed", e)
            }
        }

        // 5. Consolidated Alerts (Regional & Infrastructure)
        try {
            val nwsAlerts = repository.fetchAlerts()
            val localAlerts = if (repository.isInfrastructureAlertsEnabled()) {
                repository.fetchInfrastructureAlerts()
            } else emptyList()

            (nwsAlerts + localAlerts).forEach { event ->
                if (repository.shouldNotifyForNwsAlert(event)) {
                    val summary = event.description?.split("\n")?.firstOrNull() ?: "View details in app."
                    val isLocal = event.id.contains("infra", true)
                    
                    // Logic: NWS events use severity; Local events are always info
                    val isEmergency = if (isLocal) false else {
                        event.severity.lowercase() == "extreme" || 
                        event.severity.lowercase() == "severe" ||
                        event.title.contains("Danger", true) ||
                        event.title.contains("Emergency", true)
                    }

                    notificationHelper.showNotification(
                        title = "${if (isLocal) "📍" else "⚠️"} ${event.title}",
                        message = "$summary\n\nSource: ${if (isLocal) "Local Community" else "NWS"}",
                        isEmergency = isEmergency
                    )
                }
            }
        } catch (e: Exception) {
            ConsoleManager.logError("WeatherAlertWorker", "Alerts processing failed", e)
        }

        return Result.success()
    }
}
