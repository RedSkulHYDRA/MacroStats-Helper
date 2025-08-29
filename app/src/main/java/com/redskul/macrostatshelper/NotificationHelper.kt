package com.redskul.macrostatshelper

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "data_usage_channel"
        const val NOTIFICATION_ID = 1001
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val settingsManager = SettingsManager(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.data_usage_monitor_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun showUsageNotification(usageData: UsageData) {
        try {
            // Create intent to open settings when notification is clicked
            val settingsIntent = Intent(context, SettingsActivity::class.java)
            val settingsPendingIntent = PendingIntent.getActivity(
                context, 0, settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Create intent for update button
            val updateIntent = Intent(context, UpdateStatsReceiver::class.java).apply {
                action = UpdateStatsReceiver.ACTION_UPDATE_STATS
            }
            val updatePendingIntent = PendingIntent.getBroadcast(
                context, 1, updateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Get formatted text based on current settings
            val (shortText, expandedText) = settingsManager.getFormattedUsageText(usageData)

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(context.getString(R.string.data_usage_stats_title))
                .setContentText(shortText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
                .setContentIntent(settingsPendingIntent)
                .addAction(
                    0,
                    context.getString(R.string.notification_update),
                    updatePendingIntent
                )
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setChannelId(CHANNEL_ID)
                .build()

            notificationManager.notify(NOTIFICATION_ID, notification)

            // Debug: Log that notification was sent
            android.util.Log.d("NotificationHelper", "Notification sent with data: $shortText")

        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to show notification", e)
        }
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}
