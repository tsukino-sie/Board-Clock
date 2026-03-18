package com.example.floatingclock

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
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
import androidx.core.graphics.toColorInt
import android.graphics.Typeface
import java.io.File

class FloatingClockService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var clockContainer: View
    private lateinit var textClock: TextClock
    private lateinit var prefs: SharedPreferences

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        updateClockDesign() // 서비스가 켜질 때마다 설정값을 다시 읽음
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("ClockPrefs", MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)

        val channelId = "clock_channel"
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Floating Clock", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            Notification.Builder(this, channelId)
        } else {
            // Android 8.0 미만: 채널 불필요
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        notification.setContentTitle("시계 실행 중")
            .setSmallIcon(android.R.drawable.ic_menu_today)

        val finalNotification = notification.build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, finalNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, finalNotification)
        }

        @SuppressLint("InflateParams")
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_clock, null)
        clockContainer = floatingView.findViewById(R.id.clockContainer)
        textClock = floatingView.findViewById(R.id.textClock)

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager.addView(floatingView, params)

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
        // 1. 설정값 불러오기
        val alpha = prefs.getInt("alpha", 200)
        val bgColor = prefs.getInt("bg_color", Color.BLACK)
        val textColor = prefs.getInt("text_color", Color.WHITE)
        val cornerRadius = prefs.getInt("corner_radius", 30).toFloat()
        val clockSize = prefs.getInt("clock_size", 32).toFloat()

        // 폰트 적용
        val fontPath = prefs.getString("font_path", null)
        if (fontPath != null) {
            val fontFile = File(fontPath)
            if (fontFile.exists()) {
                try {
                    textClock.typeface = Typeface.createFromFile(fontFile)
                } catch (e: Exception) {
                    textClock.typeface = Typeface.DEFAULT
                }
            }
        } else {
            textClock.typeface = Typeface.DEFAULT
        }

        // 2. 최종 적용
        val finalBgColor = Color.argb(alpha, Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))

        // 3. UI 적용
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(finalBgColor)
            setStroke(2, Color.WHITE)
            this.cornerRadius = cornerRadius
        }

        clockContainer.background = shape
        textClock.setTextColor(textColor) // 텍스트 색상 적용
        textClock.textSize = clockSize
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        updateClockDesign() // 설정이 바뀌면 즉시 호출
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
        prefs.unregisterOnSharedPreferenceChangeListener(this)
    }
}