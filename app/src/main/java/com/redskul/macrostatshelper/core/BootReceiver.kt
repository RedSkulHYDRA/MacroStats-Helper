package com.redskul.macrostatshelper.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.redskul.macrostatshelper.data.DataUsageService

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
                    android.util.Log.d("BootReceiver", "Setup complete, starting service")

                    try {
                        val serviceIntent = Intent(context, DataUsageService::class.java)
                        context.startForegroundService(serviceIntent)
                        android.util.Log.d("BootReceiver", "DataUsageService started successfully after boot")
                    } catch (e: Exception) {
                        android.util.Log.e("BootReceiver", "Failed to start service after boot", e)
                    }
                } else {
                    android.util.Log.d("BootReceiver", "Setup not complete, skipping service start")
                }
            }
        }
    }
}