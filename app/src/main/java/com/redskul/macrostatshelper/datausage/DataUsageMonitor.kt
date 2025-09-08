package com.redskul.macrostatshelper.datausage

import android.annotation.SuppressLint
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

data class UsageData(
    val wifiDaily: String,
    val wifiWeekly: String,
    val wifiMonthly: String,
    val mobileDaily: String,
    val mobileWeekly: String,
    val mobileMonthly: String,
    val wifiLastMonth: String,
    val mobileLastMonth: String,
    val wifiLastWeek: String,
    val mobileLastWeek: String,
    val wifiYesterday: String,
    val mobileYesterday: String
)

class DataUsageMonitor(private val context: Context) {

    private val networkStatsManager: NetworkStatsManager by lazy {
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    }

    companion object {
        // Use the actual constant values to avoid deprecation warnings
        private const val TYPE_MOBILE = 0
        private const val TYPE_WIFI = 1
    }

    fun getUsageData(): UsageData {
        Log.d("DataUsageMonitor", "Starting data usage collection")

        // Calculate time periods with proper timezone handling
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        // Daily: Today from midnight to now
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val dailyStart = calendar.timeInMillis

        // Weekly from Monday 00:00 to now
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

        // Monthly from 1st 00:00 to now
        calendar.set(Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), 1)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val monthlyStart = calendar.timeInMillis

        // Last month calculation
        val lastMonthCalendar = Calendar.getInstance()
        lastMonthCalendar.add(Calendar.MONTH, -1) // Go to last month
        lastMonthCalendar.set(Calendar.DAY_OF_MONTH, 1) // First day of last month
        lastMonthCalendar.set(Calendar.HOUR_OF_DAY, 0)
        lastMonthCalendar.set(Calendar.MINUTE, 0)
        lastMonthCalendar.set(Calendar.SECOND, 0)
        lastMonthCalendar.set(Calendar.MILLISECOND, 0)
        val lastMonthStart = lastMonthCalendar.timeInMillis

        // Last day of last month
        lastMonthCalendar.add(Calendar.MONTH, 1) // Go to current month
        lastMonthCalendar.add(Calendar.DAY_OF_MONTH, -1) // Last day of last month
        lastMonthCalendar.set(Calendar.HOUR_OF_DAY, 23)
        lastMonthCalendar.set(Calendar.MINUTE, 59)
        lastMonthCalendar.set(Calendar.SECOND, 59)
        lastMonthCalendar.set(Calendar.MILLISECOND, 999)
        val lastMonthEnd = lastMonthCalendar.timeInMillis

        // Last week calculation (Monday to Sunday of previous week)
        val lastWeekCalendar = Calendar.getInstance()
        val currentWeekDaysSinceMonday = when (lastWeekCalendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> 6
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 0
        }
        // Go to Monday of current week, then subtract 7 days to get Monday of last week
        lastWeekCalendar.add(Calendar.DAY_OF_MONTH, -currentWeekDaysSinceMonday - 7)
        lastWeekCalendar.set(Calendar.HOUR_OF_DAY, 0)
        lastWeekCalendar.set(Calendar.MINUTE, 0)
        lastWeekCalendar.set(Calendar.SECOND, 0)
        lastWeekCalendar.set(Calendar.MILLISECOND, 0)
        val lastWeekStart = lastWeekCalendar.timeInMillis

        // Sunday of last week (end of last week)
        lastWeekCalendar.add(Calendar.DAY_OF_MONTH, 6) // Add 6 days to get Sunday
        lastWeekCalendar.set(Calendar.HOUR_OF_DAY, 23)
        lastWeekCalendar.set(Calendar.MINUTE, 59)
        lastWeekCalendar.set(Calendar.SECOND, 59)
        lastWeekCalendar.set(Calendar.MILLISECOND, 999)
        val lastWeekEnd = lastWeekCalendar.timeInMillis

        // Yesterday calculation
        val yesterdayCalendar = Calendar.getInstance()
        yesterdayCalendar.add(Calendar.DAY_OF_MONTH, -1) // Go to yesterday
        yesterdayCalendar.set(Calendar.HOUR_OF_DAY, 0)
        yesterdayCalendar.set(Calendar.MINUTE, 0)
        yesterdayCalendar.set(Calendar.SECOND, 0)
        yesterdayCalendar.set(Calendar.MILLISECOND, 0)
        val yesterdayStart = yesterdayCalendar.timeInMillis

