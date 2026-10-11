package com.example

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkManagerScheduler {

    private const val TAG = "WorkManagerScheduler"

    /**
     * Enqueues periodic WorkManager requests for system-managed background execution.
     * PeriodicWorkRequest has an Android system minimum interval of 15 minutes.
     */
    fun schedulePeriodicWork(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<LocationBroadcastWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES // Flex interval
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                LocationBroadcastWorker.UNIQUE_PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
            Log.i(TAG, "Periodic WorkManager location request scheduled (15min flex interval)")
            ServiceEventBus.addDiagnostic("WORKMANAGER", "Scheduled periodic WorkManager task (15m)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule periodic WorkManager task", e)
        }
    }

    /**
     * Trigger an immediate one-time WorkManager execution to fulfill location request
     * managed by the OS scheduler.
     */
    fun triggerImmediateWork(context: Context) {
        try {
            val oneTimeRequest = OneTimeWorkRequestBuilder<LocationBroadcastWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                LocationBroadcastWorker.UNIQUE_EXPEDITED_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
            Log.d(TAG, "Immediate WorkManager location request enqueued")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger immediate WorkManager task", e)
        }
    }

    /**
     * Cancel periodic background work when service or app explicitly stops.
     */
    fun cancelWork(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(LocationBroadcastWorker.UNIQUE_PERIODIC_WORK_NAME)
            Log.i(TAG, "WorkManager periodic work cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel WorkManager work", e)
        }
    }
}
