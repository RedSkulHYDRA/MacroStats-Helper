package com.redskul.macrostatshelper.data

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.redskul.macrostatshelper.notification.NotificationHelper
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.core.MainActivity
import kotlinx.coroutines.*

class BatteryService : Service() {

    private lateinit var batteryChargeMonitor: BatteryChargeMonitor

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updateJob: Job? = null

    companion object {
        const val UPDATE_INTERVAL = 900000L // 15 minutes
        const val ACTION_UPDATE_NOW = "BATTERY_UPDATE_NOW"
        const val ACTION_BATTERY_UPDATED = "com.redskul.macrostatshelper.BATTERY_UPDATED"
    }

    override fun onCreate() {
        super.onCreate()
        batteryChargeMonitor = BatteryChargeMonitor(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_NOW -> {
                android.util.Log.d("BatteryService", "Received immediate update request")
                serviceScope.launch {
                    updateBatteryData()
                }
                return START_STICKY
            }
        }

        serviceScope.launch {
            delay(2000)
            startPeriodicUpdates()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun startPeriodicUpdates() {
        updateJob?.cancel()

        updateJob = serviceScope.launch {
            updateBatteryData()

            while (isActive) {
                delay(UPDATE_INTERVAL)
                if (isActive) {
                    updateBatteryData()
                }
            }
        }
    }

    private suspend fun updateBatteryData() {
        try {
            android.util.Log.d("BatteryService", "Updating battery data")
            val chargeData = batteryChargeMonitor.getChargeData()

            withContext(Dispatchers.Main) {
                // Send broadcast to charge cycle QS tile
                val broadcastIntent = Intent(ACTION_BATTERY_UPDATED)
                sendBroadcast(broadcastIntent)
                android.util.Log.d("BatteryService", "Battery update broadcast sent")
            }
        } catch (e: Exception) {
            android.util.Log.e("BatteryService", "Error updating battery data", e)
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        serviceScope.cancel()
    }
}
