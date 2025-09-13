package com.redskul.macrostatshelper.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.redskul.macrostatshelper.datausage.UsageData

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
        private const val KEY_SHOW_TORCH_GLYPH_IN_TITLE = "show_torch_glyph_in_title"
        private const val KEY_SHOW_REFRESH_RATE_IN_TITLE = "show_refresh_rate_in_title"
        private const val KEY_SHOW_AOD_IN_TITLE = "show_aod_in_title"
        private const val KEY_SHOW_DNS_IN_TITLE = "show_dns_in_title"
        private const val KEY_BATTERY_DESIGN_CAPACITY = "battery_design_capacity"
    }

    fun saveWiFiTilePeriod(timePeriod: TimePeriod) {
        sharedPreferences.edit {
            putString(KEY_WIFI_TILE_PERIOD, timePeriod.name)
        }
    }

    fun saveMobileTilePeriod(timePeriod: TimePeriod) {
        sharedPreferences.edit {
            putString(KEY_MOBILE_TILE_PERIOD, timePeriod.name)
        }
    }

    fun saveShowPeriodInTitle(showPeriod: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_SHOW_PERIOD_IN_TITLE, showPeriod)
        }
    }

    fun saveShowChargeInTitle(showCharge: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_SHOW_CHARGE_IN_TITLE, showCharge)
        }
    }

    fun saveShowBatteryHealthInTitle(showHealth: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_SHOW_BATTERY_HEALTH_IN_TITLE, showHealth)
        }
    }

    fun saveShowScreenTimeoutInTitle(showTimeout: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_SHOW_SCREEN_TIMEOUT_IN_TITLE, showTimeout)
        }
    }

    fun saveShowTorchGlyphInTitle(showTorchGlyph: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_SHOW_TORCH_GLYPH_IN_TITLE, showTorchGlyph)
        }
    }

    fun saveShowRefreshRateInTitle(showRefreshRate: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_SHOW_REFRESH_RATE_IN_TITLE, showRefreshRate)
        }
    }

    fun saveShowAODInTitle(showAOD: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_SHOW_AOD_IN_TITLE, showAOD)
        }
    }

    fun saveShowDNSInTitle(showDNS: Boolean) {
        sharedPreferences.edit {
            putBoolean(KEY_SHOW_DNS_IN_TITLE, showDNS)
        }
    }

    fun saveBatteryDesignCapacity(capacity: Int) {
        sharedPreferences.edit {
            putInt(KEY_BATTERY_DESIGN_CAPACITY, capacity)
        }
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

    fun getShowTorchGlyphInTitle(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_TORCH_GLYPH_IN_TITLE, false)
    }

    fun getShowRefreshRateInTitle(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_REFRESH_RATE_IN_TITLE, false)
    }

    fun getShowAODInTitle(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_AOD_IN_TITLE, false)
    }

    fun getShowDNSInTitle(): Boolean {
        return sharedPreferences.getBoolean(KEY_SHOW_DNS_IN_TITLE, false)
    }

    fun getBatteryDesignCapacity(): Int {
        return sharedPreferences.getInt(KEY_BATTERY_DESIGN_CAPACITY, 0)
    }

    fun getWiFiTileText(usageData: UsageData): String {
        val period = getWiFiTilePeriod()
        return when (period) {
            TimePeriod.DAILY -> usageData.wifiDaily
            TimePeriod.WEEKLY -> usageData.wifiWeekly
            TimePeriod.MONTHLY -> usageData.wifiMonthly
        }
    }

    fun getMobileTileText(usageData: UsageData): String {
        val period = getMobileTilePeriod()
        return when (period) {
            TimePeriod.DAILY -> usageData.mobileDaily
            TimePeriod.WEEKLY -> usageData.mobileWeekly
            TimePeriod.MONTHLY -> usageData.mobileMonthly
        }
    }
}