        // End of yesterday
        yesterdayCalendar.set(Calendar.HOUR_OF_DAY, 23)
        yesterdayCalendar.set(Calendar.MINUTE, 59)
        yesterdayCalendar.set(Calendar.SECOND, 59)
        yesterdayCalendar.set(Calendar.MILLISECOND, 999)
        val yesterdayEnd = yesterdayCalendar.timeInMillis

        // Debug: Log the time periods
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        Log.d("DataUsageMonitor", "Daily period: ${dateFormat.format(dailyStart)} to ${dateFormat.format(now)}")
        Log.d("DataUsageMonitor", "Weekly period: ${dateFormat.format(weeklyStart)} to ${dateFormat.format(now)}")
        Log.d("DataUsageMonitor", "Monthly period: ${dateFormat.format(monthlyStart)} to ${dateFormat.format(now)}")
        Log.d("DataUsageMonitor", "Last month period: ${dateFormat.format(lastMonthStart)} to ${dateFormat.format(lastMonthEnd)}")
        Log.d("DataUsageMonitor", "Last week period: ${dateFormat.format(lastWeekStart)} to ${dateFormat.format(lastWeekEnd)}")
        Log.d("DataUsageMonitor", "Yesterday period: ${dateFormat.format(yesterdayStart)} to ${dateFormat.format(yesterdayEnd)}")

        val subscriberId = getSubscriberId()

        val wifiDaily = getWifiUsage(subscriberId, dailyStart, now)
        val wifiWeekly = getWifiUsage(subscriberId, weeklyStart, now)
        val wifiMonthly = getWifiUsage(subscriberId, monthlyStart, now)
        val wifiLastMonth = getWifiUsage(subscriberId, lastMonthStart, lastMonthEnd)
        val wifiLastWeek = getWifiUsage(subscriberId, lastWeekStart, lastWeekEnd)
        val wifiYesterday = getWifiUsage(subscriberId, yesterdayStart, yesterdayEnd)

        val mobileDaily = getMobileUsage(subscriberId, dailyStart, now)
        val mobileWeekly = getMobileUsage(subscriberId, weeklyStart, now)
        val mobileMonthly = getMobileUsage(subscriberId, monthlyStart, now)
        val mobileLastMonth = getMobileUsage(subscriberId, lastMonthStart, lastMonthEnd)
        val mobileLastWeek = getMobileUsage(subscriberId, lastWeekStart, lastWeekEnd)
        val mobileYesterday = getMobileUsage(subscriberId, yesterdayStart, yesterdayEnd)

        Log.d("DataUsageMonitor", "Raw bytes - WiFi Daily: $wifiDaily, Mobile Daily: $mobileDaily")
        Log.d("DataUsageMonitor", "Raw bytes - WiFi Weekly: $wifiWeekly, Mobile Weekly: $mobileWeekly")
        Log.d("DataUsageMonitor", "Raw bytes - WiFi Monthly: $wifiMonthly, Mobile Monthly: $mobileMonthly")
        Log.d("DataUsageMonitor", "Raw bytes - WiFi Last Month: $wifiLastMonth, Mobile Last Month: $mobileLastMonth")
        Log.d("DataUsageMonitor", "Raw bytes - WiFi Last Week: $wifiLastWeek, Mobile Last Week: $mobileLastWeek")
        Log.d("DataUsageMonitor", "Raw bytes - WiFi Yesterday: $wifiYesterday, Mobile Yesterday: $mobileYesterday")

