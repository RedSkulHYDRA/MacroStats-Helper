package com.redskul.macrostatshelper.refreshrate

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import com.redskul.macrostatshelper.R

/**
 * Manager class for Refresh Rate functionality.
 * Handles refresh rate switching and settings management using Global settings.
 */
class RefreshRateManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("refresh_rate_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_REFRESH_RATE_ENABLED = "refresh_rate_enabled"
        private const val KEY_CURRENT_STATE = "current_state"

        // Refresh rate modes for Global settings
        private const val REFRESH_RATE_MODE_DYNAMIC = 0
        private const val REFRESH_RATE_MODE_HIGH = 1
        private const val REFRESH_RATE_MODE_STANDARD = 2

        private const val TAG = "RefreshRateManager"
    }

    enum class RefreshRateState {
        DYNAMIC, HIGH, STANDARD
    }

    /**
     * Checks if Refresh Rate tile is enabled.
     */
    fun isRefreshRateEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_REFRESH_RATE_ENABLED, false)
    }

    /**
     * Sets Refresh Rate tile enabled state.
     */
    fun setRefreshRateEnabled(enabled: Boolean) {
        if (enabled && !hasRequiredPermissions()) {
            Log.w(TAG, "Cannot enable Refresh Rate without required permissions")
            return
        }

        sharedPreferences.edit {
            putBoolean(KEY_REFRESH_RATE_ENABLED, enabled)
        }
        Log.d(TAG, "Refresh Rate tile enabled set to: $enabled")
    }

    /**
     * Checks if required permissions are granted.
     */
    fun hasRequiredPermissions(): Boolean {
        return hasSecureSettingsPermission()
    }

    /**
     * Checks if WRITE_SECURE_SETTINGS permission is granted.
     */
    fun hasSecureSettingsPermission(): Boolean {
        return try {
            // Test by trying to read and write global settings
            val currentMode = getCurrentRefreshRateMode()
            Settings.Global.putInt(context.contentResolver, "display_refresh_rate_mode", currentMode)
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
     * Gets current refresh rate state.
     */
    fun getCurrentState(): RefreshRateState {
        val stateValue = sharedPreferences.getInt(KEY_CURRENT_STATE, REFRESH_RATE_MODE_DYNAMIC)
        return when (stateValue) {
            REFRESH_RATE_MODE_HIGH -> RefreshRateState.HIGH
            REFRESH_RATE_MODE_STANDARD -> RefreshRateState.STANDARD
            else -> RefreshRateState.DYNAMIC
        }
    }

    /**
     * Sets current refresh rate state.
     */
    private fun setCurrentState(state: RefreshRateState) {
        val stateValue = when (state) {
            RefreshRateState.HIGH -> REFRESH_RATE_MODE_HIGH
            RefreshRateState.STANDARD -> REFRESH_RATE_MODE_STANDARD
            RefreshRateState.DYNAMIC -> REFRESH_RATE_MODE_DYNAMIC
        }

        sharedPreferences.edit {
            putInt(KEY_CURRENT_STATE, stateValue)
        }
        Log.d(TAG, "State set to: $state")
    }

    /**
     * Cycles to the next state and applies the change.
     * Follows the flow: DYNAMIC -> HIGH -> STANDARD -> DYNAMIC
     */
    fun cycleToNextState(): Boolean {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Cannot cycle state without required permissions")
            return false
        }

        val currentState = getCurrentState()
        val nextState = when (currentState) {
            RefreshRateState.DYNAMIC -> RefreshRateState.HIGH
            RefreshRateState.HIGH -> RefreshRateState.STANDARD
            RefreshRateState.STANDARD -> RefreshRateState.DYNAMIC
        }

        return applyState(nextState)
    }

    /**
     * Applies a specific refresh rate state to the system.
     */
    private fun applyState(state: RefreshRateState): Boolean {
        return try {
            val modeValue = when (state) {
                RefreshRateState.DYNAMIC -> REFRESH_RATE_MODE_DYNAMIC
                RefreshRateState.HIGH -> REFRESH_RATE_MODE_HIGH
                RefreshRateState.STANDARD -> REFRESH_RATE_MODE_STANDARD
            }

            Settings.Global.putInt(context.contentResolver, "display_refresh_rate_mode", modeValue)
            setCurrentState(state)
            Log.d(TAG, "Refresh rate set to: $state (mode: $modeValue)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying refresh rate state $state", e)
            false
        }
    }

    /**
     * Gets current refresh rate mode from system settings.
     */
    private fun getCurrentRefreshRateMode(): Int {
        return try {
            Settings.Global.getInt(context.contentResolver, "display_refresh_rate_mode", REFRESH_RATE_MODE_DYNAMIC)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting refresh rate mode", e)
            REFRESH_RATE_MODE_DYNAMIC
        }
    }

    /**
     * Gets current state display text.
     */
    fun getCurrentStateText(): String {
        return when (getCurrentState()) {
            RefreshRateState.DYNAMIC -> context.getString(R.string.refresh_rate_state_dynamic)
            RefreshRateState.HIGH -> context.getString(R.string.refresh_rate_state_high)
            RefreshRateState.STANDARD -> context.getString(R.string.refresh_rate_state_standard)
        }
    }

    /**
     * Syncs current refresh rate selection with system settings.
     */
    fun syncWithSystemSettings() {
        try {
            val currentMode = getCurrentRefreshRateMode()
            val actualState = when (currentMode) {
                REFRESH_RATE_MODE_HIGH -> RefreshRateState.HIGH
                REFRESH_RATE_MODE_STANDARD -> RefreshRateState.STANDARD
                else -> RefreshRateState.DYNAMIC
            }

            setCurrentState(actualState)
            Log.d(TAG, "Synced with system settings: ${actualState}")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing with system settings", e)
        }
    }
}
