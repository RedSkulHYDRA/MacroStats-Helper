package com.redskul.macrostatshelper.autosync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.redskul.macrostatshelper.utils.PermissionHelper

/**
 * Manager class for auto-sync functionality.
 * Handles configuration and permission checking for automatic sync management.
 */
class AutoSyncManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("autosync_settings", Context.MODE_PRIVATE)

    private val permissionHelper = PermissionHelper(context)

    companion object {
        private const val KEY_AUTOSYNC_ENABLED = "autosync_enabled"
        private const val KEY_AUTOSYNC_DELAY = "autosync_delay"
        const val DEFAULT_DELAY_MINUTES = 5
        val ALLOWED_DELAYS: List<Int> = listOf(1, 5, 10, 20, 30)
    }

    /**
     * Checks if auto-sync is enabled and permissions are granted.
     * @return true if auto-sync is enabled and accessibility permission is granted
     */
    fun isAutoSyncEnabled(): Boolean {
        // Return false if accessibility permission is not granted, regardless of saved preference
        if (!permissionHelper.hasAccessibilityPermission()) {
            return false
        }
        return sharedPreferences.getBoolean(KEY_AUTOSYNC_ENABLED, false)
    }

    /**
     * Sets the auto-sync enabled state.
     * @param enabled true to enable auto-sync, false to disable
     */
    fun setAutoSyncEnabled(enabled: Boolean) {
        // Check permission before saving using the canEnableAutoSync method
        if (enabled && !canEnableAutoSync()) {
            Log.w("AutoSyncManager", "Cannot enable AutoSync without accessibility permission")
            return
        }
        sharedPreferences.edit().putBoolean(KEY_AUTOSYNC_ENABLED, enabled).apply()
        Log.d("AutoSyncManager", "AutoSync enabled set to: $enabled")
    }

    /**
     * Checks if auto-sync can be enabled (permission available).
     * @return true if accessibility permission is granted
     */
    fun canEnableAutoSync(): Boolean {
        return permissionHelper.hasAccessibilityPermission()
    }

    /**
     * Enforces permission restrictions by disabling auto-sync if permission is revoked.
     */
    fun enforcePermissionRestrictions() {
        // If accessibility permission is revoked, disable AutoSync
        if (!canEnableAutoSync() && isAutoSyncEnabledRaw()) {
            sharedPreferences.edit().putBoolean(KEY_AUTOSYNC_ENABLED, false).apply()
            Log.i("AutoSyncManager", "Disabled AutoSync due to missing accessibility permission")
        }
    }

    private fun isAutoSyncEnabledRaw(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTOSYNC_ENABLED, false)
    }

    /**
     * Gets the current auto-sync delay in minutes.
     * @return delay in minutes
     */
    fun getAutoSyncDelay(): Int {
        return sharedPreferences.getInt(KEY_AUTOSYNC_DELAY, DEFAULT_DELAY_MINUTES)
    }

    /**
     * Sets the auto-sync delay.
     * @param minutes delay in minutes, must be one of the allowed values
     */
    fun setAutoSyncDelay(minutes: Int) {
        if (ALLOWED_DELAYS.contains(minutes)) {
            sharedPreferences.edit().putInt(KEY_AUTOSYNC_DELAY, minutes).apply()
            Log.d("AutoSyncManager", "AutoSync delay set to: $minutes minutes")
        } else {
            Log.w("AutoSyncManager", "Invalid delay: $minutes. Must be one of $ALLOWED_DELAYS")
        }
    }

    /**
     * Gets the list of allowed delay values.
     * @return list of allowed delay values in minutes
     */
    fun getAllowedDelays(): List<Int> {
        return ALLOWED_DELAYS
    }

    /**
     * Gets formatted delay options for UI display.
     * @return list of formatted delay option strings
     */
    fun getDelayOptions(): List<String> {
        return ALLOWED_DELAYS.map { "${it} minute${if (it > 1) "s" else ""}" }
    }
}
