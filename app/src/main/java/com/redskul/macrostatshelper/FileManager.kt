package com.redskul.macrostatshelper

import android.content.Context
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class FileManager(private val context: Context) {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val settingsManager = SettingsManager(context)

    fun writeUsageToFile(usageData: UsageData) {
        val timestamp = dateFormat.format(Date())
        val settings = settingsManager.getDisplaySettings()
        val (shortText, expandedText) = settingsManager.getFormattedUsageText(usageData)

        // Create the main content for the file
        val content = buildString {
            appendLine("=== Data Usage Report ===")
            appendLine("Last Updated: $timestamp")
            appendLine("")
            appendLine("Current Display Settings:")
            appendLine("WiFi Periods: ${settings.wifiTimePeriods.joinToString(", ") { it.name.lowercase() }}")
            appendLine("Mobile Periods: ${settings.mobileTimePeriods.joinToString(", ") { it.name.lowercase() }}")
            appendLine("Current Display: $shortText")
            appendLine("")
            appendLine("=== Full Data ===")
            appendLine("WiFi Usage:")
            appendLine("  Daily: ${usageData.wifiDaily}")
            appendLine("  Weekly: ${usageData.wifiWeekly}")
            appendLine("  Monthly: ${usageData.wifiMonthly}")
            appendLine("")
            appendLine("Mobile Data Usage:")
            appendLine("  Daily: ${usageData.mobileDaily}")
            appendLine("  Weekly: ${usageData.mobileWeekly}")
            appendLine("  Monthly: ${usageData.mobileMonthly}")
            appendLine("")
            appendLine("=== Raw Data (for MacroDroid) ===")
            appendLine("wifi_daily=${usageData.wifiDaily}")
            appendLine("wifi_weekly=${usageData.wifiWeekly}")
            appendLine("wifi_monthly=${usageData.wifiMonthly}")
            appendLine("mobile_daily=${usageData.mobileDaily}")
            appendLine("mobile_weekly=${usageData.mobileWeekly}")
            appendLine("mobile_monthly=${usageData.mobileMonthly}")
            appendLine("")
            appendLine("=== Current Display Setting ===")
            appendLine("wifi_periods=${settings.wifiTimePeriods.joinToString(",") { it.name }}")
            appendLine("mobile_periods=${settings.mobileTimePeriods.joinToString(",") { it.name }}")
            appendLine("current_display_text=$shortText")
        }

        writeToInternalStorage("data_usage_report.txt", content)

        // Create separate files for easier MacroDroid access
        writeToInternalStorage("wifi_usage.txt",
            "Daily: ${usageData.wifiDaily}\nWeekly: ${usageData.wifiWeekly}\nMonthly: ${usageData.wifiMonthly}")

        writeToInternalStorage("mobile_usage.txt",
            "Daily: ${usageData.mobileDaily}\nWeekly: ${usageData.mobileWeekly}\nMonthly: ${usageData.mobileMonthly}")

        // Create a file with current display setting
        writeToInternalStorage("current_display.txt", shortText)

        // Create files for selected WiFi periods
        if (settings.wifiTimePeriods.isNotEmpty()) {
            val wifiValues = settings.wifiTimePeriods.map { period ->
                when (period) {
                    TimePeriod.DAILY -> usageData.wifiDaily
                    TimePeriod.WEEKLY -> usageData.wifiWeekly
                    TimePeriod.MONTHLY -> usageData.wifiMonthly
                }
            }
            writeToInternalStorage("wifi_selected.txt", wifiValues.joinToString(" | "))
        }

        // Create files for selected Mobile periods
        if (settings.mobileTimePeriods.isNotEmpty()) {
            val mobileValues = settings.mobileTimePeriods.map { period ->
                when (period) {
                    TimePeriod.DAILY -> usageData.mobileDaily
                    TimePeriod.WEEKLY -> usageData.mobileWeekly
                    TimePeriod.MONTHLY -> usageData.mobileMonthly
                }
            }
            writeToInternalStorage("mobile_selected.txt", mobileValues.joinToString(" | "))
        }
    }

    private fun writeToInternalStorage(filename: String, content: String) {
        try {
            val file = File(context.filesDir, filename)
            FileWriter(file).use { writer ->
                writer.write(content)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun getFilePaths(): Map<String, String> {
        return mapOf(
            "main_report" to File(context.filesDir, "data_usage_report.txt").absolutePath,
            "wifi_usage" to File(context.filesDir, "wifi_usage.txt").absolutePath,
            "mobile_usage" to File(context.filesDir, "mobile_usage.txt").absolutePath,
            "current_display" to File(context.filesDir, "current_display.txt").absolutePath,
            "wifi_selected" to File(context.filesDir, "wifi_selected.txt").absolutePath,
            "mobile_selected" to File(context.filesDir, "mobile_selected.txt").absolutePath
        )
    }
}
