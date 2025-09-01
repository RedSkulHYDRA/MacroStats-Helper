package com.redskul.macrostatshelper.core

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.AdapterView
import android.widget.Spinner
import android.widget.Switch
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.redskul.macrostatshelper.data.DataUsageService
import com.redskul.macrostatshelper.data.BatteryService
import com.redskul.macrostatshelper.autosync.AutoSyncAccessibilityService
import com.redskul.macrostatshelper.autosync.AutoSyncManager
import com.redskul.macrostatshelper.tiles.QSTileSettingsActivity
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.settings.SettingsActivity
import com.redskul.macrostatshelper.settings.SettingsManager
import com.redskul.macrostatshelper.utils.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var settingsManager: SettingsManager
    private lateinit var autoSyncManager: AutoSyncManager

    private lateinit var batteryOptimizationButton: Button
    private lateinit var batteryOptimizationDescription: TextView
    private lateinit var updateIntervalSpinner: Spinner
    private lateinit var autoSyncEnabledSwitch: Switch
    private lateinit var autoSyncDelaySpinner: Spinner
    private lateinit var accessibilityStatusText: TextView

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showToast("Notification permission granted")
        } else {
            showToast(getString(R.string.notification_permission_required))
        }
    }

    private val usageStatsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (permissionHelper.hasUsageStatsPermission()) {
            showToast("Usage stats permission granted")
        } else {
            showToast(getString(R.string.usage_stats_permission_required))
        }
    }

    private val writeSettingsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (permissionHelper.hasWriteSettingsPermission()) {
            showToast("Write settings permission granted")
        } else {
            showToast(getString(R.string.write_settings_permission_info))
        }
    }

    private val accessibilityPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (permissionHelper.hasAccessibilityPermission()) {
            showToast("Accessibility service enabled")
        } else {
            showToast(getString(R.string.accessibility_permission_info))
        }
    }

    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateBatteryOptimizationUI()
        if (permissionHelper.isBatteryOptimizationDisabled()) {
            showToast("Battery optimization disabled successfully")
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

        if (isFirstLaunch()) {
            showPermissionSetupUI()
        } else {
            showMainUI()
            startPermissionMonitoring()
        }
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
                    showToast("Data usage features disabled due to missing permission")
                }

                if (lastAccessibility && !currentAccessibility) {
                    android.util.Log.i("MainActivity", "Accessibility permission was revoked")
                    autoSyncManager.enforcePermissionRestrictions()
                    showToast("AutoSync features disabled due to missing permission")
                }

                // Update battery optimization UI
                updateBatteryOptimizationUI()

                lastUsageStats = currentUsageStats
                lastAccessibility = currentAccessibility
            }
        }
    }

    private fun isFirstLaunch(): Boolean {
        return !sharedPreferences.getBoolean("setup_complete", false)
    }

    private fun showMainUI() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
        }

        // Handle window insets for notch and system bars
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            layout.setPadding(
                64 + insets.left,
                64 + insets.top,
                64 + insets.right,
                64 + insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        val titleText = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }

        val statusText = TextView(this).apply {
            text = getString(R.string.data_usage_monitoring_running)
            setPadding(0, 0, 0, 16)
        }

        // Battery Optimization Section
        val batteryOptCard = createBatteryOptimizationCard()

        // Update Interval Section
        val updateIntervalCard = createUpdateIntervalCard()

        // AutoSync Section
        val autoSyncCard = createAutoSyncCard()

        val settingsButton = Button(this).apply {
            text = getString(R.string.display_settings)
            setPadding(0, 16, 0, 16)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }

        val qsTileSettingsButton = Button(this).apply {
            text = getString(R.string.qs_tile_settings)
            setPadding(0, 16, 0, 16)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, QSTileSettingsActivity::class.java))
            }
        }

        val stopServiceButton = Button(this).apply {
            text = getString(R.string.stop_monitoring)
            setPadding(0, 16, 0, 16)
            setOnClickListener {
                stopService(Intent(this@MainActivity, DataUsageService::class.java))
                stopService(Intent(this@MainActivity, BatteryService::class.java))
                showToast(getString(R.string.monitoring_stopped))
                statusText.text = "Monitoring has been stopped. You can restart it using the Start Monitoring button below."

                this.text = getString(R.string.start_monitoring)
                this.setOnClickListener {
                    startServicesAndShowSuccess()
                    this.text = getString(R.string.stop_monitoring)
                    statusText.text = getString(R.string.data_usage_monitoring_running)
                    this.setOnClickListener {
                        stopService(Intent(this@MainActivity, DataUsageService::class.java))
                        stopService(Intent(this@MainActivity, BatteryService::class.java))
                        showToast(getString(R.string.monitoring_stopped))
                        statusText.text = "Monitoring has been stopped. You can restart it using the Start Monitoring button below."
                        this.text = getString(R.string.start_monitoring)
                        setupStopServiceButton(this, statusText)
                    }
                }
            }
        }

        layout.addView(titleText)
        layout.addView(statusText)
        addSpacing(layout, 16)
        layout.addView(batteryOptCard)
        addSpacing(layout, 16)
        layout.addView(updateIntervalCard)
        addSpacing(layout, 16)
        layout.addView(autoSyncCard)
        addSpacing(layout, 16)
        layout.addView(settingsButton)
        layout.addView(qsTileSettingsButton)
        layout.addView(stopServiceButton)

        scrollView.addView(layout)
        setContentView(scrollView)

        ensureServicesRunning()
    }

    private fun createBatteryOptimizationCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.card_background, theme)
        }

        val cardTitle = TextView(this).apply {
            text = "Battery Optimization"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        batteryOptimizationDescription = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, 16)
            alpha = 0.8f
        }

        batteryOptimizationButton = Button(this).apply {
            setPadding(0, 16, 0, 16)
            setOnClickListener { requestBatteryOptimizationExemption() }
        }

        card.addView(cardTitle)
        card.addView(batteryOptimizationDescription)
        card.addView(batteryOptimizationButton)

        updateBatteryOptimizationUI()
        return card
    }

    private fun createUpdateIntervalCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.card_background, theme)
        }

        val cardTitle = TextView(this).apply {
            text = "Update Frequency"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        val description = TextView(this).apply {
            text = "How often to update data usage statistics and notifications"
            textSize = 14f
            setPadding(0, 0, 0, 16)
            alpha = 0.8f
        }

        val spinnerLabel = TextView(this).apply {
            text = "Update every:"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }

        updateIntervalSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                settingsManager.getUpdateIntervalOptions()
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            // Load current setting
            val currentInterval = settingsManager.getUpdateInterval()
            val currentIndex = settingsManager.getUpdateIntervalValues().indexOf(currentInterval)
            if (currentIndex >= 0) {
                setSelection(currentIndex)
            }

            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    val selectedInterval = settingsManager.getUpdateIntervalValues()[position]
                    settingsManager.setUpdateInterval(selectedInterval)

                    // Restart services with new interval
                    restartServicesWithNewInterval()

                    showToast("Update frequency changed to ${settingsManager.getUpdateIntervalOptions()[position]}")
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }

        card.addView(cardTitle)
        card.addView(description)
        card.addView(spinnerLabel)
        card.addView(updateIntervalSpinner)

        return card
    }

    private fun createAutoSyncCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.card_background, theme)
        }

        val cardTitle = TextView(this).apply {
            text = getString(R.string.autosync_settings_title)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        // Accessibility Status
        accessibilityStatusText = TextView(this).apply {
            textSize = 12f
            setPadding(0, 0, 0, 16)
        }

        val autoSyncLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }

        val autoSyncSwitchLabel = TextView(this).apply {
            text = getString(R.string.enable_autosync_management)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        autoSyncEnabledSwitch = Switch(this).apply {
            isChecked = autoSyncManager.isAutoSyncEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !permissionHelper.hasAccessibilityPermission()) {
                    this.isChecked = false
                    showPermissionRequiredDialog("AutoSync Management", "Accessibility Service") {
                        requestAccessibilityPermission()
                    }
                    return@setOnCheckedChangeListener
                }
                autoSyncManager.setAutoSyncEnabled(isChecked)
                autoSyncDelaySpinner.isEnabled = isChecked && permissionHelper.hasAccessibilityPermission()
            }
        }

        autoSyncLayout.addView(autoSyncSwitchLabel)
        autoSyncLayout.addView(autoSyncEnabledSwitch)

        val autoSyncDescription = TextView(this).apply {
            text = getString(R.string.autosync_description)
            textSize = 12f
            setPadding(0, 0, 0, 12)
            alpha = 0.7f
        }

        // AutoSync Delay Selection
        val delayLabel = TextView(this).apply {
            text = getString(R.string.autosync_delay_label)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 4)
        }

        autoSyncDelaySpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_item,
                autoSyncManager.getDelayOptions()
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

            // Load current setting
            val delayMinutes = autoSyncManager.getAutoSyncDelay()
            val delayIndex = autoSyncManager.getAllowedDelays().indexOf(delayMinutes)
            if (delayIndex >= 0) {
                setSelection(delayIndex)
            }

            setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    val delayMinutes = autoSyncManager.getAllowedDelays()[position]
                    autoSyncManager.setAutoSyncDelay(delayMinutes)
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            })

            isEnabled = autoSyncEnabledSwitch.isChecked && permissionHelper.hasAccessibilityPermission()
        }

        val delayDescription = TextView(this).apply {
            text = getString(R.string.autosync_delay_description)
            textSize = 11f
            setPadding(0, 4, 0, 12)
            alpha = 0.6f
        }

        val accessibilityButton = Button(this).apply {
            text = getString(R.string.open_accessibility_settings)
            textSize = 14f
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                requestAccessibilityPermission()
            }
        }

        card.addView(cardTitle)
        card.addView(accessibilityStatusText)
        card.addView(autoSyncLayout)
        card.addView(autoSyncDescription)
        card.addView(delayLabel)
        card.addView(autoSyncDelaySpinner)
        card.addView(delayDescription)
        card.addView(accessibilityButton)

        updateAccessibilityStatus()
        return card
    }

    private fun updateBatteryOptimizationUI() {
        if (::batteryOptimizationButton.isInitialized) {
            if (permissionHelper.isBatteryOptimizationDisabled()) {
                batteryOptimizationButton.text = "✓ Battery Optimization Disabled"
                batteryOptimizationButton.alpha = 0.7f
                batteryOptimizationButton.isEnabled = false
                batteryOptimizationDescription.text = "Battery optimization is disabled. Your app will run reliably in the background."
            } else {
                batteryOptimizationButton.text = "Disable Battery Optimization"
                batteryOptimizationButton.alpha = 1.0f
                batteryOptimizationButton.isEnabled = true
                batteryOptimizationDescription.text = "Battery optimization can prevent the app from working properly in the background. Disabling it ensures reliable monitoring and notifications."
            }
        }
    }

    private fun updateAccessibilityStatus() {
        if (::accessibilityStatusText.isInitialized) {
            val isAccessibilityEnabled = AutoSyncAccessibilityService.isAccessibilityServiceEnabled(this)
            accessibilityStatusText.text = if (isAccessibilityEnabled) {
                getString(R.string.accessibility_service_enabled)
            } else {
                getString(R.string.accessibility_service_disabled)
            }
            accessibilityStatusText.setTextColor(
                if (isAccessibilityEnabled) 0xFF4CAF50.toInt() else 0xFFFF5722.toInt()
            )
        }
    }

    private fun requestBatteryOptimizationExemption() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            batteryOptimizationLauncher.launch(intent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error requesting battery optimization exemption", e)
            showToast("Unable to open battery optimization settings")
        }
    }

    private fun restartServicesWithNewInterval() {
        try {
            // Stop current services
            stopService(Intent(this, DataUsageService::class.java))
            stopService(Intent(this, BatteryService::class.java))

            // Start services with new interval
            lifecycleScope.launch {
                delay(1000) // Brief delay to ensure services are stopped
                startServicesAndShowSuccess()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error restarting services", e)
        }
    }

    private fun showPermissionSetupUI() {
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
        }

        // Handle window insets for notch and system bars
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            layout.setPadding(
                64 + insets.left,
                64 + insets.top,
                64 + insets.right,
                64 + insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        val titleText = TextView(this).apply {
            text = getString(R.string.setup_title)
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }

        val descriptionText = TextView(this).apply {
            text = getString(R.string.setup_description_autosync)
            setPadding(0, 0, 0, 32)
        }

        val notificationButton = Button(this).apply {
            text = getString(R.string.grant_notification_permission)
            setPadding(0, 16, 0, 16)
            setOnClickListener { requestNotificationPermission() }
        }

        val usageStatsButton = Button(this).apply {
            text = getString(R.string.grant_usage_stats_permission)
            setPadding(0, 16, 0, 16)
            setOnClickListener { requestUsageStatsPermission() }
        }

        val writeSettingsButton = Button(this).apply {
            text = getString(R.string.grant_write_settings_permission)
            setPadding(0, 16, 0, 16)
            setOnClickListener { requestWriteSettingsPermission() }
        }

        val accessibilityButton = Button(this).apply {
            text = getString(R.string.grant_accessibility_permission)
            setPadding(0, 16, 0, 16)
            setOnClickListener { requestAccessibilityPermission() }
        }

        val batteryOptButton = Button(this).apply {
            text = "Disable Battery Optimization"
            setPadding(0, 16, 0, 16)
            setOnClickListener { requestBatteryOptimizationExemption() }
        }

        val startButton = Button(this).apply {
            text = getString(R.string.start_monitoring)
            setPadding(0, 16, 0, 16)
            setOnClickListener { completeSetup() }
        }

        layout.addView(titleText)
        layout.addView(descriptionText)
        layout.addView(notificationButton)
        layout.addView(usageStatsButton)
        layout.addView(writeSettingsButton)
        layout.addView(accessibilityButton)
        layout.addView(batteryOptButton)
        layout.addView(startButton)

        scrollView.addView(layout)
        setContentView(scrollView)

        lifecycleScope.launch {
            while (true) {
                val hasNotification = permissionHelper.hasNotificationPermission()
                val hasUsageStats = permissionHelper.hasUsageStatsPermission()
                val hasWriteSettings = permissionHelper.hasWriteSettingsPermission()
                val hasAccessibility = permissionHelper.hasAccessibilityPermission()
                val hasBatteryOpt = permissionHelper.isBatteryOptimizationDisabled()

                notificationButton.text = if (hasNotification) {
                    "✓ " + getString(R.string.grant_notification_permission)
                } else {
                    getString(R.string.grant_notification_permission)
                }
                notificationButton.alpha = if (hasNotification) 0.7f else 1.0f

                usageStatsButton.text = if (hasUsageStats) {
                    "✓ " + getString(R.string.grant_usage_stats_permission)
                } else {
                    getString(R.string.grant_usage_stats_permission)
                }
                usageStatsButton.alpha = if (hasUsageStats) 0.7f else 1.0f

                writeSettingsButton.text = if (hasWriteSettings) {
                    "✓ " + getString(R.string.grant_write_settings_permission)
                } else {
                    getString(R.string.grant_write_settings_permission)
                }
                writeSettingsButton.alpha = if (hasWriteSettings) 0.7f else 1.0f

                accessibilityButton.text = if (hasAccessibility) {
                    "✓ " + getString(R.string.grant_accessibility_permission)
                } else {
                    getString(R.string.grant_accessibility_permission)
                }
                accessibilityButton.alpha = if (hasAccessibility) 0.7f else 1.0f

                batteryOptButton.text = if (hasBatteryOpt) {
                    "✓ Disable Battery Optimization"
                } else {
                    "Disable Battery Optimization"
                }
                batteryOptButton.alpha = if (hasBatteryOpt) 0.7f else 1.0f

                startButton.isEnabled = hasNotification && hasUsageStats && hasWriteSettings && hasAccessibility
                startButton.alpha = if (startButton.isEnabled) 1.0f else 0.5f

                delay(1000)
            }
        }
    }

    private fun addSpacing(parent: LinearLayout, dpSize: Int) {
        val spacer = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (dpSize * resources.displayMetrics.density).toInt()
            )
        }
        parent.addView(spacer)
    }

    private fun setupStopServiceButton(button: Button, statusText: TextView) {
        button.setOnClickListener {
            if (button.text == getString(R.string.stop_monitoring)) {
                stopService(Intent(this@MainActivity, DataUsageService::class.java))
                stopService(Intent(this@MainActivity, BatteryService::class.java))
                showToast(getString(R.string.monitoring_stopped))
                statusText.text = "Monitoring has been stopped. You can restart it using the Start Monitoring button below."
                button.text = getString(R.string.start_monitoring)
            } else {
                startServicesAndShowSuccess()
                statusText.text = getString(R.string.data_usage_monitoring_running)
                button.text = getString(R.string.stop_monitoring)
            }
        }
    }

    private fun ensureServicesRunning() {
        try {
            val dataServiceIntent = Intent(this, DataUsageService::class.java)

            // Only start as foreground service if notifications are enabled
            if (settingsManager.isNotificationEnabled()) {
                startForegroundService(dataServiceIntent)
            } else {
                startService(dataServiceIntent)
            }

            val batteryServiceIntent = Intent(this, BatteryService::class.java)
            startService(batteryServiceIntent) // BatteryService is always background
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error ensuring services are running", e)
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                showToast("Notification permission already granted")
            }
        } else {
            showToast("Notification permission not required on this Android version")
        }
    }

    private fun requestUsageStatsPermission() {
        if (!permissionHelper.hasUsageStatsPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            usageStatsPermissionLauncher.launch(intent)
        } else {
            showToast("Usage stats permission already granted")
        }
    }

    private fun requestWriteSettingsPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!permissionHelper.hasWriteSettingsPermission()) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                writeSettingsPermissionLauncher.launch(intent)
            } else {
                showToast("Write settings permission already granted")
            }
        } else {
            showToast("Write settings permission not required on this Android version")
        }
    }

    private fun requestAccessibilityPermission() {
        if (!permissionHelper.hasAccessibilityPermission()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            accessibilityPermissionLauncher.launch(intent)
            showToast(getString(R.string.accessibility_permission_guide))
        } else {
            showToast("Accessibility service already enabled")
        }
    }

    private fun completeSetup() {
        if (permissionHelper.hasAllPermissions()) {
            sharedPreferences.edit().putBoolean("setup_complete", true).apply()
            startServicesAndTransitionToMain()
        } else {
            showToast(getString(R.string.grant_required_permissions_autosync))
        }
    }

    private fun startServicesAndTransitionToMain() {
        try {
            val dataServiceIntent = Intent(this, DataUsageService::class.java)
            val dataResult = if (settingsManager.isNotificationEnabled()) {
                startForegroundService(dataServiceIntent)
            } else {
                startService(dataServiceIntent)
            }

            val batteryServiceIntent = Intent(this, BatteryService::class.java)
            val batteryResult = startService(batteryServiceIntent)

            if (dataResult != null && batteryResult != null) {
                showToast(getString(R.string.monitoring_started_success))

                lifecycleScope.launch {
                    delay(1500)
                    showMainUI()
                    startPermissionMonitoring()
                }
            } else {
                showToast(getString(R.string.service_start_failed))
            }
        } catch (e: Exception) {
            showToast(getString(R.string.service_error, e.message))
            e.printStackTrace()
        }
    }

    private fun startServicesAndShowSuccess() {
        try {
            val dataServiceIntent = Intent(this, DataUsageService::class.java)
            if (settingsManager.isNotificationEnabled()) {
                startForegroundService(dataServiceIntent)
            } else {
                startService(dataServiceIntent)
            }

            val batteryServiceIntent = Intent(this, BatteryService::class.java)
            startService(batteryServiceIntent)

            showToast("Monitoring started successfully")
        } catch (e: Exception) {
            showToast("Error starting monitoring: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun showPermissionRequiredDialog(featureName: String, permissionName: String, onPositive: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("$featureName requires $permissionName permission to work. Would you like to grant it now?")
            .setPositiveButton("Grant") { _, _ -> onPositive() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
