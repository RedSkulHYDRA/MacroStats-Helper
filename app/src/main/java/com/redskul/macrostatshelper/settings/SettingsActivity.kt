package com.redskul.macrostatshelper.settings

import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.utils.PermissionHelper
import com.redskul.macrostatshelper.utils.VibrationManager
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
    private lateinit var vibrationManager: VibrationManager
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
            // Add haptic feedback - always enabled for app interactions
            vibrationManager.vibrateOnAppInteraction()

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

            // Update historical data card visibility based on notification switch
            updateHistoricalDataCardState()
        }

        // Setup historical data master switch
        binding.historicalDataMasterSwitch.setOnCheckedChangeListener { switch, isChecked ->
            // Add haptic feedback - always enabled for app interactions
            vibrationManager.vibrateOnAppInteraction()

            // Enable/disable radio buttons based on master switch
            updateRadioButtonsState(isChecked)

            // If turning off, clear radio button selection
            if (!isChecked) {
                binding.historicalDataRadioGroup.clearCheck()
            }
        }

        // Setup historical data radio buttons with haptic feedback
        binding.historicalDataRadioGroup.setOnCheckedChangeListener { _, _ ->
            // Add haptic feedback - always enabled for app interactions
            vibrationManager.vibrateOnAppInteraction()
        }

        // Setup WiFi checkboxes with haptic feedback
        binding.wifiDailyCheckbox.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
        }

        binding.wifiWeeklyCheckbox.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
        }

        binding.wifiMonthlyCheckbox.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
        }

        // Setup Mobile checkboxes with haptic feedback
        binding.mobileDailyCheckbox.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
        }

        binding.mobileWeeklyCheckbox.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
        }

        binding.mobileMonthlyCheckbox.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
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

    private fun updateHistoricalDataCardState() {
        val binding = binding ?: return
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()
        val notificationEnabled = binding.notificationEnabledSwitch.isChecked

        // Enable/disable the entire historical data card based on notification state and permissions
        val cardEnabled = hasUsageStats && notificationEnabled

        binding.historicalDataMasterSwitch.isEnabled = cardEnabled

        // If card is disabled, also disable master switch and radio buttons
        if (!cardEnabled) {
            binding.historicalDataMasterSwitch.isChecked = false
            updateRadioButtonsState(false)
        } else {
            // Re-enable radio buttons if master switch is on
            updateRadioButtonsState(binding.historicalDataMasterSwitch.isChecked)
        }
    }

    private fun updateRadioButtonsState(enabled: Boolean) {
        val binding = binding ?: return

        binding.historicalDataRadioGroup.isEnabled = enabled
        binding.lastMonthRadioButton.isEnabled = enabled
        binding.lastWeekRadioButton.isEnabled = enabled
        binding.yesterdayRadioButton.isEnabled = enabled

        // Visual feedback - reduce alpha when disabled
        val alpha = if (enabled) 1.0f else 0.5f
        binding.historicalDataRadioGroup.alpha = alpha
        binding.historicalDataPeriodTitle.alpha = alpha
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

        // Update historical data card state
        updateHistoricalDataCardState()
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

        // Set notification switch
        binding.notificationEnabledSwitch.isChecked = settingsManager.isNotificationEnabled()

        // Determine if any historical data is enabled to set master switch
        val showLastMonth = settingsManager.isShowLastMonthUsageEnabled()
        val showLastWeek = settingsManager.isShowLastWeekUsageEnabled()
        val showYesterday = settingsManager.isShowYesterdayUsageEnabled()
        val anyHistoricalEnabled = showLastMonth || showLastWeek || showYesterday

        // Set historical data master switch
        binding.historicalDataMasterSwitch.isChecked = anyHistoricalEnabled

        // Set historical data radio buttons
        binding.historicalDataRadioGroup.clearCheck()

        when {
            showLastMonth -> {
                binding.lastMonthRadioButton.isChecked = true
            }
            showLastWeek -> {
                binding.lastWeekRadioButton.isChecked = true
            }
            showYesterday -> {
                binding.yesterdayRadioButton.isChecked = true
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
        updateRadioButtonsState(anyHistoricalEnabled)
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

        // Save historical data preferences based on master switch and radio button selection
        val masterSwitchEnabled = binding.historicalDataMasterSwitch.isChecked
        if (masterSwitchEnabled) {
            val checkedRadioButtonId = binding.historicalDataRadioGroup.checkedRadioButtonId
            settingsManager.saveShowLastMonthUsage(checkedRadioButtonId == R.id.last_month_radio_button)
            settingsManager.saveShowLastWeekUsage(checkedRadioButtonId == R.id.last_week_radio_button)
            settingsManager.saveShowYesterdayUsage(checkedRadioButtonId == R.id.yesterday_radio_button)
        } else {
            // If master switch is off, disable all historical data options
            settingsManager.saveShowLastMonthUsage(false)
            settingsManager.saveShowLastWeekUsage(false)
            settingsManager.saveShowYesterdayUsage(false)
        }

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
            // Notification is enabled and was already enabled - update to reflect changes
            lifecycleScope.launch {
                try {
                    val usageData = dataUsageMonitor.getUsageData()
                    notificationHelper.showUsageNotification(usageData)
                    android.util.Log.d("SettingsActivity", "Notification updated to reflect historical data changes")
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
