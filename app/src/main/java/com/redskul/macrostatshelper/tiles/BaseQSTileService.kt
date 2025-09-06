package com.redskul.macrostatshelper.tiles

import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.redskul.macrostatshelper.utils.VibrationManager

/**
 * Base class for all QS Tile Services that provides centralized vibration handling
 * All tile services should extend this class instead of TileService directly
 */
abstract class BaseQSTileService : TileService() {

    private lateinit var vibrationManager: VibrationManager

    override fun onCreate() {
        super.onCreate()
        vibrationManager = VibrationManager(this)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    final override fun onClick() {
        super.onClick()

        // Centralized vibration handling
        vibrationManager.vibrateForTileClick()

        // Call the tile-specific click logic
        onTileClick()
    }

    /**
     * Override this method in each tile service for tile-specific click logic
     * This replaces the standard onClick() method
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    protected abstract fun onTileClick()
}
