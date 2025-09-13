package com.redskul.macrostatshelper.autosync

import android.app.KeyguardManager
import android.content.ContentResolver
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker that handles AutoSync disable operations.
 */
class AutoSyncEnforcementWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "AutoSyncEnforcement"
        private const val AUTOSYNC_DISABLE_WORK_NAME = "autosync_disable_exact"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            val autoSyncManager = AutoSyncManager(applicationContext)
            if (!autoSyncManager.isAutoSyncEnabled()) {
                Log.d(TAG, "AutoSync management disabled, skipping")
                return@withContext Result.success()
            }

            if (tags.contains(AUTOSYNC_DISABLE_WORK_NAME)) {
                handleScheduledAutoSyncDisable()
            } else {
                Log.d(TAG, "No matching operation for this worker")
            }

            // Always cancel after execution to prevent piling
            WorkManager.getInstance(applicationContext).cancelUniqueWork(AUTOSYNC_DISABLE_WORK_NAME)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in enforcement work", e)
            Result.retry()
        }
    }

    private fun handleScheduledAutoSyncDisable() {
        Log.d(TAG, "Handling scheduled AutoSync disable")

        try {
            val prefs = applicationContext.getSharedPreferences("autosyncstate", Context.MODE_PRIVATE)
            val isLocked = prefs.getBoolean("devicelocked", false)
            val scheduledDisableTime = prefs.getLong("scheduleddisabletime", 0)
            val currentTime = System.currentTimeMillis()

            if (isLocked && scheduledDisableTime > 0 && currentTime >= (scheduledDisableTime - 30000)) {
                val keyguardManager = applicationContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (keyguardManager.isKeyguardLocked) {
                    val currentSyncState = ContentResolver.getMasterSyncAutomatically()
                    if (currentSyncState) {
                        ContentResolver.setMasterSyncAutomatically(false)
                        Log.d(TAG, "AutoSync turned OFF at scheduled time via WorkManager")

                        prefs.edit {
                            remove("scheduleddisabletime")
                            putBoolean("syncdisablescheduled", false)
                        }
                    } else {
                        Log.d(TAG, "AutoSync already OFF")
                    }
                } else {
                    Log.d(TAG, "Device unlocked before disable, cancelling state")
                    prefs.edit {
                        remove("scheduleddisabletime")
                        putBoolean("devicelocked", false)
                        putBoolean("syncdisablescheduled", false)
                    }
                }
            } else {
                Log.d(TAG, "Conditions not met for sync disable (overdue or unlocked)")
                prefs.edit {
                    remove("scheduleddisabletime")
                    putBoolean("syncdisablescheduled", false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling sync via WorkManager", e)
        }
    }
}
