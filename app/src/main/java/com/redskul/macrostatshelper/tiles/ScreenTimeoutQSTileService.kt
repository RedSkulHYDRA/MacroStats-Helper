package com.redskul.macrostatshelper.tiles

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import com.redskul.macrostatshelper.tiles.BaseQSTileService
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import com.redskul.macrostatshelper.settings.QSTileSettingsManager
import kotlinx.coroutines.*

class ScreenTimeoutQSTileService : BaseQSTileService() {

    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private val tileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        // Screen timeout values in milliseconds
        private val TIMEOUT_VALUES = arrayOf(
            15000,   // 15 seconds
            30000,   // 30 seconds
            60000,   // 1 minute
            120000,  // 2 minutes
            300000,  // 5 minutes
            600000,  // 10 minutes
            1800000  // 30 minutes
        )

        private val TIMEOUT_LABELS = arrayOf(
            "15s", "30s", "1m", "2m", "5m", "10m", "30m"
        )

        private fun getTimeoutLabel(timeoutMs: Int): String {
            val index = TIMEOUT_VALUES.indexOf(timeoutMs)
            return if (index >= 0) TIMEOUT_LABELS[index] else "${timeoutMs / 1000}s"
        }

        private fun getNextTimeout(currentTimeoutMs: Int): Int {
            val currentIndex = TIMEOUT_VALUES.indexOf(currentTimeoutMs)
            return if (currentIndex >= 0 && currentIndex < TIMEOUT_VALUES.size - 1) {
                TIMEOUT_VALUES[currentIndex + 1]
            } else {
                TIMEOUT_VALUES[0] // Cycle back to first value
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        qsTileSettingsManager = QSTileSettingsManager(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTileClick() {

        if (!hasWriteSettingsPermission()) {
            // Request permission by opening settings
            requestWriteSettingsPermission()
            return
        }

        // Permission is granted, proceed with changing timeout
        tileScope.launch {
            try {
                val currentTimeout = getCurrentScreenTimeout()
                val nextTimeout = getNextTimeout(currentTimeout)

                if (setScreenTimeout(nextTimeout)) {
                    // Update tile
                    withContext(Dispatchers.Main) {
                        updateTile()
                    }
                } else {
                    android.util.Log.e("ScreenTimeoutQSTile", "Failed to set screen timeout")
                }
            } catch (e: Exception) {
                android.util.Log.e("ScreenTimeoutQSTile", "Error changing screen timeout", e)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestWriteSettingsPermission() {
        try {
            // Create intent for write settings permission
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = "package:$packageName".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Create PendingIntent
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Start the activity and collapse the panel using PendingIntent
            startActivityAndCollapse(pendingIntent)

            android.util.Log.d("ScreenTimeoutQSTile", "Permission request activity started with PendingIntent")
        } catch (e: Exception) {
            android.util.Log.e("ScreenTimeoutQSTile", "Error starting permission activity", e)
            // Fallback: just open general app settings
            try {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:$packageName".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // Create fallback PendingIntent
                val fallbackPendingIntent = PendingIntent.getActivity(
                    this,
                    1, // Different request code
                    fallbackIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                startActivityAndCollapse(fallbackPendingIntent)
                android.util.Log.d("ScreenTimeoutQSTile", "Fallback permission activity started with PendingIntent")
            } catch (fallbackError: Exception) {
                android.util.Log.e("ScreenTimeoutQSTile", "Fallback also failed", fallbackError)
            }
        }
    }

    private fun updateTile() {
        tileScope.launch {
            try {
                val hasPermission = hasWriteSettingsPermission()
                val currentTimeout = getCurrentScreenTimeout()
                val timeoutLabel = getTimeoutLabel(currentTimeout)
                val config = TileConfigHelper.getScreenTimeoutTileConfig(this@ScreenTimeoutQSTileService)
                val showTimeoutInTitle = qsTileSettingsManager.getShowScreenTimeoutInTitle()

                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext

                    if (hasPermission) {
                        // Permission granted - show normal tile
                        TileConfigHelper.applyScreenTimeoutConfigToTile(
                            tile,
                            config,
                            timeoutLabel,
                            showTimeoutInTitle,
                            true,
                            context = this@ScreenTimeoutQSTileService
                        )
                        tile.state = Tile.STATE_ACTIVE
                    } else {
                        // Permission not granted - show permission required state
                        tile.state = Tile.STATE_INACTIVE
                        tile.icon = config.icon
                        tile.label = getString(com.redskul.macrostatshelper.R.string.screen_timeout_permission_required)
                        tile.subtitle = getString(com.redskul.macrostatshelper.R.string.tap_to_grant)
                    }

                    tile.updateTile()
                    android.util.Log.d("ScreenTimeoutQSTile", "Tile updated - Permission: $hasPermission, Timeout: $timeoutLabel")
                }
            } catch (e: Exception) {
                android.util.Log.e("ScreenTimeoutQSTile", "Error updating Screen Timeout tile", e)
            }
        }
    }

    private fun getCurrentScreenTimeout(): Int {
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30000)
        } catch (e: Exception) {
            android.util.Log.e("ScreenTimeoutQSTile", "Error getting screen timeout", e)
            30000 // Default to 30 seconds
        }
    }

    private fun setScreenTimeout(timeoutMs: Int): Boolean {
        return try {
            val success = Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs)
            if (success) {
                android.util.Log.d("ScreenTimeoutQSTile", "Screen timeout set to: ${getTimeoutLabel(timeoutMs)}")
            } else {
                android.util.Log.e("ScreenTimeoutQSTile", "Failed to set screen timeout")
            }
            success
        } catch (e: Exception) {
            android.util.Log.e("ScreenTimeoutQSTile", "Error setting screen timeout", e)
            false
        }
    }

    private fun hasWriteSettingsPermission(): Boolean {
        return Settings.System.canWrite(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }
}
