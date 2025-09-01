package com.redskul.macrostatshelper.settings

import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.redskul.macrostatshelper.R
import com.redskul.macrostatshelper.data.DataUsageService
import com.redskul.macrostatshelper.data.UsageData
import com.redskul.macrostatshelper.utils.PermissionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var settingsManager: SettingsManager
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var wifiDailyCheckbox: CheckBox
    private lateinit var wifiWeeklyCheckbox: CheckBox
    private lateinit var wifiMonthlyCheckbox: CheckBox
    private lateinit var mobileDailyCheckbox: CheckBox
    private lateinit var mobileWeeklyCheckbox: CheckBox
    private lateinit var mobileMonthlyCheckbox: CheckBox
    private lateinit var notificationEnabledSwitch: Switch
    private lateinit var saveButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)

        settingsManager = SettingsManager(this)
        permissionHelper = PermissionHelper(this)
        createUI()
        loadCurrentSettings()
        setupListeners()
        startPermissionMonitoring()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionBasedUI()
    }

    private fun startPermissionMonitoring() {
        lifecycleScope.launch {
            var lastUsageStats = permissionHelper.hasUsageStatsPermission()

            while (true) {
                delay(1000)

                val currentUsageStats = permissionHelper.hasUsageStatsPermission()

                if (lastUsageStats != currentUsageStats) {
                    updatePermissionBasedUI()
                    if (lastUsageStats && !currentUsageStats) {
                        notificationEnabledSwitch.isChecked = false
                        showToast(getString(R.string.data_tiles_disabled))
                    }
                }

                lastUsageStats = currentUsageStats
            }
        }
    }

    private fun updatePermissionBasedUI() {
        val hasUsageStats = permissionHelper.hasUsageStatsPermission()

        // Update notification switch
        notificationEnabledSwitch.isEnabled = hasUsageStats
        if (!hasUsageStats && notificationEnabledSwitch.isChecked) {
            notificationEnabledSwitch.isChecked = false
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
            text = getString(R.string.settings_title)
            textSize = resources.getDimension(R.dimen.text_size_large_title) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val paddingSm = resources.getDimensionPixelSize(R.dimen.padding_text_sm)
            val spacingXL = resources.getDimensionPixelSize(R.dimen.spacing_xl)
            setPadding(paddingSm, 0, paddingSm, spacingXL)
        }

        // Instructions
        val instructionText = TextView(this).apply {
            text = getString(R.string.settings_instruction)
            textSize = resources.getDimension(R.dimen.text_size_body) / resources.displayMetrics.scaledDensity
            val paddingSm = resources.getDimensionPixelSize(R.dimen.padding_text_sm)
            val spacingLg = resources.getDimensionPixelSize(R.dimen.spacing_lg)
            setPadding(paddingSm, 0, paddingSm, spacingLg)
            alpha = 0.8f
        }

        // Data Usage Settings Card
        val dataUsageCard = createDataUsageCard()

        // Notification Settings Card
        val notificationCard = createNotificationCard()

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

        // Add all components to main layout
        mainLayout.addView(titleText)
        mainLayout.addView(instructionText)
        mainLayout.addView(dataUsageCard)
        addSpacing(mainLayout, R.dimen.spacing_md)
        mainLayout.addView(notificationCard)
        addSpacing(mainLayout, R.dimen.spacing_lg)
        mainLayout.addView(saveButton)

        scrollView.addView(mainLayout)
        setContentView(scrollView)
    }

    private fun createDataUsageCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = resources.getDrawable(R.drawable.card_background, theme)
        }

        val cardTitle = TextView(this).apply {
            text = getString(R.string.data_usage_display_title)
            textSize = resources.getDimension(R.dimen.text_size_heading) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, 0, 0, spacingMd)
        }

        // WiFi Section
        val wifiLabel = TextView(this).apply {
            text = getString(R.string.wifi_usage_label)
            textSize = resources.getDimension(R.dimen.text_size_subheading) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, 0, 0, spacingSm)
        }

        wifiDailyCheckbox = CheckBox(this).apply {
            text = getString(R.string.daily)
        }

        wifiWeeklyCheckbox = CheckBox(this).apply {
            text = getString(R.string.weekly)
        }

        wifiMonthlyCheckbox = CheckBox(this).apply {
            text = getString(R.string.monthly)
        }

        // Mobile Section
        val mobileLabel = TextView(this).apply {
            text = getString(R.string.mobile_data_usage_label)
            textSize = resources.getDimension(R.dimen.text_size_subheading) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, spacingMd, 0, spacingSm)
        }

        mobileDailyCheckbox = CheckBox(this).apply {
            text = getString(R.string.daily)
        }

        mobileWeeklyCheckbox = CheckBox(this).apply {
            text = getString(R.string.weekly)
        }

        mobileMonthlyCheckbox = CheckBox(this).apply {
            text = getString(R.string.monthly)
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
            textSize = resources.getDimension(R.dimen.text_size_heading) / resources.displayMetrics.scaledDensity
            setTypeface(null, android.graphics.Typeface.BOLD)
            val spacingMd = resources.getDimensionPixelSize(R.dimen.spacing_md)
            setPadding(0, 0, 0, spacingMd)
        }

        val notificationLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, 0, 0, spacingSm)
        }

        val notificationSwitchLabel = TextView(this).apply {
            text = getString(R.string.show_data_usage_notification)
            textSize = resources.getDimension(R.dimen.text_size_subheading) / resources.displayMetrics.scaledDensity
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        notificationEnabledSwitch = Switch(this).apply {
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !permissionHelper.hasUsageStatsPermission()) {
                    this.isChecked = false
                    showPermissionRequiredDialog(getString(R.string.show_data_usage_notification), getString(R.string.permission_usage_stats)) {
                        requestUsageStatsPermission()
                    }
                    return@setOnCheckedChangeListener
                }
            }
        }

        notificationLayout.addView(notificationSwitchLabel)
        notificationLayout.addView(notificationEnabledSwitch)

        val notificationDescription = TextView(this).apply {
            text = getString(R.string.notification_description)
            textSize = resources.getDimension(R.dimen.text_size_small) / resources.displayMetrics.scaledDensity
            val spacingSm = resources.getDimensionPixelSize(R.dimen.spacing_sm)
            setPadding(0, 0, 0, spacingSm)
            alpha = 0.7f
        }

        val notificationNote = TextView(this).apply {
            text = getString(R.string.notification_disabled_note)
            textSize = resources.getDimension(R.dimen.text_size_caption) / resources.displayMetrics.scaledDensity
            setPadding(0, 0, 0, 0)
            alpha = 0.6f
        }

        card.addView(cardTitle)
        card.addView(notificationLayout)
        card.addView(notificationDescription)
        card.addView(notificationNote)

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

        updatePermissionBasedUI()
    }

    private fun showPermissionRequiredDialog(featureName: String, permissionName: String, onPositive: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.permission_required_title))
            .setMessage(getString(R.string.permission_required_dialog_message, featureName, permissionName))
            .setPositiveButton(getString(R.string.grant_button)) { _, _ -> onPositive() }
            .setNegativeButton(getString(R.string.cancel_button)) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        startActivity(intent)
        showToast(getString(R.string.enable_usage_access_helper))
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

        // Notify data service about notification changes
        val dataServiceIntent = Intent(this, DataUsageService::class.java).apply {
            action = DataUsageService.ACTION_NOTIFICATION_TOGGLE_CHANGED
        }
        startService(dataServiceIntent)

        Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
