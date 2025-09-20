package com.redskul.macrostatshelper.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import com.redskul.macrostatshelper.autosync.AutoSyncAccessibilityService
import com.redskul.macrostatshelper.autosync.AutoSyncManager
import com.redskul.macrostatshelper.settings.QSTileSettingsActivity
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.settings.SettingsActivity
import com.redskul.macrostatshelper.settings.SettingsManager
import com.redskul.macrostatshelper.utils.PermissionHelper
import com.redskul.macrostatshelper.utils.VibrationManager
import com.redskul.macrostatshelper.databinding.ActivityMainBinding
import com.redskul.macrostatshelper.databinding.ActivitySetupBinding
import com.redskul.macrostatshelper.utils.WorkManagerRepository
import com.redskul.macrostatshelper.dns.DNSManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var settingsManager: SettingsManager
    private lateinit var autoSyncManager: AutoSyncManager
    private lateinit var workManagerRepository: WorkManagerRepository
    private lateinit var vibrationManager: VibrationManager
    private lateinit var dnsManager: DNSManager
    private var mainBinding: ActivityMainBinding? = null
    private var setupBinding: ActivitySetupBinding? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showToast(getString(R.string.permission_granted, getString(R.string.permission_notification)))
        } else {
            showToast(getString(R.string.permission_denied, getString(R.string.permission_notification)))
        }
    }

    private val usageStatsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionSwitches()
        updateFeatureControls()
    }

    private val writeSettingsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionSwitches()
        updateFeatureControls()
    }

    private val accessibilityPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionSwitches()
        updateFeatureControls()
    }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionButtonStates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        permissionHelper = PermissionHelper(this)
        settingsManager = SettingsManager(this)
        autoSyncManager = AutoSyncManager(this)
        workManagerRepository = WorkManagerRepository(this)
        vibrationManager = VibrationManager(this)
        dnsManager = DNSManager(this)

        if (isFirstLaunch()) {
            showPermissionSetupUI()
        } else {
            showMainUI()
            startPermissionMonitoring()
        }
        ensureMonitoringActive()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainBinding = null
        setupBinding = null
    }

    private fun startPermissionMonitoring() {
        lifecycleScope.launch {
            var lastUsageStats = permissionHelper.hasUsageStatsPermission()
            var lastAccessibility = permissionHelper.hasAccessibilityPermission()
            var lastWriteSettings = permissionHelper.hasWriteSettingsPermission()
            var lastSecureSettings = dnsManager.hasSecureSettingsPermission()

            while (true) {
                delay(2000) // Check every 2 seconds

                val currentUsageStats = permissionHelper.hasUsageStatsPermission()
                val currentAccessibility = permissionHelper.hasAccessibilityPermission()
                val currentWriteSettings = permissionHelper.hasWriteSettingsPermission()
                val currentSecureSettings = dnsManager.hasSecureSettingsPermission()

                // Check if permissions were revoked
                if (lastUsageStats && !currentUsageStats) {
                    android.util.Log.i("MainActivity", "Usage stats permission was revoked")
                    settingsManager.enforcePermissionRestrictions()
                    showToast(getString(R.string.data_tiles_disabled))
                }
                if (lastAccessibility && !currentAccessibility) {
                    android.util.Log.i("MainActivity", "Accessibility permission was revoked")
                    autoSyncManager.enforcePermissionRestrictions()
                    showToast("AutoSync disabled")
                }

                // Update UI
                updatePermissionSwitches()
                updatePermissionRequirementMessages()
                updateFeatureControls()

                lastUsageStats = currentUsageStats
                lastAccessibility = currentAccessibility
                lastWriteSettings = currentWriteSettings
                lastSecureSettings = currentSecureSettings
            }
        }
    }

    private fun isFirstLaunch(): Boolean {
        return !sharedPreferences.getBoolean("setup_complete", false)
    }

    private fun showMainUI() {
        mainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mainBinding!!.root)

        setupWindowInsets(mainBinding!!.root, mainBinding!!.mainLayout)
        setupMainUIComponents()
        observeWorkStatus()
    }

    private fun setupMainUIComponents() {
        val binding = mainBinding ?: return

        // Setup permissions card
        setupPermissionsCard(binding)

        // Setup update interval card
        setupUpdateIntervalCard(binding)

        // Setup autosync card
        setupAutoSyncCard(binding)

        // Setup action buttons
        setupActionButtons(binding)

        // Update permission-based UI
        updatePermissionRequirementMessages()
        updateFeatureControls()
    }

    private fun setupPermissionsCard(binding: ActivityMainBinding) {
        // Setup permission switches with listeners that always open settings
        binding.usageStatsPermissionSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            requestUsageStatsPermission()
        }

        binding.writeSettingsPermissionSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            requestWriteSettingsPermission()
        }

        binding.accessibilityPermissionSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            requestAccessibilityPermission()
        }

        binding.secureSettingsPermissionSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            showSecureSettingsPermissionDialog()
        }

        updatePermissionSwitches()
    }

    private fun setupUpdateIntervalCard(binding: ActivityMainBinding) {
        // Load current setting BEFORE setting up the listener
        val savedInterval = settingsManager.getUpdateInterval()
        val currentRadioId = when (savedInterval) {
            15 -> R.id.update_15_minutes
            30 -> R.id.update_30_minutes
            45 -> R.id.update_45_minutes
            60 -> R.id.update_60_minutes
            else -> R.id.update_15_minutes
        }
        binding.updateIntervalRadioGroup.check(currentRadioId)

        // Set up listener AFTER initial selection is set
        binding.updateIntervalRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            // Add haptic feedback - always enabled for app interactions
            vibrationManager.vibrateOnAppInteraction()

            val selectedInterval = when (checkedId) {
                R.id.update_15_minutes -> 15
                R.id.update_30_minutes -> 30
                R.id.update_45_minutes -> 45
                R.id.update_60_minutes -> 60
                else -> 15
            }

            val currentSavedInterval = settingsManager.getUpdateInterval()

            // Only proceed if the interval actually changed
            if (selectedInterval != currentSavedInterval) {
                settingsManager.setUpdateInterval(selectedInterval)

                // Update WorkManager interval instead of restarting services
                workManagerRepository.updateDataMonitoringInterval()

                showToast(getString(R.string.update_frequency_changed))
            }
        }
    }

    private fun setupAutoSyncCard(binding: ActivityMainBinding) {
        binding.autosyncEnabledSwitch.isChecked = autoSyncManager.isAutoSyncEnabled()

        binding.autosyncEnabledSwitch.setOnCheckedChangeListener { switch, isChecked ->
            // Add haptic feedback - always enabled for app interactions
            vibrationManager.vibrateOnAppInteraction()

            if (isChecked && !autoSyncManager.canEnableAutoSync()) {
                binding.autosyncEnabledSwitch.isChecked = false
                showPermissionRequiredDialog(getString(R.string.permission_accessibility)) {
                    requestAccessibilityPermission()
                }
                return@setOnCheckedChangeListener
            }
            autoSyncManager.setAutoSyncEnabled(isChecked)
            binding.autosyncDelayRadioGroup.isEnabled = isChecked && autoSyncManager.canEnableAutoSync()
        }

        // Load current delay setting BEFORE setting up the listener
        val delayMinutes = autoSyncManager.getAutoSyncDelay()
        val delayRadioId = when (delayMinutes) {
            15 -> R.id.delay_15_minutes
            30 -> R.id.delay_30_minutes
            45 -> R.id.delay_45_minutes
            60 -> R.id.delay_60_minutes
            else -> R.id.delay_15_minutes
        }
        binding.autosyncDelayRadioGroup.check(delayRadioId)

        // Set up delay radio group listener
        binding.autosyncDelayRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            // Add haptic feedback - always enabled for app interactions
            vibrationManager.vibrateOnAppInteraction()

            val selectedDelay = when (checkedId) {
                R.id.delay_15_minutes -> 15
                R.id.delay_30_minutes -> 30
                R.id.delay_45_minutes -> 45
                R.id.delay_60_minutes -> 60
                else -> 15
            }

            val currentDelay = autoSyncManager.getAutoSyncDelay()

            // Only proceed if the delay actually changed
            if (selectedDelay != currentDelay) {
                autoSyncManager.setAutoSyncDelay(selectedDelay)
            }
        }

        binding.autosyncDelayRadioGroup.isEnabled = binding.autosyncEnabledSwitch.isChecked && autoSyncManager.canEnableAutoSync()
    }

    private fun setupActionButtons(binding: ActivityMainBinding) {
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.qsTileSettingsButton.setOnClickListener {
            startActivity(Intent(this, QSTileSettingsActivity::class.java))
        }
    }

    private fun updatePermissionSwitches() {
        val binding = mainBinding ?: return

        // Update switch states based on current permissions (but don't trigger listeners)
        binding.usageStatsPermissionSwitch.setOnCheckedChangeListener(null)
        binding.usageStatsPermissionSwitch.isChecked = permissionHelper.hasUsageStatsPermission()
        binding.usageStatsPermissionSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            requestUsageStatsPermission()
        }

        binding.writeSettingsPermissionSwitch.setOnCheckedChangeListener(null)
        binding.writeSettingsPermissionSwitch.isChecked = permissionHelper.hasWriteSettingsPermission()
        binding.writeSettingsPermissionSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            requestWriteSettingsPermission()
        }

        binding.accessibilityPermissionSwitch.setOnCheckedChangeListener(null)
        binding.accessibilityPermissionSwitch.isChecked = permissionHelper.hasAccessibilityPermission()
        binding.accessibilityPermissionSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            requestAccessibilityPermission()
        }

        binding.secureSettingsPermissionSwitch.setOnCheckedChangeListener(null)
        binding.secureSettingsPermissionSwitch.isChecked = dnsManager.hasSecureSettingsPermission()
        binding.secureSettingsPermissionSwitch.setOnCheckedChangeListener { _, _ ->
            vibrationManager.vibrateOnAppInteraction()
            showSecureSettingsPermissionDialog()
        }
    }

    private fun updatePermissionRequirementMessages() {
        val binding = mainBinding ?: return

        // Update interval card permission message
        if (!permissionHelper.hasUsageStatsPermission()) {
            binding.updateIntervalPermissionText.text = getString(R.string.usage_stats_permission_required_for_notifications)
            binding.updateIntervalPermissionText.visibility = android.view.View.VISIBLE
        } else {
            binding.updateIntervalPermissionText.visibility = android.view.View.GONE
        }

        // AutoSync card permission message
        if (!permissionHelper.hasAccessibilityPermission()) {
            binding.autosyncPermissionText.text = getString(R.string.accessibility_permission_required_for_autosync)
            binding.autosyncPermissionText.visibility = android.view.View.VISIBLE
        } else {
            binding.autosyncPermissionText.visibility = android.view.View.GONE
        }
    }

    private fun updateFeatureControls() {
        val binding = mainBinding ?: return
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()
        val hasAccessibility = permissionHelper.hasAccessibilityPermission()

        // Update interval controls
        binding.updateIntervalRadioGroup.isEnabled = hasUsageStats
        for (i in 0 until binding.updateIntervalRadioGroup.childCount) {
            binding.updateIntervalRadioGroup.getChildAt(i).isEnabled = hasUsageStats
        }
        binding.updateIntervalRadioGroup.alpha = if (hasUsageStats) 1.0f else 0.5f

        // AutoSync controls
        binding.autosyncEnabledSwitch.isEnabled = hasAccessibility
        binding.autosyncDelayRadioGroup.isEnabled = hasAccessibility && binding.autosyncEnabledSwitch.isChecked
        for (i in 0 until binding.autosyncDelayRadioGroup.childCount) {
            binding.autosyncDelayRadioGroup.getChildAt(i).isEnabled = hasAccessibility && binding.autosyncEnabledSwitch.isChecked
        }

        val autosyncAlpha = if (hasAccessibility) 1.0f else 0.5f
        binding.autosyncEnabledSwitch.alpha = autosyncAlpha
        binding.autosyncDelayRadioGroup.alpha = if (hasAccessibility && binding.autosyncEnabledSwitch.isChecked) 1.0f else 0.5f
    }

    // NEW: Observe WorkManager status
    private fun observeWorkStatus() {
        // Observe data monitoring work status
        workManagerRepository.getDataWorkStatus().observe(this) { workInfoList ->
            val isRunning = workInfoList?.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING } == true
            android.util.Log.d("MainActivity", "Data monitoring work status: running=$isRunning")
        }

        // Observe battery monitoring work status
        workManagerRepository.getBatteryWorkStatus().observe(this) { workInfoList ->
            val isRunning = workInfoList?.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING } == true
            android.util.Log.d("MainActivity", "Battery monitoring work status: running=$isRunning")
        }
    }

    private fun requestBatteryOptimizationExemption() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:$packageName".toUri()
            }
            batteryOptimizationLauncher.launch(intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error requesting battery optimization exemption", e)
            showToast(getString(R.string.unable_open_battery_settings))
        }
    }

    private fun ensureMonitoringActive() {
        try {
            workManagerRepository.ensureMonitoringActive()
            android.util.Log.d("MainActivity", "WorkManager monitoring ensured to be active")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error ensuring monitoring is active", e)
        }
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showToast(getString(R.string.permission_granted, getString(R.string.permission_notification)))
        }
    }

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        usageStatsPermissionLauncher.launch(intent)
    }

    private fun requestWriteSettingsPermission() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = "package:$packageName".toUri()
        }
        writeSettingsPermissionLauncher.launch(intent)
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        accessibilityPermissionLauncher.launch(intent)
    }

    private fun showSecureSettingsPermissionDialog() {
        val command = dnsManager.getADBCommand()

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.secure_settings_permission_title))
            .setMessage(getString(R.string.secure_settings_permission_dialog_message))
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
        val padding = resources.getDimensionPixelSize(R.dimen.spacing_md)
        val textView = android.widget.TextView(this).apply {
            text = command
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            setBackgroundResource(android.R.drawable.editbox_background)
            setPadding(padding, padding, padding, padding)
        }
        return textView
    }

    private fun copyCommandToClipboard(command: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("ADB Command", command)
        clipboard.setPrimaryClip(clip)
        showToast(getString(R.string.command_copied))
    }

    private fun showADBInstructions() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.adb_instructions_title))
            .setMessage(getString(R.string.adb_instructions_message))
            .setPositiveButton(getString(R.string.ok_button)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showPermissionSetupUI() {
        setupBinding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(setupBinding!!.root)

        setupWindowInsets(setupBinding!!.root, setupBinding!!.setupLayout)
        setupPermissionButtons()
        startPermissionStatusUpdates()
    }

    private fun setupPermissionButtons() {
        val binding = setupBinding ?: return

        binding.notificationButton.setOnClickListener { requestNotificationPermission() }
        binding.setupBatteryOptButton.setOnClickListener { requestBatteryOptimizationExemption() }
        binding.startButton.setOnClickListener { completeSetup() }
    }

    private fun startPermissionStatusUpdates() {
        lifecycleScope.launch {
            while (setupBinding != null) {
                updatePermissionButtonStates()
                delay(1000)
            }
        }
    }

    private fun updatePermissionButtonStates() {
        val binding = setupBinding ?: return

        val hasNotification = permissionHelper.hasNotificationPermission()
        val hasBatteryOpt = permissionHelper.isBatteryOptimizationDisabled()

        // Update button texts and states
        binding.notificationButton.text = if (hasNotification) {
            "✓ " + getString(R.string.grant_permission_button, getString(R.string.permission_notification))
        } else {
            getString(R.string.grant_permission_button, getString(R.string.permission_notification))
        }
        binding.notificationButton.alpha = if (hasNotification) 0.7f else 1.0f

        binding.setupBatteryOptButton.text = if (hasBatteryOpt) {
            getString(R.string.battery_optimization_disabled_check_full)
        } else {
            getString(R.string.disable_battery_optimization)
        }
        binding.setupBatteryOptButton.alpha = if (hasBatteryOpt) 0.7f else 1.0f

        binding.startButton.isEnabled = hasNotification && hasBatteryOpt
        binding.startButton.alpha = if (binding.startButton.isEnabled) 1.0f else 0.5f
    }

    private fun setupWindowInsets(rootView: android.view.View, contentLayout: android.view.View) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { _, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            val mainPadding = resources.getDimensionPixelSize(R.dimen.padding_main)
            contentLayout.setPadding(
                mainPadding + insets.left,
                mainPadding + insets.top,
                mainPadding + insets.right,
                mainPadding + insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun completeSetup() {
        if (permissionHelper.hasNotificationPermission() && permissionHelper.isBatteryOptimizationDisabled()) {
            sharedPreferences.edit {
                putBoolean("setup_complete", true)
            }
            initializeAndTransitionToMain()
        } else {
            showToast(getString(R.string.setup_description))
        }
    }

    private fun initializeAndTransitionToMain() {
        try {
            workManagerRepository.ensureMonitoringActive()

            lifecycleScope.launch {
                delay(1500)
                showMainUI()
                startPermissionMonitoring()
            }
        } catch (e: Exception) {
            showToast(getString(R.string.service_error, e.message ?: "Unknown"))
            e.printStackTrace()
        }
    }

    private fun showPermissionRequiredDialog(permissionName: String, onPositive: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_required_title))
            .setMessage(getString(R.string.permission_required_dialog_message, "AutoSync Management", permissionName))
            .setPositiveButton(getString(R.string.grant_button)) { _, _ -> onPositive() }
            .setNegativeButton(getString(R.string.cancel_button)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
