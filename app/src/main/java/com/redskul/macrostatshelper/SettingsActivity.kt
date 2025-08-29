// Add this to a new file: SettingsActivity.kt
package com.redskul.macrostatshelper

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var dataTypeSpinner: Spinner
    private lateinit var timePeriodSpinner: Spinner
    private lateinit var customSettingsLayout: LinearLayout
    private lateinit var wifiTimePeriodSpinner: Spinner
    private lateinit var mobileTimePeriodSpinner: Spinner
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

        // Data Type Section
        val dataTypeLabel = TextView(this).apply {
            text = "What to Display:"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }

        dataTypeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                listOf("WiFi Only", "Mobile Only", "Both", "Custom Mix")
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        // Time Period Section (for WiFi Only, Mobile Only, Both)
        val timePeriodLabel = TextView(this).apply {
            text = "Time Period:"
            textSize = 16f
            setPadding(0, 16, 0, 8)
        }

        timePeriodSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                listOf("Daily", "Weekly", "Monthly")
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        // Custom Settings Section
        customSettingsLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
            visibility = LinearLayout.GONE
        }

        val customLabel = TextView(this).apply {
            text = "Custom Time Periods:"
            textSize = 16f
            setPadding(0, 0, 0, 8)
        }

        val wifiLabel = TextView(this).apply {
            text = "WiFi Time Period:"
            textSize = 14f
            setPadding(0, 0, 0, 4)
        }

        wifiTimePeriodSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                listOf("Daily", "Weekly", "Monthly")
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        val mobileLabel = TextView(this).apply {
            text = "Mobile Time Period:"
            textSize = 14f
            setPadding(0, 8, 0, 4)
        }

        mobileTimePeriodSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@SettingsActivity,
                android.R.layout.simple_spinner_item,
                listOf("Daily", "Weekly", "Monthly")
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        customSettingsLayout.addView(customLabel)
        customSettingsLayout.addView(wifiLabel)
        customSettingsLayout.addView(wifiTimePeriodSpinner)
        customSettingsLayout.addView(mobileLabel)
        customSettingsLayout.addView(mobileTimePeriodSpinner)

        // Preview Section
        val previewLabel = TextView(this).apply {
            text = "Preview:"
            textSize = 16f
            setPadding(0, 24, 0, 8)
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
        mainLayout.addView(dataTypeLabel)
        mainLayout.addView(dataTypeSpinner)
        mainLayout.addView(timePeriodLabel)
        mainLayout.addView(timePeriodSpinner)
        mainLayout.addView(customSettingsLayout)
        mainLayout.addView(previewLabel)
        mainLayout.addView(previewText)
        mainLayout.addView(saveButton)

        setContentView(ScrollView(this).apply { addView(mainLayout) })
    }

    private fun setupListeners() {
        dataTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                when (position) {
                    3 -> { // Custom Mix
                        customSettingsLayout.visibility = LinearLayout.VISIBLE
                        timePeriodSpinner.visibility = Spinner.GONE
                    }
                    else -> {
                        customSettingsLayout.visibility = LinearLayout.GONE
                        timePeriodSpinner.visibility = Spinner.VISIBLE
                    }
                }
                updatePreview()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        timePeriodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updatePreview()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        wifiTimePeriodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updatePreview()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        mobileTimePeriodSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updatePreview()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadCurrentSettings() {
        val settings = settingsManager.getDisplaySettings()

        // Set data type spinner
        dataTypeSpinner.setSelection(when (settings.dataType) {
            DataType.WIFI_ONLY -> 0
            DataType.MOBILE_ONLY -> 1
            DataType.BOTH -> 2
            DataType.CUSTOM -> 3
        })

        // Set time period spinner
        timePeriodSpinner.setSelection(when (settings.timePeriod) {
            TimePeriod.DAILY -> 0
            TimePeriod.WEEKLY -> 1
            TimePeriod.MONTHLY -> 2
        })

        // Set custom settings if available
        settings.customSettings?.let { custom ->
            wifiTimePeriodSpinner.setSelection(when (custom.wifiTimePeriod) {
                TimePeriod.DAILY -> 0
                TimePeriod.WEEKLY -> 1
                TimePeriod.MONTHLY -> 2
            })

            mobileTimePeriodSpinner.setSelection(when (custom.mobileTimePeriod) {
                TimePeriod.DAILY -> 0
                TimePeriod.WEEKLY -> 1
                TimePeriod.MONTHLY -> 2
            })
        }
    }

    private fun updatePreview() {
        val sampleData = UsageData("125 MB", "850 MB", "3.2 GB", "45 MB", "320 MB", "1.8 GB")
        val (shortText, _) = settingsManager.getFormattedUsageText(sampleData)
        previewText.text = "Notification will show:\n$shortText"
    }

    private fun saveSettings() {
        val dataType = when (dataTypeSpinner.selectedItemPosition) {
            0 -> DataType.WIFI_ONLY
            1 -> DataType.MOBILE_ONLY
            2 -> DataType.BOTH
            3 -> DataType.CUSTOM
            else -> DataType.BOTH
        }

        val timePeriod = when (timePeriodSpinner.selectedItemPosition) {
            0 -> TimePeriod.DAILY
            1 -> TimePeriod.WEEKLY
            2 -> TimePeriod.MONTHLY
            else -> TimePeriod.DAILY
        }

        val customSettings = if (dataType == DataType.CUSTOM) {
            val wifiTimePeriod = when (wifiTimePeriodSpinner.selectedItemPosition) {
                0 -> TimePeriod.DAILY
                1 -> TimePeriod.WEEKLY
                2 -> TimePeriod.MONTHLY
                else -> TimePeriod.DAILY
            }

            val mobileTimePeriod = when (mobileTimePeriodSpinner.selectedItemPosition) {
                0 -> TimePeriod.DAILY
                1 -> TimePeriod.WEEKLY
                2 -> TimePeriod.MONTHLY
                else -> TimePeriod.DAILY
            }

            CustomDisplaySettings(wifiTimePeriod, mobileTimePeriod)
        } else {
            null
        }

        val settings = DisplaySettings(dataType, timePeriod, customSettings)
        settingsManager.saveDisplaySettings(settings)

        Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
        finish()
    }
}