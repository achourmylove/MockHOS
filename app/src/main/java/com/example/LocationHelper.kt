package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.tasks.await

class LocationHelper(private val context: Context) {

    companion object {
        private const val TAG = "LocationHelper"
        // If no fix has arrived within 30 seconds, consider location stale
        private const val STALE_THRESHOLD_MS = 30_000L
    }

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationManager: LocationManager? =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @Volatile var lastAcquiredLocation: Location? = null
        private set

    @Volatile var lastAcquiredTimestamp: Long = 0L
        private set

    private var activeCallback: LocationCallback? = null

    fun isLocationEnabled(): Boolean {
        return try {
            locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                    locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        } catch (e: Exception) {
            false
        }
    }

    fun isLocationStale(): Boolean {
        val lastTime = lastAcquiredTimestamp
        return lastTime == 0L || (System.currentTimeMillis() - lastTime > STALE_THRESHOLD_MS)
    }

    /** Request the latest location fix with high accuracy. */
    @SuppressLint("MissingPermission")
    fun getLatestLocation(): Task<Location?> {
        val cts = CancellationTokenSource()
        return fusedClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cts.token
        ).addOnSuccessListener { loc ->
            if (loc != null) {
                lastAcquiredLocation = loc
                lastAcquiredTimestamp = System.currentTimeMillis()
            }
        }
    }

    /** Fallback to last known location if immediate fix is unavailable. */
    @SuppressLint("MissingPermission")
    fun getLastLocation(): Task<Location?> {
        return fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                lastAcquiredLocation = loc
                lastAcquiredTimestamp = System.currentTimeMillis()
            }
        }
    }

    /**
     * Continuous background location updates registration to ensure continuous stream
     * even when screen is locked or phone enters background execution states.
     */
    @SuppressLint("MissingPermission")
    fun startContinuousUpdates(intervalSec: Int, onLocationReceived: (Location) -> Unit) {
        stopContinuousUpdates()
        try {
            val intervalMs = (intervalSec * 1000L).coerceAtLeast(1000L)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, intervalMs)
                .setMinUpdateIntervalMillis(intervalMs / 2)
                .setMinUpdateDistanceMeters(0f)
                .setWaitForAccurateLocation(false)
                .build()

            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation
                    if (loc != null) {
                        lastAcquiredLocation = loc
                        lastAcquiredTimestamp = System.currentTimeMillis()
                        onLocationReceived(loc)
                    }
                }
            }
            activeCallback = callback
            fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
            Log.d(TAG, "Continuous background location updates registered (interval=${intervalSec}s)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register continuous location updates", e)
        }
    }

    fun stopContinuousUpdates() {
        activeCallback?.let {
            try {
                fusedClient.removeLocationUpdates(it)
                Log.d(TAG, "Continuous location updates removed")
            } catch (e: Exception) {
                Log.w(TAG, "Error removing location updates", e)
            }
            activeCallback = null
        }
    }
}
