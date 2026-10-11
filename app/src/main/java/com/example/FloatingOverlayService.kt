package com.example

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

/**
 * Foreground / WindowManager service that renders a floating overlay button
 * over the Android home screen, app drawer, and other navigation apps.
 * Tapping it maximizes and brings MainActivity to the foreground.
 */
class FloatingOverlayService : Service() {

    companion object {
        private const val TAG = "FloatingOverlayService"
        const val ACTION_START = "com.example.headunitlocation.ACTION_START_OVERLAY"
        const val ACTION_STOP = "com.example.headunitlocation.ACTION_STOP_OVERLAY"

        var isOverlayRunning: Boolean = false
            private set
    }

    private var windowManager: WindowManager? = null
    private var floatView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission not granted")
            stopSelf()
            return START_NOT_STICKY
        }

        showFloatingBubble()
        return START_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingBubble() {
        if (floatView != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 200
        }

        // Programmatically create sleek 56dp circular floating button
        val density = resources.displayMetrics.density
        val sizePx = (56 * density).toInt()

        val frameLayout = FrameLayout(this).apply {
            val bgDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0F172A")) // Slate 900
                setStroke((2 * density).toInt(), Color.parseColor("#0EA5E9")) // Cyan accent border
            }
            background = bgDrawable
            elevation = 12 * density
        }

        val iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.ic_menu_compass)
            setColorFilter(Color.parseColor("#38BDF8")) // CyanPrimaryDark
            val padding = (12 * density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        frameLayout.addView(iconView, FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER))

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        frameLayout.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - initialTouchX).toInt()
                    val deltaY = (event.rawY - initialTouchY).toInt()
                    if (abs(deltaX) > 10 || abs(deltaY) > 10) {
                        isClick = false
                    }
                    params.x = initialX + deltaX
                    params.y = initialY + deltaY
                    windowManager?.updateViewLayout(frameLayout, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        Log.i(TAG, "Floating button tapped: Maximizing MainActivity")
                        maximizeApp()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(frameLayout, params)
            floatView = frameLayout
            isOverlayRunning = true
            ServiceEventBus.addDiagnostic("OVERLAY", "Floating overlay bubble shown over screen")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating overlay view", e)
        }
    }

    private fun maximizeApp() {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(launchIntent)
    }

    override fun onDestroy() {
        floatView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing floating overlay view", e)
            }
            floatView = null
        }
        isOverlayRunning = false
        ServiceEventBus.addDiagnostic("OVERLAY", "Floating overlay bubble dismissed")
        super.onDestroy()
    }
}
