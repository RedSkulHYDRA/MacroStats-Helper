package com.redskul.macrostatshelper.autosync

import android.content.Context
import android.content.SharedPreferences
import com.redskul.macrostatshelper.utils.PermissionHelper

class AutoSyncManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("autosync_settings", Context.MODE_PRIVATE)

    private val permissionHelper = PermissionHelper(context)

    companion object {
        private const val KEY_AUTOSYNC_ENABLED = "autosync_enabled"
        private const val KEY_AUTOSYNC_DELAY = "autosync_delay"
        const val DEFAULT_DELAY_MINUTES = 5
        val ALLOWED_DELAYS = listOf(1, 5, 10, 20, 30)
    }

    fun isAutoSyncEnabled(): Boolean {
        // Return false if accessibility permission is not granted, regardless of saved preference
        if (!permissionHelper.hasAccessibilityPermission()) {
            return false
        }
        return sharedPreferences.getBoolean(KEY_AUTOSYNC_ENABLED, false)
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        // Check permission before saving
        if (enabled && !permissionHelper.hasAccessibilityPermission()) {
            android.util.Log.w("AutoSyncManager", "Cannot enable AutoSync without accessibility permission")
            return
        }
        sharedPreferences.edit().putBoolean(KEY_AUTOSYNC_ENABLED, enabled).apply()
        android.util.Log.d("AutoSyncManager", "AutoSync enabled set to: $enabled")
    }

    fun canEnableAutoSync(): Boolean {
        return permissionHelper.hasAccessibilityPermission()
    }

    fun enforcePermissionRestrictions() {
        // If accessibility permission is revoked, disable AutoSync
        if (!permissionHelper.hasAccessibilityPermission() && isAutoSyncEnabledRaw()) {
            sharedPreferences.edit().putBoolean(KEY_AUTOSYNC_ENABLED, false).apply()
            android.util.Log.i("AutoSyncManager", "Disabled AutoSync due to missing accessibility permission")
        }
    }

    private fun isAutoSyncEnabledRaw(): Boolean {
        return sharedPreferences.getBoolean(KEY_AUTOSYNC_ENABLED, false)
    }

    fun getAutoSyncDelay(): Int {
        return sharedPreferences.getInt(KEY_AUTOSYNC_DELAY, DEFAULT_DELAY_MINUTES)
    }

    fun setAutoSyncDelay(minutes: Int) {
        if (ALLOWED_DELAYS.contains(minutes)) {
            sharedPreferences.edit().putInt(KEY_AUTOSYNC_DELAY, minutes).apply()
            android.util.Log.d("AutoSyncManager", "AutoSync delay set to: $minutes minutes")
        } else {
            android.util.Log.w("AutoSyncManager", "Invalid delay: $minutes. Must be one of $ALLOWED_DELAYS")
        }
    }

    fun getAllowedDelays(): List<Int> {
        return ALLOWED_DELAYS
    }

    fun getDelayOptions(): List<String> {
        return ALLOWED_DELAYS.map { "${it} minute${if (it > 1) "s" else ""}" }
    }
}
