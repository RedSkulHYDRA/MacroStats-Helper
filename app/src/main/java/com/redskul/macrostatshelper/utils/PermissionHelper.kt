package com.redskul.macrostatshelper.utils

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.redskul.macrostatshelper.autosync.AutoSyncAccessibilityService

class PermissionHelper(private val context: Context) {

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasWriteSettingsPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.System.canWrite(context)
        } else {
            true
        }
    }

    fun hasAccessibilityPermission(): Boolean {
        return AutoSyncAccessibilityService.isAccessibilityServiceEnabled(context)
    }

    fun hasAllPermissions(): Boolean {
        return hasNotificationPermission() &&
                hasUsageStatsPermission() &&
                hasWriteSettingsPermission() &&
                hasAccessibilityPermission()
    }
}
