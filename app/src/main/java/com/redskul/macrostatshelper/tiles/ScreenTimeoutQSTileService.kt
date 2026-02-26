package com.redskul.macrostatshelper.tiles

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import com.redskul.macrostatshelper.tiles.BaseQSTileService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import com.redskul.macrostatshelper.settings.QSTileSettingsManager
import kotlinx.coroutines.*
import com.redskul.macrostatshelper.R

class ScreenTimeoutQSTileService : BaseQSTileService() {

    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private val tileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        // Screen timeout values in milliseconds
        private val TIMEOUT_VALUES = arrayOf(
            15000,   // 15 seconds
            30000,   // 30 seconds
            60000,   // 1 minute
            120000,  // 2 minutes
            300000,  // 5 minutes
            600000,  // 10 minutes
            1800000  // 30 minutes
        )

        private fun getTimeoutLabel(context: Context, timeoutMs: Int): String {
            return when (timeoutMs) {
                15000   -> context.getString(R.string.screen_timeout_15s)
                30000   -> context.getString(R.string.screen_timeout_30s)
                60000   -> context.getString(R.string.screen_timeout_1m)
                120000  -> context.getString(R.string.screen_timeout_2m)
                300000  -> context.getString(R.string.screen_timeout_5m)
                600000  -> context.getString(R.string.screen_timeout_10m)
                1800000 -> context.getString(R.string.screen_timeout_30m)
                else    -> "${timeoutMs / 1000}s"
            }
        }

