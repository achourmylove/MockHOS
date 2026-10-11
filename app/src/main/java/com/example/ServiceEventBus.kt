package com.example

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServiceEventBus {

    data class ServiceState(
        val isRunning: Boolean = false,
        val isBtConnected: Boolean = false,
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
        val logs: List<TransmissionLog> = emptyList()
    )

    private val _state = MutableStateFlow(ServiceState())
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    fun updateRunning(isRunning: Boolean) {
        _state.value = _state.value.copy(
            isRunning = isRunning,
            statusMessage = if (isRunning) "Service running" else "Service stopped"
        )
    }

    fun updateBluetooth(isConnected: Boolean, deviceName: String = "", deviceMac: String = "") {
        _state.value = _state.value.copy(
            isBtConnected = isConnected,
            connectedDeviceName = deviceName,
            connectedDeviceMac = deviceMac,
            statusMessage = if (isConnected) "Connected to $deviceName" else "Bluetooth disconnected"
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
        val updatedLogs = (_state.value.logs + newLog).takeLast(60) // Keep last 60 records
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
            statusMessage = if (delivered) "Location sent (${String.format("%.5f, %.5f", lat, lon)})" else "Failed to send over Bluetooth"
        )
    }

    fun clearLogs() {
        _state.value = _state.value.copy(logs = emptyList(), totalTransmitted = 0)
    }
}
