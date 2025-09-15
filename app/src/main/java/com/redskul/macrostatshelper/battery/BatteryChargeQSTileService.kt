package com.redskul.macrostatshelper.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.redskul.macrostatshelper.tiles.BaseQSTileService
import androidx.annotation.RequiresApi
import com.redskul.macrostatshelper.settings.QSTileSettingsManager
import com.redskul.macrostatshelper.tiles.TileConfigHelper
import com.redskul.macrostatshelper.utils.WorkManagerRepository
import kotlinx.coroutines.*
import com.redskul.macrostatshelper.R

class BatteryChargeQSTileService : BaseQSTileService() {

    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private lateinit var batteryChargeMonitor: BatteryChargeMonitor
    private lateinit var workManagerRepository: WorkManagerRepository
    private val tileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val batteryUpdateReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BatteryWorker.ACTION_BATTERY_UPDATED) {
                android.util.Log.d("ChargeQSTile", "Received battery update broadcast")
                updateTile()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        qsTileSettingsManager = QSTileSettingsManager(this)
        batteryChargeMonitor = BatteryChargeMonitor(this)
        workManagerRepository = WorkManagerRepository(this)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        // Ensure tile picker always shows the manifest label
        qsTile?.let { tile ->
            tile.label = getString(R.string.charge_cycles)
            tile.updateTile()
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onStartListening() {
        super.onStartListening()
        registerReceiver(
            batteryUpdateReceiver,
            IntentFilter(BatteryWorker.ACTION_BATTERY_UPDATED),
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

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTileClick() {
        android.util.Log.d("ChargeQSTile", "Tile clicked - triggering immediate update")

        workManagerRepository.triggerImmediateBatteryUpdate()
        updateTile()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun updateTile() {
        tileScope.launch {
            try {
                val chargeData = batteryChargeMonitor.getChargeData()
                val value = chargeData.chargeCycles
                val config = TileConfigHelper.getChargeTileConfig(this@BatteryChargeQSTileService)
                val showChargeInTitle = qsTileSettingsManager.getShowChargeInTitle()

                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext
                    TileConfigHelper.applyChargeConfigToTile(
                        tile,
                        config,
                        value,
                        showChargeInTitle,
                        context = this@BatteryChargeQSTileService
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