        return UsageData(
            wifiDaily = formatBytes(wifiDaily),
            wifiWeekly = formatBytes(wifiWeekly),
            wifiMonthly = formatBytes(wifiMonthly),
            mobileDaily = formatBytes(mobileDaily),
            mobileWeekly = formatBytes(mobileWeekly),
            mobileMonthly = formatBytes(mobileMonthly),
            wifiLastMonth = formatBytes(wifiLastMonth),
            mobileLastMonth = formatBytes(mobileLastMonth),
            wifiLastWeek = formatBytes(wifiLastWeek),
            mobileLastWeek = formatBytes(mobileLastWeek),
            wifiYesterday = formatBytes(wifiYesterday),
            mobileYesterday = formatBytes(mobileYesterday)
        )
    }

    private fun getWifiUsage(subscriberId: String?, startTime: Long, endTime: Long): Long {
        return try {
            // Use querySummaryForDevice to get device-wide WiFi usage
            val bucket = networkStatsManager.querySummaryForDevice(
                TYPE_WIFI, // Fixed: Use local constant instead of deprecated ConnectivityManager.TYPE_WIFI
                subscriberId, // Can be null for WiFi
                startTime,
                endTime
            )

            val totalBytes = if (bucket != null) {
                bucket.rxBytes + bucket.txBytes
            } else {
                0L
            }

            Log.d("DataUsageMonitor", "WiFi device usage: rx=${bucket?.rxBytes}, tx=${bucket?.txBytes}, total=$totalBytes")
            totalBytes
        } catch (e: Exception) {
            Log.e("DataUsageMonitor", "Error getting WiFi device usage", e)
            // Fallback to original method
            getWifiUsageFallback(startTime, endTime)
        }
    }

    private fun getMobileUsage(subscriberId: String?, startTime: Long, endTime: Long): Long {
        return try {
            // Use querySummaryForDevice to get device-wide mobile usage
            val bucket = networkStatsManager.querySummaryForDevice(
                TYPE_MOBILE, // Fixed: Use local constant instead of deprecated ConnectivityManager.TYPE_MOBILE
                subscriberId,
                startTime,
                endTime
            )

            val totalBytes = if (bucket != null) {
                bucket.rxBytes + bucket.txBytes
            } else {
                0L
            }

            Log.d("DataUsageMonitor", "Mobile device usage: rx=${bucket?.rxBytes}, tx=${bucket?.txBytes}, total=$totalBytes")
            totalBytes
        } catch (e: Exception) {
            Log.e("DataUsageMonitor", "Error getting mobile device usage", e)
            // Fallback to original method
            getMobileUsageFallback(subscriberId, startTime, endTime)
        }
    }

    // Fallback methods using the original querySummary approach
    private fun getWifiUsageFallback(startTime: Long, endTime: Long): Long {
        return try {
            val networkStats = networkStatsManager.querySummary(
                TYPE_WIFI, // Fixed: Use local constant instead of deprecated ConnectivityManager.TYPE_WIFI
                null, // WiFi doesn't need subscriberId
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
            Log.d("DataUsageMonitor", "WiFi fallback total bytes: $totalBytes")
            totalBytes
        } catch (e: Exception) {
            Log.e("DataUsageMonitor", "Error in WiFi fallback", e)
            0L
        }
    }

    private fun getMobileUsageFallback(subscriberId: String?, startTime: Long, endTime: Long): Long {
        return try {
            // Try with subscriberId first
            var networkStats = networkStatsManager.querySummary(
                TYPE_MOBILE, // Fixed: Use local constant instead of deprecated ConnectivityManager.TYPE_MOBILE
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

            // If no data found and subscriberId was provided, try without subscriberId
            if (totalBytes == 0L && subscriberId != null) {
                Log.d("DataUsageMonitor", "No mobile data with subscriberId, trying without...")
                networkStats = networkStatsManager.querySummary(
                    TYPE_MOBILE, // Fixed: Use local constant instead of deprecated ConnectivityManager.TYPE_MOBILE
                    null,
                    startTime,
                    endTime
                )

                while (networkStats.hasNextBucket()) {
                    networkStats.getNextBucket(bucket)
                    totalBytes += bucket.rxBytes + bucket.txBytes
                }
                networkStats.close()
            }

            Log.d("DataUsageMonitor", "Mobile fallback total bytes: $totalBytes")
            totalBytes
        } catch (e: Exception) {
            Log.e("DataUsageMonitor", "Error in mobile fallback", e)
            0L
        }
    }

    // Fixed: Added @SuppressLint to suppress the getSubscriberId deprecation warning
    @SuppressLint("HardwareIds")
    private fun getSubscriberId(): String? {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            @SuppressLint("MissingPermission")
            val subscriberId = try {
                // Note: getSubscriberId is deprecated but still functional for network stats
                // Alternative approaches would require more complex permission handling
                telephonyManager.subscriberId
            } catch (e: SecurityException) {
                Log.i("DataUsageMonitor", "Cannot access subscriber ID due to permission restriction, using null")
                null
            } catch (e: Exception) {
                Log.w("DataUsageMonitor", "Cannot access subscriber ID: ${e.message}")
                null
            }

            Log.d("DataUsageMonitor", "Retrieved subscriber ID: $subscriberId")
            subscriberId
        } catch (e: Exception) {
            Log.e("DataUsageMonitor", "Error getting subscriber ID", e)
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
