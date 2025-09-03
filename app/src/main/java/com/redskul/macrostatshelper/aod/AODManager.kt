package com.redskul.macrostatshelper.aod

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit

/**
 * Manager class for Always On Display (AOD) functionality while charging.
 * Handles AOD state management and permission checking with enhanced error handling.
 */
class AODManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("aod_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AOD_ENABLED = "aod_enabled"
        private const val KEY_SAVED_AOD_MODE = "saved_aod_mode"
        private const val KEY_SAVED_DOZE_STATE = "saved_doze_state"

        // Correct AOD display mode values based on user clarification
        private const val AOD_MODE_ALWAYS_ON = 0
        private const val AOD_MODE_SCHEDULE = 1
        private const val AOD_MODE_TAP_TO_SHOW = 2

        // Doze always on values
        private const val DOZE_ALWAYS_ON_DISABLED = 0
        private const val DOZE_ALWAYS_ON_ENABLED = 1

        private const val TAG = "AODManager"
    }

    /**
     * Checks if AOD while charging is enabled.
     */
    fun isAODWhileChargingEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_AOD_ENABLED, false)
    }

    /**
     * Sets AOD while charging enabled state.
     */
    fun setAODWhileChargingEnabled(enabled: Boolean) {
        if (enabled && !hasSecureSettingsPermission()) {
            Log.w(TAG, "Cannot enable AOD without WRITE_SECURE_SETTINGS permission")
            return
        }

        sharedPreferences.edit {
            putBoolean(KEY_AOD_ENABLED, enabled)
        }
        Log.d(TAG, "AOD while charging set to: $enabled")
    }

    /**
     * Checks if WRITE_SECURE_SETTINGS permission is granted.
     */
    fun hasSecureSettingsPermission(): Boolean {
        return try {
            // Test by trying to read and write secure settings
            val currentMode = getCurrentAODMode()
            Settings.Secure.putInt(context.contentResolver, "aod_display_mode", currentMode)
            true
        } catch (e: SecurityException) {
            Log.d(TAG, "WRITE_SECURE_SETTINGS permission not granted: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking secure settings permission", e)
            false
        }
    }

    /**
     * Checks if AOD changes are currently allowed (both permission and user setting).
     */
    fun isAODChangeAllowed(): Boolean {
        return isAODWhileChargingEnabled() && hasSecureSettingsPermission()
    }

    /**
     * Gets the ADB command needed to grant WRITE_SECURE_SETTINGS permission.
     */
    fun getADBCommand(): String {
        return "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }

    /**
     * Saves current AOD settings before changing them.
     */
    private fun saveCurrentAODSettings() {
        try {
            val currentAODMode = getCurrentAODMode()
            val currentDozeState = getCurrentDozeAlwaysOnState()

            sharedPreferences.edit {
                putInt(KEY_SAVED_AOD_MODE, currentAODMode)
                putInt(KEY_SAVED_DOZE_STATE, currentDozeState)
            }

            Log.d(TAG, "Saved AOD state - mode:$currentAODMode, doze:$currentDozeState")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving current AOD settings", e)
        }
    }

    /**
     * Restores previously saved AOD settings.
     */
    private fun restoreSavedAODSettings() {
        try {
            val savedAODMode = sharedPreferences.getInt(KEY_SAVED_AOD_MODE, AOD_MODE_TAP_TO_SHOW)
            val savedDozeState = sharedPreferences.getInt(KEY_SAVED_DOZE_STATE, DOZE_ALWAYS_ON_DISABLED)

            Settings.Secure.putInt(context.contentResolver, "aod_display_mode", savedAODMode)
            Settings.Secure.putInt(context.contentResolver, "doze_always_on", savedDozeState)

            Log.d(TAG, "Restored AOD state - mode:$savedAODMode, doze:$savedDozeState")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring AOD settings", e)
        }
    }

    /**
     * Enhanced enable method with better error handling and return value.
     */
    fun enableAODForChargingEnhanced(): Boolean {
        if (!isAODChangeAllowed()) {
            Log.w(TAG, "AOD change not allowed - enabled: ${isAODWhileChargingEnabled()}, permission: ${hasSecureSettingsPermission()}")
            return false
        }

        return try {
            // Save current settings first only if we haven't saved them already
            val hasSavedSettings = sharedPreferences.contains(KEY_SAVED_AOD_MODE)
            if (!hasSavedSettings) {
                saveCurrentAODSettings()
                Log.d(TAG, "Saved current AOD settings before enabling charging mode")
            } else {
                Log.d(TAG, "AOD settings already saved, not overwriting")
            }

            // Set charging AOD mode: doze_always_on = 1, aod_display_mode = 0 (Always On)
            Settings.Secure.putInt(context.contentResolver, "doze_always_on", DOZE_ALWAYS_ON_ENABLED)
            Settings.Secure.putInt(context.contentResolver, "aod_display_mode", AOD_MODE_ALWAYS_ON)

            Log.d(TAG, "AOD enabled for charging: doze_always_on=1, aod_display_mode=0")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error enabling AOD for charging", e)
            false
        }
    }

    /**
     * Restores AOD settings when charging stops.
     */
    fun restoreAODAfterCharging() {
        if (!isAODWhileChargingEnabled() || !hasSecureSettingsPermission()) {
            Log.d(TAG, "AOD charging disabled or no permission. Enabled: ${isAODWhileChargingEnabled()}, Permission: ${hasSecureSettingsPermission()}")
            return
        }

        try {
            // Only restore if we have saved settings
            val hasSavedSettings = sharedPreferences.contains(KEY_SAVED_AOD_MODE)
            if (hasSavedSettings) {
                restoreSavedAODSettings()

                // Clear saved settings after restoring
                sharedPreferences.edit {
                    remove(KEY_SAVED_AOD_MODE)
                    remove(KEY_SAVED_DOZE_STATE)
                }

                Log.d(TAG, "AOD settings restored after charging and cleared from preferences")
            } else {
                Log.w(TAG, "No saved AOD settings found to restore")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring AOD after charging", e)
        }
    }

    /**
     * Gets current AOD display mode.
     */
    private fun getCurrentAODMode(): Int {
        return try {
            Settings.Secure.getInt(context.contentResolver, "aod_display_mode", AOD_MODE_TAP_TO_SHOW)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting AOD mode", e)
            AOD_MODE_TAP_TO_SHOW
        }
    }

    /**
     * Gets current doze always on state.
     */
    private fun getCurrentDozeAlwaysOnState(): Int {
        return try {
            Settings.Secure.getInt(context.contentResolver, "doze_always_on", DOZE_ALWAYS_ON_DISABLED)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting doze state", e)
            DOZE_ALWAYS_ON_DISABLED
        }
    }

    /**
     * Gets human-readable status of current AOD settings.
     */
    fun getCurrentAODStatusText(): String {
        return try {
            val aodMode = getCurrentAODMode()
            val dozeState = getCurrentDozeAlwaysOnState()

            val modeText = when (aodMode) {
                AOD_MODE_ALWAYS_ON -> "Always On"
                AOD_MODE_SCHEDULE -> "Scheduled"
                AOD_MODE_TAP_TO_SHOW -> "Tap to Show"
                else -> "Unknown ($aodMode)"
            }

            val dozeText = if (dozeState == DOZE_ALWAYS_ON_ENABLED) "Enabled" else "Disabled"

            "AOD Mode: $modeText, Doze Always On: $dozeText"
        } catch (_: Exception) {
            "Unable to read AOD status"
        }
    }

    /**
     * Manual method to clear saved settings if needed for debugging.
     */
    @Suppress("unused")
    fun clearSavedSettings() {
        sharedPreferences.edit {
            remove(KEY_SAVED_AOD_MODE)
            remove(KEY_SAVED_DOZE_STATE)
        }
        Log.d(TAG, "Cleared saved AOD settings")
    }
}
