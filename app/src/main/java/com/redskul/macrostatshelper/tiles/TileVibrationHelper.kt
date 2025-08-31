package com.redskul.macrostatshelper.tiles

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

object TileVibrationHelper {

    private const val VIBRATION_DURATION = 100L // milliseconds

    /**
     * Provides haptic feedback for QS tile interactions if vibration is enabled
     * @param context Context to access vibration service and settings
     */
    fun vibrateTile(context: Context) {
        try {
            // Check if vibration is enabled in settings
            val qsTileSettingsManager = QSTileSettingsManager(context)
            if (!qsTileSettingsManager.isVibrationEnabled()) {
                android.util.Log.d("TileVibrationHelper", "Vibration disabled in settings")
                return
            }

            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.let { vib ->
                if (vib.hasVibrator()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        // Create a short, sharp vibration for feedback
                        val vibrationEffect = VibrationEffect.createOneShot(VIBRATION_DURATION, VibrationEffect.DEFAULT_AMPLITUDE)
                        vib.vibrate(vibrationEffect)
                        android.util.Log.d("TileVibrationHelper", "Vibration triggered (API 26+)")
                    } else {
                        // Fallback for older devices
                        @Suppress("DEPRECATION")
                        vib.vibrate(VIBRATION_DURATION)
                        android.util.Log.d("TileVibrationHelper", "Vibration triggered (Legacy)")
                    }
                } else {
                    android.util.Log.d("TileVibrationHelper", "Device does not support vibration")
                }
            } ?: run {
                android.util.Log.e("TileVibrationHelper", "Vibrator service not available")
            }
        } catch (e: Exception) {
            android.util.Log.e("TileVibrationHelper", "Error vibrating tile", e)
        }
    }
}
