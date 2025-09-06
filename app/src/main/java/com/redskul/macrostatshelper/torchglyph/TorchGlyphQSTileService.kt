package com.redskul.macrostatshelper.torchglyph

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.quicksettings.Tile
import com.redskul.macrostatshelper.tiles.BaseQSTileService
import androidx.annotation.RequiresApi
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.settings.QSTileSettingsActivity
import com.redskul.macrostatshelper.tiles.TileConfigHelper
import kotlinx.coroutines.*

class TorchGlyphQSTileService : BaseQSTileService() {

    private lateinit var torchGlyphManager: TorchGlyphManager
    private val tileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_TORCH_GLYPH_SETTINGS_UPDATED = "com.redskul.macrostatshelper.TORCH_GLYPH_SETTINGS_UPDATED"
    }

    private val settingsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_TORCH_GLYPH_SETTINGS_UPDATED) {
                android.util.Log.d("TorchGlyphQSTile", "Received settings update broadcast")
                updateTile()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        torchGlyphManager = TorchGlyphManager(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        registerReceiver(
            settingsUpdateReceiver,
            IntentFilter(ACTION_TORCH_GLYPH_SETTINGS_UPDATED),
            Context.RECEIVER_NOT_EXPORTED
        )
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        try {
            unregisterReceiver(settingsUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver already unregistered
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTileClick() {

        if (!torchGlyphManager.hasRequiredPermissions()) {
            // Request permission by opening settings
            requestRequiredPermissions()
            return
        }

        if (!torchGlyphManager.isTorchGlyphEnabled()) {
            // Open settings if Torch/Glyph tile is not configured
            openTorchGlyphSettings()
            return
        }

        // Cycle to next state
        tileScope.launch {
            val success = torchGlyphManager.cycleToNextState()
            withContext(Dispatchers.Main) {
                if (success) {
                    updateTile()
                    android.util.Log.d("TorchGlyphQSTile", "State changed to: ${torchGlyphManager.getCurrentStateText()}")
                } else {
                    android.util.Log.e("TorchGlyphQSTile", "Failed to change state")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestRequiredPermissions() {
        try {
            val intent = Intent(this, QSTileSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                3, // Different request code from other tiles
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            startActivityAndCollapse(pendingIntent)
            android.util.Log.d("TorchGlyphQSTile", "Permission request activity started with PendingIntent")
        } catch (e: Exception) {
            android.util.Log.e("TorchGlyphQSTile", "Error starting permission activity", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun openTorchGlyphSettings() {
        try {
            val intent = Intent(this, QSTileSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                1,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            startActivityAndCollapse(pendingIntent)
            android.util.Log.d("TorchGlyphQSTile", "Opened Torch/Glyph settings")
        } catch (e: Exception) {
            android.util.Log.e("TorchGlyphQSTile", "Error opening Torch/Glyph settings", e)
        }
    }

    private fun updateTile() {
        tileScope.launch {
            try {
                val hasPermission = torchGlyphManager.hasRequiredPermissions()
                val isTorchGlyphEnabled = torchGlyphManager.isTorchGlyphEnabled()

                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext

                    if (!hasPermission) {
                        // No permission - same pattern as other tiles
                        val config = TileConfigHelper.getTorchGlyphTileConfig(
                            this@TorchGlyphQSTileService,
                            TorchGlyphManager.TorchGlyphState.OFF
                        )
                        tile.state = Tile.STATE_INACTIVE
                        tile.icon = config.icon
                        tile.label = getString(R.string.torch_glyph_permission_required)
                        tile.subtitle = getString(R.string.tap_to_grant)
                    } else if (!isTorchGlyphEnabled) {
                        // Permission granted but not configured
                        val config = TileConfigHelper.getTorchGlyphTileConfig(
                            this@TorchGlyphQSTileService,
                            TorchGlyphManager.TorchGlyphState.OFF
                        )
                        tile.state = Tile.STATE_INACTIVE
                        tile.icon = config.icon
                        tile.label = getString(R.string.torch_glyph_not_configured)
                        tile.subtitle = getString(R.string.tap_to_setup)
                    } else {
                        // Fully functional
                        val currentState = torchGlyphManager.getCurrentState()
                        val showHeading = torchGlyphManager.getShowHeading()
                        val stateText = torchGlyphManager.getCurrentStateText()
                        val config = TileConfigHelper.getTorchGlyphTileConfig(
                            this@TorchGlyphQSTileService,
                            currentState
                        )

                        // Set tile state based on current state
                        tile.state = if (currentState == TorchGlyphManager.TorchGlyphState.OFF) {
                            Tile.STATE_INACTIVE
                        } else {
                            Tile.STATE_ACTIVE
                        }
                        tile.icon = config.icon

                        if (showHeading) {
                            // Show "Torch/Glyph" as heading and state as subtitle
                            tile.label = getString(R.string.torch_glyph_heading)
                            tile.subtitle = stateText
                        } else {
                            // Show state as main label, no subtitle
                            tile.label = stateText
                            tile.subtitle = null
                        }
                    }

                    tile.updateTile()
                    android.util.Log.d("TorchGlyphQSTile", "Tile updated successfully")
                }
            } catch (e: Exception) {
                android.util.Log.e("TorchGlyphQSTile", "Error updating Torch/Glyph tile", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
        // Cleanup when service is destroyed
        try {
            torchGlyphManager.cleanup()
        } catch (e: Exception) {
            android.util.Log.e("TorchGlyphQSTile", "Error during cleanup", e)
        }
    }
}
