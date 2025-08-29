package com.redskul.macrostatshelper

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
            text = "Display Settings"
            textSize = 24f
            setPadding(0, 0, 0, 24)
        }

        // Instructions
        val instructionText = TextView(this).apply {
            text = "Select which time periods to display for each data type:"
            textSize = 14f
            setPadding(0, 0, 0, 16)
        }

        // WiFi Section
        val wifiLabel = TextView(this).apply {
            text = "WiFi Usage:"
            textSize = 18f
            setPadding(0, 8, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        wifiDailyCheckbox = CheckBox(this).apply {
            text = "Daily"
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        wifiWeeklyCheckbox = CheckBox(this).apply {
            text = "Weekly"
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        wifiMonthlyCheckbox = CheckBox(this).apply {
            text = "Monthly"
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        // Mobile Section
        val mobileLabel = TextView(this).apply {
            text = "Mobile Data Usage:"
            textSize = 18f
            setPadding(0, 16, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        mobileDailyCheckbox = CheckBox(this).apply {
            text = "Daily"
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        mobileWeeklyCheckbox = CheckBox(this).apply {
            text = "Weekly"
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        mobileMonthlyCheckbox = CheckBox(this).apply {
            text = "Monthly"
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        // Preview Section
        val previewLabel = TextView(this).apply {
            text = "Preview:"
            textSize = 16f
            setPadding(0, 24, 0, 8)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        previewText = TextView(this).apply {
            text = "Preview will appear here"
            textSize = 12f
            setPadding(16, 8, 16, 8)
            setBackgroundColor(0xFFF0F0F0.toInt())
        }

        // Save Button
        saveButton = Button(this).apply {
            text = "Save Settings"
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

        previewText.text = "Notification will show:\n$shortText"
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

        Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
