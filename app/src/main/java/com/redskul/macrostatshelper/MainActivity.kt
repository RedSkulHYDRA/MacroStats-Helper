package com.redskul.macrostatshelper

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.startForegroundService
import androidx.lifecycle.lifecycleScope
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
            completeSetup()
        } else {
            showToast(getString(R.string.usage_stats_permission_required))
        }
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
        layout.addView(stopServiceButton)

        setContentView(layout)

        // Ensure service is running
        ensureServiceRunning()
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
        layout.addView(startButton)

        setContentView(layout)

        // Update button states using coroutines
        lifecycleScope.launch {
            while (true) {
                usageStatsButton.isEnabled = hasNotificationPermission()
                startButton.isEnabled = hasNotificationPermission() && hasUsageStatsPermission()
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
        val intent = Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
        usageStatsPermissionLauncher.launch(intent)
    }

    private fun checkUsageStatsPermission() {
        if (!hasUsageStatsPermission()) {
            requestUsageStatsPermission()
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

    private fun completeSetup() {
        if (hasNotificationPermission() && hasUsageStatsPermission()) {
            sharedPreferences.edit().putBoolean("setup_complete", true).apply()
            startServiceAndFinish()
        } else {
            showToast(getString(R.string.grant_all_permissions))
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
