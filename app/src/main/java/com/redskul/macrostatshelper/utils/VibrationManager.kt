package com.redskul.macrostatshelper.utils

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.content.edit

/**
 * Centralized vibration manager for QS tiles and app interactions
 * Handles haptic feedback with separate settings for QS tiles vs general app usage
 */
class VibrationManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("vibration_settings", Context.MODE_PRIVATE)

    private val vibrator: Vibrator by lazy {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibratorManager.defaultVibrator
    }

    companion object {
        private const val KEY_QS_TILE_VIBRATION_ENABLED = "qs_tile_vibration_enabled"
        private const val TAG = "VibrationManager"
    }

    /**
     * Checks if vibration is enabled for QS tiles specifically
     */
    fun isVibrationEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_QS_TILE_VIBRATION_ENABLED, true) // Default enabled
    }

    /**
     * Sets vibration enabled state for QS tiles specifically
     */
    fun setVibrationEnabled(enabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_QS_TILE_VIBRATION_ENABLED, enabled)
        }
        android.util.Log.d(TAG, "QS tile vibration enabled set to: $enabled")
    }

    /**
     * Performs haptic feedback for QS tile interactions
     * Only vibrates if user has enabled the QS tile vibration setting
     */
    @RequiresPermission(Manifest.permission.VIBRATE)
    fun qstilevibration() {
        if (!isVibrationEnabled()) {
            return
        }

        try {
            if (!vibrator.hasVibrator()) {
                Log.d(TAG, "Device does not have vibrator")
                return
            }

            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            vibrator.vibrate(effect)

            Log.d(TAG, "QS tile vibration triggered")
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering QS tile vibration", e)
        }
    }

    /**
     * Performs haptic feedback for general app interactions
     * Always enabled - not tied to QS tile vibration setting
     */
    @RequiresPermission(Manifest.permission.VIBRATE)
    fun vibrateOnAppInteraction() {
        try {
            if (!vibrator.hasVibrator()) {
                Log.d(TAG, "Device does not have vibrator")
                return
            }

            val effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            vibrator.vibrate(effect)

            Log.d(TAG, "App interaction vibration triggered")
        } catch (e: Exception) {
            Log.e(TAG, "Error triggering app interaction vibration", e)
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
