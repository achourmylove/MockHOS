package com.example

enum class ServiceHealthState {
    IDLE,
    HEALTHY,           // Operating normally: fresh location fixes + Bluetooth active
    DEGRADED_BT,       // Running, acquiring GPS, but Bluetooth peer unreachable
    DEGRADED_LOCATION, // Running, but GPS provider unavailable or fixes stale (>30s)
    POWER_THROTTLED,   // Battery Saver mode active, polling slowed down
    PERMISSION_REVOKED // Location or Bluetooth permission was revoked
}

data class DiagnosticsLog(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val category: String, // "SERVICE", "BLUETOOTH", "LOCATION", "POWER", "BOOT"
    val message: String,
    val isWarningOrError: Boolean = false
)
