package com.redskul.macrostatshelper.aod

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.settings.QSTileSettingsActivity
import com.redskul.macrostatshelper.settings.QSTileSettingsManager
import com.redskul.macrostatshelper.tiles.TileConfigHelper
import kotlinx.coroutines.*

class AODQSTileService : TileService() {

    private lateinit var aodManager: AODManager
    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private val tileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_AOD_SETTINGS_UPDATED = "com.redskul.macrostatshelper.AOD_SETTINGS_UPDATED"
    }

    private val settingsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_AOD_SETTINGS_UPDATED) {
                android.util.Log.d("AODQSTile", "Received settings update broadcast")
                updateTile()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        aodManager = AODManager(this)
        qsTileSettingsManager = QSTileSettingsManager(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        registerReceiver(
            settingsUpdateReceiver,
            IntentFilter(ACTION_AOD_SETTINGS_UPDATED),
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
    override fun onClick() {
        super.onClick()

        if (!aodManager.hasRequiredPermissions()) {
            // Request permission by opening settings
            requestRequiredPermissions()
            return
        }

        if (!aodManager.isAODEnabled()) {
            // Open settings if AOD tile is not configured
            openAODSettings()
            return
        }

        // Cycle to next state
        tileScope.launch {
            val success = aodManager.cycleToNextState()
            withContext(Dispatchers.Main) {
                if (success) {
                    updateTile()
                    android.util.Log.d("AODQSTile", "State changed to: ${aodManager.getCurrentStateText()}")
                } else {
                    android.util.Log.e("AODQSTile", "Failed to change state")
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
                5, // Different request code from other tiles
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            startActivityAndCollapse(pendingIntent)
            android.util.Log.d("AODQSTile", "Permission request activity started with PendingIntent")
        } catch (e: Exception) {
            android.util.Log.e("AODQSTile", "Error starting permission activity", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun openAODSettings() {
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
            android.util.Log.d("AODQSTile", "Opened AOD settings")
        } catch (e: Exception) {
            android.util.Log.e("AODQSTile", "Error opening AOD settings", e)
        }
    }

    private fun updateTile() {
        tileScope.launch {
            try {
                val hasPermission = aodManager.hasRequiredPermissions()
                val isAODEnabled = aodManager.isAODEnabled()

                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext

                    if (!hasPermission) {
                        // No permission - same pattern as other tiles
                        val config = TileConfigHelper.getAODTileConfig(
                            this@AODQSTileService,
                            AODManager.AODState.OFF
                        )
                        tile.state = Tile.STATE_INACTIVE
                        tile.icon = config.icon
                        tile.label = getString(R.string.aod_permission_required)
                        tile.subtitle = getString(R.string.tap_to_grant)
                    } else if (!isAODEnabled) {
                        // Permission granted but not configured
                        val config = TileConfigHelper.getAODTileConfig(
                            this@AODQSTileService,
                            AODManager.AODState.OFF
                        )
                        tile.state = Tile.STATE_INACTIVE
                        tile.icon = config.icon
                        tile.label = getString(R.string.aod_not_configured)
                        tile.subtitle = getString(R.string.tap_to_setup)
                    } else {
                        // Fully functional
                        aodManager.syncWithSystemSettings()
                        val currentState = aodManager.getCurrentState()
                        val showHeading = qsTileSettingsManager.getShowAODInTitle()
                        val stateText = aodManager.getCurrentStateText()
                        val config = TileConfigHelper.getAODTileConfig(
                            this@AODQSTileService,
                            currentState
                        )

                        TileConfigHelper.applyAODConfigToTile(
                            tile,
                            config,
                            stateText,
                            showHeading,
                            context = this@AODQSTileService
                        )
                    }

                    tile.updateTile()
                    android.util.Log.d("AODQSTile", "Tile updated successfully")
                }
            } catch (e: Exception) {
                android.util.Log.e("AODQSTile", "Error updating AOD tile", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }
}
