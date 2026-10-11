package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import kotlin.random.Random

class LocationBroadcastService : Service() {

    companion object {
        private const val TAG = "LocationBroadcastSvc"
        const val CHANNEL_ID = "headunit_location_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.headunitlocation.ACTION_START"
        const val ACTION_STOP = "com.example.headunitlocation.ACTION_STOP"
        const val ACTION_RECONNECT_BLUETOOTH = "com.example.headunitlocation.ACTION_RECONNECT_BLUETOOTH"
        const val EXTRA_ROLE = "extra_role"

        // Bounded exponential backoff constants for Bluetooth reconnect attempts
        private const val MIN_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 30_000L
    }

    // Process-independent coroutine scope using SupervisorJob to prevent cancellation cascades
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var workerJob: Job? = null

    private lateinit var locationHelper: LocationHelper
    private lateinit var bluetoothOutput: BluetoothOutput
    private lateinit var bluetoothReceiver: BluetoothReceiverHelper
    private lateinit var wakeLockHelper: WakeLockHelper

    private var currentRole: AppRole = AppRole.SENDER
    private var currentBackoffMs: Long = MIN_BACKOFF_MS

    override fun onCreate() {
        super.onCreate()
        ServiceEventBus.addDiagnostic("SERVICE", "Service onCreate() called - starting process initialization")
        locationHelper = LocationHelper(this)
        bluetoothOutput = BluetoothOutput(this)
        bluetoothReceiver = BluetoothReceiverHelper(this)
        wakeLockHelper = WakeLockHelper(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            ServiceEventBus.addDiagnostic("SERVICE", "Explicit user stop requested")
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_RECONNECT_BLUETOOTH) {
            ServiceEventBus.addDiagnostic("BLUETOOTH", "Immediate reconnect action triggered by Bluetooth adapter/device event")
            currentBackoffMs = MIN_BACKOFF_MS
            serviceScope.launch {
                val res = bluetoothOutput.autoConnectAnyPaired()
                if (res.isSuccess) {
                    ServiceEventBus.updateBluetooth(
                        isConnected = true,
                        deviceName = bluetoothOutput.connectedDeviceName,
                        deviceMac = "Auto"
                    )
                }
            }
            return START_STICKY
        }

        // Restore role from intent or preferences
        val roleExtra = intent?.getStringExtra(EXTRA_ROLE)
        currentRole = if (roleExtra != null) {
            try { AppRole.valueOf(roleExtra) } catch (e: Exception) { AppPreferences.getRole(this) }
        } else {
            AppPreferences.getRole(this)
        }

        if (currentRole == AppRole.NOT_SET) {
            currentRole = AppRole.SENDER
        }

        ServiceEventBus.addDiagnostic("SERVICE", "onStartCommand with role=$currentRole, flags=$flags, startId=$startId")

        // 1. Promote to Foreground promptly to satisfy Android 12+ strict rules
        try {
            startForegroundServiceWithNotification()
        } catch (e: Exception) {
            Log.e(TAG, "Foreground promotion failed", e)
            ServiceEventBus.addDiagnostic("SERVICE", "Foreground promotion failed: ${e.message}", isWarning = true)
        }

        // 2. Verify runtime permissions
        if (!hasRequiredPermissions()) {
            ServiceEventBus.updateHealth(ServiceHealthState.PERMISSION_REVOKED, "Missing Location/Bluetooth permissions")
            updateNotification("Permissions Revoked", "Open app to grant Location and Bluetooth permissions")
            return START_STICKY
        }

        // 3. Check Location Provider status
        if (!locationHelper.isLocationEnabled()) {
            ServiceEventBus.updateHealth(ServiceHealthState.DEGRADED_LOCATION, "Location services are turned off on device")
            updateNotification("GPS Disabled", "Please enable GPS in Android system settings")
        }

        // 4. Acquire partial wake lock boundedly during active streaming
        wakeLockHelper.acquire()

        ServiceEventBus.updateRunning(true)

        if (currentRole == AppRole.SENDER) {
            // Schedule WorkManager periodic tasks to ensure system-level battery-optimized background execution
            WorkManagerScheduler.schedulePeriodicWork(this)
            startSenderLoop()
        } else {
            startReceiverLoop()
        }

        // START_STICKY ensures Android OS recovers and recreates the service if killed under memory pressure
        return START_STICKY
    }

    private fun hasRequiredPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val btConnect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return (fineLocation || coarseLocation) && btConnect
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
        val title = if (currentRole == AppRole.SENDER) "GPS Sender Active" else "GPS Receiver Active"
        val notification = buildNotification(title, "Running continuously in background")
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

