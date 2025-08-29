package com.redskul.macrostatshelper

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var wifiDailyCheckbox: CheckBox
    private lateinit var wifiWeeklyCheckbox: CheckBox
    private lateinit var wifiMonthlyCheckbox: CheckBox
    private lateinit var mobileDailyCheckbox: CheckBox
    private lateinit var mobileWeeklyCheckbox: CheckBox
    private lateinit var mobileMonthlyCheckbox: CheckBox
    private lateinit var notificationEnabledSwitch: Switch
    private lateinit var saveButton: Button
    private lateinit var previewText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsManager = SettingsManager(this)
        createUI()
        loadCurrentSettings()
        setupListeners()
        updatePreview()
    }

    private fun createUI() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        // Title
        val titleText = TextView(this).apply {
            text = getString(R.string.settings_title)
            textSize = 24f
            setPadding(0, 0, 0, 24)
        }

        // Instructions
        val instructionText = TextView(this).apply {
            text = getString(R.string.settings_instruction)
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }

        // WiFi Section
        val wifiLabel = TextView(this).apply {
            text = getString(R.string.wifi_usage_label)
            textSize = 18f
            setPadding(0, 8, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
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
            textSize = 18f
            setPadding(0, 16, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
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

        // Notification Settings Section
        val notificationLabel = TextView(this).apply {
            text = getString(R.string.notification_settings_title)
            textSize = 18f
            setPadding(0, 24, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
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
                updatePreview()
            }
        }

        notificationLayout.addView(notificationSwitchLabel)
        notificationLayout.addView(notificationEnabledSwitch)

        val notificationDescription = TextView(this).apply {
            text = getString(R.string.notification_description)
            textSize = 12f
            setPadding(0, 0, 0, 8)
        }

        val notificationNote = TextView(this).apply {
            text = getString(R.string.notification_disabled_note)
            textSize = 11f
            setPadding(0, 0, 0, 16)
            alpha = 0.7f
        }

        // Preview Section
        val previewLabel = TextView(this).apply {
            text = getString(R.string.preview_label)
            textSize = 16f
            setPadding(0, 24, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        previewText = TextView(this).apply {
            text = getString(R.string.preview_default)
            textSize = 12f
            setPadding(16, 8, 16, 8)
            setBackgroundColor(0xFFF0F0F0.toInt())
        }

        // Save Button
        saveButton = Button(this).apply {
            text = getString(R.string.save_settings)
            setPadding(0, 24, 0, 0)
            setOnClickListener { saveSettings() }
        }

        // Add all views to main layout
        mainLayout.addView(titleText)
        mainLayout.addView(instructionText)
        mainLayout.addView(wifiLabel)
        mainLayout.addView(wifiDailyCheckbox)
        mainLayout.addView(wifiWeeklyCheckbox)
        mainLayout.addView(wifiMonthlyCheckbox)
        mainLayout.addView(mobileLabel)
        mainLayout.addView(mobileDailyCheckbox)
        mainLayout.addView(mobileWeeklyCheckbox)
        mainLayout.addView(mobileMonthlyCheckbox)
        mainLayout.addView(notificationLabel)
        mainLayout.addView(notificationLayout)
        mainLayout.addView(notificationDescription)
        mainLayout.addView(notificationNote)
        mainLayout.addView(previewLabel)
        mainLayout.addView(previewText)
        mainLayout.addView(saveButton)

        setContentView(ScrollView(this).apply { addView(mainLayout) })
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

        previewText.text = if (notificationEnabledSwitch.isChecked) {
            getString(R.string.notification_preview, shortText)
        } else {
            "Notification disabled - data will still be monitored for QS tiles"
        }
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

        // Save notification preference
        settingsManager.saveNotificationEnabled(notificationEnabledSwitch.isChecked)

        // Notify service about notification toggle change
        val serviceIntent = Intent(this, DataUsageService::class.java).apply {
            action = DataUsageService.ACTION_NOTIFICATION_TOGGLE_CHANGED
        }
        startService(serviceIntent)

        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }
}
