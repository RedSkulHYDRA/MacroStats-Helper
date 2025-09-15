package com.redskul.macrostatshelper.battery

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.redskul.macrostatshelper.tiles.BaseQSTileService
import androidx.annotation.RequiresApi
import com.redskul.macrostatshelper.settings.QSTileSettingsActivity
import com.redskul.macrostatshelper.settings.QSTileSettingsManager
import com.redskul.macrostatshelper.tiles.TileConfigHelper
import kotlinx.coroutines.*
import com.redskul.macrostatshelper.R

class BatteryHealthQSTileService : BaseQSTileService() {

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

    override fun onTileAdded() {
        super.onTileAdded()
        // Ensure tile picker always shows the manifest label
        qsTile?.let { tile ->
            tile.label = getString(R.string.battery_health)
            tile.updateTile()
        }
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

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTileClick() {

        // Always open settings when tapped - handles all scenarios
        openBatteryHealthSettings()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun openBatteryHealthSettings() {
        try {
            val intent = Intent(this, QSTileSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                2, // Different request code
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            startActivityAndCollapse(pendingIntent)
            android.util.Log.d("BatteryHealthQSTile", "Opened battery health settings")
        } catch (e: Exception) {
            android.util.Log.e("BatteryHealthQSTile", "Error opening battery health settings", e)
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        // Check if we have necessary permissions for battery health monitoring
        // For now, we'll assume basic battery stats are always available
        // Add specific permission checks here if needed
        return true
    }

    private fun updateTile() {
        tileScope.launch {
            try {
                val hasPermission = hasRequiredPermissions()
                val designCapacityValue = batteryHealthMonitor.getDesignCapacity()
                val healthData = batteryHealthMonitor.getBatteryHealthData()
                val showHealthInTitle = qsTileSettingsManager.getShowBatteryHealthInTitle()
                val config = TileConfigHelper.getBatteryHealthTileConfig(this@BatteryHealthQSTileService)

                // Check if health is actually calculated (not just estimating)
                val isHealthCalculated = healthData.healthPercentage.isNotBlank() &&
                        !healthData.healthPercentage.contains("Set design capacity") &&
                        !healthData.healthPercentage.contains("Calculating") &&
                        healthData.healthPercentage.matches(Regex("\\d+%"))

                android.util.Log.d("BatteryHealthQSTile", "=== Battery Health Tile Debug ===")
                android.util.Log.d("BatteryHealthQSTile", "Has Permission: $hasPermission")
                android.util.Log.d("BatteryHealthQSTile", "Design Capacity Value: $designCapacityValue")
                android.util.Log.d("BatteryHealthQSTile", "Health Percentage: '${healthData.healthPercentage}'")
                android.util.Log.d("BatteryHealthQSTile", "Is Health Calculated: $isHealthCalculated")
                android.util.Log.d("BatteryHealthQSTile", "Show Health In Title: $showHealthInTitle")

                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext
                    TileConfigHelper.applyBatteryHealthConfigToTile(
                        tile,
                        config,
                        healthData.healthPercentage,
                        healthData.currentFcc,
                        healthData.designCapacity,
                        showHealthInTitle,
                        designCapacityValue,
                        hasPermission,
                        isHealthCalculated,
                        context = this@BatteryHealthQSTileService
                    )
                    tile.updateTile()
                    android.util.Log.d("BatteryHealthQSTile", "Tile updated - State: ${tile.state}, Label: '${tile.label}', Subtitle: '${tile.subtitle}'")
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
