package com.redskul.macrostatshelper.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.net.toUri
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.battery.BatteryHealthMonitor
import com.redskul.macrostatshelper.battery.BatteryHealthQSTileService
import com.redskul.macrostatshelper.battery.BatteryWorker
import com.redskul.macrostatshelper.datausage.DataUsageWorker
import com.redskul.macrostatshelper.dns.DNSManager
import com.redskul.macrostatshelper.torchglyph.TorchGlyphManager
import com.redskul.macrostatshelper.torchglyph.TorchGlyphQSTileService
import com.redskul.macrostatshelper.refreshrate.RefreshRateManager
import com.redskul.macrostatshelper.refreshrate.RefreshRateQSTileService
import com.redskul.macrostatshelper.aod.AODManager
import com.redskul.macrostatshelper.aod.AODQSTileService
import com.redskul.macrostatshelper.utils.PermissionHelper
import com.redskul.macrostatshelper.utils.VibrationManager
import com.redskul.macrostatshelper.databinding.ActivityQsTileSettingsBinding
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.view.isGone
import androidx.core.view.isVisible

class QSTileSettingsActivity : AppCompatActivity() {

    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private lateinit var batteryHealthMonitor: BatteryHealthMonitor
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var dnsManager: DNSManager
    private lateinit var torchGlyphManager: TorchGlyphManager
    private lateinit var refreshRateManager: RefreshRateManager
    private lateinit var aodManager: AODManager
    private lateinit var vibrationManager: VibrationManager
    private var binding: ActivityQsTileSettingsBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Initialize binding
        binding = ActivityQsTileSettingsBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        // Initialize managers
        qsTileSettingsManager = QSTileSettingsManager(this)
        batteryHealthMonitor = BatteryHealthMonitor(this)
        permissionHelper = PermissionHelper(this)
        dnsManager = DNSManager(this)
        torchGlyphManager = TorchGlyphManager(this)
        refreshRateManager = RefreshRateManager(this)
        aodManager = AODManager(this)
        vibrationManager = VibrationManager(this)

