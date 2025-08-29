package com.redskul.macrostatshelper

import android.service.quicksettings.TileService
import android.service.quicksettings.Tile
import android.graphics.drawable.Icon

class WiFiDataUsageQSTileService : TileService() {

    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private lateinit var dataUsageMonitor: DataUsageMonitor

    override fun onCreate() {
        super.onCreate()
        qsTileSettingsManager = QSTileSettingsManager(this)
        dataUsageMonitor = DataUsageMonitor(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        updateTile()
    }

    private fun updateTile() {
        try {
            val usageData = dataUsageMonitor.getUsageData()
            val value = qsTileSettingsManager.getWiFiTileText(usageData)

            val tile = qsTile ?: return

            tile.state = Tile.STATE_INACTIVE
            tile.label = "│ $value"  // Using box drawing character for cleaner look
            tile.subtitle = null
            tile.icon = Icon.createWithResource(this, R.drawable.ic_wifi)

            tile.updateTile()

        } catch (e: Exception) {
            android.util.Log.e("WiFiQSTile", "Error updating WiFi tile", e)
        }
    }
}
