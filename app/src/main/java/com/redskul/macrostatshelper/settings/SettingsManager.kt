package com.redskul.macrostatshelper.settings

import android.content.Context
import android.content.SharedPreferences
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.datausage.UsageData
import com.redskul.macrostatshelper.utils.PermissionHelper

class SettingsManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)

    private val permissionHelper = PermissionHelper(context)

    companion object {
        private const val KEY_WIFI_PERIODS = "wifi_periods"
        private const val KEY_MOBILE_PERIODS = "mobile_periods"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_SHOW_NOTIFICATION = "show_notification"
        private const val KEY_SHOW_LAST_MONTH_USAGE = "show_last_month_usage"
        private const val KEY_SHOW_LAST_WEEK_USAGE = "show_last_week_usage"
        private const val KEY_SHOW_YESTERDAY_USAGE = "show_yesterday_usage"
        private const val KEY_UPDATE_INTERVAL = "update_interval_minutes"
        private const val DEFAULT_UPDATE_INTERVAL = 15 // Default interval is now 15 minutes
    }

    fun saveDisplaySettings(settings: DisplaySettings) {
        sharedPreferences.edit().apply {
            // Save WiFi periods as comma-separated string (empty string if no periods selected)
            val wifiPeriodsString = settings.wifiTimePeriods.joinToString(",") { it.name }
            putString(KEY_WIFI_PERIODS, wifiPeriodsString)

            // Save Mobile periods as comma-separated string (empty string if no periods selected)
            val mobilePeriodsString = settings.mobileTimePeriods.joinToString(",") { it.name }
            putString(KEY_MOBILE_PERIODS, mobilePeriodsString)

            // Mark that settings have been saved at least once
            putBoolean(KEY_FIRST_LAUNCH, false)

            apply()
        }
    }

    fun getDisplaySettings(): DisplaySettings {
        val isFirstLaunch = sharedPreferences.getBoolean(KEY_FIRST_LAUNCH, true)

        // If it's the first launch, provide default settings
        if (isFirstLaunch) {
            return DisplaySettings(
                wifiTimePeriods = listOf(TimePeriod.DAILY),
                mobileTimePeriods = listOf(TimePeriod.DAILY)
            )
        }

        val wifiPeriodsString = sharedPreferences.getString(KEY_WIFI_PERIODS, "")
        val mobilePeriodsString = sharedPreferences.getString(KEY_MOBILE_PERIODS, "")

        val wifiPeriods = if (wifiPeriodsString.isNullOrEmpty()) {
            emptyList()
        } else {
            wifiPeriodsString.split(",").mapNotNull { periodName ->
                try {
                    TimePeriod.valueOf(periodName.trim())
                } catch (_: Exception) {
                    null
                }
            }
        }

        val mobilePeriods = if (mobilePeriodsString.isNullOrEmpty()) {
            emptyList()
        } else {
            mobilePeriodsString.split(",").mapNotNull { periodName ->
                try {
                    TimePeriod.valueOf(periodName.trim())
                } catch (_: Exception) {
                    null
                }
            }
        }

        return DisplaySettings(wifiPeriods, mobilePeriods)
    }

    fun saveNotificationEnabled(enabled: Boolean) {
        // Check permission before saving
        if (enabled && !permissionHelper.hasUsageStatsPermission()) {
            android.util.Log.w("SettingsManager", "Cannot enable notifications without usage stats permission")
            return
        }
        sharedPreferences.edit().putBoolean(KEY_SHOW_NOTIFICATION, enabled).apply()
    }

    fun isNotificationEnabled(): Boolean {
        // Return false if permission is not granted, regardless of saved preference
        if (!permissionHelper.hasUsageStatsPermission()) {
            return false
        }
        return sharedPreferences.getBoolean(KEY_SHOW_NOTIFICATION, true)
    }

    fun saveShowLastMonthUsage(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_LAST_MONTH_USAGE, enabled).apply()
    }

    fun isShowLastMonthUsageEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_LAST_MONTH_USAGE, true)
    }

    fun saveShowLastWeekUsage(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_LAST_WEEK_USAGE, enabled).apply()
    }

    fun isShowLastWeekUsageEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_LAST_WEEK_USAGE, false)
    }

    fun saveShowYesterdayUsage(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_YESTERDAY_USAGE, enabled).apply()
    }

    fun isShowYesterdayUsageEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_YESTERDAY_USAGE, false)
    }

    fun enforcePermissionRestrictions() {
        // If usage stats permission is revoked, disable notifications
        if (!permissionHelper.hasUsageStatsPermission() && isNotificationEnabledRaw()) {
            sharedPreferences.edit().putBoolean(KEY_SHOW_NOTIFICATION, false).apply()
            android.util.Log.i("SettingsManager", "Disabled notifications due to missing usage stats permission")
        }
    }

    private fun isNotificationEnabledRaw(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_NOTIFICATION, true)
    }

    // Update interval methods
    fun setUpdateInterval(intervalMinutes: Int) {
        if (getUpdateIntervalValues().contains(intervalMinutes)) {
            sharedPreferences.edit().putInt(KEY_UPDATE_INTERVAL, intervalMinutes).apply()
            android.util.Log.d("SettingsManager", "Update interval set to: $intervalMinutes minutes")
        } else {
            android.util.Log.w("SettingsManager", "Invalid update interval: $intervalMinutes")
        }
    }

    fun getUpdateInterval(): Int {
        return sharedPreferences.getInt(KEY_UPDATE_INTERVAL, DEFAULT_UPDATE_INTERVAL)
    }

    fun getUpdateIntervalValues(): List<Int> {
        // UPDATED: Changed intervals to respect WorkManager's 15-minute minimum
        return listOf(15, 30, 45, 60)
    }

    fun getUpdateIntervalOptions(): List<String> {
        // UPDATED: Changed display options to match new interval values
        return getUpdateIntervalValues().map { minutes ->
            when (minutes) {
                60 -> "1 hour"
                else -> "$minutes minutes"
            }
        }
    }

    fun getFormattedUsageText(usageData: UsageData): Pair<String, String> {
        val settings = getDisplaySettings()
        val wifiParts = mutableListOf<String>()
        val mobileParts = mutableListOf<String>()

        // Build WiFi parts
        settings.wifiTimePeriods.forEach { period ->
            val value = when (period) {
                TimePeriod.DAILY -> usageData.wifiDaily
                TimePeriod.WEEKLY -> usageData.wifiWeekly
                TimePeriod.MONTHLY -> usageData.wifiMonthly
            }
            val periodPrefix = when (period) {
                TimePeriod.DAILY -> context.getString(R.string.daily_prefix)
                TimePeriod.WEEKLY -> context.getString(R.string.weekly_prefix)
                TimePeriod.MONTHLY -> context.getString(R.string.monthly_prefix)
            }
            wifiParts.add("$periodPrefix $value")
        }

        // Build Mobile parts
        settings.mobileTimePeriods.forEach { period ->
            val value = when (period) {
                TimePeriod.DAILY -> usageData.mobileDaily
                TimePeriod.WEEKLY -> usageData.mobileWeekly
                TimePeriod.MONTHLY -> usageData.mobileMonthly
            }
            val periodPrefix = when (period) {
                TimePeriod.DAILY -> context.getString(R.string.daily_prefix)
                TimePeriod.WEEKLY -> context.getString(R.string.weekly_prefix)
                TimePeriod.MONTHLY -> context.getString(R.string.monthly_prefix)
            }
            mobileParts.add("$periodPrefix $value")
        }

        // Create short text
        val shortTextParts = mutableListOf<String>()
        if (wifiParts.isNotEmpty()) {
            shortTextParts.add("WiFi: ${wifiParts.joinToString(", ")}")
        }
        if (mobileParts.isNotEmpty()) {
            shortTextParts.add("Mobile: ${mobileParts.joinToString(", ")}")
        }

        val shortText = if (shortTextParts.isNotEmpty()) {
            shortTextParts.joinToString()
        } else {
            context.getString(R.string.no_data_selected)
        }

        // Create expanded text with conditional additional usage sections
        val expandedText = buildString {
            if (wifiParts.isNotEmpty()) {
                appendLine(context.getString(R.string.wifi_usage_label))
                appendLine(wifiParts.joinToString(" | "))
            }
            if (mobileParts.isNotEmpty()) {
                if (wifiParts.isNotEmpty()) {
                    appendLine() // Add blank line between sections
                }
                appendLine(context.getString(R.string.mobile_data_usage_label))
                appendLine(mobileParts.joinToString(" | "))
            }

            // Add Last Month's Usage section only if enabled
            if (isShowLastMonthUsageEnabled()) {
                appendLine() // Add blank line before last month section
                appendLine(context.getString(R.string.last_month_usage_heading))
                appendLine("${context.getString(R.string.wifi_label)} ${usageData.wifiLastMonth} | ${context.getString(R.string.data_label)} ${usageData.mobileLastMonth}")
            }

            // Add Last Week's Usage section only if enabled
            if (isShowLastWeekUsageEnabled()) {
                appendLine() // Add blank line before last week section
                appendLine(context.getString(R.string.last_week_usage_heading))
                appendLine("${context.getString(R.string.wifi_label)} ${usageData.wifiLastWeek} | ${context.getString(R.string.data_label)} ${usageData.mobileLastWeek}")
            }

            // Add Yesterday's Usage section only if enabled
            if (isShowYesterdayUsageEnabled()) {
                appendLine() // Add blank line before yesterday section
                appendLine(context.getString(R.string.yesterday_usage_heading))
                appendLine("${context.getString(R.string.wifi_label)} ${usageData.wifiYesterday} | ${context.getString(R.string.data_label)} ${usageData.mobileYesterday}")
            }

            if (wifiParts.isEmpty() && mobileParts.isEmpty()) {
                appendLine(context.getString(R.string.no_data_periods_selected))
                appendLine(context.getString(R.string.configure_display_settings))
            }
        }

        return Pair(shortText, expandedText)
    }
}
