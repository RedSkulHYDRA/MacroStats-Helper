package com.redskul.macrostatshelper

import android.content.Context
import android.content.SharedPreferences

class QSTileSettingsManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("qs_tile_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WIFI_TILE_PERIOD = "wifi_tile_period"
        private const val KEY_MOBILE_TILE_PERIOD = "mobile_tile_period"
    }

    fun saveWiFiTilePeriod(timePeriod: TimePeriod) {
        sharedPreferences.edit().putString(KEY_WIFI_TILE_PERIOD, timePeriod.name).apply()
    }

    fun saveMobileTilePeriod(timePeriod: TimePeriod) {
        sharedPreferences.edit().putString(KEY_MOBILE_TILE_PERIOD, timePeriod.name).apply()
    }

    fun getWiFiTilePeriod(): TimePeriod {
        val periodString = sharedPreferences.getString(KEY_WIFI_TILE_PERIOD, "DAILY")
        return try {
            TimePeriod.valueOf(periodString ?: "DAILY")
        } catch (e: Exception) {
            TimePeriod.DAILY
        }
    }

    fun getMobileTilePeriod(): TimePeriod {
        val periodString = sharedPreferences.getString(KEY_MOBILE_TILE_PERIOD, "DAILY")
        return try {
            TimePeriod.valueOf(periodString ?: "DAILY")
        } catch (e: Exception) {
            TimePeriod.DAILY
        }
    }

    fun getWiFiTileText(usageData: UsageData): String {
        val period = getWiFiTilePeriod()
        val value = when (period) {
            TimePeriod.DAILY -> usageData.wifiDaily
            TimePeriod.WEEKLY -> usageData.wifiWeekly
            TimePeriod.MONTHLY -> usageData.wifiMonthly
        }
        return value
    }

    fun getMobileTileText(usageData: UsageData): String {
        val period = getMobileTilePeriod()
        val value = when (period) {
            TimePeriod.DAILY -> usageData.mobileDaily
            TimePeriod.WEEKLY -> usageData.mobileWeekly
            TimePeriod.MONTHLY -> usageData.mobileMonthly
        }
        return value
    }
}
