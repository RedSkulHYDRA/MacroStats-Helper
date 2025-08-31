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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

                lastUsageStats = currentUsageStats
                lastAccessibility = currentAccessibility
            }
        }
    }

    private fun isFirstLaunch(): Boolean {
        return !sharedPreferences.getBoolean("setup_complete", false)
    }

    private fun showMainUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
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

        // AutoSync status
        val autoSyncStatusText = TextView(this).apply {
            val isAccessibilityEnabled = permissionHelper.hasAccessibilityPermission()
            text = if (isAccessibilityEnabled) {
                getString(R.string.autosync_service_running)
            } else {
                getString(R.string.autosync_service_disabled)
            }
            setPadding(0, 0, 0, 32)
            setTextColor(if (isAccessibilityEnabled) 0xFF4CAF50.toInt() else 0xFFFF5722.toInt())
        }

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
        layout.addView(autoSyncStatusText)
        layout.addView(settingsButton)
        layout.addView(qsTileSettingsButton)
        layout.addView(stopServiceButton)

        setContentView(layout)

        ensureServicesRunning()
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

    private fun showPermissionSetupUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(64, 64, 64, 64)
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
        layout.addView(startButton)

        setContentView(layout)

        lifecycleScope.launch {
            while (true) {
                val hasNotification = permissionHelper.hasNotificationPermission()
                val hasUsageStats = permissionHelper.hasUsageStatsPermission()
                val hasWriteSettings = permissionHelper.hasWriteSettingsPermission()
                val hasAccessibility = permissionHelper.hasAccessibilityPermission()

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

                startButton.isEnabled = hasNotification && hasUsageStats && hasWriteSettings && hasAccessibility
                startButton.alpha = if (startButton.isEnabled) 1.0f else 0.5f

                delay(1000)
            }
        }
    }

    private fun ensureServicesRunning() {
        try {
            val dataServiceIntent = Intent(this, DataUsageService::class.java)
            startForegroundService(dataServiceIntent)

            val batteryServiceIntent = Intent(this, BatteryService::class.java)
            startService(batteryServiceIntent)
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
            val dataResult = startForegroundService(dataServiceIntent)

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
            startForegroundService(dataServiceIntent)

            val batteryServiceIntent = Intent(this, BatteryService::class.java)
            startService(batteryServiceIntent)

            showToast("Monitoring started successfully")
        } catch (e: Exception) {
            showToast("Error starting monitoring: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
