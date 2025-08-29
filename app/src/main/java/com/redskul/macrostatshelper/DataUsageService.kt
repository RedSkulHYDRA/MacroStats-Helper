package com.redskul.macrostatshelper

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class DataUsageService : Service() {

    private lateinit var dataUsageMonitor: DataUsageMonitor
    private lateinit var fileManager: FileManager
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var handler: Handler
    private lateinit var updateRunnable: Runnable

    companion object {
        const val UPDATE_INTERVAL = 30000L // 30 seconds
        const val ACTION_UPDATE_NOW = "UPDATE_NOW"
    }

    override fun onCreate() {
        super.onCreate()

        dataUsageMonitor = DataUsageMonitor(this)
        fileManager = FileManager(this)
        notificationHelper = NotificationHelper(this)
        handler = Handler(Looper.getMainLooper())

        createUpdateRunnable()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground immediately
        startForeground(NotificationHelper.NOTIFICATION_ID, createServiceNotification())

        // Handle immediate update request
        if (intent?.action == ACTION_UPDATE_NOW) {
            android.util.Log.d("DataUsageService", "Received immediate update request")
            // Trigger immediate update
            handler.post {
                updateUsageData()
            }
            return START_STICKY
        }

        // Start monitoring after a short delay to ensure service is properly started
        handler.postDelayed({
            startPeriodicUpdates()
        }, 2000) // 2 second delay

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createServiceNotification(): android.app.Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return android.app.Notification.Builder(this, NotificationHelper.CHANNEL_ID)
            .setContentTitle("Data Usage Monitor")
            .setContentText("Monitoring data usage...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createUpdateRunnable() {
        updateRunnable = object : Runnable {
            override fun run() {
                updateUsageData()
                handler.postDelayed(this, UPDATE_INTERVAL)
            }
        }
    }

    private fun startPeriodicUpdates() {
        updateUsageData() // Initial update
        handler.postDelayed(updateRunnable, UPDATE_INTERVAL)
    }

    private fun updateUsageData() {
        try {
            android.util.Log.d("DataUsageService", "Updating usage data")
            val usageData = dataUsageMonitor.getUsageData()
            fileManager.writeUsageToFile(usageData)
            notificationHelper.showUsageNotification(usageData)
        } catch (e: Exception) {
            android.util.Log.e("DataUsageService", "Error updating usage data", e)
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        notificationHelper.cancelNotification()
    }
}
