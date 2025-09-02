package com.redskul.macrostatshelper.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpdateStatsReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_UPDATE_STATS = "com.redskul.macrostatshelper.UPDATE_STATS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_UPDATE_STATS) {
            // Trigger immediate WorkManager updates instead of service calls
            try {
                val workManagerRepository = WorkManagerRepository(context)
                workManagerRepository.triggerImmediateDataUpdate()
                workManagerRepository.triggerImmediateBatteryUpdate()
                android.util.Log.d("UpdateStatsReceiver", "Immediate updates triggered via WorkManager")
            } catch (e: Exception) {
                android.util.Log.e("UpdateStatsReceiver", "Failed to trigger immediate updates", e)
            }
        }
    }
}
