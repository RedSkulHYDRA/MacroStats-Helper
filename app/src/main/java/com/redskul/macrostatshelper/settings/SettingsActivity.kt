package com.redskul.macrostatshelper.settings

import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.utils.PermissionHelper
import com.redskul.macrostatshelper.databinding.ActivitySettingsBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var permissionHelper: PermissionHelper
    private var binding: ActivitySettingsBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Initialize binding
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding!!.root)

        // Initialize managers
        settingsManager = SettingsManager(this)
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

    override fun onResume() {
        super.onResume()
        updatePermissionBasedUI()
        updatePermissionStatus()
    }

    private fun setupWindowInsets() {
        val binding = binding ?: return

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            val settingsPadding = resources.getDimensionPixelSize(R.dimen.padding_settings)
            binding.settingsLayout.setPadding(
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

        // Setup notification switch
        binding.notificationEnabledSwitch.setOnCheckedChangeListener { switch, isChecked ->
            // Add haptic feedback
            switch.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            if (isChecked && !permissionHelper.hasUsageStatsPermission()) {
                binding.notificationEnabledSwitch.isChecked = false
                showPermissionRequiredDialog(
                    getString(R.string.show_data_usage_notification),
                    getString(R.string.permission_usage_stats)
                ) {
                    requestUsageStatsPermission()
                }
                return@setOnCheckedChangeListener
            }
        }

        // Setup usage access button
        binding.usageAccessButton.setOnClickListener {
            requestUsageStatsPermission()
        }

        // Setup save button
        binding.saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun startPermissionMonitoring() {
        lifecycleScope.launch {
            var lastUsageStats = permissionHelper.hasUsageStatsPermission()

            while (binding != null) {
                delay(1000)

                val currentUsageStats = permissionHelper.hasUsageStatsPermission()

                if (lastUsageStats != currentUsageStats) {
                    updatePermissionBasedUI()
                    updatePermissionStatus()

                    if (lastUsageStats && !currentUsageStats) {
                        binding?.notificationEnabledSwitch?.isChecked = false
                        showToast(getString(R.string.data_tiles_disabled))
                    }
                }

                lastUsageStats = currentUsageStats
            }
        }
    }

    private fun updatePermissionBasedUI() {
        val binding = binding ?: return
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()

        // Enable/disable notification switch
        binding.notificationEnabledSwitch.isEnabled = hasUsageStats
        if (!hasUsageStats && binding.notificationEnabledSwitch.isChecked) {
            binding.notificationEnabledSwitch.isChecked = false
        }
    }

    private fun updatePermissionStatus() {
        val binding = binding ?: return
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()

        binding.notificationPermissionStatusText.text = if (hasUsageStats) {
            getString(R.string.usage_access_permission_enabled)
        } else {
            getString(R.string.usage_access_permission_disabled)
        }

        binding.notificationPermissionStatusText.setTextColor(
            if (hasUsageStats) 0xFF4CAF50.toInt() else 0xFFFF5722.toInt()
        )
    }

    private fun loadCurrentSettings() {
        val binding = binding ?: return
        val settings = settingsManager.getDisplaySettings()

        // Set WiFi checkboxes
        binding.wifiDailyCheckbox.isChecked = settings.wifiTimePeriods.contains(TimePeriod.DAILY)
        binding.wifiWeeklyCheckbox.isChecked = settings.wifiTimePeriods.contains(TimePeriod.WEEKLY)
        binding.wifiMonthlyCheckbox.isChecked = settings.wifiTimePeriods.contains(TimePeriod.MONTHLY)

        // Set Mobile checkboxes
        binding.mobileDailyCheckbox.isChecked = settings.mobileTimePeriods.contains(TimePeriod.DAILY)
        binding.mobileWeeklyCheckbox.isChecked = settings.mobileTimePeriods.contains(TimePeriod.WEEKLY)
        binding.mobileMonthlyCheckbox.isChecked = settings.mobileTimePeriods.contains(TimePeriod.MONTHLY)

        // Set notification switch
        binding.notificationEnabledSwitch.isChecked = settingsManager.isNotificationEnabled()

        updatePermissionBasedUI()
        updatePermissionStatus()
    }

    private fun saveSettings() {
        val binding = binding ?: return

        // Collect WiFi periods
        val wifiPeriods = mutableListOf<TimePeriod>()
        if (binding.wifiDailyCheckbox.isChecked) wifiPeriods.add(TimePeriod.DAILY)
        if (binding.wifiWeeklyCheckbox.isChecked) wifiPeriods.add(TimePeriod.WEEKLY)
        if (binding.wifiMonthlyCheckbox.isChecked) wifiPeriods.add(TimePeriod.MONTHLY)

        // Collect Mobile periods
        val mobilePeriods = mutableListOf<TimePeriod>()
        if (binding.mobileDailyCheckbox.isChecked) mobilePeriods.add(TimePeriod.DAILY)
        if (binding.mobileWeeklyCheckbox.isChecked) mobilePeriods.add(TimePeriod.WEEKLY)
        if (binding.mobileMonthlyCheckbox.isChecked) mobilePeriods.add(TimePeriod.MONTHLY)

        // Save display settings
        val settings = DisplaySettings(wifiPeriods, mobilePeriods)
        settingsManager.saveDisplaySettings(settings)

        // Save notification preference (will be validated by SettingsManager)
        settingsManager.saveNotificationEnabled(binding.notificationEnabledSwitch.isChecked)

        // REMOVED: DataUsageService intent - WorkManager handles notification changes automatically

        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showPermissionRequiredDialog(featureName: String, permissionName: String, onPositive: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_required_title))
            .setMessage(getString(R.string.permission_required_dialog_message, featureName, permissionName))
            .setPositiveButton(getString(R.string.grant_button)) { _, _ -> onPositive() }
            .setNegativeButton(getString(R.string.cancel_button)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
        showToast(getString(R.string.enable_usage_access_helper))
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
