package com.example

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

class BluetoothReceiverHelper(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothReceiverHelper"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var serverSocket: BluetoothServerSocket? = null
    private var activeSocket: BluetoothSocket? = null
    @Volatile private var isRunning: Boolean = false

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    /**
     * Start listening for incoming connections from ANY transmitter without MAC verification.
     */
    @SuppressLint("MissingPermission")
    suspend fun startListening(onMessageReceived: (String, String) -> Unit) = withContext(Dispatchers.IO) {
        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth not enabled")
            return@withContext
        }

        stop()
        isRunning = true

        try {
            serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord("HeadUnitGPSReceiver", SPP_UUID)
            Log.i(TAG, "Listening on SPP UUID: $SPP_UUID")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening server socket", e)
            return@withContext
        }

        while (isRunning) {
            try {
                Log.d(TAG, "Waiting for client connection...")
                val socket = serverSocket?.accept() ?: break
                activeSocket = socket

                val remoteDevice = socket.remoteDevice
                val senderName = try { remoteDevice.name ?: remoteDevice.address } catch (e: Exception) { remoteDevice.address }
                Log.i(TAG, "Connected client: $senderName (${remoteDevice.address})")

                ServiceEventBus.updateBluetooth(
                    isConnected = true,
                    deviceName = senderName,
                    deviceMac = remoteDevice.address
                )
                ServiceEventBus.setStatusMessage("Receiver connected to $senderName")

                val reader = BufferedReader(InputStreamReader(socket.inputStream, Charsets.UTF_8))
                var line: String? = null
                while (isRunning && reader.readLine().also { line = it } != null) {
                    val message = line?.trim()
                    if (!message.isNullOrEmpty()) {
                        Log.d(TAG, "Received message from $senderName: $message")
                        onMessageReceived(message, senderName)
                    }
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w(TAG, "Socket loop interrupted, waiting for reconnect", e)
                    ServiceEventBus.updateBluetooth(isConnected = false)
                    ServiceEventBus.setStatusMessage("Waiting for sender to connect...")
                }
            } finally {
                try {
                    activeSocket?.close()
                } catch (ignored: Exception) {}
                activeSocket = null
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            activeSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        activeSocket = null
        serverSocket = null
    }
}
