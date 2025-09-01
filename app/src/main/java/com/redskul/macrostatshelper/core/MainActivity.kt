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
            showToast("Battery optimization disabled")
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
                    showToast("Data features disabled")
                }

                if (lastAccessibility && !currentAccessibility) {
                    android.util.Log.i("MainActivity", "Accessibility permission was revoked")
                    autoSyncManager.enforcePermissionRestrictions()
                    showToast("AutoSync disabled")
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
            val mainPadding = resources.getDimensionPixelSize(R.dimen.padding_main)
            setPadding(mainPadding, mainPadding, mainPadding, mainPadding)
        }

        // Handle window insets for notch and system bars
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            val mainPadding = resources.getDimensionPixelSize(R.dimen.padding_main)
            layout.setPadding(
                mainPadding + insets.left,
                mainPadding + insets.top,
                mainPadding + insets.right,
                mainPadding + insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        val titleText = TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = resources.getDimension(R.dimen.text_size_title) / resources.displayMetrics.scaledDensity
            val spacingXL = resources.getDimensionPixelSize(R.dimen.spacing_xl)
            setPadding(0, 0, 0, spacingXL)
        }

        val statusText = TextView(this).apply {
            text = getString(R.string.data_usage_monitoring_running)
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, 0, 0, spacingMd)
        }

        // Battery Optimization Section
        val batteryOptCard = createBatteryOptimizationCard()

        // Update Interval Section
        val updateIntervalCard = createUpdateIntervalCard()

        // AutoSync Section
        val autoSyncCard = createAutoSyncCard()

        val settingsButton = Button(this).apply {
            text = getString(R.string.display_settings)
            val buttonPaddingH = resources.getDimensionPixelSize(R.dimen.button_padding_horizontal)
            val buttonPaddingV = resources.getDimensionPixelSize(R.dimen.button_padding_vertical)
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }

        val qsTileSettingsButton = Button(this).apply {
            text = getString(R.string.qs_tile_settings)
            val buttonPaddingH = resources.getDimensionPixelSize(R.dimen.button_padding_horizontal)
            val buttonPaddingV = resources.getDimensionPixelSize(R.dimen.button_padding_vertical)
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, QSTileSettingsActivity::class.java))
            }
        }

        val stopServiceButton = Button(this).apply {
            text = getString(R.string.stop_monitoring)
            val buttonPaddingH = resources.getDimensionPixelSize(R.dimen.button_padding_horizontal)
            val buttonPaddingV = resources.getDimensionPixelSize(R.dimen.button_padding_vertical)
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
            setOnClickListener {
                stopService(Intent(this@MainActivity, DataUsageService::class.java))
                stopService(Intent(this@MainActivity, BatteryService::class.java))
                showToast(getString(R.string.monitoring_stopped))
                statusText.text = "Monitoring stopped. Use Start button to restart."

                this.text = getString(R.string.start_monitoring)
                this.setOnClickListener {
                    startServicesAndShowSuccess()
                    this.text = getString(R.string.stop_monitoring)
                    statusText.text = getString(R.string.data_usage_monitoring_running)
                    this.setOnClickListener {
                        stopService(Intent(this@MainActivity, DataUsageService::class.java))
                        stopService(Intent(this@MainActivity, BatteryService::class.java))
                        showToast(getString(R.string.monitoring_stopped))
                        statusText.text = "Monitoring stopped. Use Start button to restart."
                        this.text = getString(R.string.start_monitoring)
                        setupStopServiceButton(this, statusText)
                    }
                }
            }
        }

        layout.addView(titleText)
        layout.addView(statusText)
        addSpacing(layout, R.dimen.spacing_md)
        layout.addView(batteryOptCard)
        addSpacing(layout, R.dimen.spacing_md)
        layout.addView(updateIntervalCard)
        addSpacing(layout, R.dimen.spacing_md)
        layout.addView(autoSyncCard)
        addSpacing(layout, R.dimen.spacing_md)
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
            textSize = resources.getDimension(R.dimen.text_size_heading) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, 0, 0, spacingMd)
        }

        batteryOptimizationDescription = TextView(this).apply {
            textSize = resources.getDimension(R.dimen.text_size_body) / resources.displayMetrics.scaledDensity
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, 0, 0, spacingMd)
            alpha = 0.8f
        }

        batteryOptimizationButton = Button(this).apply {
            val buttonPaddingH = resources.getDimensionPixelSize(R.dimen.button_padding_horizontal)
            val buttonPaddingV = resources.getDimensionPixelSize(R.dimen.button_padding_vertical)
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
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
            textSize = resources.getDimension(R.dimen.text_size_heading) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, 0, 0, spacingSm)
        }

        val description = TextView(this).apply {
            text = "Update frequency for data statistics"
            textSize = resources.getDimension(R.dimen.text_size_body) / resources.displayMetrics.scaledDensity
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, 0, 0, spacingMd)
            alpha = 0.8f
        }

        val spinnerLabel = TextView(this).apply {
            text = "Update every:"
            textSize = resources.getDimension(R.dimen.text_size_subheading) / resources.displayMetrics.scaledDensity
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, 0, 0, spacingSm)
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

                    showToast("Update frequency changed")
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
            textSize = resources.getDimension(R.dimen.text_size_heading) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, 0, 0, spacingSm)
        }

        // Accessibility Status
        accessibilityStatusText = TextView(this).apply {
            textSize = resources.getDimension(R.dimen.text_size_small) / resources.displayMetrics.scaledDensity
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, 0, 0, spacingMd)
        }

        val autoSyncLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, 0, 0, spacingSm)
        }

        val autoSyncSwitchLabel = TextView(this).apply {
            text = getString(R.string.enable_autosync_management)
            textSize = resources.getDimension(R.dimen.text_size_subheading) / resources.displayMetrics.scaledDensity
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        autoSyncEnabledSwitch = Switch(this).apply {
            isChecked = autoSyncManager.isAutoSyncEnabled()
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !permissionHelper.hasAccessibilityPermission()) {
                    this.isChecked = false
                    showPermissionRequiredDialog("AutoSync Management", getString(R.string.permission_accessibility)) {
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
            textSize = resources.getDimension(R.dimen.text_size_small) / resources.displayMetrics.scaledDensity
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, 0, 0, spacingMd)
            alpha = 0.7f
        }

        // AutoSync Delay Selection
        val delayLabel = TextView(this).apply {
            text = getString(R.string.autosync_delay_label)
            textSize = resources.getDimension(R.dimen.text_size_body) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, spacingSm, 0, resources.getDimensionPixelSize(R.dimen.spacing_xs))
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
            textSize = resources.getDimension(R.dimen.text_size_caption) / resources.displayMetrics.scaledDensity
            val spacingXs = resources.getDimensionPixelSize(R.dimen.spacing_xs)
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, spacingXs, 0, spacingMd)
            alpha = 0.6f
        }

        val accessibilityButton = Button(this).apply {
            text = getString(R.string.open_accessibility_settings)
            textSize = resources.getDimension(R.dimen.text_size_body) / resources.displayMetrics.scaledDensity
            val buttonPaddingH = resources.getDimensionPixelSize(R.dimen.button_padding_small_horizontal)
            val buttonPaddingV = resources.getDimensionPixelSize(R.dimen.button_padding_small_vertical)
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
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
                batteryOptimizationDescription.text = "Battery optimization disabled. App runs reliably."
            } else {
                batteryOptimizationButton.text = "Disable Battery Optimization"
                batteryOptimizationButton.alpha = 1.0f
                batteryOptimizationButton.isEnabled = true
                batteryOptimizationDescription.text = "Disable optimization for reliable monitoring."
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
            showToast("Unable to open battery settings")
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
            val mainPadding = resources.getDimensionPixelSize(R.dimen.padding_main)
            setPadding(mainPadding, mainPadding, mainPadding, mainPadding)
        }

        // Handle window insets for notch and system bars
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            val mainPadding = resources.getDimensionPixelSize(R.dimen.padding_main)
            layout.setPadding(
                mainPadding + insets.left,
                mainPadding + insets.top,
                mainPadding + insets.right,
                mainPadding + insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        val titleText = TextView(this).apply {
            text = getString(R.string.setup_title)
            textSize = resources.getDimension(R.dimen.text_size_title) / resources.displayMetrics.scaledDensity
            val spacingXL = resources.getDimensionPixelSize(R.dimen.spacing_xl)
            setPadding(0, 0, 0, spacingXL)
        }

        val descriptionText = TextView(this).apply {
            text = getString(R.string.setup_description_autosync)
            val spacingXL = resources.getDimensionPixelSize(R.dimen.spacing_xl)
            setPadding(0, 0, 0, spacingXL)
        }

        val notificationButton = Button(this).apply {
            text = getString(R.string.grant_permission_button, getString(R.string.permission_notification))
            val buttonPaddingH = resources.getDimensionPixelSize(R.dimen.button_padding_horizontal)
            val buttonPaddingV = resources.getDimensionPixelSize(R.dimen.button_padding_vertical)
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
            setOnClickListener { requestNotificationPermission() }
        }

        val usageStatsButton = Button(this).apply {
            text = getString(R.string.grant_permission_button, getString(R.string.permission_usage_stats))
            val buttonPaddingH = resources.getDimensionPixelSize(R.dimen.button_padding_horizontal)
            val buttonPaddingV = resources.getDimensionPixelSize(R.dimen.button_padding_vertical)
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
            setOnClickListener { requestUsageStatsPermission() }
        }

        val writeSettingsButton = Button(this).apply {
            text = getString(R.string.grant_permission_button, getString(R.string.permission_write_settings))
            val buttonPaddingH = resources.getDimensionPixelSize(R.dimen.button_padding_horizontal)
            val buttonPaddingV = resources.getDimensionPixelSize(R.dimen.button_padding_vertical)
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
            setOnClickListener { requestWriteSettingsPermission() }
        }

        val accessibilityButton = Button(this).apply {
            text = getString(R.string.grant_permission_button, getString(R.string.permission_accessibility))
            val buttonPaddingH = resources.getDimensionPixelSize(R.dimen.button_padding_horizontal)
            val buttonPaddingV = resources.getDimensionPixelSize(R.dimen.button_padding_vertical)
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
            setOnClickListener { requestAccessibilityPermission() }
        }

        val batteryOptButton = Button(this).apply {
            text = "Disable Battery Optimization"
            val buttonPaddingH = resources.getDimensionPixelSize(R.dimen.button_padding_horizontal)
            val buttonPaddingV = resources.getDimensionPixelSize(R.dimen.button_padding_vertical)
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
            setOnClickListener { requestBatteryOptimizationExemption() }
        }

        val startButton = Button(this).apply {
            text = getString(R.string.start_monitoring)
            val buttonPaddingH = resources.getDimensionPixelSize(R.dimen.button_padding_horizontal)
            val buttonPaddingV = resources.getDimensionPixelSize(R.dimen.button_padding_vertical)
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
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
                    "✓ " + getString(R.string.grant_permission_button, getString(R.string.permission_notification))
                } else {
                    getString(R.string.grant_permission_button, getString(R.string.permission_notification))
                }
                notificationButton.alpha = if (hasNotification) 0.7f else 1.0f

                usageStatsButton.text = if (hasUsageStats) {
                    "✓ " + getString(R.string.grant_permission_button, getString(R.string.permission_usage_stats))
                } else {
                    getString(R.string.grant_permission_button, getString(R.string.permission_usage_stats))
                }
                usageStatsButton.alpha = if (hasUsageStats) 0.7f else 1.0f

                writeSettingsButton.text = if (hasWriteSettings) {
                    "✓ " + getString(R.string.grant_permission_button, getString(R.string.permission_write_settings))
                } else {
                    getString(R.string.grant_permission_button, getString(R.string.permission_write_settings))
                }
                writeSettingsButton.alpha = if (hasWriteSettings) 0.7f else 1.0f

                accessibilityButton.text = if (hasAccessibility) {
                    "✓ " + getString(R.string.grant_permission_button, getString(R.string.permission_accessibility))
                } else {
                    getString(R.string.grant_permission_button, getString(R.string.permission_accessibility))
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

    private fun addSpacing(parent: LinearLayout, dimenRes: Int) {
        val spacer = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(dimenRes)
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
                statusText.text = "Monitoring stopped. Use Start button to restart."
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
                showToast(getString(R.string.permission_granted, getString(R.string.permission_notification)))
            }
        } else {
            showToast("Permission not required on this Android version")
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
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (!permissionHelper.hasWriteSettingsPermission()) {
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                writeSettingsPermissionLauncher.launch(intent)
            } else {
                showToast(getString(R.string.permission_granted, getString(R.string.permission_write_settings)))
            }
        } else {
            showToast("Permission not required on this Android version")
        }
    }

    private fun requestAccessibilityPermission() {
        if (!permissionHelper.hasAccessibilityPermission()) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            accessibilityPermissionLauncher.launch(intent)
            showToast(getString(R.string.permission_guide_short))
        } else {
            showToast(getString(R.string.permission_granted, getString(R.string.permission_accessibility)))
        }
    }

    private fun completeSetup() {
        if (permissionHelper.hasAllPermissions()) {
            sharedPreferences.edit().putBoolean("setup_complete", true).apply()
            startServicesAndTransitionToMain()
        } else {
            showToast(getString(R.string.setup_description))
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
                showToast(getString(R.string.monitoring_started))

                lifecycleScope.launch {
                    delay(1500)
                    showMainUI()
                    startPermissionMonitoring()
                }
            } else {
                showToast(getString(R.string.service_start_failed))
            }
        } catch (e: Exception) {
            showToast(getString(R.string.service_error, e.message ?: "Unknown"))
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

            showToast(getString(R.string.monitoring_started))
        } catch (e: Exception) {
            showToast(getString(R.string.service_error, e.message ?: "Unknown"))
            e.printStackTrace()
        }
    }

    private fun showPermissionRequiredDialog(featureName: String, permissionName: String, onPositive: () -> Unit) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("$featureName requires $permissionName permission. Grant now?")
            .setPositiveButton("Grant") { _, _ -> onPositive() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
