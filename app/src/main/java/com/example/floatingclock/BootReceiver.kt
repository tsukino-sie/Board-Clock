package com.example.floatingclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("ClockPrefs", Context.MODE_PRIVATE)

            // 시계를 켜두었는지 & 부팅 시 자동 실행 확인
            val isEnabled = prefs.getBoolean("is_clock_enabled", false)
            val isAutoStart = prefs.getBoolean("auto_start", true)

            // 자동 실행
            if (isEnabled && isAutoStart && Settings.canDrawOverlays(context)) {
                val serviceIntent = Intent(context, FloatingClockService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}