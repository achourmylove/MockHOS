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
        // Standard Serial Port Profile (SPP) UUID supported by automotive head-units
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    var isConnected: Boolean = false
        private set

    var currentTargetMac: String = ""
        private set

    /** Check if bluetooth is enabled on the device */
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /** Get list of paired / bonded Bluetooth devices */
    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        return try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting bonded devices", e)
            emptyList()
        }
    }

    /** Connect to target device via RFCOMM SPP socket */
    @SuppressLint("MissingPermission")
    suspend fun connect(deviceAddress: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isBluetoothEnabled()) {
            return@withContext Result.failure(IllegalStateException("Bluetooth is disabled"))
        }

        if (deviceAddress.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Target MAC address is empty"))
        }

        close()

        currentTargetMac = deviceAddress
        try {
            val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
                ?: return@withContext Result.failure(IllegalStateException("Device not found for address $deviceAddress"))

            // Standard RFCOMM SPP socket connection
            val createdSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            // Cancel discovery as it slows down connections
            try {
                bluetoothAdapter.cancelDiscovery()
            } catch (e: Exception) {
                // Ignore
            }

            createdSocket.connect()
            socket = createdSocket
            outputStream = createdSocket.outputStream
            isConnected = true
            Log.i(TAG, "Successfully connected to $deviceAddress (${device.name})")
            Result.success("Connected to ${device.name ?: deviceAddress}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to $deviceAddress", e)
            close()
            Result.failure(e)
        }
    }

    /** Send JSON payload string over Bluetooth socket */
    @Synchronized
    fun send(payload: String): Boolean {
        return try {
            val stream = outputStream
            if (stream != null && isConnected) {
                // Append newline delimiter for easy stream parsing on head-unit side
                val data = if (payload.endsWith("\n")) payload else "$payload\n"
                stream.write(data.toByteArray(Charsets.UTF_8))
                stream.flush()
                Log.d(TAG, "Sent payload: $payload")
                true
            } else {
                Log.w(TAG, "Socket is not connected. Message skipped.")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write payload to Bluetooth stream", e)
            isConnected = false
            try {
                outputStream?.close()
                socket?.close()
            } catch (ignored: Exception) {}
            outputStream = null
            socket = null
            false
        }
    }

    /** Close current connection and release resources */
    fun close() {
        try {
            outputStream?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing stream", e)
        }
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing socket", e)
        }
        outputStream = null
        socket = null
        isConnected = false
    }
}
