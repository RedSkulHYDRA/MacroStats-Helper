package com.redskul.macrostatshelper.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.redskul.macrostatshelper.data.DataUsageService

class UpdateStatsReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_UPDATE_STATS = "com.redskul.macrostatshelper.UPDATE_STATS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_UPDATE_STATS) {
            // Send intent to service to trigger immediate update
            val serviceIntent = Intent(context, DataUsageService::class.java).apply {
                action = DataUsageService.ACTION_UPDATE_NOW
            }
            try {
                context.startService(serviceIntent)
                android.util.Log.d("UpdateStatsReceiver", "Update stats request sent to service")
            } catch (e: Exception) {
                android.util.Log.e("UpdateStatsReceiver", "Failed to send update request", e)
            }
        }
    }
}
