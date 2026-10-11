package com.example

import android.content.Context
import android.os.PowerManager
import android.util.Log

class WakeLockHelper(private val context: Context) {

    companion object {
        private const val TAG = "WakeLockHelper"
        private const val WAKE_LOCK_TAG = "HeadUnitGPS::LocationStreamWakeLock"
        // Max timeout safety cap to prevent permanent accidental battery draining (10 minutes)
        private const val DEFAULT_TIMEOUT_MS = 10 * 60 * 1000L
    }

    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquire(timeoutMs: Long = DEFAULT_TIMEOUT_MS) {
        try {
            if (wakeLock == null) {
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    WAKE_LOCK_TAG
                )?.apply {
                    setReferenceCounted(false)
                }
            }

            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(timeoutMs)
                Log.d(TAG, "Partial wake lock acquired (timeout=${timeoutMs}ms)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire partial wake lock", e)
        }
    }

    @Synchronized
    fun release() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "Partial wake lock released")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }
}
