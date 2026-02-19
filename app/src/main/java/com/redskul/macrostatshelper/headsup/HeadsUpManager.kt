package com.redskul.macrostatshelper.headsup

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.redskul.macrostatshelper.R

/**
 * Manager class for Heads-Up notification toggle functionality.
 * Controls the system heads-up notification setting via Settings.Global,
 * which requires WRITE_SECURE_SETTINGS permission (granted via ADB).
 */
class HeadsUpManager(private val context: Context) {

    companion object {
        private const val TAG = "HeadsUpManager"
        // Settings.Global.HEADS_UP_NOTIFICATIONS_ENABLED is a hidden API,
        // so we use the raw string key directly instead.
        private const val HEADS_UP_KEY = "heads_up_notifications_enabled"
        private const val HEADS_UP_ENABLED = 1
        private const val HEADS_UP_DISABLED = 0
    }

    /**
     * Checks if WRITE_SECURE_SETTINGS permission is granted.
     * Required to read/write the heads-up notifications setting.
     */
    fun hasRequiredPermissions(): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, HEADS_UP_KEY)
            true
        } catch (e: SecurityException) {
            Log.d(TAG, "WRITE_SECURE_SETTINGS permission not granted: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission", e)
            false
        }
    }

    /**
     * Returns true if heads-up notifications are currently enabled on the system.
     */
    fun isHeadsUpEnabled(): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                HEADS_UP_KEY,
                HEADS_UP_ENABLED
            ) == HEADS_UP_ENABLED
        } catch (e: Exception) {
            Log.e(TAG, "Error reading heads-up setting", e)
            true // Assume enabled if we can't read it
        }
    }

    /**
     * Toggles heads-up notifications and returns the new state.
     * Returns null if the operation failed.
     */
    fun toggle(): Boolean? {
        return try {
            val newValue = if (isHeadsUpEnabled()) HEADS_UP_DISABLED else HEADS_UP_ENABLED
            Settings.Global.putInt(context.contentResolver, HEADS_UP_KEY, newValue)
            val result = newValue == HEADS_UP_ENABLED
            Log.d(TAG, "Heads-up toggled to: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling heads-up setting", e)
            null
        }
    }

    /**
     * Returns the ADB command required to grant WRITE_SECURE_SETTINGS.
     */
    fun getADBCommand(): String {
        return "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }

    /**
     * Returns the display text for the current state.
     */
    fun getCurrentStateText(): String {
        return if (isHeadsUpEnabled()) {
            context.getString(R.string.heads_up_state_on)
        } else {
            context.getString(R.string.heads_up_state_off)
        }
    }
}