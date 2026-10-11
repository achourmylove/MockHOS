package com.example

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BluetoothStateReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BluetoothStateReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.d(TAG, "Received Bluetooth broadcast: $action")

        when (action) {
            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_ON -> {
                        Log.i(TAG, "Bluetooth turned ON. Triggering instant reconnect in LocationBroadcastService.")
                        ServiceEventBus.addDiagnostic("BLUETOOTH", "Bluetooth adapter turned ON - triggering reconnect")
                        triggerServiceReconnect(context)
                    }
                    BluetoothAdapter.STATE_OFF -> {
                        Log.i(TAG, "Bluetooth turned OFF.")
                        ServiceEventBus.updateBluetooth(isConnected = false)
                        ServiceEventBus.addDiagnostic("BLUETOOTH", "Bluetooth adapter turned OFF", isWarning = true)
                    }
                }
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val devName = try { device?.name ?: device?.address ?: "Peer" } catch (e: Exception) { "Peer" }
                Log.i(TAG, "Bluetooth ACL Connected: $devName. Triggering reconnect logic.")
                ServiceEventBus.addDiagnostic("BLUETOOTH", "Device ACL connected: $devName")
                triggerServiceReconnect(context)
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val devName = try { device?.name ?: device?.address ?: "Peer" } catch (e: Exception) { "Peer" }
                Log.i(TAG, "Bluetooth ACL Disconnected: $devName.")
                ServiceEventBus.addDiagnostic("BLUETOOTH", "Device ACL disconnected: $devName", isWarning = true)
            }
        }
    }

    private fun triggerServiceReconnect(context: Context) {
        val role = AppPreferences.getRole(context)
        if (role == AppRole.NOT_SET) return

        val reconnectIntent = Intent(context, LocationBroadcastService::class.java).apply {
            action = LocationBroadcastService.ACTION_RECONNECT_BLUETOOTH
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(reconnectIntent)
            } else {
                context.startService(reconnectIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send reconnect intent to LocationBroadcastService", e)
        }
    }
}
