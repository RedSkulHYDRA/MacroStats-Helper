package com.redskul.macrostatshelper.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.redskul.macrostatshelper.datausage.DataUsageService
import com.redskul.macrostatshelper.battery.BatteryService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("BootReceiver", "Received broadcast: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {

                // Check if setup is complete
                val sharedPreferences = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                val setupComplete = sharedPreferences.getBoolean("setup_complete", false)

                if (setupComplete) {
                    android.util.Log.d("BootReceiver", "Setup complete, starting services")

                    try {
                        // Start data usage service
                        val dataServiceIntent = Intent(context, DataUsageService::class.java)
                        context.startForegroundService(dataServiceIntent)
                        android.util.Log.d("BootReceiver", "DataUsageService started successfully after boot")

                        // Start battery service
                        val batteryServiceIntent = Intent(context, BatteryService::class.java)
                        context.startService(batteryServiceIntent)
                        android.util.Log.d("BootReceiver", "BatteryService started successfully after boot")

                        // Note: Accessibility service will auto-start if enabled by user
                        android.util.Log.d("BootReceiver", "AutoSync accessibility service will auto-start if enabled")
                    } catch (e: Exception) {
                        android.util.Log.e("BootReceiver", "Failed to start services after boot", e)
                    }
                } else {
                    android.util.Log.d("BootReceiver", "Setup not complete, skipping service start")
                }
            }
        }
    }
}
