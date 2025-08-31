package com.redskul.macrostatshelper.tiles

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.settings.TimePeriod
import com.redskul.macrostatshelper.data.UsageData

class QSTileSettingsActivity : AppCompatActivity() {

    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private lateinit var wifiTileSpinner: Spinner
    private lateinit var mobileTileSpinner: Spinner
    private lateinit var showPeriodInTitleSwitch: Switch
    private lateinit var showChargeInTitleSwitch: Switch
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

        // Data Usage Tile Display Mode Section
        val displayModeLabel = TextView(this).apply {
            text = getString(R.string.tile_display_mode_label)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        val switchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }

        val switchLabelText = TextView(this).apply {
            text = getString(R.string.show_period_in_title)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        showPeriodInTitleSwitch = Switch(this).apply {
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        switchLayout.addView(switchLabelText)
        switchLayout.addView(showPeriodInTitleSwitch)

        val switchDescription = TextView(this).apply {
            text = getString(R.string.tile_display_mode_description)
            textSize = 12f
            setPadding(0, 0, 0, 16)
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

        // Charge Cycles Tile Section
        val chargeLabel = TextView(this).apply {
            text = getString(R.string.charge_tile_label)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }

        val chargeSwitchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }

        val chargeSwitchLabelText = TextView(this).apply {
            text = getString(R.string.show_charge_cycles_in_title)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        showChargeInTitleSwitch = Switch(this).apply {
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        chargeSwitchLayout.addView(chargeSwitchLabelText)
        chargeSwitchLayout.addView(showChargeInTitleSwitch)

        val chargeDescription = TextView(this).apply {
            text = getString(R.string.charge_tile_description)
            textSize = 12f
            setPadding(0, 0, 0, 16)
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
        mainLayout.addView(displayModeLabel)
        mainLayout.addView(switchLayout)
        mainLayout.addView(switchDescription)
        mainLayout.addView(wifiLabel)
        mainLayout.addView(wifiDescription)
        mainLayout.addView(wifiTileSpinner)
        mainLayout.addView(mobileLabel)
        mainLayout.addView(mobileDescription)
        mainLayout.addView(mobileTileSpinner)
        mainLayout.addView(chargeLabel)
        mainLayout.addView(chargeSwitchLayout)
        mainLayout.addView(chargeDescription)
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
        val showPeriodInTitle = qsTileSettingsManager.getShowPeriodInTitle()
        val showChargeInTitle = qsTileSettingsManager.getShowChargeInTitle()

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

        showPeriodInTitleSwitch.isChecked = showPeriodInTitle
        showChargeInTitleSwitch.isChecked = showChargeInTitle
    }

    private fun updatePreview() {
        val sampleData = UsageData("125 MB", "850 MB", "3.2 GB", "45 MB", "320 MB", "1.8 GB")
        val sampleChargeCycles = "342"

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

        val showPeriodInTitle = showPeriodInTitleSwitch.isChecked
        val showChargeInTitle = showChargeInTitleSwitch.isChecked

        previewText.text = buildString {
            if (showPeriodInTitle) {
                appendLine("│ WiFi Usage (${wifiPeriod.name.lowercase().replaceFirstChar { it.uppercase() }})")
                appendLine(wifiValue)
                appendLine()
                appendLine("│ Mobile Data Usage (${mobilePeriod.name.lowercase().replaceFirstChar { it.uppercase() }})")
                appendLine(mobileValue)
            } else {
                appendLine("│ $wifiValue")
                appendLine()
                appendLine("│ $mobileValue")
            }
            appendLine()
            if (showChargeInTitle) {
                appendLine("│ Charge Cycles")
                appendLine(sampleChargeCycles)
            } else {
                appendLine("│ $sampleChargeCycles")
            }
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
        qsTileSettingsManager.saveShowPeriodInTitle(showPeriodInTitleSwitch.isChecked)
        qsTileSettingsManager.saveShowChargeInTitle(showChargeInTitleSwitch.isChecked)

        Toast.makeText(this, getString(R.string.qs_settings_saved), Toast.LENGTH_LONG).show()
        finish()
    }
}
