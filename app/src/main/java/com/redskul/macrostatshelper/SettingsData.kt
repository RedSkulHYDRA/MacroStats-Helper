// Add this to a new file: SettingsData.kt
package com.redskul.macrostatshelper

enum class DataType {
    WIFI_ONLY,
    MOBILE_ONLY,
    BOTH,
    CUSTOM
}

enum class TimePeriod {
    DAILY,
    WEEKLY,
    MONTHLY
}

data class CustomDisplaySettings(
    val wifiTimePeriod: TimePeriod,
    val mobileTimePeriod: TimePeriod
)

data class DisplaySettings(
    val dataType: DataType,
    val timePeriod: TimePeriod = TimePeriod.DAILY,
    val customSettings: CustomDisplaySettings? = null
)