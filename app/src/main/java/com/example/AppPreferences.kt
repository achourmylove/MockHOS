package com.example

import android.content.Context
import android.content.SharedPreferences

enum class AppRole {
    NOT_SET,
    SENDER,    // Phone broadcasting GPS coordinates
    RECEIVER   // Head-unit receiving GPS coordinates
}

object AppPreferences {
    private const val PREF_NAME = "headunit_gps_prefs"
    private const val KEY_ROLE = "key_app_role"
    private const val KEY_AUTO_START = "key_auto_start_enabled"
    private const val KEY_INTERVAL_SEC = "key_interval_sec"
    private const val KEY_INCLUDE_TELEMETRY = "key_include_telemetry"
    private const val KEY_BATTERY_SAVER = "key_battery_saver_enabled"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun getRole(context: Context): AppRole {
        val str = getPrefs(context).getString(KEY_ROLE, AppRole.NOT_SET.name)
        return try {
            AppRole.valueOf(str ?: AppRole.NOT_SET.name)
        } catch (e: Exception) {
            AppRole.NOT_SET
        }
    }

    fun setRole(context: Context, role: AppRole) {
        getPrefs(context).edit().putString(KEY_ROLE, role.name).apply()
    }

    fun isAutoStartEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_START, true)
    }

    fun setAutoStartEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }

    fun getIntervalSec(context: Context): Int {
        return getPrefs(context).getInt(KEY_INTERVAL_SEC, 5)
    }

    fun setIntervalSec(context: Context, sec: Int) {
        getPrefs(context).edit().putInt(KEY_INTERVAL_SEC, sec).apply()
    }

    fun isTelemetryEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_INCLUDE_TELEMETRY, true)
    }

    fun setTelemetryEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_INCLUDE_TELEMETRY, enabled).apply()
    }

    fun isBatterySaverEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BATTERY_SAVER, true)
    }

    fun setBatterySaverEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BATTERY_SAVER, enabled).apply()
    }

    fun isFloatingOverlayEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean("key_floating_overlay_enabled", false)
    }

    fun setFloatingOverlayEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean("key_floating_overlay_enabled", enabled).apply()
    }
}
