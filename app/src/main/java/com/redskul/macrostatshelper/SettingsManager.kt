// Add this to a new file: SettingsManager.kt
package com.redskul.macrostatshelper

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_DATA_TYPE = "data_type"
        private const val KEY_TIME_PERIOD = "time_period"
        private const val KEY_WIFI_TIME_PERIOD = "wifi_time_period"
        private const val KEY_MOBILE_TIME_PERIOD = "mobile_time_period"
    }

    fun saveDisplaySettings(settings: DisplaySettings) {
        sharedPreferences.edit().apply {
            putString(KEY_DATA_TYPE, settings.dataType.name)
            putString(KEY_TIME_PERIOD, settings.timePeriod.name)

            // Save custom settings if available
            settings.customSettings?.let { custom ->
                putString(KEY_WIFI_TIME_PERIOD, custom.wifiTimePeriod.name)
                putString(KEY_MOBILE_TIME_PERIOD, custom.mobileTimePeriod.name)
            }

            apply()
        }
    }

    fun getDisplaySettings(): DisplaySettings {
        val dataTypeString = sharedPreferences.getString(KEY_DATA_TYPE, DataType.BOTH.name)
        val timePeriodString = sharedPreferences.getString(KEY_TIME_PERIOD, TimePeriod.DAILY.name)

        val dataType = try {
            DataType.valueOf(dataTypeString ?: DataType.BOTH.name)
        } catch (e: Exception) {
            DataType.BOTH
        }

        val timePeriod = try {
            TimePeriod.valueOf(timePeriodString ?: TimePeriod.DAILY.name)
        } catch (e: Exception) {
            TimePeriod.DAILY
        }

        val customSettings = if (dataType == DataType.CUSTOM) {
            val wifiTimePeriodString = sharedPreferences.getString(KEY_WIFI_TIME_PERIOD, TimePeriod.DAILY.name)
            val mobileTimePeriodString = sharedPreferences.getString(KEY_MOBILE_TIME_PERIOD, TimePeriod.DAILY.name)

            val wifiTimePeriod = try {
                TimePeriod.valueOf(wifiTimePeriodString ?: TimePeriod.DAILY.name)
            } catch (e: Exception) {
                TimePeriod.DAILY
            }

            val mobileTimePeriod = try {
                TimePeriod.valueOf(mobileTimePeriodString ?: TimePeriod.DAILY.name)
            } catch (e: Exception) {
                TimePeriod.DAILY
            }

            CustomDisplaySettings(wifiTimePeriod, mobileTimePeriod)
        } else {
            null
        }

        return DisplaySettings(dataType, timePeriod, customSettings)
    }

    fun getFormattedUsageText(usageData: UsageData): Pair<String, String> {
        val settings = getDisplaySettings()

        return when (settings.dataType) {
            DataType.WIFI_ONLY -> {
                val wifiValue = when (settings.timePeriod) {
                    TimePeriod.DAILY -> usageData.wifiDaily
                    TimePeriod.WEEKLY -> usageData.wifiWeekly
                    TimePeriod.MONTHLY -> usageData.wifiMonthly
                }
                val shortText = "WiFi: $wifiValue (${settings.timePeriod.name.lowercase()})"
                val expandedText = buildString {
                    appendLine("WiFi Usage (${settings.timePeriod.name.lowercase()}):")
                    appendLine("Current: $wifiValue")
                    appendLine("")
                    appendLine("All periods:")
                    appendLine("Daily: ${usageData.wifiDaily}")
                    appendLine("Weekly: ${usageData.wifiWeekly}")
                    appendLine("Monthly: ${usageData.wifiMonthly}")
                }
                Pair(shortText, expandedText)
            }

            DataType.MOBILE_ONLY -> {
                val mobileValue = when (settings.timePeriod) {
                    TimePeriod.DAILY -> usageData.mobileDaily
                    TimePeriod.WEEKLY -> usageData.mobileWeekly
                    TimePeriod.MONTHLY -> usageData.mobileMonthly
                }
                val shortText = "Mobile: $mobileValue (${settings.timePeriod.name.lowercase()})"
                val expandedText = buildString {
                    appendLine("Mobile Data Usage (${settings.timePeriod.name.lowercase()}):")
                    appendLine("Current: $mobileValue")
                    appendLine("")
                    appendLine("All periods:")
                    appendLine("Daily: ${usageData.mobileDaily}")
                    appendLine("Weekly: ${usageData.mobileWeekly}")
                    appendLine("Monthly: ${usageData.mobileMonthly}")
                }
                Pair(shortText, expandedText)
            }

            DataType.BOTH -> {
                val wifiValue = when (settings.timePeriod) {
                    TimePeriod.DAILY -> usageData.wifiDaily
                    TimePeriod.WEEKLY -> usageData.wifiWeekly
                    TimePeriod.MONTHLY -> usageData.wifiMonthly
                }
                val mobileValue = when (settings.timePeriod) {
                    TimePeriod.DAILY -> usageData.mobileDaily
                    TimePeriod.WEEKLY -> usageData.mobileWeekly
                    TimePeriod.MONTHLY -> usageData.mobileMonthly
                }
                val shortText = "WiFi: $wifiValue | Mobile: $mobileValue (${settings.timePeriod.name.lowercase()})"
                val expandedText = buildString {
                    appendLine("Data Usage (${settings.timePeriod.name.lowercase()}):")
                    appendLine("WiFi: $wifiValue")
                    appendLine("Mobile: $mobileValue")
                    appendLine("")
                    appendLine("All periods:")
                    appendLine("WiFi - Daily: ${usageData.wifiDaily}, Weekly: ${usageData.wifiWeekly}, Monthly: ${usageData.wifiMonthly}")
                    appendLine("Mobile - Daily: ${usageData.mobileDaily}, Weekly: ${usageData.mobileWeekly}, Monthly: ${usageData.mobileMonthly}")
                }
                Pair(shortText, expandedText)
            }

            DataType.CUSTOM -> {
                val customSettings = settings.customSettings ?: CustomDisplaySettings(TimePeriod.DAILY, TimePeriod.DAILY)

                val wifiValue = when (customSettings.wifiTimePeriod) {
                    TimePeriod.DAILY -> usageData.wifiDaily
                    TimePeriod.WEEKLY -> usageData.wifiWeekly
                    TimePeriod.MONTHLY -> usageData.wifiMonthly
                }

                val mobileValue = when (customSettings.mobileTimePeriod) {
                    TimePeriod.DAILY -> usageData.mobileDaily
                    TimePeriod.WEEKLY -> usageData.mobileWeekly
                    TimePeriod.MONTHLY -> usageData.mobileMonthly
                }

                val shortText = "WiFi: $wifiValue (${customSettings.wifiTimePeriod.name.lowercase()}) | Mobile: $mobileValue (${customSettings.mobileTimePeriod.name.lowercase()})"
                val expandedText = buildString {
                    appendLine("Custom Data Usage:")
                    appendLine("WiFi (${customSettings.wifiTimePeriod.name.lowercase()}): $wifiValue")
                    appendLine("Mobile (${customSettings.mobileTimePeriod.name.lowercase()}): $mobileValue")
                    appendLine("")
                    appendLine("All periods:")
                    appendLine("WiFi - Daily: ${usageData.wifiDaily}, Weekly: ${usageData.wifiWeekly}, Monthly: ${usageData.wifiMonthly}")
                    appendLine("Mobile - Daily: ${usageData.mobileDaily}, Weekly: ${usageData.mobileWeekly}, Monthly: ${usageData.mobileMonthly}")
                }
                Pair(shortText, expandedText)
            }
        }
    }
}