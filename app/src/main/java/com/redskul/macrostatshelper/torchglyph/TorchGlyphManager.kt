package com.redskul.macrostatshelper.torchglyph

import android.content.Context
import android.content.SharedPreferences
import android.hardware.camera2.CameraManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import com.redskul.macrostatshelper.R

/**
 * Manager class for Torch/Glyph functionality.
 * Handles torch and glyph state management with proper flashlight control.
 */
class TorchGlyphManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("torch_glyph_settings", Context.MODE_PRIVATE)

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    private var cameraId: String? = null

    companion object {
        private const val KEY_TORCH_GLYPH_ENABLED = "torch_glyph_enabled"
        private const val KEY_CURRENT_STATE = "current_state"

        // Torch/Glyph states
        const val STATE_OFF = 0
        const val STATE_TORCH_ON = 1
        const val STATE_GLYPH_ON = 2

        private const val TAG = "TorchGlyphManager"
    }

    enum class TorchGlyphState {
        OFF, TORCH_ON, GLYPH_ON
    }

    init {
        // Initialize camera ID for flashlight control
        initializeCameraId()
    }

    /**
     * Initialize camera ID for flashlight control
     */
    private fun initializeCameraId() {
        try {
            val cameraIds = cameraManager.cameraIdList
            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val flashAvailable = characteristics.get(
                    android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
                )
                if (flashAvailable == true) {
                    cameraId = id
                    break
                }
            }
            Log.d(TAG, "Camera ID for flashlight: $cameraId")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing camera ID", e)
        }
    }

    /**
     * Checks if Torch/Glyph tile is enabled.
     */
    fun isTorchGlyphEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_TORCH_GLYPH_ENABLED, false)
    }

    /**
     * Sets Torch/Glyph tile enabled state.
     */
    fun setTorchGlyphEnabled(enabled: Boolean) {
        if (enabled && !hasRequiredPermissions()) {
            Log.w(TAG, "Cannot enable Torch/Glyph without required permissions")
            return
        }

        sharedPreferences.edit {
            putBoolean(KEY_TORCH_GLYPH_ENABLED, enabled)
        }
        Log.d(TAG, "Torch/Glyph tile enabled set to: $enabled")
    }

    /**
     * Checks if required permissions are granted.
     */
    fun hasRequiredPermissions(): Boolean {
        return hasSecureSettingsPermission() && hasFlashlightPermission()
    }

    /**
     * Checks if WRITE_SECURE_SETTINGS permission is granted.
     */
    fun hasSecureSettingsPermission(): Boolean {
        return try {
            // Test by trying to read secure settings
            Settings.Secure.getString(context.contentResolver, "android_id")
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
     * Checks if flashlight permission is available
     */
    private fun hasFlashlightPermission(): Boolean {
        return try {
            val packageManager = context.packageManager
            packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_FLASH) &&
                    cameraId != null
        } catch (e: Exception) {
            Log.e(TAG, "Error checking flashlight permission", e)
            false
        }
    }

    /**
     * Gets the ADB command needed to grant WRITE_SECURE_SETTINGS permission.
     */
    fun getADBCommand(): String {
        return "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"
    }

    /**
     * Gets current torch/glyph state.
     */
    fun getCurrentState(): TorchGlyphState {
        val stateValue = sharedPreferences.getInt(KEY_CURRENT_STATE, STATE_OFF)
        return when (stateValue) {
            STATE_TORCH_ON -> TorchGlyphState.TORCH_ON
            STATE_GLYPH_ON -> TorchGlyphState.GLYPH_ON
            else -> TorchGlyphState.OFF
        }
    }

    /**
     * Sets current torch/glyph state.
     */
    private fun setCurrentState(state: TorchGlyphState) {
        val stateValue = when (state) {
            TorchGlyphState.TORCH_ON -> STATE_TORCH_ON
            TorchGlyphState.GLYPH_ON -> STATE_GLYPH_ON
            TorchGlyphState.OFF -> STATE_OFF
        }

        sharedPreferences.edit {
            putInt(KEY_CURRENT_STATE, stateValue)
        }
        Log.d(TAG, "State set to: $state")
    }

    /**
     * Cycles to the next state and applies the change.
     * Follows the flow: OFF -> TORCH_ON -> GLYPH_ON -> OFF
     */
    fun cycleToNextState(): Boolean {
        if (!hasRequiredPermissions()) {
            Log.w(TAG, "Cannot cycle state without required permissions")
            return false
        }

        val currentState = getCurrentState()
        val nextState = when (currentState) {
            TorchGlyphState.OFF -> TorchGlyphState.TORCH_ON
            TorchGlyphState.TORCH_ON -> TorchGlyphState.GLYPH_ON
            TorchGlyphState.GLYPH_ON -> TorchGlyphState.OFF
        }

        return applyState(nextState)
    }

    /**
     * Applies a specific torch/glyph state to the system.
     */
    private fun applyState(state: TorchGlyphState): Boolean {
        return try {
            when (state) {
                TorchGlyphState.OFF -> {
                    // Turn off both torch and glyph
                    setFlashlightEnabled(false)
                    setGlyphEnabled(false)
                    Log.d(TAG, "Turned off both torch and glyph")
                }
                TorchGlyphState.TORCH_ON -> {
                    // Turn on torch, turn off glyph
                    setFlashlightEnabled(true)
                    setGlyphEnabled(false)
                    Log.d(TAG, "Turned on torch, turned off glyph")
                }
                TorchGlyphState.GLYPH_ON -> {
                    // Turn off torch, turn on glyph
                    setFlashlightEnabled(false)
                    setGlyphEnabled(true)
                    Log.d(TAG, "Turned off torch, turned on glyph")
                }
            }

            setCurrentState(state)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying state $state", e)
            false
        }
    }

    /**
     * Controls the device flashlight using CameraManager
     */
    private fun setFlashlightEnabled(enabled: Boolean): Boolean {
        return try {
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId!!, enabled)
                Log.d(TAG, "Flashlight ${if (enabled) "enabled" else "disabled"}")
                true
            } else {
                Log.w(TAG, "Camera ID not available for flashlight control")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error controlling flashlight", e)
            false
        }
    }

    /**
     * Controls the glyph lights using system settings
     * Note: This is device-specific and may only work on Nothing phones
     */
    private fun setGlyphEnabled(enabled: Boolean): Boolean {
        return try {
            if (hasSecureSettingsPermission()) {
                // Try multiple possible glyph settings
                val glyphSettings = listOf(
                    "glyph_enable",
                    "nothing_glyph_enable",
                    "glyph_interface_enable",
                    "glyph_notification_enable"
                )

                var success = false
                for (setting in glyphSettings) {
                    try {
                        Settings.Global.putInt(
                            context.contentResolver,
                            setting,
                            if (enabled) 1 else 0
                        )
                        success = true
                        Log.d(TAG, "Glyph setting '$setting' ${if (enabled) "enabled" else "disabled"}")
                    } catch (e: Exception) {
                        Log.d(TAG, "Failed to set glyph setting '$setting': ${e.message}")
                    }
                }

                // Also try the torch-related glyph setting (but opposite of flashlight)
                try {
                    Settings.Global.putInt(
                        context.contentResolver,
                        "glyph_long_torch_enable",
                        if (enabled) 1 else 0
                    )
                    success = true
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to set glyph_long_torch_enable: ${e.message}")
                }

                return success
            } else {
                Log.w(TAG, "WRITE_SECURE_SETTINGS permission required for glyph control")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error controlling glyph", e)
            false
        }
    }

    /**
     * Gets current state display text.
     */
    fun getCurrentStateText(): String {
        return when (getCurrentState()) {
            TorchGlyphState.OFF -> context.getString(R.string.torch_glyph_state_off)
            TorchGlyphState.TORCH_ON -> context.getString(R.string.torch_glyph_state_torch)
            TorchGlyphState.GLYPH_ON -> context.getString(R.string.torch_glyph_state_glyph)
        }
    }

    /**
     * Gets status text for current torch/glyph state.
     */
    fun getCurrentStatusText(): String {
        return try {
            "Torch/Glyph: ${getCurrentStateText()}"
        } catch (e: Exception) {
            "Unable to read Torch/Glyph status"
        }
    }

    /**
     * Cleanup method to ensure everything is turned off
     */
    fun cleanup() {
        try {
            setFlashlightEnabled(false)
            setGlyphEnabled(false)
            setCurrentState(TorchGlyphState.OFF)
            Log.d(TAG, "Cleanup completed - all lights turned off")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
