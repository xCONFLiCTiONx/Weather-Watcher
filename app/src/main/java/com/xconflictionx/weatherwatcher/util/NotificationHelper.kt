package com.xconflictionx.weatherwatcher.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.xconflictionx.weatherwatcher.MainActivity

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_SEVERE_ID = "severe_alerts"
        const val CHANNEL_SEVERE_NAME = "Weather Hazards"
        
        const val CHANNEL_RAIN_ID = "rain_notices"
        const val CHANNEL_RAIN_NAME = "Rain Notices"

        const val CHANNEL_LOCAL_ID = "local_community"
        const val CHANNEL_LOCAL_NAME = "Local Community Updates"

        const val CHANNEL_DAILY_ID = "daily_briefing"
        const val CHANNEL_DAILY_NAME = "Daily Briefing"

        const val CHANNEL_GENERAL_ID = "general_info"
        const val CHANNEL_GENERAL_NAME = "General System Info"

        const val CHANNEL_LOCATION_ID = "location_updates"
        const val CHANNEL_LOCATION_NAME = "Location Updates"

        const val RAIN_NOTIFICATION_ID = 555
        const val LOCATION_NOTIFICATION_ID = 777
        const val DAILY_REPORT_ID = 101
        const val CONDITION_UPDATE_ID = 102
        const val ALERT_BASE_ID = 2000
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
            val channels = listOf(
                NotificationChannel(CHANNEL_SEVERE_ID, CHANNEL_SEVERE_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Emergency notifications for severe weather hazards (warnings/watches)"
                },
                NotificationChannel(CHANNEL_RAIN_ID, CHANNEL_RAIN_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Notifications for upcoming or starting precipitation"
                },
                NotificationChannel(CHANNEL_LOCAL_ID, CHANNEL_LOCAL_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Updates from local municipal feeds and road infrastructure"
                },
                NotificationChannel(CHANNEL_DAILY_ID, CHANNEL_DAILY_NAME, NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Your morning weather and air quality summary"
                },
                NotificationChannel(CHANNEL_GENERAL_ID, CHANNEL_GENERAL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Non-critical system notes and location detection confirmations"
                },
                NotificationChannel(CHANNEL_LOCATION_ID, CHANNEL_LOCATION_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Notifications for significant location changes during travel"
                }
            )

            notificationManager.createNotificationChannels(channels)
        }
    }

    fun showNotification(
        title: String, 
        message: String, 
        channelId: String,
        isEmergency: Boolean = false,
        iconRes: Int? = null,
        notificationId: Int? = null
    ) {
        val priority = if (isEmergency) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
        
        val finalIcon = iconRes ?: if (isEmergency) {
            android.R.drawable.ic_dialog_alert
        } else {
            android.R.drawable.ic_dialog_info
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(finalIcon)
            .setContentTitle(title)
            .setPriority(priority)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (message.isNotBlank()) {
            builder.setContentText(message)
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(message))
        }

        val id = notificationId ?: System.currentTimeMillis().toInt()

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(id, builder.build())
            } catch (e: SecurityException) {
                ConsoleManager.logError("NotificationHelper", "Notification permission missing", e)
            } catch (e: Exception) {
                ConsoleManager.logError("NotificationHelper", "Failed to show notification", e)
            }
        }
    }
}
