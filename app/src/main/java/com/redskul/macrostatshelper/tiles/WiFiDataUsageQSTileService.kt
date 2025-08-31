package com.redskul.macrostatshelper.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.quicksettings.TileService
import com.redskul.macrostatshelper.data.DataUsageMonitor
import com.redskul.macrostatshelper.data.DataUsageService
import kotlinx.coroutines.*

class WiFiDataUsageQSTileService : TileService() {

    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private lateinit var dataUsageMonitor: DataUsageMonitor
    private val tileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DataUsageService.ACTION_DATA_UPDATED) {
                android.util.Log.d("WiFiQSTile", "Received data update broadcast")
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
        registerReceiver(
            dataUpdateReceiver,
            IntentFilter(DataUsageService.ACTION_DATA_UPDATED),
            Context.RECEIVER_NOT_EXPORTED
        )
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        try {
            unregisterReceiver(dataUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver already unregistered
        }
    }

    override fun onClick() {
        super.onClick()
        android.util.Log.d("WiFiQSTile", "Tile clicked - triggering immediate update")

        // Provide vibration feedback
        TileVibrationHelper.vibrateTile(this)

        val serviceIntent = Intent(this, DataUsageService::class.java).apply {
            action = DataUsageService.ACTION_UPDATE_NOW
        }
        startService(serviceIntent)
        updateTile()
    }

    private fun updateTile() {
        tileScope.launch {
            try {
                val usageData = dataUsageMonitor.getUsageData()
                val value = qsTileSettingsManager.getWiFiTileText(usageData)
                val config = TileConfigHelper.getWiFiTileConfig(this@WiFiDataUsageQSTileService)
                val showPeriodInTitle = qsTileSettingsManager.getShowPeriodInTitle()
                val period = qsTileSettingsManager.getWiFiTilePeriod()

                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext
                    TileConfigHelper.applyConfigToTile(
                        tile,
                        config,
                        value,
                        showPeriodInTitle,
                        period,
                        isWifi = true,
                        context = this@WiFiDataUsageQSTileService
                    )
                    tile.updateTile()
                    android.util.Log.d("WiFiQSTile", "Tile updated with: $value")
                }
            } catch (e: Exception) {
                android.util.Log.e("WiFiQSTile", "Error updating WiFi tile", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }
}
