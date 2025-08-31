package com.redskul.macrostatshelper.tiles

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.settings.TimePeriod
import com.redskul.macrostatshelper.data.UsageData
import com.redskul.macrostatshelper.data.BatteryHealthMonitor
import com.redskul.macrostatshelper.utils.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class QSTileSettingsActivity : AppCompatActivity() {

    private lateinit var qsTileSettingsManager: QSTileSettingsManager
    private lateinit var batteryHealthMonitor: BatteryHealthMonitor
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var wifiTileSpinner: Spinner
    private lateinit var mobileTileSpinner: Spinner
    private lateinit var showPeriodInTitleSwitch: Switch
    private lateinit var showChargeInTitleSwitch: Switch
    private lateinit var showBatteryHealthInTitleSwitch: Switch
    private lateinit var showScreenTimeoutInTitleSwitch: Switch
    private lateinit var designCapacityEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var previewText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        qsTileSettingsManager = QSTileSettingsManager(this)
        batteryHealthMonitor = BatteryHealthMonitor(this)
        permissionHelper = PermissionHelper(this)
        createUI()
        loadCurrentSettings()
        setupListeners()
        updatePreview()
        startPermissionMonitoring()
    }

    private fun startPermissionMonitoring() {
        lifecycleScope.launch {
            var lastUsageStats = permissionHelper.hasUsageStatsPermission()
            var lastWriteSettings = permissionHelper.hasWriteSettingsPermission()

            while (true) {
                delay(1000)

                val currentUsageStats = permissionHelper.hasUsageStatsPermission()
                val currentWriteSettings = permissionHelper.hasWriteSettingsPermission()

                if (lastUsageStats != currentUsageStats || lastWriteSettings != currentWriteSettings) {
                    updatePermissionBasedUI()
                    if (lastUsageStats && !currentUsageStats) {
                        showToast("Data usage tiles disabled due to missing permission")
                    }
                    if (lastWriteSettings && !currentWriteSettings) {
                        showToast("Screen timeout tile disabled due to missing permission")
                    }
                }

                lastUsageStats = currentUsageStats
                lastWriteSettings = currentWriteSettings
            }
        }
    }

    private fun updatePermissionBasedUI() {
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()
        val hasWriteSettings = permissionHelper.hasWriteSettingsPermission()

        // Enable/disable data usage related controls
        wifiTileSpinner.isEnabled = hasUsageStats
        mobileTileSpinner.isEnabled = hasUsageStats
        showPeriodInTitleSwitch.isEnabled = hasUsageStats

        // Enable/disable screen timeout controls
        showScreenTimeoutInTitleSwitch.isEnabled = hasWriteSettings

        updatePreview()
    }

    private fun createUI() {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }

        // Title
        val titleText = TextView(this).apply {
            text = getString(R.string.qs_tile_settings_title)
            textSize = 26f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(8, 0, 8, 32)
        }

        val instructionText = TextView(this).apply {
            text = getString(R.string.qs_tile_instruction)
            textSize = 14f
            setPadding(8, 0, 8, 24)
            alpha = 0.8f
        }

        // Data Usage Tiles Card
        val dataUsageCard = createDataUsageTilesCard()

        // Battery Tiles Card
        val batteryCard = createBatteryTilesCard()

        // Screen Timeout Card
        val screenTimeoutCard = createScreenTimeoutCard()

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

        val instructionText2 = TextView(this).apply {
            text = getString(R.string.qs_tile_instruction_2)
            textSize = 12f
            setPadding(8, 16, 8, 0)
            alpha = 0.7f
        }

        // Add all components to main layout
        mainLayout.addView(titleText)
        mainLayout.addView(instructionText)
        mainLayout.addView(dataUsageCard)
        addSpacing(mainLayout, 16)
        mainLayout.addView(batteryCard)
        addSpacing(mainLayout, 16)
        mainLayout.addView(screenTimeoutCard)
        addSpacing(mainLayout, 16)
        mainLayout.addView(previewCard)
        addSpacing(mainLayout, 24)
        mainLayout.addView(saveButton)
        mainLayout.addView(instructionText2)

        setContentView(ScrollView(this).apply { addView(mainLayout) })
    }

    private fun createDataUsageTilesCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.card_background, theme)
        }

        val cardTitle = TextView(this).apply {
            text = "Data Usage Tiles"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
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
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !permissionHelper.hasUsageStatsPermission()) {
                    this.isChecked = false
                    showPermissionRequiredDialog("Data Usage Tiles", "Usage Stats Permission")
                    return@setOnCheckedChangeListener
                }
                updatePreview()
            }
        }

        switchLayout.addView(switchLabelText)
        switchLayout.addView(showPeriodInTitleSwitch)

        val switchDescription = TextView(this).apply {
            text = getString(R.string.tile_display_mode_description)
            textSize = 12f
            setPadding(0, 0, 0, 16)
            alpha = 0.7f
        }

        // WiFi Tile Section
        val wifiLabel = TextView(this).apply {
            text = getString(R.string.wifi_tile_label)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        val wifiDescription = TextView(this).apply {
            text = getString(R.string.wifi_tile_description)
            textSize = 14f
            setPadding(0, 0, 0, 8)
            alpha = 0.8f
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
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 16, 0, 8)
        }

        val mobileDescription = TextView(this).apply {
            text = getString(R.string.mobile_tile_description)
            textSize = 14f
            setPadding(0, 0, 0, 8)
            alpha = 0.8f
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

        card.addView(cardTitle)
        card.addView(switchLayout)
        card.addView(switchDescription)
        card.addView(wifiLabel)
        card.addView(wifiDescription)
        card.addView(wifiTileSpinner)
        card.addView(mobileLabel)
        card.addView(mobileDescription)
        card.addView(mobileTileSpinner)

        return card
    }

    private fun createBatteryTilesCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.card_background, theme)
        }

        val cardTitle = TextView(this).apply {
            text = "Battery Tiles"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        // Charge Cycles Section
        val chargeLabel = TextView(this).apply {
            text = getString(R.string.charge_tile_label)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
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
            alpha = 0.7f
        }

        // Battery Health Section
        val healthLabel = TextView(this).apply {
            text = getString(R.string.battery_health_tile_label)
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        }

        val healthSwitchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }

        val healthSwitchLabelText = TextView(this).apply {
            text = getString(R.string.show_battery_health_in_title)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        showBatteryHealthInTitleSwitch = Switch(this).apply {
            setOnCheckedChangeListener { _, _ -> updatePreview() }
        }

        healthSwitchLayout.addView(healthSwitchLabelText)
        healthSwitchLayout.addView(showBatteryHealthInTitleSwitch)

        val healthDescription = TextView(this).apply {
            text = getString(R.string.battery_health_tile_description)
            textSize = 12f
            setPadding(0, 0, 0, 8)
            alpha = 0.7f
        }

        // Design Capacity Input
        val capacityLabel = TextView(this).apply {
            text = getString(R.string.design_capacity_label)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 8, 0, 4)
        }

        designCapacityEditText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.design_capacity_hint)
            setPadding(16, 12, 16, 12)
        }

        val capacityDescription = TextView(this).apply {
            text = getString(R.string.design_capacity_description)
            textSize = 12f
            setPadding(0, 4, 0, 0)
            alpha = 0.7f
        }

        card.addView(cardTitle)
        card.addView(chargeLabel)
        card.addView(chargeSwitchLayout)
        card.addView(chargeDescription)
        card.addView(healthLabel)
        card.addView(healthSwitchLayout)
        card.addView(healthDescription)
        card.addView(capacityLabel)
        card.addView(designCapacityEditText)
        card.addView(capacityDescription)

        return card
    }

    private fun createScreenTimeoutCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.card_background, theme)
        }

        val cardTitle = TextView(this).apply {
            text = "Screen Timeout Tile"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 16)
        }

        val screenTimeoutSwitchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 8)
        }

        val screenTimeoutSwitchLabelText = TextView(this).apply {
            text = getString(R.string.show_screen_timeout_in_title)
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        showScreenTimeoutInTitleSwitch = Switch(this).apply {
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !permissionHelper.hasWriteSettingsPermission()) {
                    this.isChecked = false
                    showPermissionRequiredDialog("Screen Timeout Tile", "Write Settings Permission")
                    return@setOnCheckedChangeListener
                }
                updatePreview()
            }
        }

        screenTimeoutSwitchLayout.addView(screenTimeoutSwitchLabelText)
        screenTimeoutSwitchLayout.addView(showScreenTimeoutInTitleSwitch)

        val screenTimeoutDescription = TextView(this).apply {
            text = getString(R.string.screen_timeout_tile_description)
            textSize = 12f
            setPadding(0, 0, 0, 0)
            alpha = 0.7f
        }

        card.addView(cardTitle)
        card.addView(screenTimeoutSwitchLayout)
        card.addView(screenTimeoutDescription)

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

        designCapacityEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePreview()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadCurrentSettings() {
        val wifiPeriod = qsTileSettingsManager.getWiFiTilePeriod()
        val mobilePeriod = qsTileSettingsManager.getMobileTilePeriod()
        val showPeriodInTitle = qsTileSettingsManager.getShowPeriodInTitle()
        val showChargeInTitle = qsTileSettingsManager.getShowChargeInTitle()
        val showHealthInTitle = qsTileSettingsManager.getShowBatteryHealthInTitle()
        val showScreenTimeoutInTitle = qsTileSettingsManager.getShowScreenTimeoutInTitle()
        val designCapacity = qsTileSettingsManager.getBatteryDesignCapacity()

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
        showBatteryHealthInTitleSwitch.isChecked = showHealthInTitle
        showScreenTimeoutInTitleSwitch.isChecked = showScreenTimeoutInTitle

        if (designCapacity > 0) {
            designCapacityEditText.setText(designCapacity.toString())
        }

        updatePermissionBasedUI()
    }

    private fun updatePreview() {
        val sampleData = UsageData("125 MB", "850 MB", "3.2 GB", "45 MB", "320 MB", "1.8 GB")
        val sampleChargeCycles = "342"
        val sampleBatteryHealth = "87%"
        val sampleScreenTimeout = "30s"

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
        val showHealthInTitle = showBatteryHealthInTitleSwitch.isChecked
        val showScreenTimeoutInTitle = showScreenTimeoutInTitleSwitch.isChecked

        val hasUsageStats = permissionHelper.hasUsageStatsPermission()
        val hasWriteSettings = permissionHelper.hasWriteSettingsPermission()

        previewText.text = buildString {
            if (hasUsageStats) {
                if (showPeriodInTitle) {
                    appendLine("📶 WiFi Usage (${wifiPeriod.name.lowercase().replaceFirstChar { it.uppercase() }})")
                    appendLine("   $wifiValue")
                    appendLine()
                    appendLine("📱 Mobile Data Usage (${mobilePeriod.name.lowercase().replaceFirstChar { it.uppercase() }})")
                    appendLine("   $mobileValue")
                } else {
                    appendLine("📶 $wifiValue")
                    appendLine()
                    appendLine("📱 $mobileValue")
                }
                appendLine()
            } else {
                appendLine("📶📱 Data usage tiles disabled")
                appendLine("     (Usage stats permission required)")
                appendLine()
            }

            if (showChargeInTitle) {
                appendLine("🔋 Charge Cycles")
                appendLine("   $sampleChargeCycles")
            } else {
                appendLine("🔋 $sampleChargeCycles")
            }
            appendLine()
            if (showHealthInTitle) {
                appendLine("💚 Battery Health")
                appendLine("   $sampleBatteryHealth")
            } else {
                appendLine("💚 $sampleBatteryHealth")
            }
            appendLine()

            if (hasWriteSettings) {
                if (showScreenTimeoutInTitle) {
                    appendLine("⏰ Screen Timeout")
                    appendLine("   $sampleScreenTimeout")
                } else {
                    appendLine("⏰ $sampleScreenTimeout")
                }
            } else {
                appendLine("⏰ Screen timeout tile disabled")
                appendLine("   (Write settings permission required)")
            }
        }
    }

    private fun showPermissionRequiredDialog(featureName: String, permissionName: String) {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("$featureName requires $permissionName to work properly. Please grant the permission in your device settings.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
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

        val designCapacity = designCapacityEditText.text.toString().toIntOrNull() ?: 0

        qsTileSettingsManager.saveWiFiTilePeriod(wifiPeriod)
        qsTileSettingsManager.saveMobileTilePeriod(mobilePeriod)
        qsTileSettingsManager.saveShowPeriodInTitle(showPeriodInTitleSwitch.isChecked)
        qsTileSettingsManager.saveShowChargeInTitle(showChargeInTitleSwitch.isChecked)
        qsTileSettingsManager.saveShowBatteryHealthInTitle(showBatteryHealthInTitleSwitch.isChecked)
        qsTileSettingsManager.saveShowScreenTimeoutInTitle(showScreenTimeoutInTitleSwitch.isChecked)

        if (designCapacity > 0) {
            qsTileSettingsManager.saveBatteryDesignCapacity(designCapacity)
            batteryHealthMonitor.setDesignCapacity(designCapacity)
        }

        Toast.makeText(this, getString(R.string.qs_settings_saved), Toast.LENGTH_LONG).show()
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
