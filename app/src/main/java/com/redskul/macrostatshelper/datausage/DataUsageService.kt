package com.redskul.macrostatshelper.datausage

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.content.Context
import com.redskul.macrostatshelper.notification.NotificationHelper
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.settings.SettingsManager
import com.redskul.macrostatshelper.core.MainActivity
import kotlinx.coroutines.*

class DataUsageService : Service() {

    private lateinit var dataUsageMonitor: DataUsageMonitor
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var settingsManager: SettingsManager
    private lateinit var powerManager: PowerManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updateJob: Job? = null
    private var isForegroundService = false

    // Adaptive battery optimization variables
    private var lastUpdateTime = 0L
    private var consecutiveInactiveUpdates = 0
    private var isInPowerSaveMode = false

    companion object {
        const val ACTION_UPDATE_NOW = "UPDATE_NOW"
        const val ACTION_DATA_UPDATED = "com.redskul.macrostatshelper.DATA_UPDATED"
        const val ACTION_NOTIFICATION_TOGGLE_CHANGED = "NOTIFICATION_TOGGLE_CHANGED"

        // Adaptive battery constants
        private const val INACTIVE_THRESHOLD_MS = 10 * 60 * 1000L // 10 minutes
        private const val MAX_INACTIVE_MULTIPLIER = 4 // Max 4x longer intervals
        private const val POWER_SAVE_MULTIPLIER = 2 // 2x longer in power save mode
    }

    override fun onCreate() {
        super.onCreate()

        dataUsageMonitor = DataUsageMonitor(this)
        notificationHelper = NotificationHelper(this)
        settingsManager = SettingsManager(this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        lastUpdateTime = System.currentTimeMillis()
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
                stopForeground(STOP_FOREGROUND_REMOVE)
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
                stopForeground(STOP_FOREGROUND_REMOVE) // Updated to use new API
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
                // Calculate adaptive update interval for battery optimization
                val adaptiveInterval = calculateAdaptiveInterval()
                android.util.Log.d("DataUsageService", "Next update in ${adaptiveInterval / 60000} minutes (adaptive)")

                delay(adaptiveInterval)
                if (isActive) {
                    updateUsageData()
                }
            }
        }
    }

    /**
     * Calculates adaptive update interval based on device state to optimize battery usage
     */
    private fun calculateAdaptiveInterval(): Long {
        val baseInterval = settingsManager.getUpdateIntervalMillis()
        val currentTime = System.currentTimeMillis()

        // Check if device is in power save mode
        isInPowerSaveMode = powerManager.isPowerSaveMode

        // Calculate time since last update
        val timeSinceLastUpdate = currentTime - lastUpdateTime
        val isDeviceInactive = isDeviceInactive(timeSinceLastUpdate)

        // Calculate multiplier based on device state
        val intervalMultiplier = if (isDeviceInactive) {
            consecutiveInactiveUpdates++
            // Gradually increase interval for inactive device (1x -> 2x -> 3x -> 4x max)
            val multiplier = minOf(
                1 + (consecutiveInactiveUpdates / 3),
                MAX_INACTIVE_MULTIPLIER
            )
            android.util.Log.d("DataUsageService", "Device inactive for ${timeSinceLastUpdate / 60000}min, multiplier: ${multiplier}x")
            multiplier
        } else {
            consecutiveInactiveUpdates = 0
            1
        }

        // Additional multiplier for power save mode
        val finalMultiplier = if (isInPowerSaveMode) {
            val powerSaveMultiplier = intervalMultiplier * POWER_SAVE_MULTIPLIER
            android.util.Log.d("DataUsageService", "Power save mode active, applying ${POWER_SAVE_MULTIPLIER}x multiplier")
            powerSaveMultiplier
        } else {
            intervalMultiplier
        }

        val adaptiveInterval = baseInterval * finalMultiplier
        lastUpdateTime = currentTime

        android.util.Log.d("DataUsageService", "Adaptive interval: ${adaptiveInterval / 60000}min (base: ${baseInterval / 60000}min, multiplier: ${finalMultiplier}x)")

        return adaptiveInterval
    }

    /**
     * Determines if device is inactive based on various factors
     */
    private fun isDeviceInactive(timeSinceLastUpdate: Long): Boolean {
        // Device is considered inactive if:
        // 1. Screen has been off for extended period
        // 2. No user interaction detected
        // 3. Time since last update exceeds threshold

        val isScreenOff = !powerManager.isInteractive
        val hasBeenInactiveForLong = timeSinceLastUpdate > INACTIVE_THRESHOLD_MS

        // Additional checks could include:
        // - Check if device is charging and stationary
        // - Check time of day (reduce frequency during typical sleep hours)
        // - Check if device is in Do Not Disturb mode

        return isScreenOff && hasBeenInactiveForLong
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
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                android.util.Log.e("DataUsageService", "Error stopping foreground in onDestroy", e)
            }
        }
    }
}
