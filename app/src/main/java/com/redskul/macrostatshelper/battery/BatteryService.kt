package com.redskul.macrostatshelper.battery

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.redskul.macrostatshelper.settings.SettingsManager
import kotlinx.coroutines.*

class BatteryService : Service() {

    private lateinit var batteryChargeMonitor: BatteryChargeMonitor
    private lateinit var settingsManager: SettingsManager
    private lateinit var powerManager: PowerManager

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updateJob: Job? = null

    // Adaptive battery optimization variables
    private var lastUpdateTime = 0L
    private var consecutiveInactiveUpdates = 0
    private var isInPowerSaveMode = false

    companion object {
        const val ACTION_UPDATE_NOW = "BATTERY_UPDATE_NOW"
        const val ACTION_BATTERY_UPDATED = "com.redskul.macrostatshelper.BATTERY_UPDATED"

        // Adaptive battery constants
        private const val INACTIVE_THRESHOLD_MS = 10 * 60 * 1000L // 10 minutes
        private const val MAX_INACTIVE_MULTIPLIER = 4 // Max 4x longer intervals
        private const val POWER_SAVE_MULTIPLIER = 2 // 2x longer in power save mode
    }

    override fun onCreate() {
        super.onCreate()
        batteryChargeMonitor = BatteryChargeMonitor(this)
        settingsManager = SettingsManager(this)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        lastUpdateTime = System.currentTimeMillis()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_NOW -> {
                android.util.Log.d("BatteryService", "Received immediate update request")
                serviceScope.launch {
                    updateBatteryData()
                }
                return START_STICKY
            }
        }

        serviceScope.launch {
            delay(2000)
            startPeriodicUpdates()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun startPeriodicUpdates() {
        updateJob?.cancel()

        updateJob = serviceScope.launch {
            updateBatteryData()

            while (isActive) {
                // Calculate adaptive update interval for battery optimization
                val adaptiveInterval = calculateAdaptiveInterval()
                android.util.Log.d("BatteryService", "Next battery update in ${adaptiveInterval / 60000} minutes (adaptive)")

                delay(adaptiveInterval)
                if (isActive) {
                    updateBatteryData()
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
        var intervalMultiplier = 1

        if (isDeviceInactive) {
            consecutiveInactiveUpdates++
            // Gradually increase interval for inactive device (1x -> 2x -> 3x -> 4x max)
            intervalMultiplier = minOf(
                1 + (consecutiveInactiveUpdates / 3),
                MAX_INACTIVE_MULTIPLIER
            )
            android.util.Log.d("BatteryService", "Device inactive for ${timeSinceLastUpdate / 60000}min, multiplier: ${intervalMultiplier}x")
        } else {
            consecutiveInactiveUpdates = 0
            intervalMultiplier = 1
        }

        // Additional multiplier for power save mode
        if (isInPowerSaveMode) {
            intervalMultiplier *= POWER_SAVE_MULTIPLIER
            android.util.Log.d("BatteryService", "Power save mode active, applying ${POWER_SAVE_MULTIPLIER}x multiplier")
        }

        val adaptiveInterval = baseInterval * intervalMultiplier
        lastUpdateTime = currentTime

        android.util.Log.d("BatteryService", "Adaptive battery interval: ${adaptiveInterval / 60000}min (base: ${baseInterval / 60000}min, multiplier: ${intervalMultiplier}x)")

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

        return isScreenOff && hasBeenInactiveForLong
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun updateBatteryData() {
        try {
            android.util.Log.d("BatteryService", "Updating battery data")
            val chargeData = batteryChargeMonitor.getChargeData()

            withContext(Dispatchers.Main) {
                // Send broadcast to charge cycle QS tile
                val broadcastIntent = Intent(ACTION_BATTERY_UPDATED)
                sendBroadcast(broadcastIntent)
                android.util.Log.d("BatteryService", "Battery update broadcast sent")
            }
        } catch (e: Exception) {
            android.util.Log.e("BatteryService", "Error updating battery data", e)
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        serviceScope.cancel()
    }
}
