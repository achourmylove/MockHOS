package com.example

data class TransmissionLog(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val lat: Double,
    val lon: Double,
    val altitude: Double? = null,
    val speed: Float? = null,
    val accuracy: Float? = null,
    val jsonPayload: String,
    val delivered: Boolean,
    val targetMac: String,
    val note: String? = null
)
