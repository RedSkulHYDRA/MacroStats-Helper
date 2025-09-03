package com.redskul.macrostatshelper.aod

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.*

/**
 * Enhanced broadcast receiver for power connection/disconnection events.
 * Manages AOD state based on charging status with improved lock state handling.
 */
class PowerConnectionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PowerConnectionReceiver"
        private const val CHARGING_DELAY_MS = 1000L // Initial delay
        private const val LOCKED_ADDITIONAL_DELAY_MS = 3000L // Additional delay when locked
        private const val DISCONNECTION_DELAY_MS = 1000L
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 2000L
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
                val isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL)

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
                // Initial delay to ensure stable charging state
                delay(CHARGING_DELAY_MS)

                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                val isLocked = keyguardManager.isKeyguardLocked()

                Log.d(TAG, "Device lock state during charging: $isLocked")

                // If device is locked, add additional delay
                if (isLocked) {
                    Log.d(TAG, "Device locked - adding extra delay of ${LOCKED_ADDITIONAL_DELAY_MS}ms")
                    delay(LOCKED_ADDITIONAL_DELAY_MS)
                }

                // Attempt to enable AOD with retry logic
                enableAODWithRetry(context, isLocked)

            } catch (e: CancellationException) {
                Log.d(TAG, "Charging job cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling power connected", e)
            }
        }
    }

    private suspend fun enableAODWithRetry(context: Context, wasInitiallyLocked: Boolean) {
        val aodManager = AODManager(context)

        if (!aodManager.isAODWhileChargingEnabled()) {
            Log.d(TAG, "AOD while charging is disabled by user")
            return
        }

        if (!aodManager.hasSecureSettingsPermission()) {
            Log.w(TAG, "AOD permission not granted")
            return
        }

        var attempts = 0
        var success = false

        while (attempts < MAX_RETRIES && !success) {
            attempts++

            try {
                Log.d(TAG, "AOD enable attempt $attempts/${MAX_RETRIES}")

                // Double-check lock state before each attempt
                val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                val currentlyLocked = keyguardManager.isKeyguardLocked()

                Log.d(TAG, "Current lock state: $currentlyLocked (was initially: $wasInitiallyLocked)")

                // If device became unlocked since we started, add a small delay
                if (wasInitiallyLocked && !currentlyLocked) {
                    Log.d(TAG, "Device was unlocked since charging started - brief delay")
                    delay(500)
                }

                val enableResult = aodManager.enableAODForChargingEnhanced()

                if (enableResult) {
                    // Verify the change took effect
                    delay(1000) // Give system time to apply changes
                    val currentAODStatus = aodManager.getCurrentAODStatusText()
                    Log.d(TAG, "AOD status after enable attempt: $currentAODStatus")

                    success = true
                    Log.d(TAG, "AOD enabled successfully for charging on attempt $attempts")
                } else {
                    throw Exception("AOD enable returned false")
                }

            } catch (e: Exception) {
                Log.w(TAG, "AOD enable attempt $attempts failed: ${e.message}")

                if (attempts < MAX_RETRIES) {
                    Log.d(TAG, "Retrying in ${RETRY_DELAY_MS}ms...")
                    delay(RETRY_DELAY_MS)
                } else {
                    Log.e(TAG, "Failed to enable AOD after $MAX_RETRIES attempts", e)
                }
            }
        }

        if (!success) {
            Log.e(TAG, "AOD enable completely failed after all retry attempts")
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

            } catch (e: CancellationException) {
                Log.d(TAG, "Disconnection job cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling power disconnected", e)
            }
        }
    }
}
