package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServiceEventBus {

    data class ServiceState(
        val isRunning: Boolean = false,
        val isBtConnected: Boolean = false,
        val healthState: ServiceHealthState = ServiceHealthState.IDLE,
        val connectedDeviceName: String = "",
        val connectedDeviceMac: String = "",
        val lastLat: Double? = null,
        val lastLon: Double? = null,
        val lastAltitude: Double? = null,
        val lastSpeedKmh: Float? = null,
        val lastAccuracyM: Float? = null,
        val lastPayload: String? = null,
        val lastTransmissionTime: Long = 0L,
        val totalTransmitted: Int = 0,
        val statusMessage: String = "Service Idle",
        val activeIntervalSec: Int = 5,
        val isBatterySaverActive: Boolean = false,
        val logs: List<TransmissionLog> = emptyList(),
        val diagnosticLogs: List<DiagnosticsLog> = emptyList()
    )

    private val _state = MutableStateFlow(ServiceState())
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    fun updateRunning(isRunning: Boolean) {
        val health = if (isRunning) {
            if (_state.value.isBtConnected) ServiceHealthState.HEALTHY else ServiceHealthState.DEGRADED_BT
        } else {
            ServiceHealthState.IDLE
        }
        _state.value = _state.value.copy(
            isRunning = isRunning,
            healthState = health,
            statusMessage = if (isRunning) "Service running" else "Service stopped"
        )
        addDiagnostic("SERVICE", if (isRunning) "Service promoted to foreground" else "Service stopped")
    }

    fun updateBluetooth(isConnected: Boolean, deviceName: String = "", deviceMac: String = "") {
        val health = if (_state.value.isRunning) {
            if (isConnected) ServiceHealthState.HEALTHY else ServiceHealthState.DEGRADED_BT
        } else {
            ServiceHealthState.IDLE
        }
        _state.value = _state.value.copy(
            isBtConnected = isConnected,
            connectedDeviceName = deviceName,
            connectedDeviceMac = deviceMac,
            healthState = health,
            statusMessage = if (isConnected) "Connected to $deviceName" else "Bluetooth waiting for peer"
        )
        addDiagnostic("BLUETOOTH", if (isConnected) "Connected to $deviceName" else "Bluetooth peer disconnected/waiting", isWarning = !isConnected)
    }

    fun updateHealth(health: ServiceHealthState, statusMsg: String? = null) {
        _state.value = _state.value.copy(
            healthState = health,
            statusMessage = statusMsg ?: _state.value.statusMessage
        )
        if (statusMsg != null) {
            addDiagnostic("HEALTH", statusMsg, isWarning = health != ServiceHealthState.HEALTHY)
        }
    }

    fun updatePowerState(activeIntervalSec: Int, isBatterySaverActive: Boolean) {
        _state.value = _state.value.copy(
            activeIntervalSec = activeIntervalSec,
            isBatterySaverActive = isBatterySaverActive
        )
    }

    fun setStatusMessage(msg: String) {
        _state.value = _state.value.copy(statusMessage = msg)
    }

    fun recordTransmission(
        lat: Double,
        lon: Double,
        altitude: Double?,
        speedKmh: Float?,
        accuracyM: Float?,
        payload: String,
        delivered: Boolean,
        targetMac: String,
        note: String? = null
    ) {
        val newLog = TransmissionLog(
            lat = lat,
            lon = lon,
            altitude = altitude,
            speed = speedKmh,
            accuracy = accuracyM,
            jsonPayload = payload,
            delivered = delivered,
            targetMac = targetMac,
            note = note
        )
        val updatedLogs = (_state.value.logs + newLog).takeLast(60)
        _state.value = _state.value.copy(
            lastLat = lat,
            lastLon = lon,
            lastAltitude = altitude,
            lastSpeedKmh = speedKmh,
            lastAccuracyM = accuracyM,
            lastPayload = payload,
            lastTransmissionTime = System.currentTimeMillis(),
            totalTransmitted = _state.value.totalTransmitted + (if (delivered) 1 else 0),
            logs = updatedLogs,
            statusMessage = if (delivered) "Location sent (${String.format("%.5f, %.5f", lat, lon)})" else "Acquired GPS, peer disconnected"
        )
    }

    fun addDiagnostic(category: String, message: String, isWarning: Boolean = false) {
        val entry = DiagnosticsLog(
            category = category,
            message = message,
            isWarningOrError = isWarning
        )
        val updated = (_state.value.diagnosticLogs + entry).takeLast(100)
        _state.value = _state.value.copy(diagnosticLogs = updated)
    }

    fun clearLogs() {
        _state.value = _state.value.copy(logs = emptyList(), totalTransmitted = 0)
    }

    fun clearDiagnostics() {
        _state.value = _state.value.copy(diagnosticLogs = emptyList())
    }
}
