package com.redskul.macrostatshelper

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class QSTileSettingsActivity : AppCompatActivity() {

    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private lateinit var wifiTileSpinner: Spinner
    private lateinit var mobileTileSpinner: Spinner
    private lateinit var saveButton: Button
    private lateinit var previewText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        qsTileSettingsManager = QSTileSettingsManager(this)
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

        val titleText = TextView(this).apply {
            text = getString(R.string.qs_tile_settings_title)
            textSize = 24f
            setPadding(0, 0, 0, 24)
        }

        val instructionText = TextView(this).apply {
            text = getString(R.string.qs_tile_instruction)
            textSize = 14f
            setPadding(0, 0, 0, 24)
        }

        // WiFi Tile Section
        val wifiLabel = TextView(this).apply {
            text = getString(R.string.wifi_tile_label)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        val wifiDescription = TextView(this).apply {
            text = getString(R.string.wifi_tile_description)
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }

        wifiTileSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@QSTileSettingsActivity,
                android.R.layout.simple_spinner_item,
                listOf(getString(R.string.daily), getString(R.string.weekly), getString(R.string.monthly))
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        // Mobile Tile Section
        val mobileLabel = TextView(this).apply {
            text = getString(R.string.mobile_tile_label)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }

        val mobileDescription = TextView(this).apply {
            text = getString(R.string.mobile_tile_description)
            textSize = 14f
            setPadding(0, 0, 0, 8)
        }

        mobileTileSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@QSTileSettingsActivity,
                android.R.layout.simple_spinner_item,
                listOf(getString(R.string.daily), getString(R.string.weekly), getString(R.string.monthly))
            ).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }

        // Preview Section
        val previewLabel = TextView(this).apply {
            text = getString(R.string.preview_label)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 24, 0, 8)
        }

        previewText = TextView(this).apply {
            text = getString(R.string.preview_default)
            textSize = 12f
            setPadding(16, 8, 16, 8)
            setBackgroundColor(0xFFF0F0F0.toInt())
        }

        val instructionText2 = TextView(this).apply {
            text = getString(R.string.qs_tile_instruction_2)
            textSize = 12f
            setPadding(0, 16, 0, 0)
        }

        saveButton = Button(this).apply {
            text = getString(R.string.save_settings)
            setPadding(0, 24, 0, 0)
            setOnClickListener { saveSettings() }
        }

        mainLayout.addView(titleText)
        mainLayout.addView(instructionText)
        mainLayout.addView(wifiLabel)
        mainLayout.addView(wifiDescription)
        mainLayout.addView(wifiTileSpinner)
        mainLayout.addView(mobileLabel)
        mainLayout.addView(mobileDescription)
        mainLayout.addView(mobileTileSpinner)
        mainLayout.addView(previewLabel)
        mainLayout.addView(previewText)
        mainLayout.addView(instructionText2)
        mainLayout.addView(saveButton)

        setContentView(ScrollView(this).apply { addView(mainLayout) })
    }

    private fun setupListeners() {
        wifiTileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updatePreview()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        mobileTileSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updatePreview()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadCurrentSettings() {
        val wifiPeriod = qsTileSettingsManager.getWiFiTilePeriod()
        val mobilePeriod = qsTileSettingsManager.getMobileTilePeriod()

        wifiTileSpinner.setSelection(when (wifiPeriod) {
            TimePeriod.DAILY -> 0
            TimePeriod.WEEKLY -> 1
            TimePeriod.MONTHLY -> 2
        })

        mobileTileSpinner.setSelection(when (mobilePeriod) {
            TimePeriod.DAILY -> 0
            TimePeriod.WEEKLY -> 1
            TimePeriod.MONTHLY -> 2
        })
    }

    private fun updatePreview() {
        val sampleData = UsageData("125 MB", "850 MB", "3.2 GB", "45 MB", "320 MB", "1.8 GB")

        val wifiPeriod = when (wifiTileSpinner.selectedItemPosition) {
            0 -> TimePeriod.DAILY
            1 -> TimePeriod.WEEKLY
            2 -> TimePeriod.MONTHLY
            else -> TimePeriod.DAILY
        }

        val mobilePeriod = when (mobileTileSpinner.selectedItemPosition) {
            0 -> TimePeriod.DAILY
            1 -> TimePeriod.WEEKLY
            2 -> TimePeriod.MONTHLY
            else -> TimePeriod.DAILY
        }

        val wifiValue = when (wifiPeriod) {
            TimePeriod.DAILY -> sampleData.wifiDaily
            TimePeriod.WEEKLY -> sampleData.wifiWeekly
            TimePeriod.MONTHLY -> sampleData.wifiMonthly
        }

        val mobileValue = when (mobilePeriod) {
            TimePeriod.DAILY -> sampleData.mobileDaily
            TimePeriod.WEEKLY -> sampleData.mobileWeekly
            TimePeriod.MONTHLY -> sampleData.mobileMonthly
        }

        previewText.text = buildString {
            appendLine(getString(R.string.wifi_tile_preview, wifiPeriod.name.lowercase(), wifiValue))
            appendLine()
            appendLine(getString(R.string.mobile_tile_preview, mobilePeriod.name.lowercase(), mobileValue))
        }
    }

    private fun saveSettings() {
        val wifiPeriod = when (wifiTileSpinner.selectedItemPosition) {
            0 -> TimePeriod.DAILY
            1 -> TimePeriod.WEEKLY
            2 -> TimePeriod.MONTHLY
            else -> TimePeriod.DAILY
        }

        val mobilePeriod = when (mobileTileSpinner.selectedItemPosition) {
            0 -> TimePeriod.DAILY
            1 -> TimePeriod.WEEKLY
            2 -> TimePeriod.MONTHLY
            else -> TimePeriod.DAILY
        }

        qsTileSettingsManager.saveWiFiTilePeriod(wifiPeriod)
        qsTileSettingsManager.saveMobileTilePeriod(mobilePeriod)

        Toast.makeText(this, getString(R.string.qs_settings_saved), Toast.LENGTH_LONG).show()
        finish()
    }
}
