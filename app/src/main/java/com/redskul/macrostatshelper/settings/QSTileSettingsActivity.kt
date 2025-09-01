package com.redskul.macrostatshelper.settings

import android.os.Bundle
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.net.toUri
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.battery.BatteryHealthMonitor
import com.redskul.macrostatshelper.utils.PermissionHelper
import com.redskul.macrostatshelper.databinding.ActivityQsTileSettingsBinding
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QSTileSettingsActivity : AppCompatActivity() {

    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private lateinit var batteryHealthMonitor: BatteryHealthMonitor
    private lateinit var permissionHelper: PermissionHelper
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

        setupWindowInsets()
        setupUI()
        loadCurrentSettings()
        startPermissionMonitoring()
    }

    override fun onDestroy() {
        super.onDestroy()
        binding = null
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

        // Setup spinners
        setupSpinners(binding)

        // Setup switches
        setupSwitches(binding)

        // Setup buttons
        setupButtons(binding)

        // Setup EditText
        setupEditText(binding)
    }

    private fun setupSpinners(binding: ActivityQsTileSettingsBinding) {
        val periodOptions = listOf(getString(R.string.daily), getString(R.string.weekly), getString(R.string.monthly))

        // WiFi spinner
        binding.wifiTileSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            periodOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        // Mobile spinner
        binding.mobileTileSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            periodOptions
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private fun setupSwitches(binding: ActivityQsTileSettingsBinding) {
        // Setup permission-required switches
        binding.showPeriodInTitleSwitch.setOnCheckedChangeListener { switch, isChecked ->
            switch.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (isChecked && !permissionHelper.hasUsageStatsPermission()) {
                binding.showPeriodInTitleSwitch.isChecked = false
                showPermissionRequiredDialog(getString(R.string.data_usage_tiles_title), getString(R.string.permission_usage_stats))
                return@setOnCheckedChangeListener
            }
        }

        binding.showScreenTimeoutInTitleSwitch.setOnCheckedChangeListener { switch, isChecked ->
            switch.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (isChecked && !permissionHelper.hasWriteSettingsPermission()) {
                binding.showScreenTimeoutInTitleSwitch.isChecked = false
                showPermissionRequiredDialog(getString(R.string.screen_timeout_tile_label), getString(R.string.permission_write_settings))
                return@setOnCheckedChangeListener
            }
        }

        // Add haptic feedback to other switches
        binding.showChargeInTitleSwitch.setOnCheckedChangeListener { switch, _ ->
            switch.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        binding.showBatteryHealthInTitleSwitch.setOnCheckedChangeListener { switch, _ ->
            switch.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private fun setupButtons(binding: ActivityQsTileSettingsBinding) {
        binding.qsUsageAccessButton.setOnClickListener {
            requestUsageStatsPermission()
        }

        binding.writeSettingsButton.setOnClickListener {
            requestWriteSettingsPermission()
        }

        binding.qsSaveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun setupEditText(binding: ActivityQsTileSettingsBinding) {
        binding.designCapacityEditText.inputType = InputType.TYPE_CLASS_NUMBER
    }

    private fun startPermissionMonitoring() {
        lifecycleScope.launch {
            var lastUsageStats = permissionHelper.hasUsageStatsPermission()
            var lastWriteSettings = permissionHelper.hasWriteSettingsPermission()

            while (binding != null) {
                delay(1000)

                val currentUsageStats = permissionHelper.hasUsageStatsPermission()
                val currentWriteSettings = permissionHelper.hasWriteSettingsPermission()

                if (lastUsageStats != currentUsageStats || lastWriteSettings != currentWriteSettings) {
                    updatePermissionBasedUI()
                    updatePermissionStatuses()

                    if (lastUsageStats && !currentUsageStats) {
                        showToast(getString(R.string.data_tiles_disabled))
                    }
                    if (lastWriteSettings && !currentWriteSettings) {
                        showToast(getString(R.string.screen_timeout_disabled))
                    }
                }

                lastUsageStats = currentUsageStats
                lastWriteSettings = currentWriteSettings
            }
        }
    }

    private fun updatePermissionBasedUI() {
        val binding = binding ?: return
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()
        val hasWriteSettings = permissionHelper.hasWriteSettingsPermission()

        // Enable/disable data usage related controls
        binding.wifiTileSpinner.isEnabled = hasUsageStats
        binding.mobileTileSpinner.isEnabled = hasUsageStats
        binding.showPeriodInTitleSwitch.isEnabled = hasUsageStats

        // Enable/disable screen timeout controls
        binding.showScreenTimeoutInTitleSwitch.isEnabled = hasWriteSettings
    }

    private fun updatePermissionStatuses() {
        val binding = binding ?: return

        // Update usage access permission status
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()
        binding.usageAccessStatusText.text = if (hasUsageStats) {
            getString(R.string.usage_access_permission_enabled)
        } else {
            getString(R.string.usage_access_permission_disabled)
        }
        binding.usageAccessStatusText.setTextColor(
            if (hasUsageStats) 0xFF4CAF50.toInt() else 0xFFFF5722.toInt()
        )

        // Update write settings permission status
        val hasWriteSettings = permissionHelper.hasWriteSettingsPermission()
        binding.writeSettingsStatusText.text = if (hasWriteSettings) {
            getString(R.string.write_settings_permission_enabled)
        } else {
            getString(R.string.write_settings_permission_disabled)
        }
        binding.writeSettingsStatusText.setTextColor(
            if (hasWriteSettings) 0xFF4CAF50.toInt() else 0xFFFF5722.toInt()
        )
    }

    private fun loadCurrentSettings() {
        val binding = binding ?: return

        val wifiPeriod = qsTileSettingsManager.getWiFiTilePeriod()
        val mobilePeriod = qsTileSettingsManager.getMobileTilePeriod()
        val showPeriodInTitle = qsTileSettingsManager.getShowPeriodInTitle()
        val showChargeInTitle = qsTileSettingsManager.getShowChargeInTitle()
        val showHealthInTitle = qsTileSettingsManager.getShowBatteryHealthInTitle()
        val showScreenTimeoutInTitle = qsTileSettingsManager.getShowScreenTimeoutInTitle()
        val designCapacity = qsTileSettingsManager.getBatteryDesignCapacity()

        // Set spinner selections
        binding.wifiTileSpinner.setSelection(when (wifiPeriod) {
            TimePeriod.DAILY -> 0
            TimePeriod.WEEKLY -> 1
            TimePeriod.MONTHLY -> 2
        })

        binding.mobileTileSpinner.setSelection(when (mobilePeriod) {
            TimePeriod.DAILY -> 0
            TimePeriod.WEEKLY -> 1
            TimePeriod.MONTHLY -> 2
        })

        // Set switch states
        binding.showPeriodInTitleSwitch.isChecked = showPeriodInTitle
        binding.showChargeInTitleSwitch.isChecked = showChargeInTitle
        binding.showBatteryHealthInTitleSwitch.isChecked = showHealthInTitle
        binding.showScreenTimeoutInTitleSwitch.isChecked = showScreenTimeoutInTitle

        // Set design capacity
        if (designCapacity > 0) {
            binding.designCapacityEditText.setText(designCapacity.toString())
        }

        updatePermissionBasedUI()
        updatePermissionStatuses()
    }

    private fun saveSettings() {
        val binding = binding ?: return

        // Get spinner selections
        val wifiPeriod = when (binding.wifiTileSpinner.selectedItemPosition) {
            0 -> TimePeriod.DAILY
            1 -> TimePeriod.WEEKLY
            2 -> TimePeriod.MONTHLY
            else -> TimePeriod.DAILY
        }

        val mobilePeriod = when (binding.mobileTileSpinner.selectedItemPosition) {
            0 -> TimePeriod.DAILY
            1 -> TimePeriod.WEEKLY
            2 -> TimePeriod.MONTHLY
            else -> TimePeriod.DAILY
        }

        // Get design capacity
        val designCapacity = binding.designCapacityEditText.text.toString().toIntOrNull() ?: 0

        // Save all settings
        qsTileSettingsManager.saveWiFiTilePeriod(wifiPeriod)
        qsTileSettingsManager.saveMobileTilePeriod(mobilePeriod)
        qsTileSettingsManager.saveShowPeriodInTitle(binding.showPeriodInTitleSwitch.isChecked)
        qsTileSettingsManager.saveShowChargeInTitle(binding.showChargeInTitleSwitch.isChecked)
        qsTileSettingsManager.saveShowBatteryHealthInTitle(binding.showBatteryHealthInTitleSwitch.isChecked)
        qsTileSettingsManager.saveShowScreenTimeoutInTitle(binding.showScreenTimeoutInTitleSwitch.isChecked)

        if (designCapacity > 0) {
            qsTileSettingsManager.saveBatteryDesignCapacity(designCapacity)
            batteryHealthMonitor.setDesignCapacity(designCapacity)
        }

        Toast.makeText(this, getString(R.string.qs_settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showPermissionRequiredDialog(featureName: String, permissionName: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_required_title))
            .setMessage(getString(R.string.permission_required_settings_dialog_message, featureName, permissionName))
            .setPositiveButton(getString(R.string.ok_button)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
        showToast(getString(R.string.enable_usage_access_helper))
    }

    private fun requestWriteSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = "package:$packageName".toUri()
        }
        startActivity(intent)
        showToast(getString(R.string.enable_write_settings_helper))
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
