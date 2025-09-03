package com.redskul.macrostatshelper.autosync

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
 */
class AutoSyncAccessibilityService : AccessibilityService() {

    private lateinit var autoSyncManager: AutoSyncManager
    private var turnOffSyncRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isDeviceLocked = false
    private var lastEventTime = 0L

    // Receiver for handling alarm-based sync disable
    private val syncDisableReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Received alarm for sync disable")
            handleAlarmSyncDisable()
        }
    }

    companion object {
        private const val MIN_EVENT_INTERVAL = 1000L // Minimum 1 second between events
        private const val TAG = "AutoSyncAccessibility"
        private const val ALARM_REQUEST_CODE = 1001
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
            } catch (_: Settings.SettingNotFoundException) {
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

        // Register the sync disable receiver
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

        Log.d(TAG, "Scheduling autosync turn-off in $delayMinutes minutes using ${if (delayMinutes >= 15) "WorkManager" else "AlarmManager"}")

        // Cancel any existing scheduled operations
        cancelAllScheduledOperations()

        // Schedule based on delay duration
        scheduleAutoSyncDisable(delayMs)
    }

    private fun scheduleAutoSyncDisable(delayMs: Long) {
        val delayMinutes = delayMs / (60 * 1000L)

        if (delayMinutes >= 15) {
            // Use WorkManager for delays >= 15 minutes
            scheduleWithWorkManager(delayMs)
        } else {
            // Use AlarmManager for delays < 15 minutes
            scheduleWithAlarmManager(delayMs)
        }

        // Always add handler backup for additional reliability
        scheduleHandlerBackup(delayMs)
    }

    private fun scheduleWithAlarmManager(delayMs: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Check if we can schedule exact alarms for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarms, falling back to WorkManager")
                scheduleWithWorkManager(delayMs)
                return
            }
        }

        // Create PendingIntent for the internal receiver
        val intent = Intent(ACTION_SYNC_DISABLE)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + delayMs

        try {
            // Use exact alarm for precise timing with proper exception handling
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
            Log.d(TAG, "Scheduled exact alarm in ${delayMs}ms")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException scheduling exact alarm, falling back to WorkManager", e)
            scheduleWithWorkManager(delayMs)
        }
    }

    private fun scheduleWithWorkManager(delayMs: Long) {
        val workRequest = OneTimeWorkRequestBuilder<AutoSyncEnforcementWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .addTag("sync_disable_exact")
            .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "exact_sync_disable",
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        Log.d(TAG, "Scheduled WorkManager sync disable in ${delayMs}ms")
    }

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
            if (isStillLocked && scheduledTime > 0 && currentTime >= scheduledTime - 5000) { // 5s tolerance
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

                if (keyguardManager.isKeyguardLocked) {
                    try {
                        val currentSyncState = ContentResolver.getMasterSyncAutomatically()
                        if (currentSyncState) {
                            ContentResolver.setMasterSyncAutomatically(false)
                            Log.d(TAG, "AutoSync turned OFF via handler backup")
                        }

                        // Update persistence using KTX extension
                        prefs.edit {
                            putBoolean("syncdisablescheduled", false)
                            remove("scheduleddisabletime")
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

    private fun handleAlarmSyncDisable() {
        if (!autoSyncManager.isAutoSyncEnabled()) {
            Log.d(TAG, "AutoSync disabled, ignoring alarm")
            return
        }

        val prefs = getSharedPreferences("autosyncstate", Context.MODE_PRIVATE)
        val isLocked = prefs.getBoolean("devicelocked", false)
        val scheduledDisableTime = prefs.getLong("scheduleddisabletime", 0)
        val currentTime = System.currentTimeMillis()

        // Verify conditions are still met (30s tolerance for timing)
        if (isLocked && scheduledDisableTime > 0 && currentTime >= scheduledDisableTime - 30000) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

            if (keyguardManager.isKeyguardLocked) {
                try {
                    val currentSyncState = ContentResolver.getMasterSyncAutomatically()
                    if (currentSyncState) {
                        ContentResolver.setMasterSyncAutomatically(false)
                        Log.d(TAG, "AutoSync turned OFF via alarm")

                        // Update persistence using KTX extension
                        prefs.edit {
                            putBoolean("syncdisablescheduled", false)
                            remove("scheduleddisabletime")
                        }
                    }
                } catch (e: SecurityException) {
                    Log.e(TAG, "Permission denied to change sync settings", e)
                } catch (e: Exception) {
                    Log.e(TAG, "Error disabling sync via alarm", e)
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

    private fun cancelAllScheduledOperations() {
        cancelTurnOffSync() // Handler
        cancelAlarmManager() // AlarmManager
        WorkManager.getInstance(this).cancelUniqueWork("exact_sync_disable") // WorkManager
    }

    private fun cancelTurnOffSync() {
        turnOffSyncRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
            Log.d(TAG, "Cancelled scheduled autosync turn-off")
        }
        turnOffSyncRunnable = null
    }

    private fun cancelAlarmManager() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ACTION_SYNC_DISABLE)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
            Log.d(TAG, "Cancelled scheduled alarm")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelAllScheduledOperations()

        try {
            unregisterReceiver(syncDisableReceiver)
        } catch (_: IllegalArgumentException) {
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
            "autosync_enforcement",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}