        setupWindowInsets()
        setupUI()
        loadCurrentSettings()
        startPermissionMonitoring()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
    }

    override fun onResume() {
        super.onResume()
        updatePermissionRequirementMessages()
        updateFeatureControls()
    }

    private fun setupSwitches(binding: ActivityQsTileSettingsBinding) {
        // Add haptic feedback to switches and immediate tile updates
        binding.showChargeInTitleSwitch.setOnCheckedChangeListener { switch, _ ->
            // Use centralized vibration manager for app interactions
            vibrationManager.vibrateOnAppInteraction()
            triggerImmediateTileUpdates()
        }

        binding.showBatteryHealthInTitleSwitch.setOnCheckedChangeListener { switch, _ ->
            // Use centralized vibration manager for app interactions
            vibrationManager.vibrateOnAppInteraction()
            triggerImmediateTileUpdates()
        }

        binding.showPeriodInTitleSwitch.setOnCheckedChangeListener { switch, _ ->
            // Use centralized vibration manager for app interactions
            vibrationManager.vibrateOnAppInteraction()
            triggerImmediateTileUpdates()
        }

        binding.showScreenTimeoutInTitleSwitch.setOnCheckedChangeListener { switch, _ ->
            // Use centralized vibration manager for app interactions
            vibrationManager.vibrateOnAppInteraction()
            triggerImmediateTileUpdates()
        }

        // DNS heading switch - using centralized VibrationManager
        binding.dnsShowHeadingSwitch.setOnCheckedChangeListener { switch, _ ->
            // Use centralized vibration manager for app interactions
            vibrationManager.vibrateOnAppInteraction()
            triggerDNSTileUpdate()
        }

        // Torch/Glyph heading switch - using centralized VibrationManager
        binding.torchGlyphShowHeadingSwitch.setOnCheckedChangeListener { switch, _ ->
            // Use centralized vibration manager for app interactions
            vibrationManager.vibrateOnAppInteraction()
            triggerTorchGlyphTileUpdate()
        }

        // Refresh Rate heading switch - using centralized VibrationManager
        binding.refreshRateShowHeadingSwitch.setOnCheckedChangeListener { switch, _ ->
            // Use centralized vibration manager for app interactions
            vibrationManager.vibrateOnAppInteraction()
            triggerRefreshRateTileUpdate()
        }

        // AOD heading switch - using centralized VibrationManager
        binding.aodShowHeadingSwitch.setOnCheckedChangeListener { switch, _ ->
            // Use centralized vibration manager for app interactions
            vibrationManager.vibrateOnAppInteraction()
            triggerAODTileUpdate()
        }

        // Vibration switch
        binding.vibrationEnabledSwitch.setOnCheckedChangeListener { switch, isChecked ->
            // Use centralized vibration manager for app interactions
            vibrationManager.vibrateOnAppInteraction()

            if (!vibrationManager.hasVibrator() && isChecked) {
                // Device doesn't have vibrator, disable the switch and show message
                binding.vibrationEnabledSwitch.isChecked = false
                showToast(getString(R.string.vibration_disabled_no_hardware))
                return@setOnCheckedChangeListener
            }

            vibrationManager.setVibrationEnabled(isChecked)

            // Provide immediate feedback if vibration is being enabled
            if (isChecked) {
                vibrationManager.qstilevibration()
            }
        }
    }

    private fun loadCurrentSettings() {
        val binding = binding ?: return

        val wifiPeriod = qsTileSettingsManager.getWiFiTilePeriod()
        val mobilePeriod = qsTileSettingsManager.getMobileTilePeriod()
        val showPeriodInTitle = qsTileSettingsManager.getShowPeriodInTitle()
        val showChargeInTitle = qsTileSettingsManager.getShowChargeInTitle()
        val showHealthInTitle = qsTileSettingsManager.getShowBatteryHealthInTitle()
        val showScreenTimeoutInTitle = qsTileSettingsManager.getShowScreenTimeoutInTitle()
        val showTorchGlyphInTitle = qsTileSettingsManager.getShowTorchGlyphInTitle()
        val showRefreshRateInTitle = qsTileSettingsManager.getShowRefreshRateInTitle()
        val showAODInTitle = qsTileSettingsManager.getShowAODInTitle()
        val showDNSInTitle = qsTileSettingsManager.getShowDNSInTitle()
        val designCapacity = qsTileSettingsManager.getBatteryDesignCapacity()
        val vibrationEnabled = vibrationManager.isVibrationEnabled()

        // Set radio group selections
        val wifiRadioId = when (wifiPeriod) {
            TimePeriod.DAILY -> R.id.wifi_daily
            TimePeriod.WEEKLY -> R.id.wifi_weekly
            TimePeriod.MONTHLY -> R.id.wifi_monthly
        }
        binding.wifiTileRadioGroup.check(wifiRadioId)

        val mobileRadioId = when (mobilePeriod) {
            TimePeriod.DAILY -> R.id.mobile_daily
            TimePeriod.WEEKLY -> R.id.mobile_weekly
            TimePeriod.MONTHLY -> R.id.mobile_monthly
        }
        binding.mobileTileRadioGroup.check(mobileRadioId)

        // Set switch states - ALL CONSISTENT NOW
        binding.showPeriodInTitleSwitch.isChecked = showPeriodInTitle
        binding.showChargeInTitleSwitch.isChecked = showChargeInTitle
        binding.showBatteryHealthInTitleSwitch.isChecked = showHealthInTitle
        binding.showScreenTimeoutInTitleSwitch.isChecked = showScreenTimeoutInTitle
        binding.torchGlyphShowHeadingSwitch.isChecked = showTorchGlyphInTitle
        binding.refreshRateShowHeadingSwitch.isChecked = showRefreshRateInTitle
        binding.aodShowHeadingSwitch.isChecked = showAODInTitle
        binding.dnsShowHeadingSwitch.isChecked = showDNSInTitle
        binding.vibrationEnabledSwitch.isChecked = vibrationEnabled

        // Disable vibration switch if device has no vibrator
        if (!vibrationManager.hasVibrator()) {
            binding.vibrationEnabledSwitch.isEnabled = false
            binding.vibrationEnabledSwitch.isChecked = false
            binding.vibrationDescription.text = getString(R.string.vibration_disabled_no_hardware)
        }

        // Set design capacity
        if (designCapacity > 0) {
            binding.designCapacityEditText.setText(designCapacity.toString())
        }

        updatePermissionRequirementMessages()
        updateFeatureControls()
    }

    private fun saveSettings() {
        val binding = binding ?: return

        // Get radio group selections
        val wifiPeriod = when (binding.wifiTileRadioGroup.checkedRadioButtonId) {
            R.id.wifi_daily -> TimePeriod.DAILY
            R.id.wifi_weekly -> TimePeriod.WEEKLY
            R.id.wifi_monthly -> TimePeriod.MONTHLY
            else -> TimePeriod.DAILY
        }

        val mobilePeriod = when (binding.mobileTileRadioGroup.checkedRadioButtonId) {
            R.id.mobile_daily -> TimePeriod.DAILY
            R.id.mobile_weekly -> TimePeriod.WEEKLY
            R.id.mobile_monthly -> TimePeriod.MONTHLY
            else -> TimePeriod.DAILY
        }

        // Get design capacity
        val designCapacity = binding.designCapacityEditText.text.toString().toIntOrNull() ?: 0

        // Save all existing settings
        qsTileSettingsManager.saveWiFiTilePeriod(wifiPeriod)
        qsTileSettingsManager.saveMobileTilePeriod(mobilePeriod)
        qsTileSettingsManager.saveShowPeriodInTitle(binding.showPeriodInTitleSwitch.isChecked)
        qsTileSettingsManager.saveShowChargeInTitle(binding.showChargeInTitleSwitch.isChecked)
        qsTileSettingsManager.saveShowBatteryHealthInTitle(binding.showBatteryHealthInTitleSwitch.isChecked)
        qsTileSettingsManager.saveShowScreenTimeoutInTitle(binding.showScreenTimeoutInTitleSwitch.isChecked)
        qsTileSettingsManager.saveShowTorchGlyphInTitle(binding.torchGlyphShowHeadingSwitch.isChecked)
        qsTileSettingsManager.saveShowRefreshRateInTitle(binding.refreshRateShowHeadingSwitch.isChecked)
        qsTileSettingsManager.saveShowAODInTitle(binding.aodShowHeadingSwitch.isChecked)
        qsTileSettingsManager.saveShowDNSInTitle(binding.dnsShowHeadingSwitch.isChecked)

        // Save vibration setting
        vibrationManager.setVibrationEnabled(binding.vibrationEnabledSwitch.isChecked)

        if (designCapacity > 0) {
            qsTileSettingsManager.saveBatteryDesignCapacity(designCapacity)
            batteryHealthMonitor.setDesignCapacity(designCapacity)
        }

        // Save DNS settings
        saveDNSSettings(binding)

        // Save Torch/Glyph settings
        saveTorchGlyphSettings(binding)

        // Save Refresh Rate settings
        saveRefreshRateSettings(binding)

        // Save AOD settings
        saveAODSettings(binding)

        // Trigger immediate tile updates after saving all settings
        triggerImmediateTileUpdates()
        triggerDNSTileUpdate()
        triggerTorchGlyphTileUpdate()
        triggerRefreshRateTileUpdate()
        triggerAODTileUpdate()

        Toast.makeText(this, getString(R.string.qs_settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun setupWindowInsets() {
        val binding = binding ?: return

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            val settingsPadding = resources.getDimensionPixelSize(R.dimen.padding_settings)
            binding.qsSettingsLayout.setPadding(
                settingsPadding + insets.left,
                settingsPadding + insets.top,
                settingsPadding + insets.right,
                settingsPadding + insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupUI() {
        val binding = binding ?: return

        // Setup radio groups
        setupRadioGroups(binding)

        // Setup switches
        setupSwitches(binding)

        // Setup buttons
        setupButtons(binding)

        // Setup EditTexts
        setupEditTexts(binding)

        // Setup DNS section
        setupDNSSection(binding)

        // Setup DNS buttons
        setupDNSButtons(binding)
    }

    private fun setupRadioGroups(binding: ActivityQsTileSettingsBinding) {
        // WiFi radio group listener
        binding.wifiTileRadioGroup.setOnCheckedChangeListener { _, _ ->
            // Use centralized vibration manager for app interactions
            vibrationManager.vibrateOnAppInteraction()
            // Settings will be saved when save button is clicked
        }

        // Mobile radio group listener
        binding.mobileTileRadioGroup.setOnCheckedChangeListener { _, _ ->
            // Use centralized vibration manager for app interactions
            vibrationManager.vibrateOnAppInteraction()
            // Settings will be saved when save button is clicked
        }
    }

    private fun setupButtons(binding: ActivityQsTileSettingsBinding) {
        binding.qsSaveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun setupEditTexts(binding: ActivityQsTileSettingsBinding) {
        binding.designCapacityEditText.inputType = InputType.TYPE_CLASS_NUMBER
    }

    private fun setupDNSSection(binding: ActivityQsTileSettingsBinding) {
        // Load DNS options
        val dns1 = dnsManager.getDNSOption(1)
        val dns2 = dnsManager.getDNSOption(2)
        val dns3 = dnsManager.getDNSOption(3)

        binding.dns1NameEditText.setText(dns1.name)
        binding.dns1UrlEditText.setText(dns1.url)
        binding.dns2NameEditText.setText(dns2.name)
        binding.dns2UrlEditText.setText(dns2.url)
        binding.dns3NameEditText.setText(dns3.name)
        binding.dns3UrlEditText.setText(dns3.url)

        // Show DNS 2 and 3 if they have data
        if (dns2.isValid() && dns2.url.isNotEmpty()) {
            binding.dns2Container.visibility = android.view.View.VISIBLE
        }
        if (dns3.isValid() && dns3.url.isNotEmpty()) {
            binding.dns3Container.visibility = android.view.View.VISIBLE
        }

        updateDNSAddButtonVisibility()
    }

    private fun setupDNSButtons(binding: ActivityQsTileSettingsBinding) {
        // Main Add DNS button
        binding.dnsAddButton.setOnClickListener {
            addNextDNSEntry()
        }

        // Remove DNS 2 button
        binding.dnsRemove2Button.setOnClickListener {
            binding.dns2Container.visibility = android.view.View.GONE
            // Clear the fields
            binding.dns2NameEditText.setText("")
            binding.dns2UrlEditText.setText("")
            updateDNSAddButtonVisibility()
        }

        // Remove DNS 3 button
        binding.dnsRemove3Button.setOnClickListener {
            binding.dns3Container.visibility = android.view.View.GONE
            // Clear the fields
            binding.dns3NameEditText.setText("")
            binding.dns3UrlEditText.setText("")
            updateDNSAddButtonVisibility()
        }
    }

    private fun addNextDNSEntry() {
        val binding = binding ?: return

        when {
            binding.dns2Container.visibility == android.view.View.GONE -> {
                binding.dns2Container.visibility = android.view.View.VISIBLE
            }
            binding.dns3Container.isGone -> {
                binding.dns3Container.visibility = android.view.View.VISIBLE
            }
        }
        updateDNSAddButtonVisibility()
    }

    private fun updateDNSAddButtonVisibility() {
        val binding = binding ?: return

        // Hide add button if all 3 DNS entries are visible
        val allVisible = binding.dns2Container.isVisible &&
                binding.dns3Container.isVisible
        binding.dnsAddButton.visibility = if (allVisible) android.view.View.GONE else android.view.View.VISIBLE
    }

    private fun startPermissionMonitoring() {
        lifecycleScope.launch {
            var lastUsageStats = permissionHelper.hasUsageStatsPermission()
            var lastWriteSettings = permissionHelper.hasWriteSettingsPermission()
            var lastSecureSettings = dnsManager.hasSecureSettingsPermission()

            while (binding != null) {
                delay(1000)

                val currentUsageStats = permissionHelper.hasUsageStatsPermission()
                val currentWriteSettings = permissionHelper.hasWriteSettingsPermission()
                val currentSecureSettings = dnsManager.hasSecureSettingsPermission()

                if (lastUsageStats != currentUsageStats || lastWriteSettings != currentWriteSettings ||
                    lastSecureSettings != currentSecureSettings) {
                    updatePermissionRequirementMessages()
                    updateFeatureControls()

                    if (lastUsageStats && !currentUsageStats) {
                        showToast(getString(R.string.data_tiles_disabled))
                    }
                    if (lastWriteSettings && !currentWriteSettings) {
                        showToast(getString(R.string.screen_timeout_disabled))
                    }
                    if (lastSecureSettings && !currentSecureSettings) {
                        showToast(getString(R.string.advanced_tiles_disabled))
                    }
                }

                lastUsageStats = currentUsageStats
                lastWriteSettings = currentWriteSettings
                lastSecureSettings = currentSecureSettings
            }
        }
    }

    private fun updatePermissionRequirementMessages() {
        val binding = binding ?: return
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()
        val hasWriteSettings = permissionHelper.hasWriteSettingsPermission()
        val hasSecureSettings = dnsManager.hasSecureSettingsPermission()

        // Show/hide permission requirement messages
        if (!hasUsageStats) {
            binding.dataTilesPermissionText.visibility = android.view.View.VISIBLE
        } else {
            binding.dataTilesPermissionText.visibility = android.view.View.GONE
        }

        if (!hasWriteSettings) {
            binding.screenTimeoutPermissionText.visibility = android.view.View.VISIBLE
        } else {
            binding.screenTimeoutPermissionText.visibility = android.view.View.GONE
        }

        if (!hasSecureSettings) {
            binding.advancedTilesPermissionText.visibility = android.view.View.VISIBLE
        } else {
            binding.advancedTilesPermissionText.visibility = android.view.View.GONE
        }
    }

    private fun updateFeatureControls() {
        val binding = binding ?: return
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()
        val hasWriteSettings = permissionHelper.hasWriteSettingsPermission()
        val hasSecureSettings = dnsManager.hasSecureSettingsPermission()

        // Data usage tile controls (Usage Stats permission required)
        binding.wifiTileRadioGroup.isEnabled = hasUsageStats
        for (i in 0 until binding.wifiTileRadioGroup.childCount) {
            binding.wifiTileRadioGroup.getChildAt(i).isEnabled = hasUsageStats
        }
        binding.wifiTileRadioGroup.alpha = if (hasUsageStats) 1.0f else 0.5f

        binding.mobileTileRadioGroup.isEnabled = hasUsageStats
        for (i in 0 until binding.mobileTileRadioGroup.childCount) {
            binding.mobileTileRadioGroup.getChildAt(i).isEnabled = hasUsageStats
        }
        binding.mobileTileRadioGroup.alpha = if (hasUsageStats) 1.0f else 0.5f

        binding.showPeriodInTitleSwitch.isEnabled = hasUsageStats
        binding.showPeriodInTitleSwitch.alpha = if (hasUsageStats) 1.0f else 0.5f

        // Screen timeout controls (Write Settings permission required)
        binding.showScreenTimeoutInTitleSwitch.isEnabled = hasWriteSettings
        binding.showScreenTimeoutInTitleSwitch.alpha = if (hasWriteSettings) 1.0f else 0.5f

        // Advanced tiles controls (Secure Settings permission required)
        binding.dns1NameEditText.isEnabled = hasSecureSettings
        binding.dns1UrlEditText.isEnabled = hasSecureSettings
        binding.dns2NameEditText.isEnabled = hasSecureSettings
        binding.dns2UrlEditText.isEnabled = hasSecureSettings
        binding.dns3NameEditText.isEnabled = hasSecureSettings
        binding.dns3UrlEditText.isEnabled = hasSecureSettings
        binding.dnsShowHeadingSwitch.isEnabled = hasSecureSettings
        binding.dnsAddButton.isEnabled = hasSecureSettings
        binding.dnsRemove2Button.isEnabled = hasSecureSettings
        binding.dnsRemove3Button.isEnabled = hasSecureSettings
        binding.torchGlyphShowHeadingSwitch.isEnabled = hasSecureSettings
        binding.refreshRateShowHeadingSwitch.isEnabled = hasSecureSettings
        binding.aodShowHeadingSwitch.isEnabled = hasSecureSettings

        // Visual feedback for advanced tiles
        val advancedAlpha = if (hasSecureSettings) 1.0f else 0.5f
        binding.dns1NameEditText.alpha = advancedAlpha
        binding.dns1UrlEditText.alpha = advancedAlpha
        binding.dns2NameEditText.alpha = advancedAlpha
        binding.dns2UrlEditText.alpha = advancedAlpha
        binding.dns3NameEditText.alpha = advancedAlpha
        binding.dns3UrlEditText.alpha = advancedAlpha
        binding.dnsShowHeadingSwitch.alpha = advancedAlpha
        binding.dnsAddButton.alpha = advancedAlpha
        binding.dnsRemove2Button.alpha = advancedAlpha
        binding.dnsRemove3Button.alpha = advancedAlpha
        binding.torchGlyphShowHeadingSwitch.alpha = advancedAlpha
        binding.refreshRateShowHeadingSwitch.alpha = advancedAlpha
        binding.aodShowHeadingSwitch.alpha = advancedAlpha

        // Battery controls are always enabled (no specific permissions required)
        binding.showChargeInTitleSwitch.isEnabled = true
        binding.showBatteryHealthInTitleSwitch.isEnabled = true
        binding.designCapacityEditText.isEnabled = true
    }

    private fun saveDNSSettings(binding: ActivityQsTileSettingsBinding) {
        // Save DNS options
        dnsManager.saveDNSOption(
            1,
            binding.dns1NameEditText.text.toString().trim(),
            binding.dns1UrlEditText.text.toString().trim()
        )

        // Only save DNS 2 if container is visible
        if (binding.dns2Container.isVisible) {
            dnsManager.saveDNSOption(
                2,
                binding.dns2NameEditText.text.toString().trim(),
                binding.dns2UrlEditText.text.toString().trim()
            )
        } else {
            // Clear DNS 2 if hidden
            dnsManager.saveDNSOption(2, "", "")
        }

        // Only save DNS 3 if container is visible
        if (binding.dns3Container.isVisible) {
            dnsManager.saveDNSOption(
                3,
                binding.dns3NameEditText.text.toString().trim(),
                binding.dns3UrlEditText.text.toString().trim()
            )
        } else {
            // Clear DNS 3 if hidden
            dnsManager.saveDNSOption(3, "", "")
        }

        // Enable DNS tile if any DNS options are configured
        val hasValidDNS = dnsManager.getAllDNSOptions().any { !it.isOff() && !it.isAuto() && it.isValid() }
        dnsManager.setDNSEnabled(hasValidDNS)

        android.util.Log.d("QSTileSettings", "DNS settings saved. DNS tile enabled: $hasValidDNS")
    }

    private fun saveTorchGlyphSettings(binding: ActivityQsTileSettingsBinding) {
        // Enable Torch/Glyph tile if permissions are granted
        val hasValidTorchGlyph = torchGlyphManager.hasRequiredPermissions()
        torchGlyphManager.setTorchGlyphEnabled(hasValidTorchGlyph)

        android.util.Log.d("QSTileSettings", "Torch/Glyph settings saved. Torch/Glyph tile enabled: $hasValidTorchGlyph")
    }

    private fun saveRefreshRateSettings(binding: ActivityQsTileSettingsBinding) {
        // Enable Refresh Rate tile if permissions are granted
        val hasValidRefreshRate = refreshRateManager.hasRequiredPermissions()
        refreshRateManager.setRefreshRateEnabled(hasValidRefreshRate)

        android.util.Log.d("QSTileSettings", "Refresh Rate settings saved. Refresh Rate tile enabled: $hasValidRefreshRate")
    }

    private fun saveAODSettings(binding: ActivityQsTileSettingsBinding) {
        // Enable AOD tile if permissions are granted
        val hasValidAOD = aodManager.hasRequiredPermissions()
        aodManager.setAODEnabled(hasValidAOD)

        android.util.Log.d("QSTileSettings", "AOD settings saved. AOD tile enabled: $hasValidAOD")
    }

    /**
     * Triggers immediate tile updates by sending broadcasts to all tile services
     */
    private fun triggerImmediateTileUpdates() {
        lifecycleScope.launch {
            try {
                // Send broadcast for data usage tiles update
                val dataUpdateIntent = Intent(DataUsageWorker.ACTION_DATA_UPDATED).apply {
                    setPackage(packageName)
                }
                sendBroadcast(dataUpdateIntent)
                android.util.Log.d("QSTileSettings", "Data usage tiles update broadcast sent")

                // Send broadcast for battery tiles update
                val batteryUpdateIntent = Intent(BatteryWorker.ACTION_BATTERY_UPDATED).apply {
                    setPackage(packageName)
                }
                sendBroadcast(batteryUpdateIntent)
                android.util.Log.d("QSTileSettings", "Battery tiles update broadcast sent")

                // Send broadcast specifically for battery health tile
                val healthUpdateIntent = Intent(BatteryHealthQSTileService.ACTION_BATTERY_HEALTH_UPDATED).apply {
                    setPackage(packageName)
                }
                sendBroadcast(healthUpdateIntent)
                android.util.Log.d("QSTileSettings", "Battery health tile update broadcast sent")

                android.util.Log.d("QSTileSettings", "All immediate tile update broadcasts sent")
            } catch (e: Exception) {
                android.util.Log.e("QSTileSettings", "Error sending immediate tile update broadcasts", e)
            }
        }
    }

    private fun triggerDNSTileUpdate() {
        lifecycleScope.launch {
            try {
                // Send broadcast for DNS tile update
                val dnsUpdateIntent = Intent("com.redskul.macrostatshelper.DNS_SETTINGS_UPDATED").apply {
                    setPackage(packageName)
                }
                sendBroadcast(dnsUpdateIntent)
                android.util.Log.d("QSTileSettings", "DNS tile update broadcast sent")
            } catch (e: Exception) {
                android.util.Log.e("QSTileSettings", "Error sending DNS tile update broadcast", e)
            }
        }
    }

    private fun triggerTorchGlyphTileUpdate() {
        lifecycleScope.launch {
            try {
                // Send broadcast for Torch/Glyph tile update
                val torchGlyphUpdateIntent = Intent(TorchGlyphQSTileService.ACTION_TORCH_GLYPH_SETTINGS_UPDATED).apply {
                    setPackage(packageName)
                }
                sendBroadcast(torchGlyphUpdateIntent)
                android.util.Log.d("QSTileSettings", "Torch/Glyph tile update broadcast sent")
            } catch (e: Exception) {
                android.util.Log.e("QSTileSettings", "Error sending Torch/Glyph tile update broadcast", e)
            }
        }
    }

    private fun triggerRefreshRateTileUpdate() {
        lifecycleScope.launch {
            try {
                // Send broadcast for Refresh Rate tile update
                val refreshRateUpdateIntent = Intent(RefreshRateQSTileService.ACTION_REFRESH_RATE_SETTINGS_UPDATED).apply {
                    setPackage(packageName)
                }
                sendBroadcast(refreshRateUpdateIntent)
                android.util.Log.d("QSTileSettings", "Refresh Rate tile update broadcast sent")
            } catch (e: Exception) {
                android.util.Log.e("QSTileSettings", "Error sending Refresh Rate tile update broadcast", e)
            }
        }
    }

    private fun triggerAODTileUpdate() {
        lifecycleScope.launch {
            try {
                // Send broadcast for AOD tile update
                val aodUpdateIntent = Intent(AODQSTileService.ACTION_AOD_SETTINGS_UPDATED).apply {
                    setPackage(packageName)
                }
                sendBroadcast(aodUpdateIntent)
                android.util.Log.d("QSTileSettings", "AOD tile update broadcast sent")
            } catch (e: Exception) {
                android.util.Log.e("QSTileSettings", "Error sending AOD tile update broadcast", e)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
