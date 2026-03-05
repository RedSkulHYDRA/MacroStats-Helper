package com.redskul.macrostatshelper.flipglyph

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import androidx.annotation.RequiresApi
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.settings.QSTileSettingsActivity
import com.redskul.macrostatshelper.settings.QSTileSettingsManager
import com.redskul.macrostatshelper.tiles.BaseQSTileService
import com.redskul.macrostatshelper.tiles.TileConfigHelper
import kotlinx.coroutines.*

class FlipToGlyphQSTileService : BaseQSTileService() {

    private lateinit var flipToGlyphManager: FlipToGlyphManager
    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private val tileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val ACTION_FLIP_TO_GLYPH_SETTINGS_UPDATED =
            "com.redskul.macrostatshelper.FLIP_TO_GLYPH_SETTINGS_UPDATED"
    }

    private val settingsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_FLIP_TO_GLYPH_SETTINGS_UPDATED) {
                android.util.Log.d("FlipToGlyphQSTile", "Received settings update broadcast")
                updateTile()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        flipToGlyphManager   = FlipToGlyphManager(this)
        qsTileSettingsManager = QSTileSettingsManager(this)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        qsTile?.let { tile ->
            tile.label = getString(R.string.flip_to_glyph)
            tile.updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        registerReceiver(
            settingsUpdateReceiver,
            IntentFilter(ACTION_FLIP_TO_GLYPH_SETTINGS_UPDATED),
            Context.RECEIVER_NOT_EXPORTED
        )
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        try {
            unregisterReceiver(settingsUpdateReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver already unregistered — safe to ignore
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTileClick() {
        if (!flipToGlyphManager.hasRequiredPermissions()) {
            requestRequiredPermissions()
            return
        }

        val newState = flipToGlyphManager.toggle()
        if (newState != null) {
            updateTile()
            android.util.Log.d("FlipToGlyphQSTile", "Flip to Glyph toggled to: $newState")
        } else {
            android.util.Log.e("FlipToGlyphQSTile", "Failed to toggle Flip to Glyph")
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestRequiredPermissions() {
        try {
            val intent = Intent(this, QSTileSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val pendingIntent = PendingIntent.getActivity(
                this,
                7, // Unique request code (HeadsUp uses 6)
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pendingIntent)
            android.util.Log.d("FlipToGlyphQSTile", "Permission request activity started")
        } catch (e: Exception) {
            android.util.Log.e("FlipToGlyphQSTile", "Error starting permission activity", e)
        }
    }

    private fun updateTile() {
        tileScope.launch {
            try {
                val hasPermission      = flipToGlyphManager.hasRequiredPermissions()
                val isEnabled          = flipToGlyphManager.isEnabled()
                val showHeading        = qsTileSettingsManager.getShowFlipToGlyphInTitle()
                val stateText          = flipToGlyphManager.getCurrentStateText()
                val config             = TileConfigHelper.getFlipToGlyphTileConfig(
                    this@FlipToGlyphQSTileService, isEnabled)

                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext

                    if (!hasPermission) {
                        tile.state    = Tile.STATE_INACTIVE
                        tile.icon     = config.icon
                        tile.label    = getString(R.string.flip_to_glyph_permission_required)
                        tile.subtitle = getString(R.string.tap_to_grant)
                    } else {
                        TileConfigHelper.applyFlipToGlyphConfigToTile(
                            tile        = tile,
                            config      = config,
                            stateText   = stateText,
                            showFlipToGlyphInTitle = showHeading,
                            context     = this@FlipToGlyphQSTileService
                        )
                    }

                    tile.updateTile()
                }
            } catch (e: Exception) {
                android.util.Log.e("FlipToGlyphQSTile", "Error updating tile", e)
            }
        }
    }
}