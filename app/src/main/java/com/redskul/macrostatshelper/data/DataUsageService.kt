package com.redskul.macrostatshelper.data

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.redskul.macrostatshelper.notification.NotificationHelper
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.settings.SettingsManager
import com.redskul.macrostatshelper.core.MainActivity
import kotlinx.coroutines.*

class DataUsageService : Service() {

    private lateinit var dataUsageMonitor: DataUsageMonitor
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var settingsManager: SettingsManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updateJob: Job? = null
    private var isForegroundService = false

    companion object {
        const val ACTION_UPDATE_NOW = "UPDATE_NOW"
        const val ACTION_DATA_UPDATED = "com.redskul.macrostatshelper.DATA_UPDATED"
        const val ACTION_NOTIFICATION_TOGGLE_CHANGED = "NOTIFICATION_TOGGLE_CHANGED"
    }

    override fun onCreate() {
        super.onCreate()

        dataUsageMonitor = DataUsageMonitor(this)
        notificationHelper = NotificationHelper(this)
        settingsManager = SettingsManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_NOW -> {
                android.util.Log.d("DataUsageService", "Received immediate update request")
                serviceScope.launch {
                    updateUsageData()
                }
                return START_STICKY
            }
            ACTION_NOTIFICATION_TOGGLE_CHANGED -> {
                android.util.Log.d("DataUsageService", "Notification toggle changed")
                updateForegroundStatus()
                return START_STICKY
            }
        }

        // Immediately determine if we should be foreground and act accordingly
        val notificationEnabled = settingsManager.isNotificationEnabled()

        if (notificationEnabled && !isForegroundService) {
            // Promote to foreground immediately
            try {
                startForeground(NotificationHelper.NOTIFICATION_ID, createServiceNotification())
                isForegroundService = true
                android.util.Log.d("DataUsageService", "Started as foreground service")
            } catch (e: Exception) {
                android.util.Log.e("DataUsageService", "Failed to start as foreground service", e)
            }
        } else if (!notificationEnabled && isForegroundService) {
            // Demote from foreground
            try {
                stopForeground(true)
                isForegroundService = false
                android.util.Log.d("DataUsageService", "Running in background")
            } catch (e: Exception) {
                android.util.Log.e("DataUsageService", "Failed to stop foreground", e)
            }
        }

        serviceScope.launch {
            delay(2000)
            startPeriodicUpdates()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateForegroundStatus() {
        val notificationEnabled = settingsManager.isNotificationEnabled()

        if (notificationEnabled && !isForegroundService) {
            // Start as foreground service
            try {
                startForeground(NotificationHelper.NOTIFICATION_ID, createServiceNotification())
                isForegroundService = true
                android.util.Log.d("DataUsageService", "Promoted to foreground service")
            } catch (e: Exception) {
                android.util.Log.e("DataUsageService", "Failed to promote to foreground", e)
            }
        } else if (!notificationEnabled && isForegroundService) {
            // Remove from foreground but keep service running
            try {
                stopForeground(true) // true = remove notification
                isForegroundService = false
                android.util.Log.d("DataUsageService", "Demoted from foreground, service continues in background")
            } catch (e: Exception) {
                android.util.Log.e("DataUsageService", "Failed to demote from foreground", e)
            }
        }
    }

    private fun createServiceNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return android.app.Notification.Builder(this, NotificationHelper.CHANNEL_ID)
            .setContentTitle(getString(R.string.data_usage_monitor_service))
            .setContentText(getString(R.string.monitoring_data_usage))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private suspend fun startPeriodicUpdates() {
        updateJob?.cancel()

        updateJob = serviceScope.launch {
            updateUsageData()

            while (isActive) {
                // Use user-configured update interval
                val updateInterval = settingsManager.getUpdateIntervalMillis()
                android.util.Log.d("DataUsageService", "Next update in ${updateInterval / 60000} minutes")

                delay(updateInterval)
                if (isActive) {
                    updateUsageData()
                }
            }
        }
    }

    private suspend fun updateUsageData() {
        try {
            android.util.Log.d("DataUsageService", "Updating usage data")
            val usageData = dataUsageMonitor.getUsageData()

            withContext(Dispatchers.Main) {
                // Only show notification if enabled by user
                if (settingsManager.isNotificationEnabled()) {
                    notificationHelper.showUsageNotification(usageData)
                    android.util.Log.d("DataUsageService", "Data notification shown (enabled by user)")
                } else {
                    android.util.Log.d("DataUsageService", "Data notification skipped (disabled by user)")
                }

                // Always send broadcast to QS tiles regardless of notification setting
                val broadcastIntent = Intent(ACTION_DATA_UPDATED)
                sendBroadcast(broadcastIntent)
                android.util.Log.d("DataUsageService", "Data update broadcast sent")
            }
        } catch (e: Exception) {
            android.util.Log.e("DataUsageService", "Error updating usage data", e)
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        serviceScope.cancel()
        notificationHelper.cancelNotification()

        if (isForegroundService) {
            try {
                stopForeground(true)
            } catch (e: Exception) {
                android.util.Log.e("DataUsageService", "Error stopping foreground in onDestroy", e)
            }
        }
    }
}
