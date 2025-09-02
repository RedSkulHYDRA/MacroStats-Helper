package com.redskul.macrostatshelper.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.widget.Toast
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
import com.redskul.macrostatshelper.databinding.ActivityMainBinding
import com.redskul.macrostatshelper.databinding.ActivitySetupBinding
import com.redskul.macrostatshelper.utils.WorkManagerRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var settingsManager: SettingsManager
    private lateinit var autoSyncManager: AutoSyncManager
    private lateinit var workManagerRepository: WorkManagerRepository // NEW

    private var mainBinding: ActivityMainBinding? = null
    private var setupBinding: ActivitySetupBinding? = null

    // Removed service intents - no longer needed

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
        if (permissionHelper.hasUsageStatsPermission()) {
            showToast(getString(R.string.permission_granted, getString(R.string.permission_usage_stats)))
        } else {
            showToast(getString(R.string.permission_required, getString(R.string.permission_usage_stats)))
        }
    }

    private val writeSettingsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (permissionHelper.hasWriteSettingsPermission()) {
            showToast(getString(R.string.permission_granted, getString(R.string.permission_write_settings)))
        } else {
            showToast(getString(R.string.permission_denied, getString(R.string.permission_write_settings)))
        }
    }

    private val accessibilityPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (permissionHelper.hasAccessibilityPermission()) {
            showToast(getString(R.string.permission_granted, getString(R.string.permission_accessibility)))
        } else {
            showToast(getString(R.string.permission_guide_short))
        }
    }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateBatteryOptimizationUI()
        if (permissionHelper.isBatteryOptimizationDisabled()) {
            showToast(getString(R.string.battery_optimization_disabled_toast))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        permissionHelper = PermissionHelper(this)
        settingsManager = SettingsManager(this)
        autoSyncManager = AutoSyncManager(this)
        workManagerRepository = WorkManagerRepository(this) // NEW

        if (isFirstLaunch()) {
            showPermissionSetupUI()
        } else {
            showMainUI()
            startPermissionMonitoring()
        }
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

            while (true) {
                delay(2000) // Check every 2 seconds

                val currentUsageStats = permissionHelper.hasUsageStatsPermission()
                val currentAccessibility = permissionHelper.hasAccessibilityPermission()

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
                updateBatteryOptimizationUI()
                updateAccessibilityStatus()

                lastUsageStats = currentUsageStats
                lastAccessibility = currentAccessibility
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
        ensureMonitoringStarted() // UPDATED
        observeWorkStatus() // NEW
    }

    private fun setupMainUIComponents() {
        val binding = mainBinding ?: return

        // Setup battery optimization card
        setupBatteryOptimizationCard(binding)

        // Setup update interval card
        setupUpdateIntervalCard(binding)

        // Setup autosync card
        setupAutoSyncCard(binding)

        // Setup action buttons
        setupActionButtons(binding)
    }

    private fun setupBatteryOptimizationCard(binding: ActivityMainBinding) {
        updateBatteryOptimizationUI()

        binding.batteryOptButton.setOnClickListener {
            requestBatteryOptimizationExemption()
        }
    }

    private fun setupUpdateIntervalCard(binding: ActivityMainBinding) {
        // Setup adapter
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            settingsManager.getUpdateIntervalOptions()
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.updateIntervalSpinner.adapter = adapter

        // Load current setting BEFORE setting up the listener
        val savedInterval = settingsManager.getUpdateInterval()
        val currentIndex = settingsManager.getUpdateIntervalValues().indexOf(savedInterval)
        if (currentIndex >= 0) {
            binding.updateIntervalSpinner.setSelection(currentIndex)
        }

        // Set up listener AFTER initial selection is set
        binding.updateIntervalSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var isInitialSelection = true

            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                // Skip the first trigger which happens during initial setup
                if (isInitialSelection) {
                    isInitialSelection = false
                    return
                }

                val selectedInterval = settingsManager.getUpdateIntervalValues()[position]
                val currentSavedInterval = settingsManager.getUpdateInterval()

                // Only proceed if the interval actually changed
                if (selectedInterval != currentSavedInterval) {
                    settingsManager.setUpdateInterval(selectedInterval)

                    // Update WorkManager interval instead of restarting services
                    workManagerRepository.updateDataMonitoringInterval() // UPDATED

                    showToast(getString(R.string.update_frequency_changed))
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupAutoSyncCard(binding: ActivityMainBinding) {
        binding.autosyncEnabledSwitch.isChecked = autoSyncManager.isAutoSyncEnabled()

        binding.autosyncEnabledSwitch.setOnCheckedChangeListener { switch, isChecked ->
            // Add haptic feedback
            switch.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)

            if (isChecked && !autoSyncManager.canEnableAutoSync()) {
                binding.autosyncEnabledSwitch.isChecked = false
                showPermissionRequiredDialog(getString(R.string.permission_accessibility)) {
                    requestAccessibilityPermission()
                }
                return@setOnCheckedChangeListener
            }
            autoSyncManager.setAutoSyncEnabled(isChecked)
            binding.autosyncDelaySpinner.isEnabled = isChecked && autoSyncManager.canEnableAutoSync()
        }

        // Setup delay spinner
        val delayAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            autoSyncManager.getDelayOptions()
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.autosyncDelaySpinner.adapter = delayAdapter

        // Load current delay setting BEFORE setting up the listener
        val delayMinutes = autoSyncManager.getAutoSyncDelay()
        val delayIndex = autoSyncManager.getAllowedDelays().indexOf(delayMinutes)
        if (delayIndex >= 0) {
            binding.autosyncDelaySpinner.setSelection(delayIndex)
        }

        // Set up delay spinner listener with initial selection flag
        binding.autosyncDelaySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var isInitialSelection = true

            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                // Skip the first trigger which happens during initial setup
                if (isInitialSelection) {
                    isInitialSelection = false
                    return
                }

                val selectedDelay = autoSyncManager.getAllowedDelays()[position]
                val currentDelay = autoSyncManager.getAutoSyncDelay()

                // Only proceed if the delay actually changed
                if (selectedDelay != currentDelay) {
                    autoSyncManager.setAutoSyncDelay(selectedDelay)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.autosyncDelaySpinner.isEnabled = binding.autosyncEnabledSwitch.isChecked && autoSyncManager.canEnableAutoSync()

        binding.accessibilityButton.setOnClickListener {
            requestAccessibilityPermission()
        }

        updateAccessibilityStatus()
    }

    private fun setupActionButtons(binding: ActivityMainBinding) {
        binding.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.qsTileSettingsButton.setOnClickListener {
            startActivity(Intent(this, QSTileSettingsActivity::class.java))
        }

        setupStopServiceButton(binding)
    }

    private fun setupStopServiceButton(binding: ActivityMainBinding) {
        binding.stopServiceButton.setOnClickListener {
            if (binding.stopServiceButton.text == getString(R.string.stop_monitoring)) {
                // Stop WorkManager monitoring instead of services
                workManagerRepository.stopMonitoring() // UPDATED
                showToast(getString(R.string.monitoring_stopped))
                binding.statusText.text = getString(R.string.monitoring_stopped_restart_note)
                binding.stopServiceButton.text = getString(R.string.start_monitoring)
            } else {
                startMonitoringAndShowSuccess() // UPDATED
                binding.statusText.text = getString(R.string.data_usage_monitoring_running)
                binding.stopServiceButton.text = getString(R.string.stop_monitoring)
            }
        }
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

    private fun updateBatteryOptimizationUI() {
        val binding = mainBinding ?: return

        if (permissionHelper.isBatteryOptimizationDisabled()) {
            binding.batteryOptButton.text = getString(R.string.battery_optimization_disabled_check)
            binding.batteryOptButton.alpha = 0.7f
            binding.batteryOptButton.isEnabled = false
            binding.batteryOptDescription.text = getString(R.string.battery_optimization_disabled_desc)
        } else {
            binding.batteryOptButton.text = getString(R.string.disable_battery_optimization)
            binding.batteryOptButton.alpha = 1.0f
            binding.batteryOptButton.isEnabled = true
            binding.batteryOptDescription.text = getString(R.string.disable_optimization_desc)
        }
    }

    private fun updateAccessibilityStatus() {
        val binding = mainBinding ?: return

        val isAccessibilityEnabled = AutoSyncAccessibilityService.isAccessibilityServiceEnabled(this)
        binding.accessibilityStatusText.text = if (isAccessibilityEnabled) {
            getString(R.string.accessibility_service_enabled)
        } else {
            getString(R.string.accessibility_service_disabled)
        }
        binding.accessibilityStatusText.setTextColor(
            if (isAccessibilityEnabled) 0xFF4CAF50.toInt() else 0xFFFF5722.toInt()
        )
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
        binding.usageStatsButton.setOnClickListener { requestUsageStatsPermission() }
        binding.writeSettingsButton.setOnClickListener { requestWriteSettingsPermission() }
        binding.setupAccessibilityButton.setOnClickListener { requestAccessibilityPermission() }
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
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()
        val hasWriteSettings = permissionHelper.hasWriteSettingsPermission()
        val hasAccessibility = permissionHelper.hasAccessibilityPermission()
        val hasBatteryOpt = permissionHelper.isBatteryOptimizationDisabled()

        // Update button texts and states
        binding.notificationButton.text = if (hasNotification) {
            "✓ " + getString(R.string.grant_permission_button, getString(R.string.permission_notification))
        } else {
            getString(R.string.grant_permission_button, getString(R.string.permission_notification))
        }
        binding.notificationButton.alpha = if (hasNotification) 0.7f else 1.0f

        binding.usageStatsButton.text = if (hasUsageStats) {
            "✓ " + getString(R.string.grant_permission_button, getString(R.string.permission_usage_stats))
        } else {
            getString(R.string.grant_permission_button, getString(R.string.permission_usage_stats))
        }
        binding.usageStatsButton.alpha = if (hasUsageStats) 0.7f else 1.0f

        binding.writeSettingsButton.text = if (hasWriteSettings) {
            "✓ " + getString(R.string.grant_permission_button, getString(R.string.permission_write_settings))
        } else {
            getString(R.string.grant_permission_button, getString(R.string.permission_write_settings))
        }
        binding.writeSettingsButton.alpha = if (hasWriteSettings) 0.7f else 1.0f

        binding.setupAccessibilityButton.text = if (hasAccessibility) {
            "✓ " + getString(R.string.grant_permission_button, getString(R.string.permission_accessibility))
        } else {
            getString(R.string.grant_permission_button, getString(R.string.permission_accessibility))
        }
        binding.setupAccessibilityButton.alpha = if (hasAccessibility) 0.7f else 1.0f

        binding.setupBatteryOptButton.text = if (hasBatteryOpt) {
            getString(R.string.battery_optimization_disabled_check_full)
        } else {
            getString(R.string.disable_battery_optimization)
        }
        binding.setupBatteryOptButton.alpha = if (hasBatteryOpt) 0.7f else 1.0f

        binding.startButton.isEnabled = hasNotification && hasUsageStats && hasWriteSettings && hasAccessibility
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

    // UPDATED: Use WorkManager instead of services
    private fun ensureMonitoringStarted() {
        try {
            workManagerRepository.startMonitoring()
            android.util.Log.d("MainActivity", "WorkManager monitoring ensured to be running")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error ensuring monitoring is running", e)
        }
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showToast(getString(R.string.permission_granted, getString(R.string.permission_notification)))
        }
    }

    private fun requestUsageStatsPermission() {
        if (!permissionHelper.hasUsageStatsPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            usageStatsPermissionLauncher.launch(intent)
        } else {
            showToast(getString(R.string.permission_granted, getString(R.string.permission_usage_stats)))
        }
    }

    private fun requestWriteSettingsPermission() {
        if (!permissionHelper.hasWriteSettingsPermission()) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
            writeSettingsPermissionLauncher.launch(intent)
        } else {
            showToast(getString(R.string.permission_granted, getString(R.string.permission_write_settings)))
        }
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        accessibilityPermissionLauncher.launch(intent)
    }

    private fun completeSetup() {
        if (permissionHelper.hasAllPermissions()) {
            sharedPreferences.edit {
                putBoolean("setup_complete", true)
            }
            startMonitoringAndTransitionToMain() // UPDATED
        } else {
            showToast(getString(R.string.setup_description))
        }
    }

    // UPDATED: Use WorkManager instead of services
    private fun startMonitoringAndTransitionToMain() {
        try {
            workManagerRepository.startMonitoring()
            showToast(getString(R.string.monitoring_started))

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

    // UPDATED: Use WorkManager instead of services
    private fun startMonitoringAndShowSuccess() {
        try {
            workManagerRepository.startMonitoring()
            showToast(getString(R.string.monitoring_started))
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
