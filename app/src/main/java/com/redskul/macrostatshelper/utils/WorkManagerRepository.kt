package com.redskul.macrostatshelper.utils

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import androidx.work.*
import androidx.work.WorkManager
import com.redskul.macrostatshelper.settings.SettingsManager
import com.redskul.macrostatshelper.battery.BatteryWorker
import com.redskul.macrostatshelper.datausage.DataUsageWorker
import java.util.concurrent.TimeUnit

/**
 * Repository class to manage WorkManager operations
 * Centralizes all background work scheduling and management
 */
class WorkManagerRepository(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)
    private val settingsManager = SettingsManager(context)

    companion object {
        private const val TAG = "WorkManager"
        private const val IMMEDIATE_DATA_WORK_TAG = "immediate_data_update"
        private const val IMMEDIATE_BATTERY_WORK_TAG = "immediate_battery_update"
    }

    /**
     * Start all monitoring work with proper constraints
     */
    fun startMonitoring() {
        startDataUsageMonitoring()
        startBatteryMonitoring()
        android.util.Log.d(TAG, "All monitoring work started")
    }

    /**
     * Stop all monitoring work
     */
    fun stopMonitoring() {
        workManager.cancelUniqueWork(DataUsageWorker.WORK_NAME)
        workManager.cancelUniqueWork(BatteryWorker.WORK_NAME)
        // Also cancel any pending immediate work
        workManager.cancelAllWorkByTag(IMMEDIATE_DATA_WORK_TAG)
        workManager.cancelAllWorkByTag(IMMEDIATE_BATTERY_WORK_TAG)
        android.util.Log.d(TAG, "All monitoring work stopped")
    }

    /**
     * Start data usage monitoring with adaptive constraints
     */
    private fun startDataUsageMonitoring() {
        val baseInterval = settingsManager.getUpdateInterval().toLong()
        val adaptiveInterval = calculateAdaptiveInterval(baseInterval)

        // Define constraints for data monitoring
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        // Create periodic work request
        val dataWorkRequest = PeriodicWorkRequestBuilder<DataUsageWorker>(
            adaptiveInterval, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                30, TimeUnit.SECONDS
            )
            .addTag("data_monitoring")
            .build()

        // Enqueue unique work (replaces any existing work)
        workManager.enqueueUniquePeriodicWork(
            DataUsageWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            dataWorkRequest
        )

        android.util.Log.d(TAG, "Data usage monitoring started with ${adaptiveInterval}min interval")
    }

    /**
     * Start battery monitoring with user-configured interval and charging constraint
     */
    private fun startBatteryMonitoring() {
        val baseInterval = settingsManager.getUpdateInterval().toLong()
        val adaptiveInterval = calculateAdaptiveInterval(baseInterval)

        // Define constraints for battery monitoring
        val constraints = Constraints.Builder()
            .setRequiresCharging(false) // Can run on battery
            .setRequiresBatteryNotLow(false) // Users want battery info even when battery is low
            .build()

        // Create periodic work request using the same adaptive interval as data monitoring
        val batteryWorkRequest = PeriodicWorkRequestBuilder<BatteryWorker>(
            adaptiveInterval, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                1, TimeUnit.MINUTES
            )
            .addTag("battery_monitoring")
            .build()

        // Enqueue unique work
        workManager.enqueueUniquePeriodicWork(
            BatteryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE, // CHANGED: Replace instead of KEEP to allow interval updates
            batteryWorkRequest
        )

        android.util.Log.d(TAG, "Battery monitoring started with ${adaptiveInterval}min interval")
    }

    /**
     * Calculate adaptive update interval based on device state for battery optimization
     */
    private fun calculateAdaptiveInterval(baseInterval: Long): Long {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager

            val isPowerSaveMode = powerManager.isPowerSaveMode
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            when {
                isPowerSaveMode -> {
                    android.util.Log.d(TAG, "Power save mode active, applying 2x multiplier")
                    (baseInterval * 2) // Double interval in power save
                }
                batteryLevel in 1..19 -> {
                    android.util.Log.d(TAG, "Battery low ($batteryLevel%), applying 1.5x multiplier")
                    (baseInterval * 1.5).toLong() // 1.5x when battery low
                }
                else -> {
                    android.util.Log.d(TAG, "Normal conditions, using base interval")
                    baseInterval
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error calculating adaptive interval, using base", e)
            baseInterval
        }
    }

    /**
     * Update monitoring intervals when settings change
     */
    fun updateDataMonitoringInterval() {
        // Restart both data and battery monitoring with new interval
        workManager.cancelUniqueWork(DataUsageWorker.WORK_NAME)
        workManager.cancelUniqueWork(BatteryWorker.WORK_NAME)
        startDataUsageMonitoring()
        startBatteryMonitoring()
        android.util.Log.d(TAG, "Both data and battery monitoring intervals updated")
    }

    /**
     * Get work info for monitoring status
     */
    fun getDataWorkStatus() = workManager.getWorkInfosForUniqueWorkLiveData(DataUsageWorker.WORK_NAME)

    fun getBatteryWorkStatus() = workManager.getWorkInfosForUniqueWorkLiveData(BatteryWorker.WORK_NAME)

    /**
     * Force immediate data update with high priority
     */
    fun triggerImmediateDataUpdate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val immediateWork = OneTimeWorkRequestBuilder<DataUsageWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(IMMEDIATE_DATA_WORK_TAG)
            .build()

        // Use enqueueUniqueWork to prevent duplicate immediate requests
        workManager.enqueueUniqueWork(
            "immediate_data_update_${System.currentTimeMillis()}",
            ExistingWorkPolicy.REPLACE,
            immediateWork
        )
        android.util.Log.d(TAG, "Immediate data update triggered")
    }

    /**
     * Force immediate battery update with high priority
     */
    fun triggerImmediateBatteryUpdate() {
        val immediateWork = OneTimeWorkRequestBuilder<BatteryWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(IMMEDIATE_BATTERY_WORK_TAG)
            .build()

        // Use enqueueUniqueWork to prevent duplicate immediate requests
        workManager.enqueueUniqueWork(
            "immediate_battery_update_${System.currentTimeMillis()}",
            ExistingWorkPolicy.REPLACE,
            immediateWork
        )
        android.util.Log.d(TAG, "Immediate battery update triggered")
    }

    /**
     * Trigger both data and battery updates immediately
     */
    fun triggerImmediateUpdates() {
        triggerImmediateDataUpdate()
        triggerImmediateBatteryUpdate()
        android.util.Log.d(TAG, "All immediate updates triggered")
    }
}
