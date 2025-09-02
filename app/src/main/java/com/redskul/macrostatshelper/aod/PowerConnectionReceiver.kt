package com.redskul.macrostatshelper.aod

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.*

/**
 * Broadcast receiver for power connection/disconnection events.
 * Manages AOD state based on charging status with proper delay.
 */
class PowerConnectionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PowerConnectionReceiver"
        private const val CHARGING_DELAY_MS = 2000L // 2 seconds delay
        private const val DISCONNECTION_DELAY_MS = 1000L // 1 second delay
    }

    private var chargingJob: Job? = null

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")

        // Cancel any pending operations
        chargingJob?.cancel()

        when (intent.action) {
            Intent.ACTION_POWER_CONNECTED -> {
                Log.d(TAG, "Power connected")
                handlePowerConnected(context)
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.d(TAG, "Power disconnected")
                handlePowerDisconnected(context)
            }
            Intent.ACTION_BATTERY_CHANGED -> {
                // Additional check for charging status changes
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                Log.d(TAG, "Battery status changed. Charging: $isCharging, Status: $status")

                if (isCharging) {
                    handlePowerConnected(context)
                } else {
                    handlePowerDisconnected(context)
                }
            }
        }
    }

    private fun handlePowerConnected(context: Context) {
        chargingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Small delay to ensure stable charging state
                delay(CHARGING_DELAY_MS)

                val aodManager = AODManager(context)
                if (aodManager.isAODWhileChargingEnabled()) {
                    aodManager.enableAODForCharging()
                    Log.d(TAG, "AOD enabled for charging after delay")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling power connected", e)
            }
        }
    }

    private fun handlePowerDisconnected(context: Context) {
        chargingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                // Small delay to ensure stable disconnection
                delay(DISCONNECTION_DELAY_MS)

                val aodManager = AODManager(context)
                if (aodManager.isAODWhileChargingEnabled()) {
                    aodManager.restoreAODAfterCharging()
                    Log.d(TAG, "AOD settings restored after charging with delay")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling power disconnected", e)
            }
        }
    }
}
