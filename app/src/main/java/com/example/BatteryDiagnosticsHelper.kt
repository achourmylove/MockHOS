package com.example

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import android.util.Log

object BatteryDiagnosticsHelper {

    private const val TAG = "BatteryDiagnostics"

    data class PowerStatus(
        val isIgnoringBatteryOptimizations: Boolean,
        val isPowerSaveMode: Boolean,
        val isDeviceIdleMode: Boolean,
        val isBatterySaverSettingEnabled: Boolean,
        val effectiveIntervalSec: Int,
        val batteryLevelPct: Int,
        val isCharging: Boolean
    )

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    }

    fun isPowerSaveMode(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isPowerSaveMode ?: false
    }

    fun isDeviceIdleMode(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isDeviceIdleMode ?: false
    }

    fun getBatteryInfo(context: Context): Pair<Int, Boolean> {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1
            Pair(pct, isCharging)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery info", e)
            Pair(-1, false)
        }
    }

    /**
     * Calculates the active polling interval:
     * - Base configured interval
     * - If Battery Saver mode is enabled AND phone is in PowerSaveMode or below low threshold (<=20% and not charging),
     *   dynamically increases interval by 3x (or minimum 30 seconds) to conserve power.
     */
    fun computeEffectiveIntervalSec(context: Context): Int {
        val baseInterval = AppPreferences.getIntervalSec(context)
        val isBatterySaverEnabled = AppPreferences.isBatterySaverEnabled(context)
        if (!isBatterySaverEnabled) return baseInterval

        val inPowerSave = isPowerSaveMode(context)
        val (batteryPct, isCharging) = getBatteryInfo(context)
        val isLowBattery = batteryPct in 1..20 && !isCharging

        return if (inPowerSave || isLowBattery) {
            val dynamicInterval = (baseInterval * 3).coerceAtLeast(30)
            Log.d(TAG, "Battery Saver Active! Base: ${baseInterval}s -> Throttled: ${dynamicInterval}s (powerSave=$inPowerSave, pct=$batteryPct%)")
            dynamicInterval
        } else {
            baseInterval
        }
    }

    fun getPowerStatus(context: Context): PowerStatus {
        val (level, charging) = getBatteryInfo(context)
        return PowerStatus(
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context),
            isPowerSaveMode = isPowerSaveMode(context),
            isDeviceIdleMode = isDeviceIdleMode(context),
            isBatterySaverSettingEnabled = AppPreferences.isBatterySaverEnabled(context),
            effectiveIntervalSec = computeEffectiveIntervalSec(context),
            batteryLevelPct = level,
            isCharging = charging
        )
    }
}
