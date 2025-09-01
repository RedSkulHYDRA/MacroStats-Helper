package com.redskul.macrostatshelper.tiles

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.settings.TimePeriod
import com.redskul.macrostatshelper.data.UsageData
import com.redskul.macrostatshelper.data.BatteryHealthMonitor
import com.redskul.macrostatshelper.utils.PermissionHelper
import android.content.Intent
import android.provider.Settings
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

    // Permission status text views
    private lateinit var usageAccessStatusText: TextView
    private lateinit var writeSettingsStatusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        qsTileSettingsManager = QSTileSettingsManager(this)
        batteryHealthMonitor = BatteryHealthMonitor(this)
        permissionHelper = PermissionHelper(this)
        createUI()
        loadCurrentSettings()
        setupListeners()
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
                    updatePermissionStatuses()

                    if (lastUsageStats && !currentUsageStats) {
                        showToast(getString(R.string.data_tiles_disabled))
                    }
                    if (lastWriteSettings && !currentWriteSettings) {
                        showToast(getString(R.string.screen_timeout_disabled))
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
    }

    private fun updatePermissionStatuses() {
        if (::usageAccessStatusText.isInitialized) {
            val hasUsageStats = permissionHelper.hasUsageStatsPermission()
            usageAccessStatusText.text = if (hasUsageStats) {
                getString(R.string.usage_access_permission_enabled)
            } else {
                getString(R.string.usage_access_permission_disabled)
            }
            usageAccessStatusText.setTextColor(
                if (hasUsageStats) 0xFF4CAF50.toInt() else 0xFFFF5722.toInt()
            )
        }

        if (::writeSettingsStatusText.isInitialized) {
            val hasWriteSettings = permissionHelper.hasWriteSettingsPermission()
            writeSettingsStatusText.text = if (hasWriteSettings) {
                getString(R.string.write_settings_permission_enabled)
            } else {
                getString(R.string.write_settings_permission_disabled)
            }
            writeSettingsStatusText.setTextColor(
                if (hasWriteSettings) 0xFF4CAF50.toInt() else 0xFFFF5722.toInt()
            )
        }
    }

    private fun createUI() {
        val scrollView = ScrollView(this)
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Handle window insets
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            val settingsPadding = resources.getDimensionPixelSize(R.dimen.padding_settings)
            mainLayout.setPadding(
                settingsPadding + insets.left,
                settingsPadding + insets.top,
                settingsPadding + insets.right,
                settingsPadding + insets.bottom
            )
            WindowInsetsCompat.CONSUMED
        }

        // Title
        val titleText = TextView(this).apply {
            text = getString(R.string.qs_tile_settings_title)
            textSize = resources.getDimension(R.dimen.text_size_large_title) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val paddingSm = resources.getDimensionPixelSize(R.dimen.padding_text_sm)
            val spacingXL = resources.getDimensionPixelSize(R.dimen.spacing_xl)
            setPadding(paddingSm, 0, paddingSm, spacingXL)
        }

        val instructionText = TextView(this).apply {
            text = getString(R.string.qs_tile_instruction)
            textSize = resources.getDimension(R.dimen.text_size_body) / resources.displayMetrics.scaledDensity
            val paddingSm = resources.getDimensionPixelSize(R.dimen.padding_text_sm)
            val spacingLg = resources.getDimensionPixelSize(R.dimen.spacing_lg)
            setPadding(paddingSm, 0, paddingSm, spacingLg)
            alpha = 0.8f
        }

        // Data Usage Tiles Card
        val dataUsageCard = createDataUsageTilesCard()

        // Battery Tiles Card
        val batteryCard = createBatteryTilesCard()

        // Screen Timeout Card
        val screenTimeoutCard = createScreenTimeoutCard()

        // Save Button
        saveButton = Button(this).apply {
            text = getString(R.string.save_settings)
            textSize = resources.getDimension(R.dimen.text_size_subheading) / resources.displayMetrics.scaledDensity
            val buttonPaddingH = resources.getDimensionPixelSize(R.dimen.button_padding_horizontal)
            val buttonPaddingV = resources.getDimensionPixelSize(R.dimen.button_padding_vertical)
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
            setBackgroundResource(android.R.drawable.btn_default)
            setOnClickListener { saveSettings() }
        }

        val instructionText2 = TextView(this).apply {
            text = getString(R.string.qs_tile_instruction_2)
            textSize = resources.getDimension(R.dimen.text_size_small) / resources.displayMetrics.scaledDensity
            val paddingSm = resources.getDimensionPixelSize(R.dimen.padding_text_sm)
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(paddingSm, spacingMd, paddingSm, 0)
            alpha = 0.7f
        }

        // Add all components to main layout
        mainLayout.addView(titleText)
        mainLayout.addView(instructionText)
        mainLayout.addView(dataUsageCard)
        addSpacing(mainLayout, R.dimen.spacing_md)
        mainLayout.addView(batteryCard)
        addSpacing(mainLayout, R.dimen.spacing_md)
        mainLayout.addView(screenTimeoutCard)
        addSpacing(mainLayout, R.dimen.spacing_lg)
        mainLayout.addView(saveButton)
        mainLayout.addView(instructionText2)

        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }

    private fun createDataUsageTilesCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.card_background, theme)
        }

        val cardTitle = TextView(this).apply {
            text = getString(R.string.data_usage_tiles_title)
            textSize = resources.getDimension(R.dimen.text_size_heading) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, 0, 0, spacingMd)
        }

        val switchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, 0, 0, spacingSm)
        }

        val switchLabelText = TextView(this).apply {
            text = getString(R.string.show_period_in_title)
            textSize = resources.getDimension(R.dimen.text_size_subheading) / resources.displayMetrics.scaledDensity
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        showPeriodInTitleSwitch = Switch(this).apply {
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !permissionHelper.hasUsageStatsPermission()) {
                    this.isChecked = false
                    showPermissionRequiredDialog(getString(R.string.data_usage_tiles_title), getString(R.string.permission_usage_stats))
                    return@setOnCheckedChangeListener
                }
            }
        }

        switchLayout.addView(switchLabelText)
        switchLayout.addView(showPeriodInTitleSwitch)

        // WiFi Tile Section
        val wifiLabel = TextView(this).apply {
            text = getString(R.string.wifi_tile_label)
            textSize = resources.getDimension(R.dimen.text_size_subheading) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, spacingMd, 0, spacingSm)
        }

        val wifiDescription = TextView(this).apply {
            text = getString(R.string.wifi_tile_description)
            textSize = resources.getDimension(R.dimen.text_size_body) / resources.displayMetrics.scaledDensity
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, 0, 0, spacingSm)
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
            textSize = resources.getDimension(R.dimen.text_size_subheading) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, spacingMd, 0, spacingSm)
        }

        val mobileDescription = TextView(this).apply {
            text = getString(R.string.mobile_tile_description)
            textSize = resources.getDimension(R.dimen.text_size_body) / resources.displayMetrics.scaledDensity
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, 0, 0, spacingMd)
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

        // Usage Access Permission Status (moved to bottom)
        usageAccessStatusText = TextView(this).apply {
            textSize = resources.getDimension(R.dimen.text_size_small) / resources.displayMetrics.scaledDensity
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, spacingMd, 0, spacingMd)
        }

        val usageAccessButton = Button(this).apply {
            text = getString(R.string.open_usage_access_settings)
            textSize = resources.getDimension(R.dimen.text_size_body) / resources.displayMetrics.scaledDensity
            val buttonPaddingH = resources.getDimensionPixelSize(R.dimen.button_padding_small_horizontal)
            val buttonPaddingV = resources.getDimensionPixelSize(R.dimen.button_padding_small_vertical)
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
            setOnClickListener {
                requestUsageStatsPermission()
            }
        }

        card.addView(cardTitle)
        card.addView(switchLayout)
        card.addView(wifiLabel)
        card.addView(wifiDescription)
        card.addView(wifiTileSpinner)
        card.addView(mobileLabel)
        card.addView(mobileDescription)
        card.addView(mobileTileSpinner)
        card.addView(usageAccessStatusText)
        card.addView(usageAccessButton)

        return card
    }

    private fun createBatteryTilesCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.card_background, theme)
        }

        val cardTitle = TextView(this).apply {
            text = getString(R.string.battery_tiles_title)
            textSize = resources.getDimension(R.dimen.text_size_heading) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, 0, 0, spacingMd)
        }

        // Charge Cycles Section
        val chargeLabel = TextView(this).apply {
            text = getString(R.string.charge_tile_label)
            textSize = resources.getDimension(R.dimen.text_size_subheading) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, 0, 0, spacingSm)
        }

        val chargeSwitchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, 0, 0, spacingSm)
        }

        val chargeSwitchLabelText = TextView(this).apply {
            text = getString(R.string.show_charge_cycles_in_title)
            textSize = resources.getDimension(R.dimen.text_size_subheading) / resources.displayMetrics.scaledDensity
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        showChargeInTitleSwitch = Switch(this)

        chargeSwitchLayout.addView(chargeSwitchLabelText)
        chargeSwitchLayout.addView(showChargeInTitleSwitch)

        // Battery Health Section
        val healthLabel = TextView(this).apply {
            text = getString(R.string.battery_health_tile_label)
            textSize = resources.getDimension(R.dimen.text_size_subheading) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, spacingMd, 0, spacingSm)
        }

        val healthSwitchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, 0, 0, spacingSm)
        }

        val healthSwitchLabelText = TextView(this).apply {
            text = getString(R.string.show_battery_health_in_title)
            textSize = resources.getDimension(R.dimen.text_size_subheading) / resources.displayMetrics.scaledDensity
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        showBatteryHealthInTitleSwitch = Switch(this)

        healthSwitchLayout.addView(healthSwitchLabelText)
        healthSwitchLayout.addView(showBatteryHealthInTitleSwitch)

        // Design Capacity Input
        val capacityLabel = TextView(this).apply {
            text = getString(R.string.design_capacity_label)
            textSize = resources.getDimension(R.dimen.text_size_body) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, spacingMd, 0, resources.getDimensionPixelSize(R.dimen.spacing_xs))
        }

        designCapacityEditText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.design_capacity_hint)
            val inputPaddingH = resources.getDimensionPixelSize(R.dimen.input_padding_horizontal)
            val inputPaddingV = resources.getDimensionPixelSize(R.dimen.input_padding_vertical)
            setPadding(inputPaddingH, inputPaddingV, inputPaddingH, inputPaddingV)
        }

        val capacityDescription = TextView(this).apply {
            text = getString(R.string.design_capacity_description)
            textSize = resources.getDimension(R.dimen.text_size_small) / resources.displayMetrics.scaledDensity
            val spacingXs = resources.getDimensionPixelSize(R.dimen.spacing_xs)
            setPadding(0, spacingXs, 0, 0)
            alpha = 0.7f
        }

        card.addView(cardTitle)
        card.addView(chargeLabel)
        card.addView(chargeSwitchLayout)
        card.addView(healthLabel)
        card.addView(healthSwitchLayout)
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
            text = getString(R.string.screen_timeout_tile_label)
            textSize = resources.getDimension(R.dimen.text_size_heading) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, 0, 0, spacingMd)
        }

        val screenTimeoutSwitchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, 0, 0, spacingMd)
        }

        val screenTimeoutSwitchLabelText = TextView(this).apply {
            text = getString(R.string.show_screen_timeout_in_title)
            textSize = resources.getDimension(R.dimen.text_size_subheading) / resources.displayMetrics.scaledDensity
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        showScreenTimeoutInTitleSwitch = Switch(this).apply {
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !permissionHelper.hasWriteSettingsPermission()) {
                    this.isChecked = false
                    showPermissionRequiredDialog(getString(R.string.screen_timeout_tile_label), getString(R.string.permission_write_settings))
                    return@setOnCheckedChangeListener
                }
            }
        }

        screenTimeoutSwitchLayout.addView(screenTimeoutSwitchLabelText)
        screenTimeoutSwitchLayout.addView(showScreenTimeoutInTitleSwitch)

        // Write Settings Permission Status (moved to bottom)
        writeSettingsStatusText = TextView(this).apply {
            textSize = resources.getDimension(R.dimen.text_size_small) / resources.displayMetrics.scaledDensity
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, spacingMd, 0, spacingMd)
        }

        val writeSettingsButton = Button(this).apply {
            text = getString(R.string.open_write_settings)
            textSize = resources.getDimension(R.dimen.text_size_body) / resources.displayMetrics.scaledDensity
            val buttonPaddingH = resources.getDimensionPixelSize(R.dimen.button_padding_small_horizontal)
            val buttonPaddingV = resources.getDimensionPixelSize(R.dimen.button_padding_small_vertical)
            setPadding(buttonPaddingH, buttonPaddingV, buttonPaddingH, buttonPaddingV)
            setOnClickListener {
                requestWriteSettingsPermission()
            }
        }

        card.addView(cardTitle)
        card.addView(screenTimeoutSwitchLayout)
        card.addView(writeSettingsStatusText)
        card.addView(writeSettingsButton)

        return card
    }

    private fun addSpacing(parent: LinearLayout, dimenRes: Int) {
        val spacer = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                resources.getDimensionPixelSize(dimenRes)
            )
        }
        parent.addView(spacer)
    }

    private fun setupListeners() {
        // No listeners needed since preview is removed
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
        updatePermissionStatuses()
    }

    private fun showPermissionRequiredDialog(featureName: String, permissionName: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_required_title))
            .setMessage(getString(R.string.permission_required_settings_dialog_message, featureName, permissionName))
            .setPositiveButton(getString(R.string.ok_button)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
        showToast(getString(R.string.enable_usage_access_helper))
    }

    private fun requestWriteSettingsPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
            showToast(getString(R.string.enable_write_settings_helper))
        } else {
            showToast(getString(R.string.permission_not_required_version))
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

        Toast.makeText(this, getString(R.string.qs_settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
