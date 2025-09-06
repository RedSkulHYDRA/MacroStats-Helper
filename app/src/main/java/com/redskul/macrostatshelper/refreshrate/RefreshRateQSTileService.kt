package com.redskul.macrostatshelper.refreshrate

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
import com.redskul.macrostatshelper.settings.QSTileSettingsManager
import com.redskul.macrostatshelper.tiles.TileConfigHelper
import kotlinx.coroutines.*

class RefreshRateQSTileService : BaseQSTileService() {

    private lateinit var refreshRateManager: RefreshRateManager
    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private val tileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_REFRESH_RATE_SETTINGS_UPDATED = "com.redskul.macrostatshelper.REFRESH_RATE_SETTINGS_UPDATED"
    }

    private val settingsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_REFRESH_RATE_SETTINGS_UPDATED) {
                android.util.Log.d("RefreshRateQSTile", "Received settings update broadcast")
                updateTile()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        refreshRateManager = RefreshRateManager(this)
        qsTileSettingsManager = QSTileSettingsManager(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        registerReceiver(
            settingsUpdateReceiver,
            IntentFilter(ACTION_REFRESH_RATE_SETTINGS_UPDATED),
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

        if (!refreshRateManager.hasRequiredPermissions()) {
            // Request permission by opening settings
            requestRequiredPermissions()
            return
        }

        if (!refreshRateManager.isRefreshRateEnabled()) {
            // Open settings if Refresh Rate tile is not configured
            openRefreshRateSettings()
            return
        }

        // Cycle to next state
        tileScope.launch {
            val success = refreshRateManager.cycleToNextState()
            withContext(Dispatchers.Main) {
                if (success) {
                    updateTile()
                    android.util.Log.d("RefreshRateQSTile", "State changed to: ${refreshRateManager.getCurrentStateText()}")
                } else {
                    android.util.Log.e("RefreshRateQSTile", "Failed to change state")
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
                4, // Different request code from other tiles
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            startActivityAndCollapse(pendingIntent)
            android.util.Log.d("RefreshRateQSTile", "Permission request activity started with PendingIntent")
        } catch (e: Exception) {
            android.util.Log.e("RefreshRateQSTile", "Error starting permission activity", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun openRefreshRateSettings() {
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
            android.util.Log.d("RefreshRateQSTile", "Opened Refresh Rate settings")
        } catch (e: Exception) {
            android.util.Log.e("RefreshRateQSTile", "Error opening Refresh Rate settings", e)
        }
    }

    private fun updateTile() {
        tileScope.launch {
            try {
                val hasPermission = refreshRateManager.hasRequiredPermissions()
                val isRefreshRateEnabled = refreshRateManager.isRefreshRateEnabled()

                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext

                    if (!hasPermission) {
                        // No permission - same pattern as other tiles
                        val config = TileConfigHelper.getRefreshRateTileConfig(
                            this@RefreshRateQSTileService,
                            RefreshRateManager.RefreshRateState.DYNAMIC
                        )
                        tile.state = Tile.STATE_INACTIVE
                        tile.icon = config.icon
                        tile.label = getString(R.string.refresh_rate_permission_required)
                        tile.subtitle = getString(R.string.tap_to_grant)
                    } else if (!isRefreshRateEnabled) {
                        // Permission granted but not configured
                        val config = TileConfigHelper.getRefreshRateTileConfig(
                            this@RefreshRateQSTileService,
                            RefreshRateManager.RefreshRateState.DYNAMIC
                        )
                        tile.state = Tile.STATE_INACTIVE
                        tile.icon = config.icon
                        tile.label = getString(R.string.refresh_rate_not_configured)
                        tile.subtitle = getString(R.string.tap_to_setup)
                    } else {
                        // Fully functional
                        refreshRateManager.syncWithSystemSettings()
                        val currentState = refreshRateManager.getCurrentState()
                        val showHeading = qsTileSettingsManager.getShowRefreshRateInTitle()
                        val stateText = refreshRateManager.getCurrentStateText()
                        val config = TileConfigHelper.getRefreshRateTileConfig(
                            this@RefreshRateQSTileService,
                            currentState
                        )

                        TileConfigHelper.applyRefreshRateConfigToTile(
                            tile,
                            config,
                            stateText,
                            showHeading,
                            context = this@RefreshRateQSTileService
                        )
                    }

                    tile.updateTile()
                    android.util.Log.d("RefreshRateQSTile", "Tile updated successfully")
                }
            } catch (e: Exception) {
                android.util.Log.e("RefreshRateQSTile", "Error updating Refresh Rate tile", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }
}
