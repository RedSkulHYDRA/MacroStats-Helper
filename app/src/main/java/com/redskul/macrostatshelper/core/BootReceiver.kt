package com.redskul.macrostatshelper.core

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import com.redskul.macrostatshelper.autosync.AutoSyncManager
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

                        // This ensures fresh data is available immediately after device restart
                        workManagerRepository.triggerImmediateUpdates()
                        android.util.Log.d("BootReceiver", "Immediate data and battery updates triggered after boot")

                        // Note: Accessibility service will auto-start if enabled by user
                        android.util.Log.d("BootReceiver", "AutoSync accessibility service will auto-start if enabled")

                        // Handle AutoSync state restoration on boot
                        val autoSyncManager = AutoSyncManager(context)
                        if (autoSyncManager.isAutoSyncEnabled()) {
                            // Check if device is currently locked and restore appropriate sync state
                            val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
                            if (!keyguardManager.isKeyguardLocked) {
                                // Device is unlocked on boot, ensure sync is enabled
                                try {
                                    ContentResolver.setMasterSyncAutomatically(true)
                                    android.util.Log.d("BootReceiver", "Enabled AutoSync on boot (device unlocked)")
                                } catch (e: Exception) {
                                    android.util.Log.e("BootReceiver", "Error enabling sync on boot", e)
                                }
                            }
                        }
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
