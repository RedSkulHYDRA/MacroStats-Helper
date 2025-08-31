package com.redskul.macrostatshelper.autosync

import android.accessibilityservice.AccessibilityService
import android.content.ContentResolver
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

class AutoSyncAccessibilityService : AccessibilityService() {

    private lateinit var autoSyncManager: AutoSyncManager
    private var turnOffSyncRunnable: Runnable? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isDeviceLocked = false
    private var lastEventTime = 0L

    companion object {
        const val MIN_EVENT_INTERVAL = 1000L // Minimum 1 second between events

        // Helper method to check if accessibility service is enabled
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
        android.util.Log.d("AutoSyncAccessibility", "Accessibility service created")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        android.util.Log.d("AutoSyncAccessibility", "Accessibility service connected")

        // Initialize device lock state
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        isDeviceLocked = keyguardManager.isKeyguardLocked
        android.util.Log.d("AutoSyncAccessibility", "Initial lock state: $isDeviceLocked")
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
                android.util.Log.d("AutoSyncAccessibility", "Lock state changed to: $isDeviceLocked")

                if (isDeviceLocked) {
                    handleDeviceLocked()
                } else {
                    handleDeviceUnlocked()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AutoSyncAccessibility", "Error checking lock state", e)
        }
    }

    private fun handleDeviceLocked() {
        android.util.Log.d("AutoSyncAccessibility", "Device locked detected")

        if (!autoSyncManager.isAutoSyncEnabled()) {
            android.util.Log.d("AutoSyncAccessibility", "AutoSync management disabled, ignoring lock event")
            return
        }

        val delayMinutes = autoSyncManager.getAutoSyncDelay()
        val delayMs = delayMinutes * 60 * 1000L

        android.util.Log.d("AutoSyncAccessibility", "Scheduling autosync turn-off in $delayMinutes minutes")

        // Cancel any existing scheduled operation
        cancelTurnOffSync()

        // Schedule turning off sync after delay
        turnOffSyncRunnable = Runnable {
            if (autoSyncManager.isAutoSyncEnabled() && isDeviceLocked) {
                try {
                    val currentSyncState = ContentResolver.getMasterSyncAutomatically()
                    if (currentSyncState) {
                        ContentResolver.setMasterSyncAutomatically(false)
                        android.util.Log.d("AutoSyncAccessibility", "AutoSync turned OFF after $delayMinutes minutes")
                    } else {
                        android.util.Log.d("AutoSyncAccessibility", "AutoSync was already OFF")
                    }
                } catch (e: SecurityException) {
                    android.util.Log.e("AutoSyncAccessibility", "Permission denied to change sync settings", e)
                } catch (e: Exception) {
                    android.util.Log.e("AutoSyncAccessibility", "Error turning off sync", e)
                }
            } else {
                android.util.Log.d("AutoSyncAccessibility", "Sync turn-off cancelled - device unlocked or feature disabled")
            }
        }

        handler.postDelayed(turnOffSyncRunnable!!, delayMs)
    }

    private fun handleDeviceUnlocked() {
        android.util.Log.d("AutoSyncAccessibility", "Device unlocked detected")

        if (!autoSyncManager.isAutoSyncEnabled()) {
            android.util.Log.d("AutoSyncAccessibility", "AutoSync management disabled, ignoring unlock event")
            return
        }

        // Cancel any pending turn-off sync operation
        cancelTurnOffSync()

        // Turn on sync immediately when device is unlocked
        try {
            val currentSyncState = ContentResolver.getMasterSyncAutomatically()
            if (!currentSyncState) {
                ContentResolver.setMasterSyncAutomatically(true)
                android.util.Log.d("AutoSyncAccessibility", "AutoSync turned ON (device unlocked)")
            } else {
                android.util.Log.d("AutoSyncAccessibility", "AutoSync was already ON")
            }
        } catch (e: SecurityException) {
            android.util.Log.e("AutoSyncAccessibility", "Permission denied to change sync settings", e)
        } catch (e: Exception) {
            android.util.Log.e("AutoSyncAccessibility", "Error turning on sync", e)
        }
    }

    private fun cancelTurnOffSync() {
        turnOffSyncRunnable?.let { runnable ->
            handler.removeCallbacks(runnable)
            android.util.Log.d("AutoSyncAccessibility", "Cancelled scheduled autosync turn-off")
        }
        turnOffSyncRunnable = null
    }

    override fun onInterrupt() {
        android.util.Log.d("AutoSyncAccessibility", "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTurnOffSync()
        android.util.Log.d("AutoSyncAccessibility", "Accessibility service destroyed")
    }
}
