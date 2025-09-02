package com.redskul.macrostatshelper.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import android.widget.AdapterView
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
import com.redskul.macrostatshelper.battery.BatteryHealthQSTileService
import com.redskul.macrostatshelper.battery.BatteryWorker
import com.redskul.macrostatshelper.datausage.DataUsageWorker
import com.redskul.macrostatshelper.dns.DNSManager
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
    private lateinit var dnsManager: DNSManager
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

        // Setup EditTexts
        setupEditTexts(binding)

        // Setup DNS section
        setupDNSSection(binding)

        // Setup DNS buttons
        setupDNSButtons(binding)
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
            triggerImmediateTileUpdates()
        }

        binding.showScreenTimeoutInTitleSwitch.setOnCheckedChangeListener { switch, isChecked ->
            switch.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (isChecked && !permissionHelper.hasWriteSettingsPermission()) {
                binding.showScreenTimeoutInTitleSwitch.isChecked = false
                showPermissionRequiredDialog(getString(R.string.screen_timeout_tile_label), getString(R.string.permission_write_settings))
                return@setOnCheckedChangeListener
            }
            triggerImmediateTileUpdates()
        }

        // Add haptic feedback to other switches and immediate tile updates
        binding.showChargeInTitleSwitch.setOnCheckedChangeListener { switch, _ ->
            switch.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            triggerImmediateTileUpdates()
        }
        binding.showBatteryHealthInTitleSwitch.setOnCheckedChangeListener { switch, _ ->
            switch.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            triggerImmediateTileUpdates()
        }

        // DNS heading switch
        binding.dnsShowHeadingSwitch.setOnCheckedChangeListener { switch, _ ->
            switch.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            triggerDNSTileUpdate()
        }
    }

    private fun setupButtons(binding: ActivityQsTileSettingsBinding) {
        binding.qsUsageAccessButton.setOnClickListener {
            requestUsageStatsPermission()
        }

        binding.writeSettingsButton.setOnClickListener {
            requestWriteSettingsPermission()
        }

        binding.dnsPermissionButton.setOnClickListener {
            showDNSPermissionDialog()
        }

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

        binding.dnsShowHeadingSwitch.isChecked = dnsManager.getShowHeading()

        // Show DNS 2 and 3 if they have data
        if (dns2.isValid() && dns2.url.isNotEmpty()) {
            binding.dns2Container.visibility = View.VISIBLE
        }
        if (dns3.isValid() && dns3.url.isNotEmpty()) {
            binding.dns3Container.visibility = View.VISIBLE
        }

        updateDNSAddButtonVisibility()
        updateDNSPermissionStatus()
    }

    private fun setupDNSButtons(binding: ActivityQsTileSettingsBinding) {
        // Main Add DNS button
        binding.dnsAddButton.setOnClickListener {
            addNextDNSEntry()
        }

        // Remove DNS 2 button
        binding.dnsRemove2Button.setOnClickListener {
            binding.dns2Container.visibility = View.GONE
            // Clear the fields
            binding.dns2NameEditText.setText("")
            binding.dns2UrlEditText.setText("")
            updateDNSAddButtonVisibility()
        }

        // Remove DNS 3 button
        binding.dnsRemove3Button.setOnClickListener {
            binding.dns3Container.visibility = View.GONE
            // Clear the fields
            binding.dns3NameEditText.setText("")
            binding.dns3UrlEditText.setText("")
            updateDNSAddButtonVisibility()
        }
    }

    private fun addNextDNSEntry() {
        val binding = binding ?: return

        when {
            binding.dns2Container.visibility == View.GONE -> {
                binding.dns2Container.visibility = View.VISIBLE
            }
            binding.dns3Container.visibility == View.GONE -> {
                binding.dns3Container.visibility = View.VISIBLE
            }
        }
        updateDNSAddButtonVisibility()
    }

    private fun updateDNSAddButtonVisibility() {
        val binding = binding ?: return

        // Hide add button if all 3 DNS entries are visible
        val allVisible = binding.dns2Container.visibility == View.VISIBLE &&
                binding.dns3Container.visibility == View.VISIBLE
        binding.dnsAddButton.visibility = if (allVisible) View.GONE else View.VISIBLE
    }

    private fun showDNSPermissionDialog() {
        val command = dnsManager.getADBCommand()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.secure_settings_permission_title))
            .setMessage(getString(R.string.dns_permission_message))
            .setView(createCommandView(command))
            .setPositiveButton(getString(R.string.copy_command)) { _, _ ->
                copyCommandToClipboard(command)
            }
            .setNegativeButton(getString(R.string.cancel_button)) { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton(getString(R.string.learn_more)) { _, _ ->
                showADBInstructions()
            }
            .show()
    }

    private fun createCommandView(command: String): android.view.View {
        val textView = android.widget.TextView(this).apply {
            text = command
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundResource(android.R.drawable.editbox_background)
            setPadding(16, 16, 16, 16)
        }
        return textView
    }

    private fun copyCommandToClipboard(command: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("ADB Command", command)
        clipboard.setPrimaryClip(clip)
        showToast(getString(R.string.command_copied))
    }

    private fun showADBInstructions() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.adb_instructions_title))
            .setMessage(getString(R.string.adb_instructions_message))
            .setPositiveButton(getString(R.string.ok_button)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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

                if (lastUsageStats != currentUsageStats || lastWriteSettings != currentWriteSettings || lastSecureSettings != currentSecureSettings) {
                    updatePermissionBasedUI()
                    updatePermissionStatuses()
                    updateDNSPermissionStatus()

                    if (lastUsageStats && !currentUsageStats) {
                        showToast(getString(R.string.data_tiles_disabled))
                    }
                    if (lastWriteSettings && !currentWriteSettings) {
                        showToast(getString(R.string.screen_timeout_disabled))
                    }
                    if (lastSecureSettings && !currentSecureSettings) {
                        showToast("DNS tile disabled")
                    }
                }

                lastUsageStats = currentUsageStats
                lastWriteSettings = currentWriteSettings
                lastSecureSettings = currentSecureSettings
            }
        }
    }

    private fun updatePermissionBasedUI() {
        val binding = binding ?: return
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()
        val hasWriteSettings = permissionHelper.hasWriteSettingsPermission()
        val hasSecureSettings = dnsManager.hasSecureSettingsPermission()

        // Enable/disable data usage related controls
        binding.wifiTileSpinner.isEnabled = hasUsageStats
        binding.mobileTileSpinner.isEnabled = hasUsageStats
        binding.showPeriodInTitleSwitch.isEnabled = hasUsageStats

        // Enable/disable screen timeout controls
        binding.showScreenTimeoutInTitleSwitch.isEnabled = hasWriteSettings

        // Enable/disable DNS controls
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

    private fun updateDNSPermissionStatus() {
        val binding = binding ?: return
        val hasPermission = dnsManager.hasSecureSettingsPermission()

        binding.dnsPermissionStatusText.text = if (hasPermission) {
            "✓ WRITE_SECURE_SETTINGS permission granted\n${dnsManager.getCurrentDNSStatusText()}"
        } else {
            "⚠ WRITE_SECURE_SETTINGS permission required for DNS management"
        }

        binding.dnsPermissionStatusText.setTextColor(
            if (hasPermission) 0xFF4CAF50.toInt() else 0xFFFF5722.toInt()
        )

        binding.dnsPermissionButton.text = if (hasPermission) {
            getString(R.string.permission_granted_check)
        } else {
            getString(R.string.grant_secure_settings_permission)
        }

        binding.dnsPermissionButton.isEnabled = !hasPermission
        binding.dnsPermissionButton.alpha = if (hasPermission) 0.7f else 1.0f
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
        updateDNSPermissionStatus()
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

        // Save all existing settings
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

        // Save DNS settings
        saveDNSSettings(binding)

        // Trigger immediate tile updates after saving all settings
        triggerImmediateTileUpdates()
        triggerDNSTileUpdate()

        Toast.makeText(this, getString(R.string.qs_settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun saveDNSSettings(binding: ActivityQsTileSettingsBinding) {
        // Save DNS options
        dnsManager.saveDNSOption(
            1,
            binding.dns1NameEditText.text.toString().trim(),
            binding.dns1UrlEditText.text.toString().trim()
        )

        // Only save DNS 2 if container is visible
        if (binding.dns2Container.visibility == View.VISIBLE) {
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
        if (binding.dns3Container.visibility == View.VISIBLE) {
            dnsManager.saveDNSOption(
                3,
                binding.dns3NameEditText.text.toString().trim(),
                binding.dns3UrlEditText.text.toString().trim()
            )
        } else {
            // Clear DNS 3 if hidden
            dnsManager.saveDNSOption(3, "", "")
        }

        // Save DNS heading preference
        dnsManager.setShowHeading(binding.dnsShowHeadingSwitch.isChecked)

        // Enable DNS tile if any DNS options are configured
        val hasValidDNS = dnsManager.getAllDNSOptions().any { !it.isOff() && !it.isAuto() && it.isValid() }
        dnsManager.setDNSEnabled(hasValidDNS)

        android.util.Log.d("QSTileSettings", "DNS settings saved. DNS tile enabled: $hasValidDNS")
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
