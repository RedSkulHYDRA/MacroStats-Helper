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
import com.redskul.macrostatshelper.headsup.HeadsUpManager
import com.redskul.macrostatshelper.headsup.HeadsUpQSTileService
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
    private lateinit var headsUpManager: HeadsUpManager
    private lateinit var vibrationManager: VibrationManager
    private var binding: ActivityQsTileSettingsBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityQsTileSettingsBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        qsTileSettingsManager = QSTileSettingsManager(this)
        batteryHealthMonitor = BatteryHealthMonitor(this)
        permissionHelper = PermissionHelper(this)
        dnsManager = DNSManager(this)
        torchGlyphManager = TorchGlyphManager(this)
        refreshRateManager = RefreshRateManager(this)
        aodManager = AODManager(this)
        headsUpManager = HeadsUpManager(this)
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
        binding.showChargeInTitleSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            triggerImmediateTileUpdates()
        }

        binding.showBatteryHealthInTitleSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            triggerImmediateTileUpdates()
        }

        binding.showPeriodInTitleSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            triggerImmediateTileUpdates()
        }

        binding.showScreenTimeoutInTitleSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            triggerImmediateTileUpdates()
        }

        binding.dnsShowHeadingSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            triggerDNSTileUpdate()
        }

        binding.torchGlyphShowHeadingSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            triggerTorchGlyphTileUpdate()
        }

        binding.refreshRateShowHeadingSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            triggerRefreshRateTileUpdate()
        }

        binding.aodShowHeadingSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            triggerAODTileUpdate()
        }

        binding.headsUpShowHeadingSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            triggerHeadsUpTileUpdate()
        }

        binding.vibrationEnabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            vibrationManager.vibrateOnAppInteraction()

            if (!vibrationManager.hasVibrator() && isChecked) {
                binding.vibrationEnabledSwitch.isChecked = false
                showToast(getString(R.string.vibration_disabled_no_hardware))
                return@setOnCheckedChangeListener
            }

            vibrationManager.setVibrationEnabled(isChecked)

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
        val showHeadsUpInTitle = qsTileSettingsManager.getShowHeadsUpInTitle()
        val designCapacity = qsTileSettingsManager.getBatteryDesignCapacity()
        val vibrationEnabled = vibrationManager.isVibrationEnabled()

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

        binding.showPeriodInTitleSwitch.isChecked = showPeriodInTitle
        binding.showChargeInTitleSwitch.isChecked = showChargeInTitle
        binding.showBatteryHealthInTitleSwitch.isChecked = showHealthInTitle
        binding.showScreenTimeoutInTitleSwitch.isChecked = showScreenTimeoutInTitle
        binding.torchGlyphShowHeadingSwitch.isChecked = showTorchGlyphInTitle
        binding.refreshRateShowHeadingSwitch.isChecked = showRefreshRateInTitle
        binding.aodShowHeadingSwitch.isChecked = showAODInTitle
        binding.dnsShowHeadingSwitch.isChecked = showDNSInTitle
        binding.headsUpShowHeadingSwitch.isChecked = showHeadsUpInTitle
        binding.vibrationEnabledSwitch.isChecked = vibrationEnabled

        if (!vibrationManager.hasVibrator()) {
            binding.vibrationEnabledSwitch.isEnabled = false
            binding.vibrationEnabledSwitch.isChecked = false
            binding.vibrationDescription.text = getString(R.string.vibration_disabled_no_hardware)
        }

        if (designCapacity > 0) {
            binding.designCapacityEditText.setText(designCapacity.toString())
        }

        updatePermissionRequirementMessages()
        updateFeatureControls()
    }

    private fun saveSettings() {
        val binding = binding ?: return

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

        val designCapacity = binding.designCapacityEditText.text.toString().toIntOrNull() ?: 0

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
        qsTileSettingsManager.saveShowHeadsUpInTitle(binding.headsUpShowHeadingSwitch.isChecked)

        vibrationManager.setVibrationEnabled(binding.vibrationEnabledSwitch.isChecked)

        if (designCapacity > 0) {
            qsTileSettingsManager.saveBatteryDesignCapacity(designCapacity)
            batteryHealthMonitor.setDesignCapacity(designCapacity)
        }

        saveDNSSettings(binding)
        saveTorchGlyphSettings(binding)
        saveRefreshRateSettings(binding)
        saveAODSettings(binding)

        triggerImmediateTileUpdates()
        triggerDNSTileUpdate()
        triggerTorchGlyphTileUpdate()
        triggerRefreshRateTileUpdate()
        triggerAODTileUpdate()
        triggerHeadsUpTileUpdate()

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

        setupRadioGroups(binding)
        setupSwitches(binding)
        setupButtons(binding)
        setupEditTexts(binding)
        setupDNSSection(binding)
        setupDNSButtons(binding)
    }

    private fun setupRadioGroups(binding: ActivityQsTileSettingsBinding) {
        binding.wifiTileRadioGroup.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
        }

        binding.mobileTileRadioGroup.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
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
        val dns1 = dnsManager.getDNSOption(1)
        val dns2 = dnsManager.getDNSOption(2)
        val dns3 = dnsManager.getDNSOption(3)

        binding.dns1NameEditText.setText(dns1.name)
        binding.dns1UrlEditText.setText(dns1.url)
        binding.dns2NameEditText.setText(dns2.name)
        binding.dns2UrlEditText.setText(dns2.url)
        binding.dns3NameEditText.setText(dns3.name)
        binding.dns3UrlEditText.setText(dns3.url)

        if (dns2.isValid() && dns2.url.isNotEmpty()) {
            binding.dns2Container.visibility = android.view.View.VISIBLE
        }
        if (dns3.isValid() && dns3.url.isNotEmpty()) {
            binding.dns3Container.visibility = android.view.View.VISIBLE
        }

        updateDNSAddButtonVisibility()
    }

    private fun setupDNSButtons(binding: ActivityQsTileSettingsBinding) {
        binding.dnsAddButton.setOnClickListener {
            addNextDNSEntry()
        }

        binding.dnsRemove2Button.setOnClickListener {
            binding.dns2Container.visibility = android.view.View.GONE
            binding.dns2NameEditText.setText("")
            binding.dns2UrlEditText.setText("")
            updateDNSAddButtonVisibility()
        }

        binding.dnsRemove3Button.setOnClickListener {
            binding.dns3Container.visibility = android.view.View.GONE
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
        val allVisible = binding.dns2Container.isVisible && binding.dns3Container.isVisible
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

                    if (lastUsageStats && !currentUsageStats) showToast(getString(R.string.data_tiles_disabled))
                    if (lastWriteSettings && !currentWriteSettings) showToast(getString(R.string.screen_timeout_disabled))
                    if (lastSecureSettings && !currentSecureSettings) showToast(getString(R.string.advanced_tiles_disabled))
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

        binding.dataTilesPermissionText.visibility =
            if (!hasUsageStats) android.view.View.VISIBLE else android.view.View.GONE
        binding.screenTimeoutPermissionText.visibility =
            if (!hasWriteSettings) android.view.View.VISIBLE else android.view.View.GONE
        binding.advancedTilesPermissionText.visibility =
            if (!hasSecureSettings) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun updateFeatureControls() {
        val binding = binding ?: return
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()
        val hasWriteSettings = permissionHelper.hasWriteSettingsPermission()
        val hasSecureSettings = dnsManager.hasSecureSettingsPermission()

        // Data usage tile controls
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

        // Screen timeout controls
        binding.showScreenTimeoutInTitleSwitch.isEnabled = hasWriteSettings
        binding.showScreenTimeoutInTitleSwitch.alpha = if (hasWriteSettings) 1.0f else 0.5f

        // Advanced tiles controls (all require WRITE_SECURE_SETTINGS)
        val advancedAlpha = if (hasSecureSettings) 1.0f else 0.5f

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
        binding.headsUpShowHeadingSwitch.isEnabled = hasSecureSettings

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
        binding.headsUpShowHeadingSwitch.alpha = advancedAlpha

        // Battery controls are always enabled
        binding.showChargeInTitleSwitch.isEnabled = true
        binding.showBatteryHealthInTitleSwitch.isEnabled = true
        binding.designCapacityEditText.isEnabled = true
    }

    private fun saveDNSSettings(binding: ActivityQsTileSettingsBinding) {
        dnsManager.saveDNSOption(
            1,
            binding.dns1NameEditText.text.toString().trim(),
            binding.dns1UrlEditText.text.toString().trim()
        )

        if (binding.dns2Container.isVisible) {
            dnsManager.saveDNSOption(
                2,
                binding.dns2NameEditText.text.toString().trim(),
                binding.dns2UrlEditText.text.toString().trim()
            )
        } else {
            dnsManager.saveDNSOption(2, "", "")
        }

        if (binding.dns3Container.isVisible) {
            dnsManager.saveDNSOption(
                3,
                binding.dns3NameEditText.text.toString().trim(),
                binding.dns3UrlEditText.text.toString().trim()
            )
        } else {
            dnsManager.saveDNSOption(3, "", "")
        }

        val hasValidDNS = dnsManager.getAllDNSOptions().any { !it.isOff() && !it.isAuto() && it.isValid() }
        dnsManager.setDNSEnabled(hasValidDNS)
    }

    private fun saveTorchGlyphSettings(binding: ActivityQsTileSettingsBinding) {
        val hasValidTorchGlyph = torchGlyphManager.hasRequiredPermissions()
        torchGlyphManager.setTorchGlyphEnabled(hasValidTorchGlyph)
    }

    private fun saveRefreshRateSettings(binding: ActivityQsTileSettingsBinding) {
        val hasValidRefreshRate = refreshRateManager.hasRequiredPermissions()
        refreshRateManager.setRefreshRateEnabled(hasValidRefreshRate)
    }

    private fun saveAODSettings(binding: ActivityQsTileSettingsBinding) {
        val hasValidAOD = aodManager.hasRequiredPermissions()
        aodManager.setAODEnabled(hasValidAOD)
    }

    private fun triggerImmediateTileUpdates() {
        lifecycleScope.launch {
            try {
                sendBroadcast(Intent(DataUsageWorker.ACTION_DATA_UPDATED).apply { setPackage(packageName) })
                sendBroadcast(Intent(BatteryWorker.ACTION_BATTERY_UPDATED).apply { setPackage(packageName) })
                sendBroadcast(Intent(BatteryHealthQSTileService.ACTION_BATTERY_HEALTH_UPDATED).apply { setPackage(packageName) })
            } catch (e: Exception) {
                android.util.Log.e("QSTileSettings", "Error sending tile update broadcasts", e)
            }
        }
    }

    private fun triggerDNSTileUpdate() {
        lifecycleScope.launch {
            try {
                sendBroadcast(Intent("com.redskul.macrostatshelper.DNS_SETTINGS_UPDATED").apply { setPackage(packageName) })
            } catch (e: Exception) {
                android.util.Log.e("QSTileSettings", "Error sending DNS tile update broadcast", e)
            }
        }
    }

    private fun triggerTorchGlyphTileUpdate() {
        lifecycleScope.launch {
            try {
                sendBroadcast(Intent(TorchGlyphQSTileService.ACTION_TORCH_GLYPH_SETTINGS_UPDATED).apply { setPackage(packageName) })
            } catch (e: Exception) {
                android.util.Log.e("QSTileSettings", "Error sending Torch/Glyph tile update broadcast", e)
            }
        }
    }

    private fun triggerRefreshRateTileUpdate() {
        lifecycleScope.launch {
            try {
                sendBroadcast(Intent(RefreshRateQSTileService.ACTION_REFRESH_RATE_SETTINGS_UPDATED).apply { setPackage(packageName) })
            } catch (e: Exception) {
                android.util.Log.e("QSTileSettings", "Error sending Refresh Rate tile update broadcast", e)
            }
        }
    }

    private fun triggerAODTileUpdate() {
        lifecycleScope.launch {
            try {
                sendBroadcast(Intent(AODQSTileService.ACTION_AOD_SETTINGS_UPDATED).apply { setPackage(packageName) })
            } catch (e: Exception) {
                android.util.Log.e("QSTileSettings", "Error sending AOD tile update broadcast", e)
            }
        }
    }

    private fun triggerHeadsUpTileUpdate() {
        lifecycleScope.launch {
            try {
                sendBroadcast(Intent(HeadsUpQSTileService.ACTION_HEADS_UP_SETTINGS_UPDATED).apply { setPackage(packageName) })
            } catch (e: Exception) {
                android.util.Log.e("QSTileSettings", "Error sending Heads-Up tile update broadcast", e)
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}