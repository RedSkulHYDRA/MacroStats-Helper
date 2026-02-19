package com.redskul.macrostatshelper.tiles

import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.redskul.macrostatshelper.notification.NotificationHelper
import com.redskul.macrostatshelper.utils.VibrationManager

/**
 * Base class for all QS Tile Services that provides centralized vibration handling
 * and a persistent status notification. All tile services should extend this class
 * instead of TileService directly.
 */
abstract class BaseQSTileService : TileService() {

    private lateinit var vibrationManager: VibrationManager
    private lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        vibrationManager = VibrationManager(this)
        notificationHelper = NotificationHelper(this)
        notificationHelper.showPersistentNotification()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    final override fun onClick() {
        super.onClick()

        // Centralized vibration handling
        vibrationManager.qstilevibration()

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
