package com.example.floatingclock

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
//import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextClock
import android.content.pm.ServiceInfo
//import android.graphics.Color.parseColor
import androidx.core.graphics.toColorInt


class FloatingClockService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var clockContainer: View
    private lateinit var textClock: TextClock
    private lateinit var prefs: SharedPreferences

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        // 포그라운드 서비스 알림 설정 (안드로이드 8.0 이상 필수)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "clock_channel",
                "Floating Clock",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
            val notification = Notification.Builder(this, "clock_channel")
                .setContentTitle("시계 실행 중")
                .setSmallIcon(android.R.drawable.ic_menu_today)
                .build()

            // Android 14 (API 34) 이상인지 확인 후 타입 지정해서 실행
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(1, notification)
            }
        }

        prefs = getSharedPreferences("ClockPrefs", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)

        @SuppressLint("InflateParams")
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_clock, null)
        clockContainer = floatingView.findViewById(R.id.clockContainer)
        textClock = floatingView.findViewById(R.id.textClock)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            // 8.0 미만 버전 기기를 위한 호환성 처리
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType, // 위에서 만든 변수 사용
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, params)

        // 드래그
        val dragHandle = floatingView.findViewById<View>(R.id.dragHandle)
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> {
                    dragHandle.performClick()
                    false
                }
            }
        }

        updateClockDesign()
    }

    private fun updateClockDesign() {
        val bgColor = prefs.getInt("bg_color", "#99000000".toColorInt())
        val cornerRadius = prefs.getInt("corner_radius", 30).toFloat()
        val clockSize = prefs.getInt("clock_size", 32).toFloat()

        // 모서리 곡률, 배경색 적용
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(bgColor)
            setStroke(2, Color.WHITE)
            this.cornerRadius = cornerRadius
        }

        clockContainer.background = shape
        textClock.textSize = clockSize
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        updateClockDesign()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }
}