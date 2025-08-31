package com.redskul.macrostatshelper.data

import android.content.Context
import android.os.BatteryManager
import com.redskul.macrostatshelper.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class BatteryHealthData(
    val healthPercentage: String,
    val currentFcc: String,
    val designCapacity: String,
    val healthStatus: String = ""
)

class BatteryHealthMonitor(private val context: Context) {

    private val batteryManager: BatteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    companion object {
        private const val CURRENT_FULL_IN_MA = 25
        private const val PREFS_NAME = "battery_health_prefs"
        private const val KEY_ESTIMATED_FCC = "estimated_fcc"
        private const val KEY_DESIGN_CAPACITY = "design_capacity"
    }

    suspend fun getBatteryHealthData(): BatteryHealthData = withContext(Dispatchers.IO) {
        android.util.Log.d("BatteryHealthMonitor", "Getting battery health data")

        val currentFcc = getEstimatedFcc()
        val designCapacity = getDesignCapacity()
        val healthPercentage = calculateHealthPercentage(currentFcc, designCapacity)
        val healthStatus = getHealthCalculationStatus(currentFcc, designCapacity)

        return@withContext BatteryHealthData(
            healthPercentage = healthPercentage,
            currentFcc = if (currentFcc > 0) "${currentFcc} mAh" else context.getString(R.string.estimating_fcc),
            designCapacity = if (designCapacity > 0) "${designCapacity} mAh" else context.getString(R.string.not_set),
            healthStatus = healthStatus
        )
    }

    private suspend fun getEstimatedFcc(): Int = withContext(Dispatchers.IO) {
        try {
            val currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            val batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

            if (kotlin.math.abs(currentNow) <= CURRENT_FULL_IN_MA && batteryLevel == 100) {
                val chargeCounter = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
                if (chargeCounter > 0) {
                    val fullChargeCapacity = (chargeCounter / (batteryLevel / 100.0)).toInt() / 1000
                    android.util.Log.d("BatteryHealthMonitor", "Calculated new FCC: $fullChargeCapacity mAh")
                    saveFccToPreferences(fullChargeCapacity)
                    return@withContext fullChargeCapacity
                }
            }

            // Return saved FCC value
            val savedFcc = getSavedFccFromPreferences()
            android.util.Log.d("BatteryHealthMonitor", "Using saved FCC: $savedFcc mAh")
            return@withContext savedFcc

        } catch (e: Exception) {
            android.util.Log.e("BatteryHealthMonitor", "Error getting estimated FCC", e)
            return@withContext getSavedFccFromPreferences()
        }
    }

    private fun calculateHealthPercentage(currentFcc: Int, designCapacity: Int): String {
        return if (currentFcc > 0 && designCapacity > 0) {
            val healthPercent = (currentFcc.toFloat() / designCapacity.toFloat() * 100).toInt()
            "${healthPercent}%"
        } else {
            if (designCapacity <= 0) context.getString(R.string.set_design_capacity) else context.getString(R.string.calculating_health)
        }
    }

    private fun getHealthCalculationStatus(currentFcc: Int, designCapacity: Int): String {
        val currentNow = try {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        } catch (e: Exception) {
            0
        }
        val batteryLevel = try {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) {
            0
        }

        return when {
            designCapacity <= 0 -> context.getString(R.string.health_enter_design_capacity)
            currentFcc > 0 && designCapacity > 0 -> context.getString(R.string.health_calculated_successfully)
            batteryLevel < 100 -> context.getString(R.string.charge_battery_to_100, batteryLevel)
            kotlin.math.abs(currentNow) > CURRENT_FULL_IN_MA -> context.getString(R.string.wait_for_charging_complete, kotlin.math.abs(currentNow), CURRENT_FULL_IN_MA)
            else -> context.getString(R.string.calculating_ensure_conditions)
        }
    }

    private fun saveFccToPreferences(fcc: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_ESTIMATED_FCC, fcc).apply()
    }

    private fun getSavedFccFromPreferences(): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_ESTIMATED_FCC, 0)
    }

    fun getDesignCapacity(): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_DESIGN_CAPACITY, 0)
    }

    fun setDesignCapacity(capacity: Int) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_DESIGN_CAPACITY, capacity).apply()
        android.util.Log.d("BatteryHealthMonitor", "Design capacity set to: $capacity mAh")
    }
}
