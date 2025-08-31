package com.redskul.macrostatshelper.tiles

import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.settings.TimePeriod

data class TileConfiguration(
    val icon: Icon,
    val labelPrefix: String,
    val defaultState: Int = Tile.STATE_ACTIVE
)

object TileConfigHelper {

    // Length threshold above which divider is hidden to prevent animation issues
    private const val HIDE_DIVIDER_LENGTH_THRESHOLD = 12

    fun getWiFiTileConfig(context: Context): TileConfiguration {
        return TileConfiguration(
            icon = Icon.createWithResource(context, R.drawable.ic_wifi),
            labelPrefix = context.getString(R.string.wifi_tile_prefix),
            defaultState = Tile.STATE_ACTIVE
        )
    }

    fun getMobileTileConfig(context: Context): TileConfiguration {
        return TileConfiguration(
            icon = Icon.createWithResource(context, R.drawable.ic_mobile),
            labelPrefix = context.getString(R.string.mobile_tile_prefix),
            defaultState = Tile.STATE_ACTIVE
        )
    }

    fun getChargeTileConfig(context: Context): TileConfiguration {
        return TileConfiguration(
            icon = Icon.createWithResource(context, R.drawable.ic_battery_cc),
            labelPrefix = context.getString(R.string.charge_tile_prefix),
            defaultState = Tile.STATE_ACTIVE
        )
    }

    fun getBatteryHealthTileConfig(context: Context): TileConfiguration {
        return TileConfiguration(
            icon = Icon.createWithResource(context, R.drawable.ic__battery_health),
            labelPrefix = context.getString(R.string.battery_health_tile_prefix),
            defaultState = Tile.STATE_ACTIVE
        )
    }

    fun applyConfigToTile(
        tile: Tile,
        config: TileConfiguration,
        value: String,
        showPeriodInTitle: Boolean = false,
        period: TimePeriod? = null,
        isWifi: Boolean = true,
        context: Context? = null
    ) {
        tile.state = config.defaultState
        tile.icon = config.icon

        if (showPeriodInTitle && period != null && context != null) {
            // Show period in title WITHOUT separator, value as subtitle
            val titleText = when (period) {
                TimePeriod.DAILY -> if (isWifi) context.getString(R.string.wifi_usage_daily) else context.getString(R.string.mobile_data_usage_daily)
                TimePeriod.WEEKLY -> if (isWifi) context.getString(R.string.wifi_usage_weekly) else context.getString(R.string.mobile_data_usage_weekly)
                TimePeriod.MONTHLY -> if (isWifi) context.getString(R.string.wifi_usage_monthly) else context.getString(R.string.mobile_data_usage_monthly)
            }
            tile.label = titleText
            tile.subtitle = value
        } else {
            // Show divider only if text is short enough to not animate
            val showDivider = value.length < HIDE_DIVIDER_LENGTH_THRESHOLD
            val prefix = if (showDivider) config.labelPrefix.trimEnd() else ""

            tile.label = if (prefix.isNotEmpty()) "$prefix $value" else value
            tile.subtitle = null
        }
    }

    fun applyChargeConfigToTile(
        tile: Tile,
        config: TileConfiguration,
        value: String,
        showChargeInTitle: Boolean = false,
        context: Context? = null
    ) {
        tile.state = config.defaultState
        tile.icon = config.icon

        if (showChargeInTitle && context != null) {
            // Show "Charge Cycles" in title WITHOUT separator, value as subtitle
            tile.label = context.getString(R.string.charge_cycles)
            tile.subtitle = value
        } else {
            // For charge cycles, include "Charging Cycles" text
            val fullText = if (context != null) {
                "$value ${context.getString(R.string.charging_cycles)}"
            } else {
                "$value Charging Cycles"
            }
            val showDivider = fullText.length < HIDE_DIVIDER_LENGTH_THRESHOLD
            val prefix = if (showDivider) config.labelPrefix.trimEnd() else ""

            tile.label = if (prefix.isNotEmpty()) "$prefix $fullText" else fullText
            tile.subtitle = null
        }
    }

    fun applyBatteryHealthConfigToTile(
        tile: Tile,
        config: TileConfiguration,
        healthPercentage: String,
        currentFcc: String,
        designCapacity: String,
        showHealthInTitle: Boolean = false,
        context: Context? = null
    ) {
        tile.state = config.defaultState
        tile.icon = config.icon

        // Format: "86% (3480mAh / 4000mAh)"
        val valueText = "$healthPercentage ($currentFcc / $designCapacity)"

        if (showHealthInTitle && context != null) {
            // Show "Battery Health" in title WITHOUT separator, detailed stats as subtitle
            tile.label = context.getString(R.string.battery_health)
            tile.subtitle = valueText
        } else {
            // Show detailed stats directly in title (no separator for long text)
            val showDivider = valueText.length < HIDE_DIVIDER_LENGTH_THRESHOLD
            val prefix = if (showDivider) config.labelPrefix.trimEnd() else ""

            tile.label = if (prefix.isNotEmpty()) "$prefix $valueText" else valueText
            tile.subtitle = null
        }
    }
}
