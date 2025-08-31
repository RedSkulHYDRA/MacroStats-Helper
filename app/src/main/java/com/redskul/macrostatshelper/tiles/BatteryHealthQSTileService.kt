package com.redskul.macrostatshelper.tiles

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.quicksettings.TileService
import com.redskul.macrostatshelper.data.BatteryHealthMonitor
import kotlinx.coroutines.*

class BatteryHealthQSTileService : TileService() {

    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private lateinit var batteryHealthMonitor: BatteryHealthMonitor
    private val tileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_BATTERY_HEALTH_UPDATED = "com.redskul.macrostatshelper.BATTERY_HEALTH_UPDATED"
    }

    private val batteryUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_BATTERY_HEALTH_UPDATED,
                Intent.ACTION_BATTERY_CHANGED -> {
                    android.util.Log.d("BatteryHealthQSTile", "Received battery update broadcast")
                    updateTile()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        qsTileSettingsManager = QSTileSettingsManager(this)
        batteryHealthMonitor = BatteryHealthMonitor(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        val filter = IntentFilter().apply {
            addAction(ACTION_BATTERY_HEALTH_UPDATED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        registerReceiver(batteryUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
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
        android.util.Log.d("BatteryHealthQSTile", "Tile clicked - triggering immediate update")

        // Provide vibration feedback
        TileVibrationHelper.vibrateTile(this)

        updateTile()

        // Send broadcast to trigger update
        val broadcastIntent = Intent(ACTION_BATTERY_HEALTH_UPDATED).apply {
            setPackage(packageName)
        }
        sendBroadcast(broadcastIntent)
    }

    private fun updateTile() {
        tileScope.launch {
            try {
                val healthData = batteryHealthMonitor.getBatteryHealthData()
                val healthPercentage = healthData.healthPercentage
                val currentFcc = healthData.currentFcc
                val designCapacity = healthData.designCapacity
                val config = TileConfigHelper.getBatteryHealthTileConfig(this@BatteryHealthQSTileService)
                val showHealthInTitle = qsTileSettingsManager.getShowBatteryHealthInTitle()

                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext
                    TileConfigHelper.applyBatteryHealthConfigToTile(
                        tile,
                        config,
                        healthPercentage,
                        currentFcc,
                        designCapacity,
                        showHealthInTitle,
                        context = this@BatteryHealthQSTileService
                    )
                    tile.updateTile()
                    android.util.Log.d("BatteryHealthQSTile", "Tile updated with detailed stats: $healthPercentage ($currentFcc / $designCapacity)")
                }
            } catch (e: Exception) {
                android.util.Log.e("BatteryHealthQSTile", "Error updating Battery Health tile", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }
}
