package com.redskul.macrostatshelper.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.quicksettings.TileService
import com.redskul.macrostatshelper.data.BatteryChargeMonitor
import com.redskul.macrostatshelper.data.BatteryService
import kotlinx.coroutines.*

class ChargeQSTileService : TileService() {

    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private lateinit var batteryChargeMonitor: BatteryChargeMonitor
    private val tileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val batteryUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BatteryService.ACTION_BATTERY_UPDATED) {
                android.util.Log.d("ChargeQSTile", "Received battery update broadcast")
                updateTile()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        qsTileSettingsManager = QSTileSettingsManager(this)
        batteryChargeMonitor = BatteryChargeMonitor(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        registerReceiver(
            batteryUpdateReceiver,
            IntentFilter(BatteryService.ACTION_BATTERY_UPDATED),
            Context.RECEIVER_NOT_EXPORTED
        )
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        try {
            unregisterReceiver(batteryUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver already unregistered
        }
    }

    override fun onClick() {
        super.onClick()
        android.util.Log.d("ChargeQSTile", "Tile clicked - triggering immediate update")

        val serviceIntent = Intent(this, BatteryService::class.java).apply {
            action = BatteryService.ACTION_UPDATE_NOW
        }
        startService(serviceIntent)
        updateTile()
    }

    private fun updateTile() {
        tileScope.launch {
            try {
                val chargeData = batteryChargeMonitor.getChargeData()
                val value = chargeData.chargeCycles
                val config = TileConfigHelper.getChargeTileConfig(this@ChargeQSTileService)
                val showChargeInTitle = qsTileSettingsManager.getShowChargeInTitle()

                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext
                    TileConfigHelper.applyChargeConfigToTile(
                        tile,
                        config,
                        value,
                        showChargeInTitle,
                        context = this@ChargeQSTileService
                    )
                    tile.updateTile()
                    android.util.Log.d("ChargeQSTile", "Tile updated with: $value")
                }
            } catch (e: Exception) {
                android.util.Log.e("ChargeQSTile", "Error updating Charge tile", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }
}
