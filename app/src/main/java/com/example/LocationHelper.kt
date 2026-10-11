package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task

class LocationHelper(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /** Request the latest location fix with high accuracy. */
    @SuppressLint("MissingPermission")
    fun getLatestLocation(): Task<Location?> {
        val cts = CancellationTokenSource()
        return fusedClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cts.token
        )
    }

    /** Fallback to last known location if immediate fix is unavailable. */
    @SuppressLint("MissingPermission")
    fun getLastLocation(): Task<Location?> {
        return fusedClient.lastLocation
    }
}
