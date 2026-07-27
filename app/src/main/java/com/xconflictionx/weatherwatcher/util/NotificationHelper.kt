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
        const val CHANNEL_ALERTS_ID = "weather_alerts"
        const val CHANNEL_ALERTS_NAME = "Severe Weather Alerts"
        
        const val CHANNEL_INFO_ID = "weather_info"
        const val CHANNEL_INFO_NAME = "Daily Briefing & Rain Notices"
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
            // 1. Severe Alerts Channel (High Importance, Warning Sound)
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS_ID, 
                CHANNEL_ALERTS_NAME, 
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Emergency notifications for severe weather hazards"
            }
            
            // 2. Info Channel (Default Importance, Info Icon)
            val infoChannel = NotificationChannel(
                CHANNEL_INFO_ID, 
                CHANNEL_INFO_NAME, 
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Informational updates for daily briefings and rain notices"
            }

            notificationManager.createNotificationChannel(alertChannel)
            notificationManager.createNotificationChannel(infoChannel)
        }
    }

    fun showNotification(
        title: String, 
        message: String, 
        isEmergency: Boolean = true,
        iconRes: Int? = null
    ) {
        val channelId = if (isEmergency) CHANNEL_ALERTS_ID else CHANNEL_INFO_ID
        val priority = if (isEmergency) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
        
        // Use provided icon, or default to alert/info icons
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

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(System.currentTimeMillis().toInt(), builder.build())
            } catch (e: SecurityException) {
                // Handle permission not granted
            }
        }
    }
}
