package com.redskul.macrostatshelper.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.utils.UpdateStatsReceiver
import com.redskul.macrostatshelper.datausage.UsageData
import com.redskul.macrostatshelper.settings.SettingsActivity
import com.redskul.macrostatshelper.settings.SettingsManager

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "data_usage_channel"
        const val NOTIFICATION_ID = 1001

        const val PERSISTENT_CHANNEL_ID = "app_status_channel"
        const val PERSISTENT_NOTIFICATION_ID = 1002
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val settingsManager = SettingsManager(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        // Existing data usage channel
        val dataChannel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.data_usage_monitor_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_description)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannel(dataChannel)

        // Persistent app status channel — silent, collapsed, undismissable
        val persistentChannel = NotificationChannel(
            PERSISTENT_CHANNEL_ID,
            "App Status",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Persistent status indicator for MacroStats Helper"
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            enableLights(false)
        }
        notificationManager.createNotificationChannel(persistentChannel)
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

            android.util.Log.d("NotificationHelper", "Notification sent with data: $shortText")

        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to show notification", e)
        }
    }

    fun showPersistentNotification() {
        try {
            val intent = Intent(context, SettingsActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 2, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, PERSISTENT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("MacroStats Helper")
                .setContentText("Running")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setAutoCancel(false)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build()

            notificationManager.notify(PERSISTENT_NOTIFICATION_ID, notification)

            android.util.Log.d("NotificationHelper", "Persistent notification shown")
        } catch (e: Exception) {
            android.util.Log.e("NotificationHelper", "Failed to show persistent notification", e)
        }
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }
}