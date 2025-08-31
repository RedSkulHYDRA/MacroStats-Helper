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
            icon = Icon.createWithResource(context, R.drawable.ic_battery),
            labelPrefix = context.getString(R.string.charge_tile_prefix),
            defaultState = Tile.STATE_ACTIVE
        )
    }

    fun applyConfigToTile(
        tile: Tile,
        config: TileConfiguration,
        value: String,
        showPeriodInTitle: Boolean = false,
        period: TimePeriod? = null,
        isWifi: Boolean = true
    ) {
        tile.state = config.defaultState
        tile.icon = config.icon

        if (showPeriodInTitle && period != null) {
            // Show period in title with divider, value as subtitle
            val periodText = period.name.lowercase().replaceFirstChar { it.uppercase() }
            val prefix = config.labelPrefix.trimEnd()
            val titleText = if (isWifi) {
                "$prefix WiFi Usage ($periodText)"
            } else {
                "$prefix Mobile Data Usage ($periodText)"
            }
            tile.label = titleText
            tile.subtitle = value
        } else {
            // Show value as title with divider (original behavior)
            val prefix = config.labelPrefix.trimEnd()
            tile.label = "$prefix $value"
            tile.subtitle = null
        }
    }

    fun applyChargeConfigToTile(
        tile: Tile,
        config: TileConfiguration,
        value: String,
        showChargeInTitle: Boolean = false
    ) {
        tile.state = config.defaultState
        tile.icon = config.icon

        if (showChargeInTitle) {
            // Show "Charge Cycles" in title with divider, value as subtitle
            val prefix = config.labelPrefix.trimEnd()
            tile.label = "$prefix Charge Cycles"
            tile.subtitle = value
        } else {
            // Show value as title with divider (original behavior)
            val prefix = config.labelPrefix.trimEnd()
            tile.label = "$prefix $value"
            tile.subtitle = null
        }
    }
}