    private fun buildNotification(title: String, statusText: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
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
            .setContentTitle(title)
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Maximize App", pendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(title: String, statusText: String) {
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(title, statusText))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification", e)
        }
    }

    /**
     * SENDER LOOP with:
     * - Battery Saver dynamic interval throttling
     * - Bounded exponential backoff + jitter for Bluetooth reconnects (no tight loops)
     * - Resilient location fallback
     * - Independence from Activity / UI
     */
    private fun startSenderLoop() {
        workerJob?.cancel()
        workerJob = serviceScope.launch {
            ServiceEventBus.addDiagnostic("SERVICE", "Sender background transmission loop initialized")

            while (isActive) {
                // Dynamically compute effective interval based on Battery Saver mode & power state
                val effectiveIntervalSec = BatteryDiagnosticsHelper.computeEffectiveIntervalSec(this@LocationBroadcastService)
                val isThrottled = effectiveIntervalSec > AppPreferences.getIntervalSec(this@LocationBroadcastService)
                ServiceEventBus.updatePowerState(effectiveIntervalSec, isThrottled)

                // 1. Connection check with bounded exponential backoff
                if (!bluetoothOutput.isConnected) {
                    val connectRes = bluetoothOutput.autoConnectAnyPaired()
                    if (connectRes.isSuccess) {
                        currentBackoffMs = MIN_BACKOFF_MS
                        ServiceEventBus.updateBluetooth(
                            isConnected = true,
                            deviceName = bluetoothOutput.connectedDeviceName,
                            deviceMac = "Auto"
                        )
                    } else {
                        ServiceEventBus.updateBluetooth(isConnected = false)
                        val jitter = Random.nextLong(200, 800)
                        val waitTime = (currentBackoffMs + jitter).coerceAtMost(MAX_BACKOFF_MS)
                        currentBackoffMs = (currentBackoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
                        Log.d(TAG, "BT peer unavailable, next reconnect in ${waitTime}ms")
                    }
                }

                // 2. Acquire fresh GPS fix
                try {
                    val location: Location? = try {
                        locationHelper.getLatestLocation().await()
                            ?: locationHelper.getLastLocation().await()
                    } catch (e: Exception) {
                        locationHelper.getLastLocation().await()
                    }

                    if (location != null) {
                        val lat = location.latitude
                        val lon = location.longitude
                        val alt = if (location.hasAltitude()) location.altitude else null
                        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else null
                        val accuracy = if (location.hasAccuracy()) location.accuracy else null

                        val includeTelemetry = AppPreferences.isTelemetryEnabled(this@LocationBroadcastService)
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
                            targetMac = bluetoothOutput.connectedDeviceName.ifEmpty { "Auto-Broadcast" },
                            note = if (delivered) {
                                if (isThrottled) "Sent (Battery Saver ${effectiveIntervalSec}s)" else "Sent over SPP"
                            } else {
                                "Acquired fix, waiting for peer"
                            }
                        )

                        val healthState = when {
                            !bluetoothOutput.isConnected -> ServiceHealthState.DEGRADED_BT
                            isThrottled -> ServiceHealthState.POWER_THROTTLED
                            else -> ServiceHealthState.HEALTHY
                        }
                        ServiceEventBus.updateHealth(healthState)

                        val notifText = if (delivered) {
                            val modeTag = if (isThrottled) " [Battery Saver]" else ""
                            "Streaming: ${String.format(java.util.Locale.US, "%.4f, %.4f", lat, lon)}$modeTag"
                        } else {
                            "Acquired GPS (${String.format(java.util.Locale.US, "%.4f, %.4f", lat, lon)}) - looking for receiver..."
                        }
                        updateNotification("GPS Sender Active", notifText)
                    } else {
                        ServiceEventBus.updateHealth(ServiceHealthState.DEGRADED_LOCATION, "Waiting for satellite fix...")
                        updateNotification("GPS Sender Active", "Acquiring GPS fix...")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Sender loop error", e)
                    ServiceEventBus.addDiagnostic("LOCATION", "GPS error: ${e.message}", isWarning = true)
                }

                // Sleep for the dynamically computed effective interval
                delay(effectiveIntervalSec * 1000L)
            }
        }
    }

    /**
     * RECEIVER LOOP: Listens on SPP server socket resiliently
     */
    private fun startReceiverLoop() {
        workerJob?.cancel()
        workerJob = serviceScope.launch {
            ServiceEventBus.addDiagnostic("SERVICE", "Receiver background listening loop initialized")
            bluetoothReceiver.startListening { payload, senderName ->
                try {
                    val jsonObj = JSONObject(payload)
                    val lat = jsonObj.optDouble("lat", 0.0)
                    val lon = jsonObj.optDouble("lon", 0.0)
                    val alt = if (jsonObj.has("alt")) jsonObj.optDouble("alt") else null
                    val speed = if (jsonObj.has("speed")) jsonObj.optDouble("speed").toFloat() else null
                    val acc = if (jsonObj.has("acc")) jsonObj.optDouble("acc").toFloat() else null

                    ServiceEventBus.recordTransmission(
                        lat = lat,
                        lon = lon,
                        altitude = alt,
                        speedKmh = speed,
                        accuracyM = acc,
                        payload = payload,
                        delivered = true,
                        targetMac = senderName,
                        note = "Received from $senderName"
                    )
                    ServiceEventBus.updateHealth(ServiceHealthState.HEALTHY)

                    updateNotification(
                        "GPS Receiver Active",
                        "Received: ${String.format(java.util.Locale.US, "%.4f, %.4f", lat, lon)} from $senderName"
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing incoming JSON: $payload", e)
                    ServiceEventBus.addDiagnostic("RECEIVER", "Malformed JSON payload: ${e.message}", isWarning = true)
                }
            }
        }
    }

    override fun onDestroy() {
        ServiceEventBus.addDiagnostic("SERVICE", "Service onDestroy() called - releasing resources")
        workerJob?.cancel()
        serviceScope.cancel()
        wakeLockHelper.release()
        WorkManagerScheduler.cancelWork(this)
        locationHelper.stopContinuousUpdates()
        bluetoothOutput.close()
        bluetoothReceiver.stop()
        ServiceEventBus.updateRunning(false)
        ServiceEventBus.updateBluetooth(false)
        ServiceEventBus.updateHealth(ServiceHealthState.IDLE, "Service Stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
