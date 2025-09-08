package com.redskul.macrostatshelper.autosync

import android.app.KeyguardManager
import android.content.ContentResolver
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Enhanced enforcement worker that handles both periodic enforcement every 15 minutes
 * and one-time sync disable operations for WorkManager-based scheduling.
 */
class AutoSyncEnforcementWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AutoSyncEnforcement"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val autoSyncManager = AutoSyncManager(applicationContext)
            if (!autoSyncManager.isAutoSyncEnabled()) {
                Log.d(TAG, "AutoSync management disabled, skipping")
                return@withContext Result.success()
            }

            // Check if this is a one-time disable operation or periodic enforcement
            val isOneTimeDisable = tags.contains("syncdisableexact")
            if (isOneTimeDisable) {
                handleOneTimeDisable()
            } else {
                handlePeriodicEnforcement()
            }

            Result.success()
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied in enforcement worker", e)
            Result.failure()
        } catch (e: Exception) {
            Log.e(TAG, "Error in enforcement work", e)
            Result.retry()
        }
    }

    private fun handleOneTimeDisable() {
        Log.d(TAG, "Handling one-time sync disable")

        val prefs = applicationContext.getSharedPreferences("autosyncstate", Context.MODE_PRIVATE)
        val isLocked = prefs.getBoolean("devicelocked", false)
        val scheduledDisableTime = prefs.getLong("scheduleddisabletime", 0)
        val currentTime = System.currentTimeMillis()

        // Verify we're at or past the scheduled disable time
        if (isLocked && scheduledDisableTime > 0 && currentTime >= scheduledDisableTime) {
            val keyguardManager = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

            // Double-check device is still locked
            if (keyguardManager.isKeyguardLocked) {
                val currentSyncState = ContentResolver.getMasterSyncAutomatically()
                if (currentSyncState) {
                    ContentResolver.setMasterSyncAutomatically(false)
                    Log.d(TAG, "AutoSync turned OFF at scheduled time via WorkManager")

                    prefs.edit {
                        remove("scheduleddisabletime")
                        putBoolean("syncdisablescheduled", false)
                    }
                }
            } else {
                Log.d(TAG, "Device was unlocked before disable time, cancelling")
                // Clear scheduled time since device is unlocked
                prefs.edit {
                    remove("scheduleddisabletime")
                }
            }
        }
    }

    private fun handlePeriodicEnforcement() {
        Log.d(TAG, "Handling periodic enforcement check")

        val autoSyncManager = AutoSyncManager(applicationContext)
        val prefs = applicationContext.getSharedPreferences("autosyncstate", Context.MODE_PRIVATE)
        val wasLocked = prefs.getBoolean("devicelocked", false)
        val scheduledDisableTime = prefs.getLong("scheduleddisabletime", 0)
        val keyguardManager = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val currentlyLocked = keyguardManager.isKeyguardLocked
        val currentTime = System.currentTimeMillis()

        when {
            // Case 1: Device was locked, still locked, and past scheduled disable time
            wasLocked && currentlyLocked && scheduledDisableTime > 0 && currentTime >= scheduledDisableTime -> {
                val currentSyncState = ContentResolver.getMasterSyncAutomatically()
                if (currentSyncState) {
                    ContentResolver.setMasterSyncAutomatically(false)
                    Log.d(TAG, "Periodic enforcement disabled sync (overdue)")
                }
                // Clear the scheduled time since we've acted on it
                prefs.edit {
                    remove("scheduleddisabletime")
                    putBoolean("syncdisablescheduled", false)
                }
            }

            // Case 2: Device was locked but is now unlocked - enable sync
            wasLocked && !currentlyLocked -> {
                val currentSyncState = ContentResolver.getMasterSyncAutomatically()
                if (!currentSyncState) {
                    ContentResolver.setMasterSyncAutomatically(true)
                    Log.d(TAG, "Periodic enforcement enabled sync (device unlocked)")
                }
                // Clear lock state since device is unlocked
                prefs.edit {
                    putBoolean("devicelocked", false)
                    remove("locktimestamp")
                    remove("scheduleddisabletime")
                    putBoolean("syncdisablescheduled", false)
                }
            }

            // Case 3: DELETED - No longer automatically enabling sync when unlocked
            // This prevents overriding user/system AutoSync preferences

            // Case 4: Persistent state mismatch - device got locked but we missed it
            !wasLocked && currentlyLocked -> {
                Log.d(TAG, "Updating persistent lock state to match current: $currentlyLocked")
                // Since we only reach this case when wasLocked=false and currentlyLocked=true,
                // we know the device got locked but we missed it
                val newLockTimestamp = System.currentTimeMillis()
                val delayMs = autoSyncManager.getAutoSyncDelay() * 60 * 1000L

                prefs.edit {
                    putBoolean("devicelocked", true)
                    putLong("locktimestamp", newLockTimestamp)
                    putLong("scheduleddisabletime", newLockTimestamp + delayMs)
                    putBoolean("syncdisablescheduled", true)
                }
            }
        }
    }
}
