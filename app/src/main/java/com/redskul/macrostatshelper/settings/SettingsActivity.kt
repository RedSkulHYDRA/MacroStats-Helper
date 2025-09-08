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
import com.redskul.macrostatshelper.datausage.DataUsageMonitor
import com.redskul.macrostatshelper.notification.NotificationHelper
import com.redskul.macrostatshelper.utils.WorkManagerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var dataUsageMonitor: DataUsageMonitor
    private lateinit var workManagerRepository: WorkManagerRepository
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
        notificationHelper = NotificationHelper(this)
        dataUsageMonitor = DataUsageMonitor(this)
        workManagerRepository = WorkManagerRepository(this)

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

        // Setup historical data checkboxes with haptic feedback AND mutual exclusivity
        binding.lastMonthCheckbox.setOnCheckedChangeListener { _, isChecked ->
            binding.lastMonthCheckbox.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (isChecked) {
                // Uncheck other historical data options
                binding.lastWeekCheckbox.isChecked = false
                binding.yesterdayCheckbox.isChecked = false
            }
        }

        binding.lastWeekCheckbox.setOnCheckedChangeListener { _, isChecked ->
            binding.lastWeekCheckbox.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (isChecked) {
                // Uncheck other historical data options
                binding.lastMonthCheckbox.isChecked = false
                binding.yesterdayCheckbox.isChecked = false
            }
        }

        binding.yesterdayCheckbox.setOnCheckedChangeListener { _, isChecked ->
            binding.yesterdayCheckbox.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (isChecked) {
                // Uncheck other historical data options
                binding.lastMonthCheckbox.isChecked = false
                binding.lastWeekCheckbox.isChecked = false
            }
        }

        // Setup WiFi checkboxes with haptic feedback
        binding.wifiDailyCheckbox.setOnCheckedChangeListener { _, _ ->
            binding.wifiDailyCheckbox.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        binding.wifiWeeklyCheckbox.setOnCheckedChangeListener { _, _ ->
            binding.wifiWeeklyCheckbox.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        binding.wifiMonthlyCheckbox.setOnCheckedChangeListener { _, _ ->
            binding.wifiMonthlyCheckbox.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        // Setup Mobile checkboxes with haptic feedback
        binding.mobileDailyCheckbox.setOnCheckedChangeListener { _, _ ->
            binding.mobileDailyCheckbox.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        binding.mobileWeeklyCheckbox.setOnCheckedChangeListener { _, _ ->
            binding.mobileWeeklyCheckbox.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }

        binding.mobileMonthlyCheckbox.setOnCheckedChangeListener { _, _ ->
            binding.mobileMonthlyCheckbox.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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

        // Historical data checkboxes don't require permissions, but are dependent on notification being enabled
        binding.lastMonthCheckbox.isEnabled = hasUsageStats && binding.notificationEnabledSwitch.isChecked
        binding.lastWeekCheckbox.isEnabled = hasUsageStats && binding.notificationEnabledSwitch.isChecked
        binding.yesterdayCheckbox.isEnabled = hasUsageStats && binding.notificationEnabledSwitch.isChecked
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

        // Set notification switch (moved to top)
        binding.notificationEnabledSwitch.isChecked = settingsManager.isNotificationEnabled()

        // Set historical data checkboxes (mutually exclusive - only one can be true)
        val showLastMonth = settingsManager.isShowLastMonthUsageEnabled()
        val showLastWeek = settingsManager.isShowLastWeekUsageEnabled()
        val showYesterday = settingsManager.isShowYesterdayUsageEnabled()

        // Ensure only one is selected (priority: Last Month > Last Week > Yesterday)
        when {
            showLastMonth -> {
                binding.lastMonthCheckbox.isChecked = true
                binding.lastWeekCheckbox.isChecked = false
                binding.yesterdayCheckbox.isChecked = false
            }
            showLastWeek -> {
                binding.lastMonthCheckbox.isChecked = false
                binding.lastWeekCheckbox.isChecked = true
                binding.yesterdayCheckbox.isChecked = false
            }
            showYesterday -> {
                binding.lastMonthCheckbox.isChecked = false
                binding.lastWeekCheckbox.isChecked = false
                binding.yesterdayCheckbox.isChecked = true
            }
            else -> {
                // None selected
                binding.lastMonthCheckbox.isChecked = false
                binding.lastWeekCheckbox.isChecked = false
                binding.yesterdayCheckbox.isChecked = false
            }
        }

        // Set WiFi checkboxes
        binding.wifiDailyCheckbox.isChecked = settings.wifiTimePeriods.contains(TimePeriod.DAILY)
        binding.wifiWeeklyCheckbox.isChecked = settings.wifiTimePeriods.contains(TimePeriod.WEEKLY)
        binding.wifiMonthlyCheckbox.isChecked = settings.wifiTimePeriods.contains(TimePeriod.MONTHLY)

        // Set Mobile checkboxes
        binding.mobileDailyCheckbox.isChecked = settings.mobileTimePeriods.contains(TimePeriod.DAILY)
        binding.mobileWeeklyCheckbox.isChecked = settings.mobileTimePeriods.contains(TimePeriod.WEEKLY)
        binding.mobileMonthlyCheckbox.isChecked = settings.mobileTimePeriods.contains(TimePeriod.MONTHLY)

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

        // Handle notification setting changes immediately
        val wasNotificationEnabled = settingsManager.isNotificationEnabled()
        val isNotificationEnabled = binding.notificationEnabledSwitch.isChecked

        // Save notification preference (will be validated by SettingsManager)
        settingsManager.saveNotificationEnabled(isNotificationEnabled)

        // Save historical data preferences (mutually exclusive)
        // Only save true for the checked one, false for others
        settingsManager.saveShowLastMonthUsage(binding.lastMonthCheckbox.isChecked)
        settingsManager.saveShowLastWeekUsage(binding.lastWeekCheckbox.isChecked)
        settingsManager.saveShowYesterdayUsage(binding.yesterdayCheckbox.isChecked)

        // Handle immediate notification changes
        if (isNotificationEnabled && !wasNotificationEnabled) {
            // Notification was just enabled - show immediately
            lifecycleScope.launch {
                try {
                    val usageData = dataUsageMonitor.getUsageData()
                    notificationHelper.showUsageNotification(usageData)
                    android.util.Log.d("SettingsActivity", "Notification shown immediately after enabling")
                } catch (e: Exception) {
                    android.util.Log.e("SettingsActivity", "Error showing immediate notification", e)
                }
            }
        } else if (!isNotificationEnabled && wasNotificationEnabled) {
            // Notification was just disabled - hide immediately
            notificationHelper.cancelNotification()
            android.util.Log.d("SettingsActivity", "Notification hidden immediately after disabling")
        } else if (isNotificationEnabled) {
            // Notification is enabled and was already enabled - update to reflect checkbox changes
            lifecycleScope.launch {
                try {
                    val usageData = dataUsageMonitor.getUsageData()
                    notificationHelper.showUsageNotification(usageData)
                    android.util.Log.d("SettingsActivity", "Notification updated to reflect historical data checkbox changes")
                } catch (e: Exception) {
                    android.util.Log.e("SettingsActivity", "Error updating notification", e)
                }
            }
        }

        // Trigger immediate data update to refresh everything
        workManagerRepository.triggerImmediateDataUpdate()

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
