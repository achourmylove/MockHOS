package com.example

import android.content.Context
import android.location.Location
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.tasks.await

/**
 * WorkManager CoroutineWorker for system-managed periodic location requests.
 * Aligns with Android's battery optimizations, Doze maintenance windows,
 * and system execution constraints.
 */
class LocationBroadcastWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val TAG = "LocationBroadcastWorker"
        const val UNIQUE_PERIODIC_WORK_NAME = "headunit_location_periodic_work"
        const val UNIQUE_EXPEDITED_WORK_NAME = "headunit_location_expedited_work"
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "LocationBroadcastWorker doWork() triggered by WorkManager")
        ServiceEventBus.addDiagnostic("WORKMANAGER", "Periodic location worker triggered by system scheduler")

        val role = AppPreferences.getRole(applicationContext)
        if (role != AppRole.SENDER) {
            Log.d(TAG, "Device role is not SENDER ($role). Skipping location transmission.")
            return Result.success()
        }

        val locationHelper = LocationHelper(applicationContext)
        val bluetoothOutput = BluetoothOutput(applicationContext)

        return try {
            // 1. Acquire Location fix
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

                val includeTelemetry = AppPreferences.isTelemetryEnabled(applicationContext)
                val json = if (includeTelemetry) {
                    val altStr = if (alt != null) ", \"alt\": ${String.format(java.util.Locale.US, "%.1f", alt)}" else ""
                    val speedStr = if (speedKmh != null) ", \"speed\": ${String.format(java.util.Locale.US, "%.1f", speedKmh)}" else ""
                    val accStr = if (accuracy != null) ", \"acc\": ${String.format(java.util.Locale.US, "%.1f", accuracy)}" else ""
                    "{\"lat\": ${String.format(java.util.Locale.US, "%.6f", lat)}, \"lon\": ${String.format(java.util.Locale.US, "%.6f", lon)}$altStr$speedStr$accStr, \"time\": ${System.currentTimeMillis()}}"
                } else {
                    "{\"lat\": ${String.format(java.util.Locale.US, "%.6f", lat)}, \"lon\": ${String.format(java.util.Locale.US, "%.6f", lon)}}"
                }

                Log.d(TAG, "WorkManager acquired GPS JSON: $json")

                // 2. Ensure Bluetooth connection & send
                var delivered = false
                if (!bluetoothOutput.isConnected) {
                    val connectRes = bluetoothOutput.autoConnectAnyPaired()
                    if (connectRes.isSuccess) {
                        delivered = bluetoothOutput.send(json)
                    }
                } else {
                    delivered = bluetoothOutput.send(json)
                }

                ServiceEventBus.recordTransmission(
                    lat = lat,
                    lon = lon,
                    altitude = alt,
                    speedKmh = speedKmh,
                    accuracyM = accuracy,
                    payload = json,
                    delivered = delivered,
                    targetMac = bluetoothOutput.connectedDeviceName.ifEmpty { "WorkManager-Auto" },
                    note = if (delivered) "Delivered via WorkManager" else "GPS acquired, peer unavailable"
                )

                ServiceEventBus.addDiagnostic("WORKMANAGER", "Location processed: lat=$lat, lon=$lon, delivered=$delivered")
                Result.success()
            } else {
                Log.w(TAG, "No GPS fix available during WorkManager execution")
                ServiceEventBus.addDiagnostic("WORKMANAGER", "No GPS fix available during scheduled run", isWarning = true)
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager execution error", e)
            ServiceEventBus.addDiagnostic("WORKMANAGER", "Worker error: ${e.message}", isWarning = true)
            Result.retry()
        } finally {
            bluetoothOutput.close()
        }
    }
}
