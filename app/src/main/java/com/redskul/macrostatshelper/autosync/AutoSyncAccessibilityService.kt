package com.redskul.macrostatshelper.autosync

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ContentResolver
import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.edit
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Accessibility service that manages auto-sync functionality based on device lock state.
 * Monitors device lock/unlock events and automatically toggles sync settings after a configured delay.
 *
 * Uses:
 * - Foreground service for 15-minute delay (precise timing)
 * - WorkManager for delays > 15 minutes (30, 45, 60 minutes - reliable background execution)
 *
 * Simplified: Removed periodic enforcement, handler backup, and legacy alarm-based broadcast receiver.
 */
class AutoSyncAccessibilityService : AccessibilityService() {

    private lateinit var autoSyncManager: AutoSyncManager
    private var isDeviceLocked = false
    private var lastEventTime = 0L

    companion object {
        private const val MIN_EVENT_INTERVAL = 1000L // Minimum 1 second between events
        private const val TAG = "AutoSyncAccessibility"

        /**
         * Helper method to check if accessibility service is enabled
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val accessibilityEnabled = try {
                Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED
                )
            } catch (e: Settings.SettingNotFoundException) {
                0
            }

            if (accessibilityEnabled == 1) {
                val services = Settings.Secure.getString(
                    context.contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ) ?: ""
                val packageName = context.packageName

                return services.contains("$packageName/.autosync.AutoSyncAccessibilityService") ||
                        services.contains("$packageName/com.redskul.macrostatshelper.autosync.AutoSyncAccessibilityService") ||
                        services.contains("AutoSyncAccessibilityService")
            }
            return false
        }
    }

    override fun onCreate() {
        super.onCreate()
        autoSyncManager = AutoSyncManager(this)
        Log.d(TAG, "Accessibility service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")

        // Initialize lock state
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        isDeviceLocked = keyguardManager.isKeyguardLocked
        Log.d(TAG, "Initial lock state: $isDeviceLocked")

        // Check persisted state on restart
        checkPersistedState()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!autoSyncManager.isAutoSyncEnabled()) return

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEventTime < MIN_EVENT_INTERVAL) return
        lastEventTime = currentTime

        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    checkLockStateChange()
                }
                else -> { /* ignore other events */ }
            }
        }
    }

    private fun checkLockStateChange() {
        try {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val currentLockState = keyguardManager.isKeyguardLocked

            if (currentLockState != isDeviceLocked) {
                isDeviceLocked = currentLockState
                Log.d(TAG, "Lock state changed to: $isDeviceLocked")

                if (isDeviceLocked) {
                    handleDeviceLocked()
                } else {
                    handleDeviceUnlocked()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking lock state", e)
        }
    }

    private fun handleDeviceLocked() {
        Log.d(TAG, "Device locked detected")

        if (!autoSyncManager.isAutoSyncEnabled()) return

        val lockTimestamp = System.currentTimeMillis()
        persistLockState(true, lockTimestamp)

        val delayMinutes = autoSyncManager.getAutoSyncDelay()
        val delayMs = delayMinutes * 60 * 1000L

        Log.d(TAG, "Scheduling autosync turn-off in $delayMinutes minutes using " +
                if (delayMinutes == 15) "ForegroundService" else "WorkManager")

        cancelAllScheduledOperations()
        scheduleAutoSyncDisable(delayMs)
    }

    private fun scheduleAutoSyncDisable(delayMs: Long) {
        val delayMinutes = autoSyncManager.getAutoSyncDelay()

        when (delayMinutes) {
            15 -> scheduleWithForegroundService(delayMs)
            30, 45, 60 -> scheduleWithWorkManager(delayMs)
            else -> scheduleWithWorkManager(delayMs)
        }
    }

    private fun scheduleWithForegroundService(delayMs: Long) {
        val prefs = getSharedPreferences("autosyncstate", Context.MODE_PRIVATE)
        val scheduledTime = prefs.getLong("scheduleddisabletime", 0)

        // ✅ Use remaining delay instead of full delay
        val currentTime = System.currentTimeMillis()
        val remainingDelay = if (scheduledTime > 0) {
            kotlin.math.max(0, scheduledTime - currentTime)
        } else {
            delayMs
        }

        AutoSyncForegroundService.scheduleAutoSyncDisable(this, remainingDelay, scheduledTime)
        Log.d(TAG, "Scheduled foreground service sync disable in ${remainingDelay}ms (was ${delayMs}ms)")
    }

    private fun scheduleWithWorkManager(delayMs: Long) {
        val workRequest = OneTimeWorkRequestBuilder<AutoSyncEnforcementWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag("syncdisableexact")
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "exactsyncdisable",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
        Log.d(TAG, "Scheduled WorkManager sync disable in ${delayMs}ms")
    }

    private fun handleDeviceUnlocked() {
        Log.d(TAG, "Device unlocked detected")

        persistLockState(false)

        if (!autoSyncManager.isAutoSyncEnabled()) return

        cancelAllScheduledOperations()

        try {
            if (!ContentResolver.getMasterSyncAutomatically()) {
                ContentResolver.setMasterSyncAutomatically(true)
                Log.d(TAG, "AutoSync turned ON (device unlocked)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error turning on sync", e)
        }
    }

    private fun cancelAllScheduledOperations() {
        AutoSyncForegroundService.cancelScheduledSyncDisable(this)
        WorkManager.getInstance(this).cancelUniqueWork("exactsyncdisable")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAllScheduledOperations()
        Log.d(TAG, "Accessibility service destroyed")
    }

    private fun persistLockState(isLocked: Boolean, lockTimestamp: Long = 0) {
        val prefs = getSharedPreferences("autosyncstate", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("devicelocked", isLocked)
            putLong("locktimestamp", lockTimestamp)
            putBoolean("syncdisablescheduled", isLocked)

            if (isLocked && lockTimestamp > 0) {
                val delayMs = autoSyncManager.getAutoSyncDelay() * 60 * 1000L
                putLong("scheduleddisabletime", lockTimestamp + delayMs)
            } else {
                remove("scheduleddisabletime")
            }
        }
    }

    private fun checkPersistedState() {
        val prefs = getSharedPreferences("autosyncstate", Context.MODE_PRIVATE)
        val wasLocked = prefs.getBoolean("devicelocked", false)
        val lockTimestamp = prefs.getLong("locktimestamp", 0)
        val scheduledDisableTime = prefs.getLong("scheduleddisabletime", 0)
        val currentTime = System.currentTimeMillis()

        if (wasLocked && lockTimestamp > 0 && scheduledDisableTime > 0) {
            val delayMs = autoSyncManager.getAutoSyncDelay() * 60 * 1000L
            val timeSinceLock = currentTime - lockTimestamp

            if (timeSinceLock >= delayMs) {
                if (ContentResolver.getMasterSyncAutomatically()) {
                    ContentResolver.setMasterSyncAutomatically(false)
                    Log.d(TAG, "Disabled sync on service restart (overdue)")
                }
            } else if (currentTime < scheduledDisableTime) {
                val remainingDelay = scheduledDisableTime - currentTime
                Log.d(TAG, "Rescheduling sync disable with remaining delay: ${remainingDelay}ms")
                scheduleAutoSyncDisable(remainingDelay)
            }
        }
    }
}
