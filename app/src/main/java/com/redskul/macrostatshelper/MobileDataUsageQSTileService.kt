package com.redskul.macrostatshelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.quicksettings.TileService
import android.service.quicksettings.Tile
import android.graphics.drawable.Icon
import kotlinx.coroutines.*

class MobileDataUsageQSTileService : TileService() {

    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private lateinit var dataUsageMonitor: DataUsageMonitor
    private val tileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DataUsageService.ACTION_DATA_UPDATED) {
                android.util.Log.d("MobileQSTile", "Received data update broadcast")
                updateTile()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        qsTileSettingsManager = QSTileSettingsManager(this)
        dataUsageMonitor = DataUsageMonitor(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        // Register for data update broadcasts (Android 13+ only)
        registerReceiver(
            dataUpdateReceiver,
            IntentFilter(DataUsageService.ACTION_DATA_UPDATED),
            Context.RECEIVER_NOT_EXPORTED
        )
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        // Unregister broadcast receiver
        try {
            unregisterReceiver(dataUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver already unregistered
        }
    }

    override fun onClick() {
        super.onClick()
        android.util.Log.d("MobileQSTile", "Tile clicked - triggering immediate update")

        // Trigger immediate service update
        val serviceIntent = Intent(this, DataUsageService::class.java).apply {
            action = DataUsageService.ACTION_UPDATE_NOW
        }
        startService(serviceIntent)

        // Also update tile immediately with current data
        updateTile()
    }

    private fun updateTile() {
        tileScope.launch {
            try {
                val usageData = dataUsageMonitor.getUsageData()
                val value = qsTileSettingsManager.getMobileTileText(usageData)

                // Switch to main thread for UI updates
                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext

                    tile.state = Tile.STATE_INACTIVE
                    tile.label = "│ $value"
                    tile.subtitle = null
                    tile.icon = Icon.createWithResource(this@MobileDataUsageQSTileService, R.drawable.ic_mobile)

                    tile.updateTile()
                    android.util.Log.d("MobileQSTile", "Tile updated with: $value")
                }
            } catch (e: Exception) {
                android.util.Log.e("MobileQSTile", "Error updating Mobile tile", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }
}
