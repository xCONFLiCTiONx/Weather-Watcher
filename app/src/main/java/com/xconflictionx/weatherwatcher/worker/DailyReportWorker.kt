package com.xconflictionx.weatherwatcher.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.xconflictionx.weatherwatcher.data.WeatherRepository
import com.xconflictionx.weatherwatcher.util.ConsoleManager
import com.xconflictionx.weatherwatcher.util.NotificationHelper
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyReportWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        Log.d("DailyReportWorker", "DailyReportWorker started execution")
        val repository = WeatherRepository(applicationContext)
        val notificationHelper = NotificationHelper(applicationContext)
        
        repository.saveLastBackgroundSyncTime(System.currentTimeMillis())

        if (!repository.isDailyReportEnabled()) {
            Log.d("DailyReportWorker", "Daily report disabled, skipping")
            return Result.success()
        }

        try {
            Log.d("DailyReportWorker", "Fetching weather for daily report...")
            val weather = repository.fetchCurrentWeather()
            val alerts = repository.fetchAlerts()
            val hourly = repository.fetchForecast()
            
            val temp = weather?.temperature?.toInt()?.toString() ?: "--"
            val high = weather?.highTemp?.toString() ?: "--"
            val low = weather?.lowTemp?.toString() ?: "--"
            val condition = weather?.condition ?: "N/A"
            val aqi = weather?.aqi ?: "N/A"
            val aqiLabel = if (weather?.aqi != null) " (${repository.getAqiLabel(weather.aqi)})" else ""
            
            val alertCount = alerts?.size ?: 0
            val alertText = if (alertCount > 0) "\n⚠️ $alertCount active alerts." else "\n✅ No active hazards."

            var rainText = ""
            if (hourly != null) {
                val now = java.time.ZonedDateTime.now()
                val endOfDay = now.with(java.time.LocalTime.MAX)
                val activeRain = hourly.firstOrNull { 
                    try {
                        val startTime = java.time.ZonedDateTime.parse(it.startTime)
                        val endTime = java.time.ZonedDateTime.parse(it.endTime)
                        val prob = it.probabilityOfPrecipitation?.value ?: 0
                        val isRain = it.shortForecast?.contains("Rain", ignoreCase = true) == true ||
                                     it.shortForecast?.contains("Showers", ignoreCase = true) == true ||
                                     it.shortForecast?.contains("Thunderstorm", ignoreCase = true) == true
                        
                        val isCurrent = now.isAfter(startTime) && now.isBefore(endTime)
                        val isFutureToday = startTime.isAfter(now) && startTime.isBefore(endOfDay)
                        
                        (isCurrent || isFutureToday) && prob > 20 && isRain
                    } catch (e: Exception) { 
                        ConsoleManager.logError("DailyReportWorker", "Rain forecast filter error", e)
                        false 
                    }
                }
                
                if (activeRain != null) {
                    val startTime = java.time.ZonedDateTime.parse(activeRain.startTime)
                    val endTime = java.time.ZonedDateTime.parse(activeRain.endTime)
                    
                    if (now.isAfter(startTime)) {
                        val time = endTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                        val intensity = activeRain.shortForecast ?: "Rain"
                        val verb = if (intensity.contains("Showers", true) || 
                                     intensity.contains("Thunderstorms", true)) "are" else "is"
                        rainText = "\n🌧️ $intensity $verb in progress, expected to end at $time."
                    } else {
                        val time = startTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
                        rainText = "\n🌧️ Rain expected today at $time (${activeRain.probabilityOfPrecipitation?.value}%)."
                    }
                }
            }

            val title = "🌤️ Your Daily Weather Report"
            val message = "Current: $temp° ($condition) | High: $high° | Low: $low° | AQI: $aqi$aqiLabel.$rainText$alertText"

            Log.d("DailyReportWorker", "Sending notification: $title")
            notificationHelper.showNotification(
                title = title, 
                message = message, 
                channelId = NotificationHelper.CHANNEL_DAILY_ID,
                notificationId = NotificationHelper.DAILY_REPORT_ID
            )
            
        } catch (e: Exception) {
            ConsoleManager.logError("DailyReportWorker", "Daily report generation failed", e)
            // Schedule next report even on error to keep the cycle alive
            scheduleNext(applicationContext, repository)
            return Result.retry()
        }

        // Schedule next report
        Log.d("DailyReportWorker", "Scheduling next report...")
        scheduleNext(applicationContext, repository)

        return Result.success()
    }

    companion object {
        fun scheduleNext(context: Context, repository: WeatherRepository) {
            if (!repository.isDailyReportEnabled()) return

            val (hour, minute) = repository.getDailyReportTime()
            val calendar = Calendar.getInstance()
            val now = calendar.timeInMillis

            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            if (calendar.timeInMillis <= now) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            val delay = calendar.timeInMillis - now
            val workRequest = OneTimeWorkRequestBuilder<DailyReportWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("DailyReportWork")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "DailyReportWork",
                androidx.work.ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
