package com.redskul.macrostatshelper.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.redskul.macrostatshelper.utils.WorkManagerRepository

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
                    android.util.Log.d("BootReceiver", "Setup complete, starting WorkManager monitoring")

                    try {
                        // WorkManager handles persistence automatically, but we can restart to be sure
                        val workManagerRepository = WorkManagerRepository(context)
                        workManagerRepository.startMonitoring()
                        android.util.Log.d("BootReceiver", "WorkManager monitoring started successfully after boot")

                        // Note: Accessibility service will auto-start if enabled by user
                        android.util.Log.d("BootReceiver", "AutoSync accessibility service will auto-start if enabled")
                    } catch (e: Exception) {
                        android.util.Log.e("BootReceiver", "Failed to start WorkManager monitoring after boot", e)
                    }
                } else {
                    android.util.Log.d("BootReceiver", "Setup not complete, skipping monitoring start")
                }
            }
        }
    }
}
