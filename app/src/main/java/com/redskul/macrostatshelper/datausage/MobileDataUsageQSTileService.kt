package com.redskul.macrostatshelper.datausage

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import com.redskul.macrostatshelper.tiles.BaseQSTileService
import androidx.annotation.RequiresApi
import com.redskul.macrostatshelper.settings.QSTileSettingsManager
import com.redskul.macrostatshelper.tiles.TileConfigHelper
import com.redskul.macrostatshelper.utils.PermissionHelper
import com.redskul.macrostatshelper.utils.WorkManagerRepository
import kotlinx.coroutines.*
import com.redskul.macrostatshelper.R

class MobileDataUsageQSTileService : BaseQSTileService() {

    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private lateinit var dataUsageMonitor: DataUsageMonitor
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var workManagerRepository: WorkManagerRepository
    private val tileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val dataUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == DataUsageWorker.ACTION_DATA_UPDATED) {
                android.util.Log.d("MobileQSTile", "Received data update broadcast")
                updateTile()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        qsTileSettingsManager = QSTileSettingsManager(this)
        dataUsageMonitor = DataUsageMonitor(this)
        permissionHelper = PermissionHelper(this)
        workManagerRepository = WorkManagerRepository(this)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        // Ensure tile picker always shows the manifest label
        qsTile?.let { tile ->
            tile.label = getString(R.string.mobile_data)
            tile.updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        registerReceiver(
            dataUpdateReceiver,
            IntentFilter(DataUsageWorker.ACTION_DATA_UPDATED),
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

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTileClick() {

        if (!permissionHelper.hasUsageStatsPermission()) {
            // Request permission by opening settings
            requestUsageStatsPermission()
            return
        }

        // Permission is granted, proceed with normal functionality
        android.util.Log.d("MobileQSTile", "Tile clicked - triggering immediate update")

        workManagerRepository.triggerImmediateDataUpdate()
        updateTile()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestUsageStatsPermission() {
        try {
            // Create intent for usage stats permission
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Create PendingIntent
            val pendingIntent = PendingIntent.getActivity(
                this,
                1, // Different request code from WiFi tile
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Start the activity and collapse the panel using PendingIntent
            startActivityAndCollapse(pendingIntent)

            android.util.Log.d("MobileQSTile", "Permission request activity started with PendingIntent")
        } catch (e: Exception) {
            android.util.Log.e("MobileQSTile", "Error starting permission activity", e)
        }
    }

    private fun updateTile() {
        tileScope.launch {
            try {
                val hasPermission = permissionHelper.hasUsageStatsPermission()
                val config = TileConfigHelper.getMobileTileConfig(this@MobileDataUsageQSTileService)
                val showPeriodInTitle = qsTileSettingsManager.getShowPeriodInTitle()
                val period = qsTileSettingsManager.getMobileTilePeriod()

                if (hasPermission) {
                    val usageData = dataUsageMonitor.getUsageData()
                    val value = qsTileSettingsManager.getMobileTileText(usageData)

                    withContext(Dispatchers.Main) {
                        val tile = qsTile ?: return@withContext
                        TileConfigHelper.applyConfigToTile(
                            tile,
                            config,
                            value,
                            showPeriodInTitle,
                            period,
                            isWifi = false,
                            hasPermission = true,
                            context = this@MobileDataUsageQSTileService
                        )
                        tile.updateTile()
                        android.util.Log.d("MobileQSTile", "Tile updated with: $value")
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        val tile = qsTile ?: return@withContext
                        TileConfigHelper.applyConfigToTile(
                            tile,
                            config,
                            "",
                            showPeriodInTitle,
                            period,
                            isWifi = false,
                            hasPermission = false,
                            context = this@MobileDataUsageQSTileService
                        )
                        tile.updateTile()
                        android.util.Log.d("MobileQSTile", "Tile updated - Permission required")
                    }
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
