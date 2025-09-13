package com.redskul.macrostatshelper.aod

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import com.redskul.macrostatshelper.R

/**
 * Manager class for Always-on Display (AOD) functionality.
 * Handles AOD state management using secure settings.
 */
class AODManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("aod_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AOD_ENABLED = "aod_enabled"
        private const val KEY_CURRENT_STATE = "current_state"

        // AOD states
        const val STATE_OFF = 0
        const val STATE_ALWAYS_ON = 1
        const val STATE_SCHEDULE = 2
        const val STATE_TAP_TO_SHOW = 3

        // Secure settings keys
        private const val DOZE_ALWAYS_ON = "doze_always_on"
        private const val AOD_DISPLAY_MODE = "aod_display_mode"

        // AOD display modes
        private const val AOD_MODE_ALWAYS_ON = 0
        private const val AOD_MODE_SCHEDULE = 1
        private const val AOD_MODE_TAP_TO_SHOW = 2

        private const val TAG = "AODManager"
    }

    enum class AODState {
        OFF, ALWAYS_ON, SCHEDULE, TAP_TO_SHOW
    }

    /**
     * Checks if AOD tile is enabled.
     */
    fun isAODEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_AOD_ENABLED, false)
    }

    /**
     * Sets AOD tile enabled state.
     */
    fun setAODEnabled(enabled: Boolean) {
        if (enabled && !hasRequiredPermissions()) {
            Log.w(TAG, "Cannot enable AOD without required permissions")
            return
        }

        sharedPreferences.edit {
            putBoolean(KEY_AOD_ENABLED, enabled)
        }
        Log.d(TAG, "AOD tile enabled set to: $enabled")
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
            // Test by trying to read and write secure settings
            val currentDozeAlwaysOn = getCurrentDozeAlwaysOn()
            Settings.Secure.putInt(context.contentResolver, DOZE_ALWAYS_ON, currentDozeAlwaysOn)
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
     * Gets current AOD state.
     */
    fun getCurrentState(): AODState {
        val stateValue = sharedPreferences.getInt(KEY_CURRENT_STATE, STATE_OFF)
        return when (stateValue) {
            STATE_ALWAYS_ON -> AODState.ALWAYS_ON
            STATE_SCHEDULE -> AODState.SCHEDULE
            STATE_TAP_TO_SHOW -> AODState.TAP_TO_SHOW
            else -> AODState.OFF
        }
    }

    /**
     * Sets current AOD state.
     */
    private fun setCurrentState(state: AODState) {
        val stateValue = when (state) {
            AODState.ALWAYS_ON -> STATE_ALWAYS_ON
            AODState.SCHEDULE -> STATE_SCHEDULE
            AODState.TAP_TO_SHOW -> STATE_TAP_TO_SHOW
            AODState.OFF -> STATE_OFF
        }

        sharedPreferences.edit {
            putInt(KEY_CURRENT_STATE, stateValue)
        }
        Log.d(TAG, "State set to: $state")
    }

    /**
     * Cycles to the next state and applies the change.
     * Follows the flow: OFF -> ALWAYS_ON -> SCHEDULE -> TAP_TO_SHOW -> OFF
     */
    fun cycleToNextState(): Boolean {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Cannot cycle state without required permissions")
            return false
        }

        val currentState = getCurrentState()
        val nextState = when (currentState) {
            AODState.OFF -> AODState.ALWAYS_ON
            AODState.ALWAYS_ON -> AODState.SCHEDULE
            AODState.SCHEDULE -> AODState.TAP_TO_SHOW
            AODState.TAP_TO_SHOW -> AODState.OFF
        }

        return applyState(nextState)
    }

    /**
     * Applies a specific AOD state to the system.
     */
    private fun applyState(state: AODState): Boolean {
        return try {
            when (state) {
                AODState.OFF -> {
                    // Turn off AOD completely
                    Settings.Secure.putInt(context.contentResolver, DOZE_ALWAYS_ON, 0)
                    Log.d(TAG, "AOD turned off")
                }
                AODState.ALWAYS_ON -> {
                    // Turn on AOD with Always On mode
                    Settings.Secure.putInt(context.contentResolver, DOZE_ALWAYS_ON, 1)
                    Settings.Secure.putInt(context.contentResolver, AOD_DISPLAY_MODE, AOD_MODE_ALWAYS_ON)
                    Log.d(TAG, "AOD set to Always On mode")
                }
                AODState.SCHEDULE -> {
                    // Turn on AOD with Schedule mode
                    Settings.Secure.putInt(context.contentResolver, DOZE_ALWAYS_ON, 1)
                    Settings.Secure.putInt(context.contentResolver, AOD_DISPLAY_MODE, AOD_MODE_SCHEDULE)
                    Log.d(TAG, "AOD set to Schedule mode")
                }
                AODState.TAP_TO_SHOW -> {
                    // Turn on AOD with Tap to show mode
                    Settings.Secure.putInt(context.contentResolver, DOZE_ALWAYS_ON, 1)
                    Settings.Secure.putInt(context.contentResolver, AOD_DISPLAY_MODE, AOD_MODE_TAP_TO_SHOW)
                    Log.d(TAG, "AOD set to Tap to show mode")
                }
            }

            setCurrentState(state)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying AOD state $state", e)
            false
        }
    }

    /**
     * Gets current doze_always_on value from system settings.
     */
    private fun getCurrentDozeAlwaysOn(): Int {
        return try {
            Settings.Secure.getInt(context.contentResolver, DOZE_ALWAYS_ON, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting doze_always_on", e)
            0
        }
    }

    /**
     * Gets current aod_display_mode value from system settings.
     */
    private fun getCurrentAODDisplayMode(): Int {
        return try {
            Settings.Secure.getInt(context.contentResolver, AOD_DISPLAY_MODE, AOD_MODE_ALWAYS_ON)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting aod_display_mode", e)
            AOD_MODE_ALWAYS_ON
        }
    }

    /**
     * Gets current state display text.
     */
    fun getCurrentStateText(): String {
        return when (getCurrentState()) {
            AODState.OFF -> context.getString(R.string.aod_state_off)
            AODState.ALWAYS_ON -> context.getString(R.string.aod_state_always_on)
            AODState.SCHEDULE -> context.getString(R.string.aod_state_schedule)
            AODState.TAP_TO_SHOW -> context.getString(R.string.aod_state_tap_to_show)
        }
    }

    /**
     * Syncs current AOD selection with system settings.
     */
    fun syncWithSystemSettings() {
        try {
            val currentDozeAlwaysOn = getCurrentDozeAlwaysOn()
            val currentAODDisplayMode = getCurrentAODDisplayMode()

            val actualState = when {
                currentDozeAlwaysOn == 0 -> AODState.OFF
                currentDozeAlwaysOn == 1 -> when (currentAODDisplayMode) {
                    AOD_MODE_ALWAYS_ON -> AODState.ALWAYS_ON
                    AOD_MODE_SCHEDULE -> AODState.SCHEDULE
                    AOD_MODE_TAP_TO_SHOW -> AODState.TAP_TO_SHOW
                    else -> AODState.ALWAYS_ON
                }
                else -> AODState.OFF
            }

            setCurrentState(actualState)
            Log.d(TAG, "Synced with system settings: $actualState")
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing with system settings", e)
        }
    }
}
