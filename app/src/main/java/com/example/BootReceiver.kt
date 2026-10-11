package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON" ||
            intent.action == "com.htc.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i(TAG, "Device boot completed received. Checking auto-start configuration...")

            val role = AppPreferences.getRole(context)
            val isAutoStartEnabled = AppPreferences.isAutoStartEnabled(context)

            if (isAutoStartEnabled && role != AppRole.NOT_SET) {
                Log.i(TAG, "Triggering foreground service for role: $role after restart")
                val serviceIntent = Intent(context, LocationBroadcastService::class.java).apply {
                    action = LocationBroadcastService.ACTION_START
                    putExtra(LocationBroadcastService.EXTRA_ROLE, role.name)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.i(TAG, "Auto-start skipped: role=$role, autoStart=$isAutoStartEnabled")
            }
        }
    }
}
