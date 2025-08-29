package com.redskul.macrostatshelper.tiles

import android.content.Context
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import com.redskul.macrostatshelper.R

data class TileConfiguration(
    val icon: Icon,
    val labelPrefix: String,
    val defaultState: Int = Tile.STATE_INACTIVE
)

object TileConfigHelper {

    fun getWiFiTileConfig(context: Context): TileConfiguration {
        return TileConfiguration(
            icon = Icon.createWithResource(context, R.drawable.ic_wifi),
            labelPrefix = context.getString(R.string.wifi_tile_prefix),
            defaultState = Tile.STATE_INACTIVE
        )
    }

    fun getMobileTileConfig(context: Context): TileConfiguration {
        return TileConfiguration(
            icon = Icon.createWithResource(context, R.drawable.ic_mobile),
            labelPrefix = context.getString(R.string.mobile_tile_prefix),
            defaultState = Tile.STATE_INACTIVE
        )
    }

    fun applyConfigToTile(tile: Tile, config: TileConfiguration, value: String) {
        tile.state = config.defaultState
        // Ensure proper spacing by explicitly adding space if prefix doesn't end with one
        val prefix = config.labelPrefix.trimEnd()
        tile.label = "$prefix $value"  // ← Fixed: Explicit space between prefix and value
        tile.subtitle = null
        tile.icon = config.icon
    }
}
