package com.redskul.macrostatshelper.core

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
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
import com.redskul.macrostatshelper.tiles.QSTileSettingsActivity
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.settings.SettingsActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkUsageStatsPermission()
        } else {
            showToast(getString(R.string.notification_permission_required))
        }
    }

    private val usageStatsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasUsageStatsPermission()) {
            checkAccessibilityPermission()
        } else {
            showToast(getString(R.string.usage_stats_permission_required))
        }
    }

    private val accessibilityPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Note: We don't require accessibility permission to complete setup
        // It's optional for enhanced features
        completeSetup()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

        if (isFirstLaunch()) {
            showPermissionSetupUI()
        } else {
            showMainUI()
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
            setPadding(0, 0, 0, 32)
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

        val accessibilityButton = Button(this).apply {
            text = if (hasAccessibilityPermission()) {
                getString(R.string.accessibility_enabled)
            } else {
                getString(R.string.enable_accessibility_service)
            }
            setPadding(0, 16, 0, 16)
            setOnClickListener {
                requestAccessibilityPermission()
            }
            isEnabled = !hasAccessibilityPermission()
        }

        val stopServiceButton = Button(this).apply {
            text = getString(R.string.stop_monitoring)
            setPadding(0, 16, 0, 16)
            setOnClickListener {
                stopService(Intent(this@MainActivity, DataUsageService::class.java))
                showToast(getString(R.string.monitoring_stopped))
                finish()
            }
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(settingsButton)
        layout.addView(qsTileSettingsButton)
        layout.addView(accessibilityButton)
        layout.addView(stopServiceButton)

        setContentView(layout)

        // Ensure service is running
        ensureServiceRunning()

        // Update accessibility button state periodically
        lifecycleScope.launch {
            while (true) {
                val hasAccessibility = hasAccessibilityPermission()
                accessibilityButton.text = if (hasAccessibility) {
                    getString(R.string.accessibility_enabled)
                } else {
                    getString(R.string.enable_accessibility_service)
                }
                accessibilityButton.isEnabled = !hasAccessibility
                delay(2000)
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
            text = getString(R.string.setup_description)
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
            isEnabled = hasNotificationPermission()
        }

        val accessibilityButton = Button(this).apply {
            text = getString(R.string.grant_accessibility_permission)
            setPadding(0, 16, 0, 16)
            setOnClickListener { requestAccessibilityPermission() }
            isEnabled = hasNotificationPermission() && hasUsageStatsPermission()
        }

        val accessibilityNote = TextView(this).apply {
            text = getString(R.string.accessibility_optional_note)
            textSize = 12f
            setPadding(0, 0, 0, 16)
            alpha = 0.7f
        }

        val startButton = Button(this).apply {
            text = getString(R.string.start_monitoring)
            setPadding(0, 16, 0, 16)
            setOnClickListener { completeSetup() }
            isEnabled = hasNotificationPermission() && hasUsageStatsPermission()
        }

        layout.addView(titleText)
        layout.addView(descriptionText)
        layout.addView(notificationButton)
        layout.addView(usageStatsButton)
        layout.addView(accessibilityButton)
        layout.addView(accessibilityNote)
        layout.addView(startButton)

        setContentView(layout)

        // Update button states using coroutines
        lifecycleScope.launch {
            while (true) {
                val hasNotification = hasNotificationPermission()
                val hasUsageStats = hasUsageStatsPermission()
                val hasAccessibility = hasAccessibilityPermission()

                usageStatsButton.isEnabled = hasNotification
                accessibilityButton.isEnabled = hasNotification && hasUsageStats
                startButton.isEnabled = hasNotification && hasUsageStats

                // Update accessibility button text
                accessibilityButton.text = if (hasAccessibility) {
                    getString(R.string.accessibility_granted)
                } else {
                    getString(R.string.grant_accessibility_permission)
                }

                delay(1000)
            }
        }
    }

    private fun ensureServiceRunning() {
        try {
            val serviceIntent = Intent(this, DataUsageService::class.java)
            startForegroundService(serviceIntent)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error ensuring service is running", e)
        }
    }

    private fun requestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkUsageStatsPermission()
            }
        } else {
            checkUsageStatsPermission()
        }
    }

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        usageStatsPermissionLauncher.launch(intent)
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        accessibilityPermissionLauncher.launch(intent)
        showToast(getString(R.string.accessibility_permission_instruction))
    }

    private fun checkUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
        } else {
            checkAccessibilityPermission()
        }
    }

    private fun checkAccessibilityPermission() {
        if (!hasAccessibilityPermission()) {
            // Don't automatically request accessibility - let user decide
            android.util.Log.d("MainActivity", "Accessibility service not enabled, but continuing setup")
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun hasAccessibilityPermission(): Boolean {
        return try {
            val accessibilityEnabled = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )

            if (accessibilityEnabled == 1) {
                val enabledServices = Settings.Secure.getString(
                    contentResolver,
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )
                val serviceName = "$packageName/com.redskul.macrostatshelper.accessibility.MacroStatsAccessibilityService"
                enabledServices?.contains(serviceName) == true
            } else {
                false
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error checking accessibility permission", e)
            false
        }
    }

    private fun completeSetup() {
        if (hasNotificationPermission() && hasUsageStatsPermission()) {
            sharedPreferences.edit().putBoolean("setup_complete", true).apply()
            startServiceAndFinish()
        } else {
            showToast(getString(R.string.grant_required_permissions))
        }
    }

    private fun startServiceAndFinish() {
        try {
            val serviceIntent = Intent(this, DataUsageService::class.java)

            // Start the service
            val result = startForegroundService(serviceIntent)

            if (result != null) {
                showToast(getString(R.string.monitoring_started_success))

                // Use coroutines for delay instead of Handler
                lifecycleScope.launch {
                    delay(3000) // 3 second delay
                    finishAndRemoveTask()
                }
            } else {
                showToast(getString(R.string.service_start_failed))
            }
        } catch (e: Exception) {
            showToast(getString(R.string.service_error, e.message))
            e.printStackTrace()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}