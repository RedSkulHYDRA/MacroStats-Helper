package com.redskul.macrostatshelper.headsup

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import androidx.annotation.RequiresApi
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.settings.QSTileSettingsActivity
import com.redskul.macrostatshelper.settings.QSTileSettingsManager
import com.redskul.macrostatshelper.tiles.BaseQSTileService
import kotlinx.coroutines.*

class HeadsUpQSTileService : BaseQSTileService() {

    private lateinit var headsUpManager: HeadsUpManager
    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private val tileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_HEADS_UP_SETTINGS_UPDATED = "com.redskul.macrostatshelper.HEADS_UP_SETTINGS_UPDATED"
    }

    private val settingsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_HEADS_UP_SETTINGS_UPDATED) {
                android.util.Log.d("HeadsUpQSTile", "Received settings update broadcast")
                updateTile()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        headsUpManager = HeadsUpManager(this)
        qsTileSettingsManager = QSTileSettingsManager(this)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        qsTile?.let { tile ->
            tile.label = getString(R.string.heads_up)
            tile.updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        registerReceiver(
            settingsUpdateReceiver,
            IntentFilter(ACTION_HEADS_UP_SETTINGS_UPDATED),
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

        if (!headsUpManager.hasRequiredPermissions()) {
            requestRequiredPermissions()
            return
        }

        // Toggle immediately on the main thread — no coroutine needed
        val newState = headsUpManager.toggle()
        if (newState != null) {
            updateTile()
            android.util.Log.d("HeadsUpQSTile", "Heads-up toggled to: $newState")
        } else {
            android.util.Log.e("HeadsUpQSTile", "Failed to toggle heads-up")
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
                6,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            startActivityAndCollapse(pendingIntent)
            android.util.Log.d("HeadsUpQSTile", "Permission request activity started")
        } catch (e: Exception) {
            android.util.Log.e("HeadsUpQSTile", "Error starting permission activity", e)
        }
    }

    private fun updateTile() {
        tileScope.launch {
            try {
                val hasPermission = headsUpManager.hasRequiredPermissions()
                val isEnabled = headsUpManager.isHeadsUpEnabled()
                val showHeading = qsTileSettingsManager.getShowHeadsUpInTitle()
                val stateText = headsUpManager.getCurrentStateText()

                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext

                    if (!hasPermission) {
                        tile.state = Tile.STATE_INACTIVE
                        tile.icon = Icon.createWithResource(
                            this@HeadsUpQSTileService,
                            R.drawable.ic_heads_up_off
                        )
                        tile.label = getString(R.string.heads_up_permission_required)
                        tile.subtitle = getString(R.string.tap_to_grant)
                    } else {
                        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                        tile.icon = Icon.createWithResource(
                            this@HeadsUpQSTileService,
                            if (isEnabled) R.drawable.ic_heads_up_on else R.drawable.ic_heads_up_off
                        )

                        if (showHeading) {
                            tile.label = getString(R.string.heads_up_heading)
                            tile.subtitle = stateText
                        } else {
                            tile.label = stateText
                            tile.subtitle = null
                        }
                    }

                    tile.updateTile()
                    android.util.Log.d("HeadsUpQSTile", "Tile updated — enabled: $isEnabled")
                }
            } catch (e: Exception) {
                android.util.Log.e("HeadsUpQSTile", "Error updating tile", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }
}