package com.redskul.macrostatshelper.datausage

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.redskul.macrostatshelper.notification.NotificationHelper
import com.redskul.macrostatshelper.settings.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager Worker that replaces DataUsageService
 * Handles periodic data usage monitoring and notifications
 */
class DataUsageWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "data_usage_monitoring"
        const val ACTION_DATA_UPDATED = "com.redskul.macrostatshelper.DATA_UPDATED"
        private const val TAG = "DataUsageWorker"
    }

    private val dataUsageMonitor = DataUsageMonitor(context)
    private val notificationHelper = NotificationHelper(context)
    private val settingsManager = SettingsManager(context)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            android.util.Log.d(TAG, "Starting data usage monitoring work")

            // Get usage data
            val usageData = dataUsageMonitor.getUsageData()

            // Show notification if enabled
            if (settingsManager.isNotificationEnabled()) {
                withContext(Dispatchers.Main) {
                    notificationHelper.showUsageNotification(usageData)
                }
                android.util.Log.d(TAG, "Data notification shown")
            }

            // Send broadcast to QS tiles
            val broadcastIntent = Intent(ACTION_DATA_UPDATED)
            context.sendBroadcast(broadcastIntent)
            android.util.Log.d(TAG, "Data update broadcast sent")

            // Return success with data for chaining if needed
            val outputData = workDataOf(
                "wifi_daily" to usageData.wifiDaily,
                "wifi_weekly" to usageData.wifiWeekly,
                "wifi_monthly" to usageData.wifiMonthly,
                "mobile_daily" to usageData.mobileDaily,
                "mobile_weekly" to usageData.mobileWeekly,
                "mobile_monthly" to usageData.mobileMonthly
            )

            Result.success(outputData)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in data usage monitoring work", e)
            Result.retry()
        }
    }
}
