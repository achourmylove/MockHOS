package com.example

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

class BluetoothOutput(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothOutput"
        // Standard Serial Port Profile (SPP) UUID
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    var isConnected: Boolean = false
        private set

    var connectedDeviceName: String = ""
        private set

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting bonded devices", e)
            emptyList()
        }
    }

    /**
     * Automatically attempts to connect to ANY paired device without verification,
     * or to a specific address if provided.
     */
    @SuppressLint("MissingPermission")
    suspend fun autoConnectAnyPaired(): Result<String> = withContext(Dispatchers.IO) {
        if (!isBluetoothEnabled()) {
            return@withContext Result.failure(IllegalStateException("Bluetooth is disabled"))
        }

        val devices = getBondedDevices()
        if (devices.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("No paired devices found. Pair phone & head-unit once in Bluetooth settings."))
        }

        // Try connecting to bonded devices sequentially until one accepts the SPP connection
        for (device in devices) {
            val devName = try { device.name ?: device.address } catch (e: Exception) { device.address }
            Log.d(TAG, "Attempting auto-connect to $devName (${device.address})")

            val res = connectToDevice(device)
            if (res.isSuccess) {
                return@withContext res
            }
        }

        Result.failure(IllegalStateException("Could not connect to any of ${devices.size} paired devices"))
    }

    @SuppressLint("MissingPermission")
    suspend fun connect(deviceAddress: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isBluetoothEnabled()) {
            return@withContext Result.failure(IllegalStateException("Bluetooth is disabled"))
        }

        val device = try {
            bluetoothAdapter?.getRemoteDevice(deviceAddress)
        } catch (e: Exception) {
            null
        } ?: return@withContext Result.failure(IllegalStateException("Invalid device: $deviceAddress"))

        connectToDevice(device)
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice): Result<String> {
        close()
        return try {
            try {
                bluetoothAdapter?.cancelDiscovery()
            } catch (e: Exception) {
                // Ignore
            }

            val devName = try { device.name ?: device.address } catch (e: Exception) { device.address }
            val createdSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            createdSocket.connect()

            socket = createdSocket
            outputStream = createdSocket.outputStream
            isConnected = true
            connectedDeviceName = devName

            Log.i(TAG, "Successfully connected to $devName (${device.address})")
            Result.success("Connected to $devName")
        } catch (e: Exception) {
            close()
            Result.failure(e)
        }
    }

    @Synchronized
    fun send(payload: String): Boolean {
        return try {
            val stream = outputStream
            if (stream != null && isConnected) {
                val data = if (payload.endsWith("\n")) payload else "$payload\n"
                stream.write(data.toByteArray(Charsets.UTF_8))
                stream.flush()
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write payload to Bluetooth stream", e)
            close()
            false
        }
    }

    fun close() {
        try {
            outputStream?.close()
        } catch (e: Exception) {
            // Ignore
        }
        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        outputStream = null
        socket = null
        isConnected = false
        connectedDeviceName = ""
    }
}
