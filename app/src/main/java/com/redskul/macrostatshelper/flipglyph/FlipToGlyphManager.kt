package com.redskul.macrostatshelper.flipglyph

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.redskul.macrostatshelper.R

/**
 * Manager class for Flip to Glyph functionality.
 * Toggles the "led_effect_gestures_flip_ebable" global setting.
 * (The typo "ebable" is intentional — it matches the actual Nothing Phone system key.)
 *
 * NOTE: If the toggle has no effect on your device, change both Settings.Global calls
 * to Settings.Secure — Nothing Phone firmware varies by model/build.
 */
class FlipToGlyphManager(private val context: Context) {

    companion object {
        private const val FLIP_TO_GLYPH_KEY = "led_effect_gestures_flip_ebable"
        private const val ENABLED  = 1
        private const val DISABLED = 0
        private const val TAG = "FlipToGlyphManager"
    }

    /**
     * Returns true when WRITE_SECURE_SETTINGS has been granted via ADB.
     */
    fun hasRequiredPermissions(): Boolean {
        return try {
            val current = Settings.Global.getInt(context.contentResolver, FLIP_TO_GLYPH_KEY, DISABLED)
            Settings.Global.putInt(context.contentResolver, FLIP_TO_GLYPH_KEY, current)
            true
        } catch (e: SecurityException) {
            Log.d(TAG, "WRITE_SECURE_SETTINGS not granted: ${e.message}")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permission", e)
            false
        }
    }

    /**
     * Returns true when Flip to Glyph is currently enabled on the system.
     */
    fun isEnabled(): Boolean {
        return try {
            Settings.Global.getInt(context.contentResolver, FLIP_TO_GLYPH_KEY, DISABLED) == ENABLED
        } catch (e: Exception) {
            Log.e(TAG, "Error reading Flip to Glyph setting", e)
            false
        }
    }

    /**
     * Toggles Flip to Glyph and returns the new enabled state, or null on failure.
     */
    fun toggle(): Boolean? {
        return try {
            val newValue = if (isEnabled()) DISABLED else ENABLED
            Settings.Global.putInt(context.contentResolver, FLIP_TO_GLYPH_KEY, newValue)
            val result = newValue == ENABLED
            Log.d(TAG, "Flip to Glyph toggled to: $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling Flip to Glyph", e)
            null
        }
    }

    /** Returns the ADB command needed to grant WRITE_SECURE_SETTINGS. */
    fun getADBCommand(): String =
        "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

    /** Human-readable label for the current state. */
    fun getCurrentStateText(): String =
        if (isEnabled()) context.getString(R.string.flip_to_glyph_state_on)
        else context.getString(R.string.flip_to_glyph_state_off)
}