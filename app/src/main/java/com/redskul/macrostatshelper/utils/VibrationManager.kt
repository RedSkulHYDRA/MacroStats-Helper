package com.redskul.macrostatshelper.utils

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.edit

/**
 * Centralized vibration manager for QS tiles
 * Handles haptic feedback with user preference support
 */
class VibrationManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("vibration_settings", Context.MODE_PRIVATE)

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    companion object {
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val VIBRATION_DURATION_MS = 25L // Short, subtle vibration
        private const val TAG = "VibrationManager"
    }

    /**
     * Checks if vibration is enabled for QS tiles
     */
    fun isVibrationEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_VIBRATION_ENABLED, true) // Default enabled
    }

    /**
     * Sets vibration enabled state for QS tiles
     */
    fun setVibrationEnabled(enabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_VIBRATION_ENABLED, enabled)
        }
        android.util.Log.d(TAG, "Vibration enabled set to: $enabled")
    }

    /**
     * Performs haptic feedback for QS tile interactions
     * Only vibrates if user has enabled the setting
     */
    @RequiresPermission(Manifest.permission.VIBRATE)
    fun vibrateOnClick() {
        if (!isVibrationEnabled()) {
            return
        }

        try {
            if (!vibrator.hasVibrator()) {
                Log.d(TAG, "Device does not have vibrator")
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Use modern VibrationEffect API
                val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Use predefined effect for better UX on newer devices
                    VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                } else {
                    // Fallback for API 26-28
                    VibrationEffect.createOneShot(VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrator.vibrate(effect)
            } else {
                // Fallback for older devices
                @Suppress("DEPRECATION")
                vibrator.vibrate(VIBRATION_DURATION_MS)
            }

            Log.d(TAG, "QS tile vibration triggered")
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering vibration", e)
        }
    }

    /**
     * Checks if device has vibrator capability
     */
    fun hasVibrator(): Boolean {
        return try {
            vibrator.hasVibrator()
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error checking vibrator capability", e)
            false
        }
    }
}
