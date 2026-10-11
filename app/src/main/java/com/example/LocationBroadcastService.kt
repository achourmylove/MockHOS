package com.example

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LocationBroadcastService : Service() {

    companion object {
        private const val TAG = "LocationBroadcastSvc"
        const val CHANNEL_ID = "headunit_location_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.headunitlocation.ACTION_START"
        const val ACTION_STOP = "com.example.headunitlocation.ACTION_STOP"
        const val EXTRA_MAC_ADDRESS = "extra_mac_address"
        const val EXTRA_INTERVAL_SEC = "extra_interval_sec"
        const val EXTRA_INCLUDE_TELEMETRY = "extra_include_telemetry"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var transmissionJob: Job? = null

    private lateinit var locationHelper: LocationHelper
    private lateinit var bluetoothOutput: BluetoothOutput

    private var targetMac: String = ""
    private var sendIntervalSec: Int = 10
    private var includeTelemetry: Boolean = true

    override fun onCreate() {
        super.onCreate()
        locationHelper = LocationHelper(this)
        bluetoothOutput = BluetoothOutput(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        targetMac = intent?.getStringExtra(EXTRA_MAC_ADDRESS) ?: targetMac
        sendIntervalSec = intent?.getIntExtra(EXTRA_INTERVAL_SEC, 10)?.coerceAtLeast(1) ?: 10
        includeTelemetry = intent?.getBooleanExtra(EXTRA_INCLUDE_TELEMETRY, true) ?: true

        startForegroundServiceWithNotification()
        ServiceEventBus.updateRunning(true)
        ServiceEventBus.setStatusMessage("Starting transmitter...")

        startTransmissionLoop()

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Head-Unit GPS Streamer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of GPS coordinates streaming to automotive head-unit"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceWithNotification() {
        val notification = buildNotification("Initializing GPS transmission...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LocationBroadcastService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Head-Unit GPS Streamer")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(statusText))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    private fun startTransmissionLoop() {
        transmissionJob?.cancel()
        transmissionJob = serviceScope.launch {
            Log.d(TAG, "Transmission loop started. Interval: ${sendIntervalSec}s, Target: $targetMac")

            // Reconnection / connection loop
            while (isActive) {
                // Ensure Bluetooth connection
                if (!bluetoothOutput.isConnected && targetMac.isNotBlank()) {
                    ServiceEventBus.setStatusMessage("Connecting to $targetMac...")
                    val connectResult = bluetoothOutput.connect(targetMac)
                    if (connectResult.isSuccess) {
                        ServiceEventBus.updateBluetooth(
                            isConnected = true,
                            deviceName = targetMac,
                            deviceMac = targetMac
                        )
                    } else {
                        ServiceEventBus.updateBluetooth(
                            isConnected = false,
                            deviceName = "",
                            deviceMac = targetMac
                        )
                        ServiceEventBus.setStatusMessage("Bluetooth waiting to connect: ${connectResult.exceptionOrNull()?.message ?: "Disconnected"}")
                    }
                }

                // Acquire location
                try {
                    val location: Location? = try {
                        locationHelper.getLatestLocation().await()
                            ?: locationHelper.getLastLocation().await()
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not get immediate fix, checking last location", e)
                        locationHelper.getLastLocation().await()
                    }

                    if (location != null) {
                        val lat = location.latitude
                        val lon = location.longitude
                        val alt = if (location.hasAltitude()) location.altitude else null
                        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else null
                        val accuracy = if (location.hasAccuracy()) location.accuracy else null

                        // Format JSON as requested by head-unit spec
                        val json = if (includeTelemetry) {
                            val altStr = if (alt != null) ", \"alt\": ${String.format(java.util.Locale.US, "%.1f", alt)}" else ""
                            val speedStr = if (speedKmh != null) ", \"speed\": ${String.format(java.util.Locale.US, "%.1f", speedKmh)}" else ""
                            val accStr = if (accuracy != null) ", \"acc\": ${String.format(java.util.Locale.US, "%.1f", accuracy)}" else ""
                            "{\"lat\": ${String.format(java.util.Locale.US, "%.6f", lat)}, \"lon\": ${String.format(java.util.Locale.US, "%.6f", lon)}$altStr$speedStr$accStr, \"time\": ${System.currentTimeMillis()}}"
                        } else {
                            "{\"lat\": ${String.format(java.util.Locale.US, "%.6f", lat)}, \"lon\": ${String.format(java.util.Locale.US, "%.6f", lon)}}"
                        }

                        Log.d(TAG, "GPS JSON: $json")

                        val delivered = if (bluetoothOutput.isConnected) {
                            bluetoothOutput.send(json)
                        } else {
                            false
                        }

                        ServiceEventBus.recordTransmission(
                            lat = lat,
                            lon = lon,
                            altitude = alt,
                            speedKmh = speedKmh,
                            accuracyM = accuracy,
                            payload = json,
                            delivered = delivered,
                            targetMac = targetMac,
                            note = if (!bluetoothOutput.isConnected) "BT disconnected (saved in queue)" else "Sent over SPP"
                        )

                        val notifMsg = if (delivered) {
                            "Broadcasting: ${String.format(java.util.Locale.US, "%.4f, %.4f", lat, lon)}"
                        } else {
                            "Acquired GPS (${String.format(java.util.Locale.US, "%.4f, %.4f", lat, lon)}), BT disconnected"
                        }
                        updateNotification(notifMsg)
                    } else {
                        ServiceEventBus.setStatusMessage("Waiting for GPS satellite fix...")
                        updateNotification("Waiting for GPS satellite fix...")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in transmission loop", e)
                    ServiceEventBus.setStatusMessage("GPS error: ${e.message}")
                }

                delay(sendIntervalSec * 1000L)
            }
        }
    }

    override fun onDestroy() {
        transmissionJob?.cancel()
        serviceScope.cancel()
        bluetoothOutput.close()
        ServiceEventBus.updateRunning(false)
        ServiceEventBus.updateBluetooth(false)
        ServiceEventBus.setStatusMessage("Service Stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
