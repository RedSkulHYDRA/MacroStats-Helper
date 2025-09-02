package com.redskul.macrostatshelper.dns

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.TypedValue
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.settings.QSTileSettingsActivity
import com.redskul.macrostatshelper.tiles.TileConfigHelper
import com.redskul.macrostatshelper.utils.PermissionHelper
import kotlinx.coroutines.*

class DNSQSTileService : TileService() {

    private lateinit var dnsManager: DNSManager
    private lateinit var permissionHelper: PermissionHelper
    private val tileScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        dnsManager = DNSManager(this)
        permissionHelper = PermissionHelper(this)
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onClick() {
        super.onClick()

        if (!dnsManager.hasSecureSettingsPermission()) {
            // Request permission by opening settings
            requestSecureSettingsPermission()
            return
        }

        if (!dnsManager.isDNSEnabled()) {
            // Open settings if DNS tile is not configured
            openDNSSettings()
            return
        }

        // Show DNS selection dialog
        showDNSSelectionDialog()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun requestSecureSettingsPermission() {
        try {
            val intent = Intent(this, QSTileSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            startActivityAndCollapse(pendingIntent)
            android.util.Log.d("DNSQSTile", "Opened settings for permission")
        } catch (e: Exception) {
            android.util.Log.e("DNSQSTile", "Error opening settings", e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun openDNSSettings() {
        try {
            val intent = Intent(this, QSTileSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val pendingIntent = PendingIntent.getActivity(
                this,
                1,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            startActivityAndCollapse(pendingIntent)
            android.util.Log.d("DNSQSTile", "Opened DNS settings")
        } catch (e: Exception) {
            android.util.Log.e("DNSQSTile", "Error opening DNS settings", e)
        }
    }

    private fun showDNSSelectionDialog() {
        val dnsOptions = dnsManager.getAllDNSOptions()
        val currentDNS = dnsManager.getCurrentDNS()

        if (dnsOptions.isEmpty()) {
            android.util.Log.w("DNSQSTile", "No DNS options available")
            return
        }

        // Create dialog with proper theming for dark mode
        val dialogTheme = if (isDarkTheme()) {
            android.R.style.Theme_DeviceDefault_Dialog_Alert
        } else {
            android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        }

        val dialogBuilder = AlertDialog.Builder(this, dialogTheme)

        // Create custom content with proper theming
        val radioGroup = createThemedRadioGroup(dnsOptions, currentDNS)

        dialogBuilder.setTitle(getString(R.string.select_dns_provider))
            .setView(radioGroup)
            .setPositiveButton(getString(R.string.apply_button)) { _, _ ->
                val selectedIndex = radioGroup.checkedRadioButtonId
                if (selectedIndex >= 0 && selectedIndex < dnsOptions.size) {
                    val selectedDNS = dnsOptions[selectedIndex]
                    tileScope.launch {
                        val success = dnsManager.setCurrentDNS(selectedDNS)
                        withContext(Dispatchers.Main) {
                            if (success) {
                                updateTile()
                                android.util.Log.d("DNSQSTile", "DNS changed to: ${selectedDNS.name}")
                            } else {
                                android.util.Log.e("DNSQSTile", "Failed to change DNS to: ${selectedDNS.name}")
                            }
                        }
                    }
                }
            }
            .setNegativeButton(getString(R.string.cancel_button)) { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton(getString(R.string.settings_button)) { _, _ ->
                try {
                    val intent = Intent(this, QSTileSettingsActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    android.util.Log.e("DNSQSTile", "Error opening settings", e)
                }
            }

        showDialog(dialogBuilder.create())
    }

    private fun isDarkTheme(): Boolean {
        return when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            Configuration.UI_MODE_NIGHT_NO -> false
            else -> false
        }
    }

    private fun createThemedRadioGroup(dnsOptions: List<DNSManager.DNSOption>, currentDNS: DNSManager.DNSOption): RadioGroup {
        val radioGroup = RadioGroup(this)
        radioGroup.orientation = RadioGroup.VERTICAL

        // Add padding
        val padding = resources.getDimensionPixelSize(R.dimen.spacing_md)
        radioGroup.setPadding(padding, padding, padding, padding)

        // Get appropriate text color for current theme
        val textColor = if (isDarkTheme()) {
            ContextCompat.getColor(this, android.R.color.primary_text_dark)
        } else {
            ContextCompat.getColor(this, android.R.color.primary_text_light)
        }

        // Create radio buttons for each DNS option
        dnsOptions.forEachIndexed { index, dnsOption ->
            val radioButton = RadioButton(this)
            radioButton.id = index

            // Set text based on DNS option type
            radioButton.text = when {
                dnsOption.isOff() -> dnsOption.name
                dnsOption.isAuto() -> dnsOption.name
                dnsOption.isCustom() -> "${dnsOption.name}\n${dnsOption.url}"
                else -> dnsOption.name
            }

            // Apply proper text color for theme
            radioButton.setTextColor(textColor)
            radioButton.setPadding(0, padding / 2, 0, padding / 2)

            // Check if this is the current selection
            if (dnsOption.url == currentDNS.url) {
                radioButton.isChecked = true
            }

            radioGroup.addView(radioButton)
        }

        return radioGroup
    }

    /**
     * Formats DNS display text based on DNS option type
     */
    private fun formatDNSDisplayText(dnsOption: DNSManager.DNSOption): String {
        return when {
            dnsOption.isOff() -> dnsOption.name  // "Off"
            dnsOption.isAuto() -> dnsOption.name  // "Automatic"
            dnsOption.isCustom() -> "${dnsOption.name} (${dnsOption.url})"  // "Google DNS (dns.google)"
            else -> dnsOption.name
        }
    }

    private fun updateTile() {
        tileScope.launch {
            try {
                val hasPermission = dnsManager.hasSecureSettingsPermission()
                val isDNSEnabled = dnsManager.isDNSEnabled()
                val config = TileConfigHelper.getDNSTileConfig(this@DNSQSTileService)

                withContext(Dispatchers.Main) {
                    val tile = qsTile ?: return@withContext

                    if (!hasPermission) {
                        // No permission
                        tile.state = Tile.STATE_INACTIVE
                        tile.icon = config.icon
                        tile.label = getString(R.string.dns_permission_required)
                        tile.subtitle = getString(R.string.tap_to_grant)
                    } else if (!isDNSEnabled) {
                        // Permission granted but not configured
                        tile.state = Tile.STATE_INACTIVE
                        tile.icon = config.icon
                        tile.label = getString(R.string.dns_not_configured)
                        tile.subtitle = getString(R.string.tap_to_setup)
                    } else {
                        // Fully functional
                        dnsManager.syncWithSystemSettings()
                        val currentDNS = dnsManager.getCurrentDNS()
                        val showHeading = dnsManager.getShowHeading()
                        val dnsDisplayText = formatDNSDisplayText(currentDNS)

                        // Set tile state based on DNS selection
                        tile.state = if (currentDNS.isOff()) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
                        tile.icon = config.icon

                        if (showHeading) {
                            // Show "Private DNS" as heading and DNS info as subtitle
                            tile.label = getString(R.string.private_dns_heading)
                            tile.subtitle = dnsDisplayText
                        } else {
                            // Show DNS info as main label, no subtitle
                            tile.label = dnsDisplayText
                            tile.subtitle = null
                        }
                    }

                    tile.updateTile()
                    android.util.Log.d("DNSQSTile", "Tile updated successfully")
                }
            } catch (e: Exception) {
                android.util.Log.e("DNSQSTile", "Error updating DNS tile", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tileScope.cancel()
    }
}
