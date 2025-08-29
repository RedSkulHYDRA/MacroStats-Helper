package com.redskul.macrostatshelper.settings

enum class TimePeriod {
    DAILY,
    WEEKLY,
    MONTHLY
}

data class DisplaySettings(
    val wifiTimePeriods: List<TimePeriod> = emptyList(),
    val mobileTimePeriods: List<TimePeriod> = emptyList()
)
