package com.redskul.macrostatshelper.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.data.DataUsageService
import com.redskul.macrostatshelper.data.UsageData
import com.redskul.macrostatshelper.autosync.AutoSyncManager
import com.redskul.macrostatshelper.autosync.AutoSyncAccessibilityService
import com.redskul.macrostatshelper.utils.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var autoSyncManager: AutoSyncManager
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var wifiDailyCheckbox: CheckBox
    private lateinit var wifiWeeklyCheckbox: CheckBox
    private lateinit var wifiMonthlyCheckbox: CheckBox
    private lateinit var mobileDailyCheckbox: CheckBox
    private lateinit var mobileWeeklyCheckbox: CheckBox
    private lateinit var mobileMonthlyCheckbox: CheckBox
    private lateinit var notificationEnabledSwitch: Switch
    private lateinit var autoSyncEnabledSwitch: Switch
    private lateinit var autoSyncDelaySpinner: Spinner
    private lateinit var saveButton: Button
    private lateinit var previewText: TextView
    private lateinit var accessibilityStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager(this)
        autoSyncManager = AutoSyncManager(this)
        permissionHelper = PermissionHelper(this)
        createUI()
        loadCurrentSettings()
        setupListeners()
        updatePreview()
        startPermissionMonitoring()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        updatePermissionBasedUI()
    }

    private fun startPermissionMonitoring() {
        lifecycleScope.launch {
            var lastUsageStats = permissionHelper.hasUsageStatsPermission()
            var lastAccessibility = permissionHelper.hasAccessibilityPermission()

            while (true) {
                delay(1000)

                val currentUsageStats = permissionHelper.hasUsageStatsPermission()
                val currentAccessibility = permissionHelper.hasAccessibilityPermission()

                if (lastUsageStats != currentUsageStats || lastAccessibility != currentAccessibility) {
                    updatePermissionBasedUI()
                    if (lastUsageStats && !currentUsageStats) {
                        notificationEnabledSwitch.isChecked = false
                        showToast("Data usage features disabled due to missing permission")
                    }
                    if (lastAccessibility && !currentAccessibility) {
                        autoSyncEnabledSwitch.isChecked = false
                        showToast("AutoSync features disabled due to missing permission")
                    }
                }

                lastUsageStats = currentUsageStats
                lastAccessibility = currentAccessibility
            }
        }
    }

    private fun updatePermissionBasedUI() {
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()
        val hasAccessibility = permissionHelper.hasAccessibilityPermission()

        // Update notification switch
        notificationEnabledSwitch.isEnabled = hasUsageStats
        if (!hasUsageStats && notificationEnabledSwitch.isChecked) {
            notificationEnabledSwitch.isChecked = false
        }

        // Update AutoSync switch
        autoSyncEnabledSwitch.isEnabled = hasAccessibility
        autoSyncDelaySpinner.isEnabled = hasAccessibility && autoSyncEnabledSwitch.isChecked

        if (!hasAccessibility && autoSyncEnabledSwitch.isChecked) {
            autoSyncEnabledSwitch.isChecked = false
        }
    }

    private fun createUI() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        // Title
        val titleText = TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 26f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(8, 0, 8, 32)
        }

        // Instructions
        val instructionText = TextView(this).apply {
            text = getString(R.string.settings_instruction)
            textSize = 14f
            setPadding(8, 0, 8, 24)
            alpha = 0.8f
        }

        // Data Usage Settings Card
        val dataUsageCard = createDataUsageCard()

        // Notification Settings Card
        val notificationCard = createNotificationCard()

        // AutoSync Settings Card
        val autoSyncCard = createAutoSyncCard()

        // Preview Card
        val previewCard = createPreviewCard()

        // Save Button
        saveButton = Button(this).apply {
            text = getString(R.string.save_settings)
            textSize = 16f
            setPadding(32, 16, 32, 16)
            setBackgroundResource(android.R.drawable.btn_default)
            setOnClickListener { saveSettings() }
        }

        // Add all components to main layout
        mainLayout.addView(titleText)
        mainLayout.addView(instructionText)
        mainLayout.addView(dataUsageCard)
        addSpacing(mainLayout, 16)
        mainLayout.addView(notificationCard)
        addSpacing(mainLayout, 16)
        mainLayout.addView(autoSyncCard)
        addSpacing(mainLayout, 16)
        mainLayout.addView(previewCard)
        addSpacing(mainLayout, 24)
        mainLayout.addView(saveButton)

        setContentView(ScrollView(this).apply { addView(mainLayout) })
    }

    private fun createDataUsageCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.card_background, theme)
        }

        val cardTitle = TextView(this).apply {
            text = "Data Usage Display"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        // WiFi Section
        val wifiLabel = TextView(this).apply {
            text = getString(R.string.wifi_usage_label)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        wifiDailyCheckbox = CheckBox(this).apply {
            text = getString(R.string.daily)
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        wifiWeeklyCheckbox = CheckBox(this).apply {
            text = getString(R.string.weekly)
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        wifiMonthlyCheckbox = CheckBox(this).apply {
            text = getString(R.string.monthly)
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        // Mobile Section
        val mobileLabel = TextView(this).apply {
            text = getString(R.string.mobile_data_usage_label)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }

        mobileDailyCheckbox = CheckBox(this).apply {
            text = getString(R.string.daily)
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        mobileWeeklyCheckbox = CheckBox(this).apply {
            text = getString(R.string.weekly)
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        mobileMonthlyCheckbox = CheckBox(this).apply {
            text = getString(R.string.monthly)
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        card.addView(cardTitle)
        card.addView(wifiLabel)
        card.addView(wifiDailyCheckbox)
        card.addView(wifiWeeklyCheckbox)
        card.addView(wifiMonthlyCheckbox)
        card.addView(mobileLabel)
        card.addView(mobileDailyCheckbox)
        card.addView(mobileWeeklyCheckbox)
        card.addView(mobileMonthlyCheckbox)

        return card
    }

    private fun createNotificationCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.card_background, theme)
        }

        val cardTitle = TextView(this).apply {
            text = getString(R.string.notification_settings_title)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        val notificationLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }

        val notificationSwitchLabel = TextView(this).apply {
            text = getString(R.string.show_data_usage_notification)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        notificationEnabledSwitch = Switch(this).apply {
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !permissionHelper.hasUsageStatsPermission()) {
                    this.isChecked = false
                    showPermissionRequiredDialog("Data Usage Notification", "Usage Stats") {
                        requestUsageStatsPermission()
                    }
                    return@setOnCheckedChangeListener
                }
                updatePreview()
            }
        }

        notificationLayout.addView(notificationSwitchLabel)
        notificationLayout.addView(notificationEnabledSwitch)

        val notificationDescription = TextView(this).apply {
            text = getString(R.string.notification_description)
            textSize = 12f
            setPadding(0, 0, 0, 8)
            alpha = 0.7f
        }

        val notificationNote = TextView(this).apply {
            text = getString(R.string.notification_disabled_note)
            textSize = 11f
            setPadding(0, 0, 0, 0)
            alpha = 0.6f
        }

        card.addView(cardTitle)
        card.addView(notificationLayout)
        card.addView(notificationDescription)
        card.addView(notificationNote)

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
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !permissionHelper.hasAccessibilityPermission()) {
                    this.isChecked = false
                    showPermissionRequiredDialog("AutoSync Management", "Accessibility Service") {
                        requestAccessibilityPermission()
                    }
                    return@setOnCheckedChangeListener
                }
                autoSyncDelaySpinner.isEnabled = isChecked && permissionHelper.hasAccessibilityPermission()
                updatePreview()
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
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                autoSyncManager.getDelayOptions()
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    updatePreview()
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            })
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

        return card
    }

    private fun createPreviewCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.card_background, theme)
        }

        val previewLabel = TextView(this).apply {
            text = getString(R.string.preview_label)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        previewText = TextView(this).apply {
            text = getString(R.string.preview_default)
            textSize = 12f
            setPadding(12, 12, 12, 12)
            setBackgroundColor(0xFFF5F5F5.toInt())
            setTypeface(android.graphics.Typeface.MONOSPACE)
        }

        card.addView(previewLabel)
        card.addView(previewText)

        return card
    }

    private fun addSpacing(parent: LinearLayout, dpSize: Int) {
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (dpSize * resources.displayMetrics.density).toInt()
            )
        }
        parent.addView(spacer)
    }

    private fun setupListeners() {
        // Listeners are already set in createUI()
    }

    private fun loadCurrentSettings() {
        val settings = settingsManager.getDisplaySettings()

        // Set WiFi checkboxes
        wifiDailyCheckbox.isChecked = settings.wifiTimePeriods.contains(TimePeriod.DAILY)
        wifiWeeklyCheckbox.isChecked = settings.wifiTimePeriods.contains(TimePeriod.WEEKLY)
        wifiMonthlyCheckbox.isChecked = settings.wifiTimePeriods.contains(TimePeriod.MONTHLY)

        // Set Mobile checkboxes
        mobileDailyCheckbox.isChecked = settings.mobileTimePeriods.contains(TimePeriod.DAILY)
        mobileWeeklyCheckbox.isChecked = settings.mobileTimePeriods.contains(TimePeriod.WEEKLY)
        mobileMonthlyCheckbox.isChecked = settings.mobileTimePeriods.contains(TimePeriod.MONTHLY)

        // Set notification switch
        notificationEnabledSwitch.isChecked = settingsManager.isNotificationEnabled()

        // Set AutoSync settings
        autoSyncEnabledSwitch.isChecked = autoSyncManager.isAutoSyncEnabled()

        // Set delay spinner selection based on saved value
        val delayMinutes = autoSyncManager.getAutoSyncDelay()
        val delayIndex = autoSyncManager.getAllowedDelays().indexOf(delayMinutes)
        if (delayIndex >= 0) {
            autoSyncDelaySpinner.setSelection(delayIndex)
        }

        updateAccessibilityStatus()
        updatePermissionBasedUI()
    }

    private fun updateAccessibilityStatus() {
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

    private fun updatePreview() {
        val sampleData = UsageData("125 MB", "850 MB", "3.2 GB", "45 MB", "320 MB", "1.8 GB")

        // Create temporary settings based on current checkbox states
        val wifiPeriods = mutableListOf<TimePeriod>()
        if (wifiDailyCheckbox.isChecked) wifiPeriods.add(TimePeriod.DAILY)
        if (wifiWeeklyCheckbox.isChecked) wifiPeriods.add(TimePeriod.WEEKLY)
        if (wifiMonthlyCheckbox.isChecked) wifiPeriods.add(TimePeriod.MONTHLY)

        val mobilePeriods = mutableListOf<TimePeriod>()
        if (mobileDailyCheckbox.isChecked) mobilePeriods.add(TimePeriod.DAILY)
        if (mobileWeeklyCheckbox.isChecked) mobilePeriods.add(TimePeriod.WEEKLY)
        if (mobileMonthlyCheckbox.isChecked) mobilePeriods.add(TimePeriod.MONTHLY)

        val tempSettings = DisplaySettings(wifiPeriods, mobilePeriods)

        // Save temporarily to get formatted text
        val originalSettings = settingsManager.getDisplaySettings()
        settingsManager.saveDisplaySettings(tempSettings)
        val (shortText, _) = settingsManager.getFormattedUsageText(sampleData)
        settingsManager.saveDisplaySettings(originalSettings) // Restore original settings

        val autoSyncEnabled = autoSyncEnabledSwitch.isChecked
        val delayMinutes = if (autoSyncDelaySpinner.selectedItemPosition >= 0) {
            autoSyncManager.getAllowedDelays()[autoSyncDelaySpinner.selectedItemPosition]
        } else {
            autoSyncManager.getAutoSyncDelay()
        }

        val isAccessibilityEnabled = AutoSyncAccessibilityService.isAccessibilityServiceEnabled(this)
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()

        previewText.text = buildString {
            if (notificationEnabledSwitch.isChecked && hasUsageStats) {
                appendLine("📱 ${getString(R.string.notification_preview, shortText)}")
            } else if (notificationEnabledSwitch.isChecked && !hasUsageStats) {
                appendLine("📱 Notification disabled - Usage stats permission required")
            } else {
                appendLine("📱 Notification disabled - data will still be monitored for QS tiles")
            }
            appendLine()
            if (autoSyncEnabled && isAccessibilityEnabled) {
                appendLine("🔄 ${getString(R.string.autosync_preview, delayMinutes)}")
            } else if (autoSyncEnabled && !isAccessibilityEnabled) {
                appendLine("🔄 AutoSync enabled but requires accessibility service permission")
            } else {
                appendLine("🔄 AutoSync management disabled")
            }
        }
    }

    private fun showPermissionRequiredDialog(featureName: String, permissionName: String, onPositive: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("$featureName requires $permissionName permission to work. Would you like to grant it now?")
            .setPositiveButton("Grant") { _, _ -> onPositive() }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
        showToast("Please enable usage access for MacroStats Helper")
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        showToast("Please enable accessibility service for MacroStats Helper")
    }

    private fun saveSettings() {
        val wifiPeriods = mutableListOf<TimePeriod>()
        if (wifiDailyCheckbox.isChecked) wifiPeriods.add(TimePeriod.DAILY)
        if (wifiWeeklyCheckbox.isChecked) wifiPeriods.add(TimePeriod.WEEKLY)
        if (wifiMonthlyCheckbox.isChecked) wifiPeriods.add(TimePeriod.MONTHLY)

        val mobilePeriods = mutableListOf<TimePeriod>()
        if (mobileDailyCheckbox.isChecked) mobilePeriods.add(TimePeriod.DAILY)
        if (mobileWeeklyCheckbox.isChecked) mobilePeriods.add(TimePeriod.WEEKLY)
        if (mobileMonthlyCheckbox.isChecked) mobilePeriods.add(TimePeriod.MONTHLY)

        val settings = DisplaySettings(wifiPeriods, mobilePeriods)
        settingsManager.saveDisplaySettings(settings)

        // Save notification preference (will be validated by SettingsManager)
        settingsManager.saveNotificationEnabled(notificationEnabledSwitch.isChecked)

        // Save AutoSync settings (will be validated by AutoSyncManager)
        autoSyncManager.setAutoSyncEnabled(autoSyncEnabledSwitch.isChecked)

        // Map spinner selection to minutes
        val selectedIndex = autoSyncDelaySpinner.selectedItemPosition
        if (selectedIndex >= 0) {
            val delayMinutes = autoSyncManager.getAllowedDelays()[selectedIndex]
            autoSyncManager.setAutoSyncDelay(delayMinutes)
        }

        // Notify data service about notification changes
        val dataServiceIntent = Intent(this, DataUsageService::class.java).apply {
            action = DataUsageService.ACTION_NOTIFICATION_TOGGLE_CHANGED
        }
        startService(dataServiceIntent)

        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
