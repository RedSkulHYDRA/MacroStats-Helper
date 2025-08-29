package com.redskul.macrostatshelper

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WIFI_PERIODS = "wifi_periods"
        private const val KEY_MOBILE_PERIODS = "mobile_periods"
        private const val KEY_FIRST_LAUNCH = "first_launch"
        private const val KEY_SHOW_NOTIFICATION = "show_notification"
    }

    // Extension function to get display string from TimePeriod
    private fun TimePeriod.toDisplayString(): String = when (this) {
        TimePeriod.DAILY -> context.getString(R.string.daily)
        TimePeriod.WEEKLY -> context.getString(R.string.weekly)
        TimePeriod.MONTHLY -> context.getString(R.string.monthly)
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
                } catch (e: Exception) {
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
                } catch (e: Exception) {
                    null
                }
            }
        }

        return DisplaySettings(wifiPeriods, mobilePeriods)
    }

    fun saveNotificationEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_NOTIFICATION, enabled).apply()
    }

    fun isNotificationEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_NOTIFICATION, true) // Default to true
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
            val periodName = period.toDisplayString()
            wifiParts.add("$value ($periodName)")
        }

        // Build Mobile parts
        settings.mobileTimePeriods.forEach { period ->
            val value = when (period) {
                TimePeriod.DAILY -> usageData.mobileDaily
                TimePeriod.WEEKLY -> usageData.mobileWeekly
                TimePeriod.MONTHLY -> usageData.mobileMonthly
            }
            val periodName = period.toDisplayString()
            mobileParts.add("$value ($periodName)")
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
            shortTextParts.joinToString(" | ")
        } else {
            context.getString(R.string.no_data_selected)
        }

        // Create expanded text (same as short text for now, since user only wants selected data)
        val expandedText = buildString {
            if (wifiParts.isNotEmpty()) {
                appendLine(context.getString(R.string.wifi_usage_label))
                wifiParts.forEach { part ->
                    appendLine("  $part")
                }
                if (mobileParts.isNotEmpty()) appendLine()
            }
            if (mobileParts.isNotEmpty()) {
                appendLine(context.getString(R.string.mobile_data_usage_label))
                mobileParts.forEach { part ->
                    appendLine("  $part")
                }
            }
            if (wifiParts.isEmpty() && mobileParts.isEmpty()) {
                appendLine(context.getString(R.string.no_data_periods_selected))
                appendLine(context.getString(R.string.configure_display_settings))
            }
        }

        return Pair(shortText, expandedText)
    }
}
