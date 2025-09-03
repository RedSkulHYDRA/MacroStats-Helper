package com.redskul.macrostatshelper.autosync

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.edit
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Accessibility service that manages auto-sync functionality based on device lock state.
 * Monitors device lock/unlock events and automatically toggles sync settings after a configured delay.
 *
 * Updated to use:
 * - Foreground service for delays ≤ 15 minutes (precise timing)
 * - WorkManager for delays > 15 minutes (reliable background execution)
 */
class AutoSyncAccessibilityService : AccessibilityService() {

    private lateinit var autoSyncManager: AutoSyncManager
    private var turnOffSyncRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isDeviceLocked = false
    private var lastEventTime = 0L

    // Receiver for handling alarm-based sync disable (kept for backward compatibility)
    private val syncDisableReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Received broadcast for sync disable")
            handleLegacySyncDisable()
        }
    }

    companion object {
        private const val MIN_EVENT_INTERVAL = 1000L // Minimum 1 second between events
        private const val TAG = "AutoSyncAccessibility"
        private const val ACTION_SYNC_DISABLE = "com.redskul.macrostatshelper.SYNC_DISABLE"

        /**
         * Helper method to check if accessibility service is enabled
         * @param context The application context
         * @return true if accessibility service is enabled, false otherwise
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

                // Try multiple possible formats
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

        // Register the legacy sync disable receiver
        val filter = IntentFilter(ACTION_SYNC_DISABLE)
        registerReceiver(syncDisableReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        Log.d(TAG, "Accessibility service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")

        // Initialize device lock state
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        isDeviceLocked = keyguardManager.isKeyguardLocked
        Log.d(TAG, "Initial lock state: $isDeviceLocked")

        // Check persisted state on service restart
        checkPersistedState()

        // Schedule backup enforcement
        scheduleBackupEnforcement()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!autoSyncManager.isAutoSyncEnabled()) return

        // Throttle events to prevent spam
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEventTime < MIN_EVENT_INTERVAL) return
        lastEventTime = currentTime

        event?.let {
            when (it.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    checkLockStateChange()
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    // Secondary check for lock state changes
                    checkLockStateChange()
                }
                // Add empty cases for other events to satisfy the warning
                else -> {
                    // No action needed for other event types
                }
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

        if (!autoSyncManager.isAutoSyncEnabled()) {
            Log.d(TAG, "AutoSync management disabled, ignoring lock event")
            return
        }

        val lockTimestamp = System.currentTimeMillis()
        persistLockState(true, lockTimestamp)

        val delayMinutes = autoSyncManager.getAutoSyncDelay()
        val delayMs = delayMinutes * 60 * 1000L

        Log.d(TAG, "Scheduling autosync turn-off in $delayMinutes minutes using ${
            if (delayMinutes <= 15) "ForegroundService" else "WorkManager"
        }")

        // Cancel any existing scheduled operations
        cancelAllScheduledOperations()

        // Schedule based on delay duration
        scheduleAutoSyncDisable(delayMs)
    }

    /**
     * Schedules auto sync disable using the appropriate method based on delay duration
     * @param delayMs Delay in milliseconds
     */
    private fun scheduleAutoSyncDisable(delayMs: Long) {
        val delayMinutes = delayMs / (60 * 1000L)

        if (delayMinutes <= 15) {
            // Use foreground service for short delays (≤ 15 minutes)
            scheduleWithForegroundService(delayMs)
        } else {
            // Use WorkManager for longer delays (> 15 minutes)
            scheduleWithWorkManager(delayMs)
        }

        // Always add handler backup for additional reliability
        scheduleHandlerBackup(delayMs)
    }

    /**
     * Schedules sync disable using foreground service for precise timing
     * @param delayMs Delay in milliseconds
     */
    private fun scheduleWithForegroundService(delayMs: Long) {
        val prefs = getSharedPreferences("autosyncstate", Context.MODE_PRIVATE)
        val scheduledTime = prefs.getLong("scheduleddisabletime", 0)

        // Start the foreground service
        AutoSyncForegroundService.scheduleAutoSyncDisable(this, delayMs, scheduledTime)
        Log.d(TAG, "Scheduled foreground service sync disable in ${delayMs}ms")
    }

    /**
     * Schedules sync disable using WorkManager for longer delays
     * @param delayMs Delay in milliseconds
     */
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

    /**
     * Schedules handler backup for additional reliability
     * @param delayMs Delay in milliseconds
     */
    private fun scheduleHandlerBackup(delayMs: Long) {
        // Cancel existing
        cancelTurnOffSync()

        // Create runnable with enhanced checks
        turnOffSyncRunnable = Runnable {
            if (!autoSyncManager.isAutoSyncEnabled()) return@Runnable

            val prefs = getSharedPreferences("autosyncstate", Context.MODE_PRIVATE)
            val isStillLocked = prefs.getBoolean("devicelocked", false)
            val scheduledTime = prefs.getLong("scheduleddisabletime", 0)
            val currentTime = System.currentTimeMillis()

            // Only execute if we're at or past the scheduled time
            if (isStillLocked && scheduledTime > 0 && currentTime >= (scheduledTime - 5000)) { // 5s tolerance
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                if (keyguardManager.isKeyguardLocked) {
                    try {
                        val currentSyncState = ContentResolver.getMasterSyncAutomatically()
                        if (currentSyncState) {
                            ContentResolver.setMasterSyncAutomatically(false)
                            Log.d(TAG, "AutoSync turned OFF via handler backup")

                            // Update persistence using KTX extension
                            prefs.edit {
                                putBoolean("syncdisablescheduled", false)
                                remove("scheduleddisabletime")
                            }
                        }
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Permission denied to change sync settings", e)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in handler backup", e)
                    }
                }
            }
        }

        turnOffSyncRunnable?.let { handler.postDelayed(it, delayMs) }
    }

    /**
     * Handle legacy alarm-based sync disable (kept for backward compatibility)
     */
    private fun handleLegacySyncDisable() {
        if (!autoSyncManager.isAutoSyncEnabled()) {
            Log.d(TAG, "AutoSync disabled, ignoring legacy alarm")
            return
        }

        val prefs = getSharedPreferences("autosyncstate", Context.MODE_PRIVATE)
        val isLocked = prefs.getBoolean("devicelocked", false)
        val scheduledDisableTime = prefs.getLong("scheduleddisabletime", 0)
        val currentTime = System.currentTimeMillis()

        // Verify conditions are still met (30s tolerance for timing)
        if (isLocked && scheduledDisableTime > 0 && currentTime >= (scheduledDisableTime - 30000)) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            if (keyguardManager.isKeyguardLocked) {
                try {
                    val currentSyncState = ContentResolver.getMasterSyncAutomatically()
                    if (currentSyncState) {
                        ContentResolver.setMasterSyncAutomatically(false)
                        Log.d(TAG, "AutoSync turned OFF via legacy alarm")

                        // Update persistence using KTX extension
                        prefs.edit {
                            putBoolean("syncdisablescheduled", false)
                            remove("scheduleddisabletime")
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission denied to change sync settings", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Error disabling sync via legacy alarm", e)
                }
            } else {
                Log.d(TAG, "Device unlocked before alarm trigger, ignoring")
                // Clear scheduled time since device is unlocked
                prefs.edit {
                    remove("scheduleddisabletime")
                }
            }
        }
    }

    private fun handleDeviceUnlocked() {
        Log.d(TAG, "Device unlocked detected")

        // Clear persistence immediately
        persistLockState(false)

        if (!autoSyncManager.isAutoSyncEnabled()) {
            Log.d(TAG, "AutoSync management disabled, ignoring unlock event")
            return
        }

        // Cancel any pending turn-off sync operation
        cancelAllScheduledOperations()

        // Turn on sync immediately when device is unlocked
        try {
            val currentSyncState = ContentResolver.getMasterSyncAutomatically()
            if (!currentSyncState) {
                ContentResolver.setMasterSyncAutomatically(true)
                Log.d(TAG, "AutoSync turned ON (device unlocked)")
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to change sync settings", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error turning on sync", e)
        }
    }

    /**
     * Cancel all scheduled operations across all methods
     */
    private fun cancelAllScheduledOperations() {
        cancelTurnOffSync() // Handler
        cancelForegroundService() // Foreground service
        // WorkManager
        WorkManager.getInstance(this).cancelUniqueWork("exactsyncdisable")
    }

    private fun cancelTurnOffSync() {
        turnOffSyncRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
            Log.d(TAG, "Cancelled scheduled autosync turn-off")
        }
        turnOffSyncRunnable = null
    }

    /**
     * Cancel the foreground service
     */
    private fun cancelForegroundService() {
        AutoSyncForegroundService.cancelScheduledSyncDisable(this)
        Log.d(TAG, "Cancelled foreground service")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAllScheduledOperations()

        try {
            unregisterReceiver(syncDisableReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered
        }

        Log.d(TAG, "Accessibility service destroyed")
    }

    // Enhanced Persistence methods
    private fun persistLockState(isLocked: Boolean, lockTimestamp: Long = 0) {
        val prefs = getSharedPreferences("autosyncstate", Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean("devicelocked", isLocked)
            putLong("locktimestamp", lockTimestamp)
            putBoolean("syncdisablescheduled", isLocked)

            // Store the exact time when sync should be disabled
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
                // Should have disabled sync by now
                if (ContentResolver.getMasterSyncAutomatically()) {
                    ContentResolver.setMasterSyncAutomatically(false)
                    Log.d(TAG, "Disabled sync on service restart (overdue)")
                }
            } else if (currentTime < scheduledDisableTime) {
                // Still within the delay period, reschedule
                val remainingDelay = scheduledDisableTime - currentTime
                Log.d(TAG, "Rescheduling sync disable with remaining delay: ${remainingDelay}ms")
                scheduleAutoSyncDisable(remainingDelay)
            }
        }
    }

    private fun scheduleBackupEnforcement() {
        val workRequest = PeriodicWorkRequestBuilder<AutoSyncEnforcementWorker>(
            15, TimeUnit.MINUTES // Check every 15 minutes
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "autosyncenforcement",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
