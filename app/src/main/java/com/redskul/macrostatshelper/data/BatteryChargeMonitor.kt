package com.redskul.macrostatshelper.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.redskul.macrostatshelper.R

data class ChargeData(
    val chargeCycles: String,
    val batteryCapacity: String
)

class BatteryChargeMonitor(private val context: Context) {

    private val batteryManager: BatteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    companion object {
        private const val BATTERY_PROPERTY_CYCLE_COUNT = 7
    }

    fun getChargeData(): ChargeData {
        android.util.Log.d("BatteryChargeMonitor", "Starting battery charge data collection")

        val chargeCycles = getChargeCycles()
        val batteryCapacity = getBatteryCapacity()

        android.util.Log.d("BatteryChargeMonitor", "Charge cycles: $chargeCycles, Capacity: $batteryCapacity")

        return ChargeData(
            chargeCycles = chargeCycles,
            batteryCapacity = batteryCapacity
        )
    }

    private fun getChargeCycles(): String {
        try {
            android.util.Log.d("BatteryChargeMonitor", "Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            android.util.Log.d("BatteryChargeMonitor", "Android: ${Build.VERSION.SDK_INT}")

            // Method 1: Try BatteryManager.EXTRA_CYCLE_COUNT from intent
            val intentCycles = getCyclesFromIntent()
            if (intentCycles != -1) {
                android.util.Log.d("BatteryChargeMonitor", "Got cycles from intent: $intentCycles")
                return intentCycles.toString()
            }

            // Method 2: Try official API for Android 14+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val cycles = batteryManager.getIntProperty(BATTERY_PROPERTY_CYCLE_COUNT)
                android.util.Log.d("BatteryChargeMonitor", "Official API returned: $cycles")
                if (cycles > 0 && cycles != Int.MAX_VALUE && cycles != Int.MIN_VALUE) {
                    return cycles.toString()
                }
            }

            // Method 3: Try reading from kernel files
            val kernelResult = readCyclesFromKernel()
            if (kernelResult != context.getString(R.string.not_available)) {
                android.util.Log.d("BatteryChargeMonitor", "Got cycles from kernel: $kernelResult")
                return kernelResult
            }

            // Method 4: Try system properties
            val propResult = getCyclesFromSystemProperty()
            if (propResult != context.getString(R.string.not_available)) {
                android.util.Log.d("BatteryChargeMonitor", "Got cycles from property: $propResult")
                return propResult
            }

            // If all methods fail, return appropriate message
            return when {
                Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> context.getString(R.string.android_14_required)
                isKnownUnsupportedDevice() -> context.getString(R.string.not_supported_by_device)
                else -> context.getString(R.string.not_available)
            }

        } catch (e: Exception) {
            android.util.Log.e("BatteryChargeMonitor", "Error getting charge cycles", e)
            return context.getString(R.string.error_message, e.message ?: "Unknown error")
        }
    }

    private fun getCyclesFromIntent(): Int {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val cycleCount = intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1) ?: -1
            android.util.Log.d("BatteryChargeMonitor", "Intent cycle count: $cycleCount")

            // Return valid cycle count, or -1 if not available/supported
            if (cycleCount > 0) cycleCount else -1
        } catch (e: Exception) {
            android.util.Log.e("BatteryChargeMonitor", "Error reading cycles from intent", e)
            -1
        }
    }

    private fun readCyclesFromKernel(): String {
        val kernelPaths = listOf(
            "/sys/class/power_supply/battery/cycle_count",
            "/sys/class/power_supply/battery/cycles_count",
            "/sys/class/power_supply/bms/cycle_count",
            "/sys/class/power_supply/bms/cycles_count",
            "/sys/class/oplus_chg/battery/cycle_count"
        )

        for (path in kernelPaths) {
            try {
                val file = java.io.File(path)
                if (file.exists() && file.canRead()) {
                    val content = file.readText().trim()
                    if (content.isNotEmpty() && content != "0" && content != "-1") {
                        android.util.Log.d("BatteryChargeMonitor", "Found cycles at: $path = $content")
                        return content
                    }
                }
            } catch (e: Exception) {
                android.util.Log.d("BatteryChargeMonitor", "Failed to read $path: ${e.message}")
            }
        }
        return context.getString(R.string.not_available)
    }

    private fun getCyclesFromSystemProperty(): String {
        val properties = listOf(
            "sys.battery.charge_cycles",
            "persist.vendor.charge.cycles",
            "ro.battery.charge_cycles",
            "vendor.battery.cycle_count",
            "persist.vendor.battery.cycles"
        )

        for (prop in properties) {
            try {
                val process = Runtime.getRuntime().exec("getprop $prop")
                val output = process.inputStream.bufferedReader().readLine()?.trim()
                process.waitFor()

                if (!output.isNullOrEmpty() && output != "0" && output != "unknown") {
                    android.util.Log.d("BatteryChargeMonitor", "Found cycles in property $prop: $output")
                    return output
                }
            } catch (e: Exception) {
                android.util.Log.d("BatteryChargeMonitor", "Failed to read property $prop: ${e.message}")
            }
        }
        return context.getString(R.string.not_available)
    }

    private fun isKnownUnsupportedDevice(): Boolean {
        val unsupportedBrands = listOf("huawei", "honor", "oppo", "vivo")
        return unsupportedBrands.any {
            Build.MANUFACTURER.lowercase().contains(it)
        }
    }

    private fun getBatteryCapacity(): String {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val capacity = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            if (capacity >= 0) {
                "$capacity%"
            } else {
                context.getString(R.string.not_available)
            }
        } catch (e: Exception) {
            android.util.Log.e("BatteryChargeMonitor", "Error getting battery capacity", e)
            context.getString(R.string.error_message, e.message ?: "Unknown error")
        }
    }
}
