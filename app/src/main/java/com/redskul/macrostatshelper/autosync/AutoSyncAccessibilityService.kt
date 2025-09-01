package com.redskul.macrostatshelper.autosync

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.ContentResolver
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent

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

    companion object {
        private const val MIN_EVENT_INTERVAL = 1000L // Minimum 1 second between events

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
        Log.d("AutoSyncAccessibility", "Accessibility service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AutoSyncAccessibility", "Accessibility service connected")

        // Initialize device lock state
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        isDeviceLocked = keyguardManager.isKeyguardLocked
        Log.d("AutoSyncAccessibility", "Initial lock state: $isDeviceLocked")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!autoSyncManager.isAutoSyncEnabled()) {
            return
        }

        // Throttle events to prevent spam
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastEventTime < MIN_EVENT_INTERVAL) {
            return
        }
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
            }
        }
    }

    private fun checkLockStateChange() {
        try {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val currentLockState = keyguardManager.isKeyguardLocked

            if (currentLockState != isDeviceLocked) {
                isDeviceLocked = currentLockState
                Log.d("AutoSyncAccessibility", "Lock state changed to: $isDeviceLocked")

                if (isDeviceLocked) {
                    handleDeviceLocked()
                } else {
                    handleDeviceUnlocked()
                }
            }
        } catch (e: Exception) {
            Log.e("AutoSyncAccessibility", "Error checking lock state", e)
        }
    }

    private fun handleDeviceLocked() {
        Log.d("AutoSyncAccessibility", "Device locked detected")

        if (!autoSyncManager.isAutoSyncEnabled()) {
            Log.d("AutoSyncAccessibility", "AutoSync management disabled, ignoring lock event")
            return
        }

        val delayMinutes = autoSyncManager.getAutoSyncDelay()
        val delayMs = delayMinutes * 60 * 1000L

        Log.d("AutoSyncAccessibility", "Scheduling autosync turn-off in $delayMinutes minutes")

        // Cancel any existing scheduled operation
        cancelTurnOffSync()

        // Schedule turning off sync after delay
        turnOffSyncRunnable = Runnable {
            if (autoSyncManager.isAutoSyncEnabled() && isDeviceLocked) {
                try {
                    val currentSyncState = ContentResolver.getMasterSyncAutomatically()
                    if (currentSyncState) {
                        ContentResolver.setMasterSyncAutomatically(false)
                        Log.d("AutoSyncAccessibility", "AutoSync turned OFF after $delayMinutes minutes")
                    } else {
                        Log.d("AutoSyncAccessibility", "AutoSync was already OFF")
                    }
                } catch (e: SecurityException) {
                    Log.e("AutoSyncAccessibility", "Permission denied to change sync settings", e)
                } catch (e: Exception) {
                    Log.e("AutoSyncAccessibility", "Error turning off sync", e)
                }
            } else {
                Log.d("AutoSyncAccessibility", "Sync turn-off cancelled - device unlocked or feature disabled")
            }
        }

        turnOffSyncRunnable?.let { handler.postDelayed(it, delayMs) }
    }

    private fun handleDeviceUnlocked() {
        Log.d("AutoSyncAccessibility", "Device unlocked detected")

        if (!autoSyncManager.isAutoSyncEnabled()) {
            Log.d("AutoSyncAccessibility", "AutoSync management disabled, ignoring unlock event")
            return
        }

        // Cancel any pending turn-off sync operation
        cancelTurnOffSync()

        // Turn on sync immediately when device is unlocked
        try {
            val currentSyncState = ContentResolver.getMasterSyncAutomatically()
            if (!currentSyncState) {
                ContentResolver.setMasterSyncAutomatically(true)
                Log.d("AutoSyncAccessibility", "AutoSync turned ON (device unlocked)")
            } else {
                Log.d("AutoSyncAccessibility", "AutoSync was already ON")
            }
        } catch (e: SecurityException) {
            Log.e("AutoSyncAccessibility", "Permission denied to change sync settings", e)
        } catch (e: Exception) {
            Log.e("AutoSyncAccessibility", "Error turning on sync", e)
        }
    }

    private fun cancelTurnOffSync() {
        turnOffSyncRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
            Log.d("AutoSyncAccessibility", "Cancelled scheduled autosync turn-off")
        }
        turnOffSyncRunnable = null
    }

    override fun onInterrupt() {
        Log.d("AutoSyncAccessibility", "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTurnOffSync()
        Log.d("AutoSyncAccessibility", "Accessibility service destroyed")
    }
}
