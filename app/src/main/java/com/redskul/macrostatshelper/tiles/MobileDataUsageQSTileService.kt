package com.redskul.macrostatshelper.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.quicksettings.TileService
import com.redskul.macrostatshelper.data.DataUsageMonitor
import com.redskul.macrostatshelper.data.DataUsageService
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
        android.util.Log.d("MobileQSTile", "Tile clicked - triggering immediate update")

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
                val value = qsTileSettingsManager.getMobileTileText(usageData)
                val config = TileConfigHelper.getMobileTileConfig(this@MobileDataUsageQSTileService)
                val showPeriodInTitle = qsTileSettingsManager.getShowPeriodInTitle()
                val period = qsTileSettingsManager.getMobileTilePeriod()

                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext
                    TileConfigHelper.applyConfigToTile(
                        tile,
                        config,
                        value,
                        showPeriodInTitle,
                        period,
                        isWifi = false,
                        context = this@MobileDataUsageQSTileService
                    )
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
