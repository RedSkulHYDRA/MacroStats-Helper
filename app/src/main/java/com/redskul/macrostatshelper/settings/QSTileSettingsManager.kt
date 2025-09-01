package com.redskul.macrostatshelper.settings

import android.content.Context
import android.content.SharedPreferences
import com.redskul.macrostatshelper.data.UsageData

class QSTileSettingsManager(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("qs_tile_settings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_WIFI_TILE_PERIOD = "wifi_tile_period"
        private const val KEY_MOBILE_TILE_PERIOD = "mobile_tile_period"
        private const val KEY_SHOW_PERIOD_IN_TITLE = "show_period_in_title"
        private const val KEY_SHOW_CHARGE_IN_TITLE = "show_charge_in_title"
        private const val KEY_SHOW_BATTERY_HEALTH_IN_TITLE = "show_battery_health_in_title"
        private const val KEY_SHOW_SCREEN_TIMEOUT_IN_TITLE = "show_screen_timeout_in_title"
        private const val KEY_BATTERY_DESIGN_CAPACITY = "battery_design_capacity"
    }

    fun saveWiFiTilePeriod(timePeriod: TimePeriod) {
        sharedPreferences.edit().putString(KEY_WIFI_TILE_PERIOD, timePeriod.name).apply()
    }

    fun saveMobileTilePeriod(timePeriod: TimePeriod) {
        sharedPreferences.edit().putString(KEY_MOBILE_TILE_PERIOD, timePeriod.name).apply()
    }

    fun saveShowPeriodInTitle(showPeriod: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_PERIOD_IN_TITLE, showPeriod).apply()
    }

    fun saveShowChargeInTitle(showCharge: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_CHARGE_IN_TITLE, showCharge).apply()
    }

    fun saveShowBatteryHealthInTitle(showHealth: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_BATTERY_HEALTH_IN_TITLE, showHealth).apply()
    }

    fun saveShowScreenTimeoutInTitle(showTimeout: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_SHOW_SCREEN_TIMEOUT_IN_TITLE, showTimeout).apply()
    }

    fun saveBatteryDesignCapacity(capacity: Int) {
        sharedPreferences.edit().putInt(KEY_BATTERY_DESIGN_CAPACITY, capacity).apply()
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

    fun getShowPeriodInTitle(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_PERIOD_IN_TITLE, false)
    }

    fun getShowChargeInTitle(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_CHARGE_IN_TITLE, false)
    }

    fun getShowBatteryHealthInTitle(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_BATTERY_HEALTH_IN_TITLE, false)
    }

    fun getShowScreenTimeoutInTitle(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_SCREEN_TIMEOUT_IN_TITLE, false)
    }

    fun getBatteryDesignCapacity(): Int {
        return sharedPreferences.getInt(KEY_BATTERY_DESIGN_CAPACITY, 0)
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
