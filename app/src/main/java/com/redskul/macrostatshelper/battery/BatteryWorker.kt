package com.redskul.macrostatshelper.battery

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager Worker that replaces BatteryService
 * Handles periodic battery monitoring with intelligent scheduling
 */
class BatteryWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "battery_monitoring"
        const val ACTION_BATTERY_UPDATED = "com.redskul.macrostatshelper.BATTERY_UPDATED"
        private const val TAG = "BatteryWorker"
    }

    private val batteryChargeMonitor = BatteryChargeMonitor(context)

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            android.util.Log.d(TAG, "Starting battery monitoring work")

            // Get battery data
            val chargeData = batteryChargeMonitor.getChargeData()

            // Log battery data for validation
            android.util.Log.d(TAG, "Battery data updated - Cycles: ${chargeData.chargeCycles}, Capacity: ${chargeData.batteryCapacity}")

            // Send broadcast to battery tiles
            val broadcastIntent = Intent(ACTION_BATTERY_UPDATED)
            context.sendBroadcast(broadcastIntent)
            android.util.Log.d(TAG, "Battery update broadcast sent")

            // Return success with battery data
            val outputData = workDataOf(
                "charge_cycles" to chargeData.chargeCycles,
                "battery_capacity" to chargeData.batteryCapacity
            )

            Result.success(outputData)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error in battery monitoring work", e)
            Result.retry()
        }
    }
}
