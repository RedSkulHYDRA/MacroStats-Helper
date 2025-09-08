package com.redskul.macrostatshelper.autosync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.redskul.macrostatshelper.R

/**
 * Foreground service for handling short autosync delays with precise timing.
 * Used for delays shorter than or equal to 15 minutes to ensure reliable execution.
 * Provides immediate, accurate timing without being affected by Doze mode or battery optimization.
 */
class AutoSyncForegroundService : Service() {

    private var syncDisableRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var notificationManager: NotificationManager

    companion object {
        private const val TAG = "AutoSyncForegroundService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "autosync_channel"
        private const val CHANNEL_NAME = "AutoSync Management"

        const val ACTION_SCHEDULE_SYNC_DISABLE = "com.redskul.macrostatshelper.SCHEDULE_SYNC_DISABLE"
        const val EXTRA_DELAY_MS = "delay_ms"
        const val EXTRA_SCHEDULED_TIME = "scheduled_time"

        /**
         * Start the foreground service to schedule sync disable after a delay
         */
        fun scheduleAutoSyncDisable(context: Context, delayMs: Long, scheduledTime: Long) {
            val intent = Intent(context, AutoSyncForegroundService::class.java).apply {
                action = ACTION_SCHEDULE_SYNC_DISABLE
                putExtra(EXTRA_DELAY_MS, delayMs)
                putExtra(EXTRA_SCHEDULED_TIME, scheduledTime)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Log.d(TAG, "Foreground service start requested with delay: ${delayMs}ms")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground service", e)
            }
        }

        /**
         * Stop the foreground service and cancel any pending sync disable
         */
        fun cancelScheduledSyncDisable(context: Context) {
            try {
                val intent = Intent(context, AutoSyncForegroundService::class.java)
                context.stopService(intent)
                Log.d(TAG, "Foreground service stop requested")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping foreground service", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        Log.d(TAG, "AutoSync foreground service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SCHEDULE_SYNC_DISABLE -> {
                val delayMs = intent.getLongExtra(EXTRA_DELAY_MS, 0)
                val scheduledTime = intent.getLongExtra(EXTRA_SCHEDULED_TIME, 0)

                if (delayMs > 0) {
                    // CRITICAL: Call startForeground() immediately
                    val notification = createNotification(delayMs)
                    startForeground(NOTIFICATION_ID, notification)

                    // Calculate actual remaining time if service restarted
                    val currentTime = System.currentTimeMillis()
                    val remainingDelay = if (scheduledTime > 0) {
                        (scheduledTime - currentTime).coerceAtLeast(1000L) // At least 1 second
                    } else {
                        delayMs
                    }

                    Log.d(TAG, "=== FOREGROUND SERVICE TIMING DEBUG ===")
                    Log.d(TAG, "Original delay: ${delayMs}ms")
                    Log.d(TAG, "Scheduled time: $scheduledTime")
                    Log.d(TAG, "Current time: $currentTime")
                    Log.d(TAG, "Calculated remaining delay: ${remainingDelay}ms")

                    scheduleSyncDisable(remainingDelay, scheduledTime)
                    Log.d(TAG, "Foreground service started with remaining delay: ${remainingDelay}ms")
                } else {
                    Log.w(TAG, "Invalid delay provided: $delayMs, stopping service")
                    stopSelf()
                }
            }
            else -> {
                Log.w(TAG, "Unknown action: ${intent?.action}, stopping service")
                stopSelf()
            }
        }

        // Don't restart if killed - we have other backup mechanisms
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Creates notification channel for Android O and above
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Manages automatic sync disable timing for precise execution"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }
    }

    /**
     * Creates the foreground service notification
     */
    private fun createNotification(delayMs: Long): Notification {
        val delayMinutes = (delayMs / (60 * 1000L)).toInt()
        val delaySeconds = ((delayMs % (60 * 1000L)) / 1000L).toInt()

        val contentText = when {
            delayMinutes > 0 -> "AutoSync will turn off in $delayMinutes minutes"
            delaySeconds > 30 -> "AutoSync will turn off in less than a minute"
            else -> "AutoSync turning off shortly..."
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AutoSync Management Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_sync)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setShowWhen(false)
            .build().also {
                Log.d(TAG, "Notification created: $contentText")
            }
    }

    /**
     * Schedules the sync disable operation
     * Use the exact remaining delay, don't recalculate
     */
    private fun scheduleSyncDisable(delayMs: Long, scheduledTime: Long) {
        // Cancel any existing scheduled operation
        syncDisableRunnable?.let { handler.removeCallbacks(it) }

        Log.d(TAG, "Scheduling sync disable in exactly ${delayMs}ms at absolute time: $scheduledTime")

        syncDisableRunnable = Runnable {
            Log.d(TAG, "=== EXECUTING SYNC DISABLE ===")
            Log.d(TAG, "Actual execution time: ${System.currentTimeMillis()}")
            Log.d(TAG, "Was scheduled for: $scheduledTime")
            Log.d(TAG, "Delay from start: ${System.currentTimeMillis() - (scheduledTime - delayMs)}ms")

            try {
                executeSyncDisable(scheduledTime)
            } finally {
                // Always stop service after execution attempt
                stopSelf()
            }
        }

        // Use the EXACT remaining delay passed in, don't recalculate
        syncDisableRunnable?.let { handler.postDelayed(it, delayMs) }
    }

    /**
     * Executes the actual sync disable operation with safety checks
     */
    private fun executeSyncDisable(scheduledTime: Long) {
        Log.d(TAG, "Executing sync disable operation")

        try {
            // Verify AutoSync is still enabled
            val autoSyncManager = AutoSyncManager(this)
            if (!autoSyncManager.isAutoSyncEnabled()) {
                Log.d(TAG, "AutoSync disabled by user, skipping sync disable")
                return
            }

            // Check persistent state to ensure conditions are still valid
            val prefs = getSharedPreferences("autosyncstate", Context.MODE_PRIVATE)
            val isLocked = prefs.getBoolean("devicelocked", false)
            val savedScheduledTime = prefs.getLong("scheduleddisabletime", 0)
            val currentTime = System.currentTimeMillis()

            // Verify timing and device state (allow 30s tolerance for timing)
            if (isLocked && scheduledTime > 0 && (currentTime >= scheduledTime - 30000)) {
                // Double-check device is still locked using KeyguardManager
                val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager

                if (keyguardManager.isKeyguardLocked) {
                    // Device is confirmed locked, proceed with sync disable
                    val currentSyncState = ContentResolver.getMasterSyncAutomatically()
                    if (currentSyncState) {
                        ContentResolver.setMasterSyncAutomatically(false)
                        Log.d(TAG, "AutoSync turned OFF via foreground service")

                        // Update persistent state to reflect completed operation
                        prefs.edit {
                            putBoolean("syncdisablescheduled", false)
                            remove("scheduleddisabletime")
                        }
                    } else {
                        Log.d(TAG, "AutoSync was already disabled")
                    }
                } else {
                    Log.d(TAG, "Device was unlocked before scheduled time, cancelling sync disable")
                    // Clear scheduled time since device is unlocked
                    prefs.edit {
                        remove("scheduleddisabletime")
                    }
                }
            } else {
                Log.d(TAG, "Conditions no longer met for sync disable. " +
                        "Locked: $isLocked, ScheduledTime: $scheduledTime, CurrentTime: $currentTime")
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "Permission denied to change sync settings", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling sync via foreground service", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up any pending operations
        syncDisableRunnable?.let { handler.removeCallbacks(it) }
        syncDisableRunnable = null

        Log.d(TAG, "AutoSync foreground service destroyed")
    }
}