        private fun getNextTimeout(currentTimeoutMs: Int): Int {
            val currentIndex = TIMEOUT_VALUES.indexOf(currentTimeoutMs)
            return if (currentIndex >= 0 && currentIndex < TIMEOUT_VALUES.size - 1) {
                TIMEOUT_VALUES[currentIndex + 1]
            } else {
                TIMEOUT_VALUES[0] // Cycle back to first value
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        qsTileSettingsManager = QSTileSettingsManager(this)
    }

    override fun onTileAdded() {
        super.onTileAdded()
        // Ensure tile picker always shows the manifest label
        qsTile?.let { tile ->
            tile.label = getString(R.string.screen_timeout)
            tile.updateTile()
        }
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onTileClick() {

        if (!hasWriteSettingsPermission()) {
            // Request permission by opening settings
            requestWriteSettingsPermission()
            return
        }

        // Permission is granted, show picker dialog
        showTimeoutDialog()
    }

    private fun showTimeoutDialog() {
        val currentTimeout = getCurrentScreenTimeout()
        val checkedIndex = TIMEOUT_VALUES.indexOf(currentTimeout).coerceAtLeast(0)
        val labels = TIMEOUT_VALUES.map { getTimeoutLabel(this, it) }

        val dialogTheme = if (isDarkTheme())
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        else
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert

        val radioGroup = createThemedRadioGroup(labels, checkedIndex)

        AlertDialog.Builder(this, dialogTheme)
            .setTitle(getString(R.string.screen_timeout))
            .setView(radioGroup)
            .setPositiveButton(getString(R.string.apply_button)) { _, _ ->
                val selectedIndex = radioGroup.checkedRadioButtonId
                if (selectedIndex >= 0 && selectedIndex < TIMEOUT_VALUES.size) {
                    val selectedLabel = labels[selectedIndex]
                    tileScope.launch {
                        val success = setScreenTimeout(TIMEOUT_VALUES[selectedIndex])
                        withContext(Dispatchers.Main) {
                            if (success) {
                                // Update tile
                                updateTile()
                                Toast.makeText(this@ScreenTimeoutQSTileService, "${getString(R.string.screen_timeout)}: $selectedLabel", Toast.LENGTH_SHORT).show()
                            } else {
                                android.util.Log.e("ScreenTimeoutQSTile", "Failed to set screen timeout")
                            }
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel_button)) { dialog, _ ->
                dialog.dismiss()
            }
            .also { showDialog(it.create()) }
    }

    private fun isDarkTheme(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun createThemedRadioGroup(labels: List<String>, checkedIndex: Int): RadioGroup {
        val radioGroup = RadioGroup(this)
        radioGroup.orientation = RadioGroup.VERTICAL

        // Add padding around the radio group
        val padding = resources.getDimensionPixelSize(R.dimen.spacing_md)
        val padding_bottom = resources.getDimensionPixelSize(R.dimen.spacing_sm)
        radioGroup.setPadding(padding, padding, padding, padding_bottom)

        // Get appropriate text color for current theme
        val textColor = if (isDarkTheme()) {
            ContextCompat.getColor(this, android.R.color.primary_text_dark)
        } else {
            ContextCompat.getColor(this, android.R.color.primary_text_light)
        }

        labels.forEachIndexed { index, label ->
            val radioButton = RadioButton(this)
            radioButton.id = index
            radioButton.text = label
            radioButton.setTextColor(textColor)
            radioButton.setPadding((padding * 0.5).toInt(), padding / 2, padding * 2, padding / 2)
            radioButton.isChecked = (index == checkedIndex)
            radioGroup.addView(radioButton)
        }

        return radioGroup
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestWriteSettingsPermission() {
        try {
            // Create intent for write settings permission
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = "package:$packageName".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Create PendingIntent
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            // Start the activity and collapse the panel using PendingIntent
            startActivityAndCollapse(pendingIntent)

            android.util.Log.d("ScreenTimeoutQSTile", "Permission request activity started with PendingIntent")
        } catch (e: Exception) {
            android.util.Log.e("ScreenTimeoutQSTile", "Error starting permission activity", e)
            // Fallback: just open general app settings
            try {
                val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:$packageName".toUri()
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }

                // Create fallback PendingIntent
                val fallbackPendingIntent = PendingIntent.getActivity(
                    this,
                    1, // Different request code
                    fallbackIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                startActivityAndCollapse(fallbackPendingIntent)
                android.util.Log.d("ScreenTimeoutQSTile", "Fallback permission activity started with PendingIntent")
            } catch (fallbackError: Exception) {
                android.util.Log.e("ScreenTimeoutQSTile", "Fallback also failed", fallbackError)
            }
        }
    }

    private fun updateTile() {
        tileScope.launch {
            try {
                val hasPermission = hasWriteSettingsPermission()
                val currentTimeout = getCurrentScreenTimeout()
                val timeoutLabel = getTimeoutLabel(this@ScreenTimeoutQSTileService, currentTimeout)
                val config = TileConfigHelper.getScreenTimeoutTileConfig(this@ScreenTimeoutQSTileService)
                val showTimeoutInTitle = qsTileSettingsManager.getShowScreenTimeoutInTitle()

                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext

                    if (hasPermission) {
                        // Permission granted - show normal tile
                        TileConfigHelper.applyScreenTimeoutConfigToTile(
                            tile,
                            config,
                            timeoutLabel,
                            showTimeoutInTitle,
                            true,
                            context = this@ScreenTimeoutQSTileService
                        )
                        tile.state = Tile.STATE_ACTIVE
                    } else {
                        // Permission not granted - show permission required state
                        tile.state = Tile.STATE_INACTIVE
                        tile.icon = config.icon
                        tile.label = getString(R.string.screen_timeout_permission_required)
                        tile.subtitle = getString(R.string.tap_to_grant)
                    }

                    tile.updateTile()
                    android.util.Log.d("ScreenTimeoutQSTile", "Tile updated - Permission: $hasPermission, Timeout: $timeoutLabel")
                }
            } catch (e: Exception) {
                android.util.Log.e("ScreenTimeoutQSTile", "Error updating Screen Timeout tile", e)
            }
        }
    }

    private fun getCurrentScreenTimeout(): Int {
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 30000)
        } catch (e: Exception) {
            android.util.Log.e("ScreenTimeoutQSTile", "Error getting screen timeout", e)
            30000 // Default to 30 seconds
        }
    }

    private fun setScreenTimeout(timeoutMs: Int): Boolean {
        return try {
            val success = Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, timeoutMs)
            if (success) {
                android.util.Log.d("ScreenTimeoutQSTile", "Screen timeout set to: ${getTimeoutLabel(applicationContext, timeoutMs)}")
            } else {
                android.util.Log.e("ScreenTimeoutQSTile", "Failed to set screen timeout")
            }
            success
        } catch (e: Exception) {
            android.util.Log.e("ScreenTimeoutQSTile", "Error setting screen timeout", e)
            false
        }
    }

    private fun hasWriteSettingsPermission(): Boolean {
        return Settings.System.canWrite(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }
}