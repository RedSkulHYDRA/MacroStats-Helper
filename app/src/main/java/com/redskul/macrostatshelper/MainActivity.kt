// Updated MainActivity.kt
package com.redskul.macrostatshelper

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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startForegroundService

class MainActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var fileManager: FileManager

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkUsageStatsPermission()
        } else {
            showToast("Notification permission is required for the app to work properly")
        }
    }

    private val usageStatsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasUsageStatsPermission()) {
            completeSetup()
        } else {
            showToast("Usage stats permission is required to monitor data usage")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        fileManager = FileManager(this)

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
            text = "MacroStats Helper"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }

        val statusText = TextView(this).apply {
            text = "Data usage monitoring is running in the background.\n\nTap notification to access settings or use the button below."
            setPadding(0, 0, 0, 32)
        }

        val settingsButton = Button(this).apply {
            text = "Display Settings"
            setPadding(0, 16, 0, 16)
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }

        val stopServiceButton = Button(this).apply {
            text = "Stop Monitoring"
            setPadding(0, 16, 0, 16)
            setOnClickListener {
                stopService(Intent(this@MainActivity, DataUsageService::class.java))
                showToast("Monitoring stopped")
                finish()
            }
        }

        val pathsText = TextView(this).apply {
            text = buildString {
                appendLine("File paths for MacroDroid:")
                fileManager.getFilePaths().forEach { (key, path) ->
                    appendLine("$key: $path")
                }
            }
            setPadding(0, 32, 0, 0)
            textSize = 12f
        }

        layout.addView(titleText)
        layout.addView(statusText)
        layout.addView(settingsButton)
        layout.addView(stopServiceButton)
        layout.addView(pathsText)

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
            text = "MacroStats Helper Setup"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }

        val descriptionText = TextView(this).apply {
            text = "This app needs permissions to monitor your data usage and show notifications. Please grant the required permissions."
            setPadding(0, 0, 0, 32)
        }

        val notificationButton = Button(this).apply {
            text = "Grant Notification Permission"
            setPadding(0, 16, 0, 16)
            setOnClickListener { requestNotificationPermission() }
        }

        val usageStatsButton = Button(this).apply {
            text = "Grant Usage Stats Permission"
            setPadding(0, 16, 0, 16)
            setOnClickListener { requestUsageStatsPermission() }
            isEnabled = hasNotificationPermission()
        }

        val startButton = Button(this).apply {
            text = "Start Monitoring"
            setPadding(0, 16, 0, 16)
            setOnClickListener { completeSetup() }
            isEnabled = hasNotificationPermission() && hasUsageStatsPermission()
        }

        val pathsText = TextView(this).apply {
            text = buildString {
                appendLine("File paths for MacroDroid:")
                fileManager.getFilePaths().forEach { (key, path) ->
                    appendLine("$key: $path")
                }
            }
            setPadding(0, 32, 0, 0)
            textSize = 12f
        }

        layout.addView(titleText)
        layout.addView(descriptionText)
        layout.addView(notificationButton)
        layout.addView(usageStatsButton)
        layout.addView(startButton)
        layout.addView(pathsText)

        setContentView(layout)

        // Update button states periodically
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val updateButtons = object : Runnable {
            override fun run() {
                usageStatsButton.isEnabled = hasNotificationPermission()
                startButton.isEnabled = hasNotificationPermission() && hasUsageStatsPermission()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(updateButtons)
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
            showToast("Please grant all required permissions")
        }
    }

    private fun startServiceAndFinish() {
        try {
            val serviceIntent = Intent(this, DataUsageService::class.java)

            // Start the service
            val result = startForegroundService(serviceIntent)

            if (result != null) {
                showToast("Data usage monitoring started successfully")

                // Give the service time to start, then finish
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    finishAndRemoveTask()
                }, 3000) // 3 second delay
            } else {
                showToast("Failed to start monitoring service")
            }
        } catch (e: Exception) {
            showToast("Error starting service: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}