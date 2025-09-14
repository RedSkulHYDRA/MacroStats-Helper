package com.redskul.macrostatshelper.utils

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import androidx.work.*
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
     * Ensure all monitoring work is active with proper constraints
     */
    fun ensureMonitoringActive() {
        ensureDataUsageMonitoringActive()
        ensureBatteryMonitoringActive()
        android.util.Log.d(TAG, "All monitoring work ensured active")
    }

    /**
     * Ensure data usage monitoring is active with adaptive constraints
     * (User-configurable interval is kept for data usage only)
     */
    private fun ensureDataUsageMonitoringActive() {
        val baseInterval = settingsManager.getUpdateInterval().toLong()
        val adaptiveInterval = calculateAdaptiveInterval(baseInterval)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val dataWorkRequest = PeriodicWorkRequestBuilder<DataUsageWorker>(
            adaptiveInterval, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            // Use WorkRequest.MIN_BACKOFF_MILLIS instead of old constants
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("data_monitoring")
            .build()

        workManager.enqueueUniquePeriodicWork(
            DataUsageWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            dataWorkRequest
        )

        android.util.Log.d(TAG, "Data usage monitoring ensured active with ${adaptiveInterval}min interval")
    }

    /**
     * UPDATED: Battery monitoring is no longer tied to the user-set interval.
     * Runs every 15 minutes, ONLY when the device is charging.
     */
    private fun ensureBatteryMonitoringActive() {
        // Removed user interval and adaptive interval logic
        val constraints = Constraints.Builder()
            .setRequiresCharging(true) // Only run when charging
            .build()

        val batteryWorkRequest = PeriodicWorkRequestBuilder<BatteryWorker>(
            15, TimeUnit.MINUTES // Fixed 15 minute interval
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.LINEAR,
                WorkRequest.MIN_BACKOFF_MILLIS, // Minimum safe backoff
                TimeUnit.MILLISECONDS
            )
            .addTag("battery_monitoring")
            .build()

        workManager.enqueueUniquePeriodicWork(
            BatteryWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            batteryWorkRequest
        )

        android.util.Log.d(TAG, "Battery monitoring ensured active: 15min interval, only when charging")
    }

    /**
     * Adaptive interval calculation (still used for Data Usage only)
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
                    (baseInterval * 2)
                }
                batteryLevel in 1..19 -> {
                    android.util.Log.d(TAG, "Battery low ($batteryLevel%), applying 1.5x multiplier")
                    (baseInterval * 1.5).toLong()
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
     * Battery monitoring is fixed and does not depend on user interval anymore.
     */
    fun updateDataMonitoringInterval() {
        workManager.cancelUniqueWork(DataUsageWorker.WORK_NAME)
        // No need to cancel or restart battery monitoring for user changes
        ensureDataUsageMonitoringActive()
        android.util.Log.d(TAG, "Data usage monitoring interval updated")
    }

    fun getDataWorkStatus() = workManager.getWorkInfosForUniqueWorkLiveData(DataUsageWorker.WORK_NAME)
    fun getBatteryWorkStatus() = workManager.getWorkInfosForUniqueWorkLiveData(BatteryWorker.WORK_NAME)

    fun triggerImmediateDataUpdate() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val immediateWork = OneTimeWorkRequestBuilder<DataUsageWorker>()
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(IMMEDIATE_DATA_WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            "immediate_data_update_${System.currentTimeMillis()}",
            ExistingWorkPolicy.REPLACE,
            immediateWork
        )
        android.util.Log.d(TAG, "Immediate data update triggered")
    }

    fun triggerImmediateBatteryUpdate() {
        val immediateWork = OneTimeWorkRequestBuilder<BatteryWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(IMMEDIATE_BATTERY_WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            "immediate_battery_update_${System.currentTimeMillis()}",
            ExistingWorkPolicy.REPLACE,
            immediateWork
        )
        android.util.Log.d(TAG, "Immediate battery update triggered")
    }

    fun triggerImmediateUpdates() {
        triggerImmediateDataUpdate()
        triggerImmediateBatteryUpdate()
        android.util.Log.d(TAG, "All immediate updates triggered")
    }
}
