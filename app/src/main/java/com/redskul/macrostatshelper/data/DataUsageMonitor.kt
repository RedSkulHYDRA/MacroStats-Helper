package com.redskul.macrostatshelper.data

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.telephony.TelephonyManager
import java.util.*

data class UsageData(
    val wifiDaily: String,
    val wifiWeekly: String,
    val wifiMonthly: String,
    val mobileDaily: String,
    val mobileWeekly: String,
    val mobileMonthly: String
)

class DataUsageMonitor(private val context: Context) {

    private val networkStatsManager: NetworkStatsManager by lazy {
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    }

    fun getUsageData(): UsageData {
        android.util.Log.d("DataUsageMonitor", "Starting data usage collection")

        val subscriberId = getSubscriberId()
        android.util.Log.d("DataUsageMonitor", "Subscriber ID: $subscriberId")

        // Calculate time periods
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        // Daily: Today from midnight to now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val dailyStart = calendar.timeInMillis

        // FIXED: Weekly from Monday 00:00 to now
        val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
        val daysSinceMonday = when (currentDayOfWeek) {
            Calendar.SUNDAY -> 6
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 0
        }
        calendar.add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val weeklyStart = calendar.timeInMillis

        // FIXED: Monthly from 1st 00:00 to now (was using last day before)
        calendar.set(Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val monthlyStart = calendar.timeInMillis

        // Debug: Log the time periods
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        android.util.Log.d("DataUsageMonitor", "Daily period: ${dateFormat.format(dailyStart)} to ${dateFormat.format(now)}")
        android.util.Log.d("DataUsageMonitor", "Weekly period: ${dateFormat.format(weeklyStart)} to ${dateFormat.format(now)}")
        android.util.Log.d("DataUsageMonitor", "Monthly period: ${dateFormat.format(monthlyStart)} to ${dateFormat.format(now)}")

        val wifiDaily = getWifiUsage(dailyStart, now)
        val wifiWeekly = getWifiUsage(weeklyStart, now)
        val wifiMonthly = getWifiUsage(monthlyStart, now)
        val mobileDaily = getMobileUsage(subscriberId, dailyStart, now)
        val mobileWeekly = getMobileUsage(subscriberId, weeklyStart, now)
        val mobileMonthly = getMobileUsage(subscriberId, monthlyStart, now)

        android.util.Log.d("DataUsageMonitor", "Raw bytes - WiFi Daily: $wifiDaily, Mobile Daily: $mobileDaily")
        android.util.Log.d("DataUsageMonitor", "Raw bytes - WiFi Weekly: $wifiWeekly, Mobile Weekly: $mobileWeekly")
        android.util.Log.d("DataUsageMonitor", "Raw bytes - WiFi Monthly: $wifiMonthly, Mobile Monthly: $mobileMonthly")

        return UsageData(
            wifiDaily = formatBytes(wifiDaily),
            wifiWeekly = formatBytes(wifiWeekly),
            wifiMonthly = formatBytes(wifiMonthly),
            mobileDaily = formatBytes(mobileDaily),
            mobileWeekly = formatBytes(mobileWeekly),
            mobileMonthly = formatBytes(mobileMonthly)
        )
    }

    private fun getWifiUsage(startTime: Long, endTime: Long): Long {
        return try {
            val networkStats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_WIFI,
                null,
                startTime,
                endTime
            )

            var totalBytes = 0L
            val bucket = NetworkStats.Bucket()
            while (networkStats.hasNextBucket()) {
                networkStats.getNextBucket(bucket)
                totalBytes += bucket.rxBytes + bucket.txBytes
            }
            networkStats.close()
            totalBytes
        } catch (e: Exception) {
            0L
        }
    }

    private fun getMobileUsage(subscriberId: String?, startTime: Long, endTime: Long): Long {
        return try {
            val networkStats = networkStatsManager.querySummary(
                ConnectivityManager.TYPE_MOBILE,
                subscriberId,
                startTime,
                endTime
            )

            var totalBytes = 0L
            val bucket = NetworkStats.Bucket()
            while (networkStats.hasNextBucket()) {
                networkStats.getNextBucket(bucket)
                totalBytes += bucket.rxBytes + bucket.txBytes
            }
            networkStats.close()
            totalBytes
        } catch (e: Exception) {
            0L
        }
    }

    @Suppress("MissingPermission")
    private fun getSubscriberId(): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            telephonyManager.subscriberId
        } catch (e: SecurityException) {
            android.util.Log.d("DataUsageMonitor", "Cannot access subscriber ID (expected for non-system apps)")
            null
        } catch (e: Exception) {
            android.util.Log.e("DataUsageMonitor", "Error getting subscriber ID", e)
            null
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "0 B"

        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        return if (size >= 100 || unitIndex == 0) {
            "${size.toInt()} ${units[unitIndex]}"
        } else if (size >= 10) {
            "%.1f ${units[unitIndex]}".format(size)
        } else {
            "%.2f ${units[unitIndex]}".format(size)
        }
    }
}
