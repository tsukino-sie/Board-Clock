package com.example.floatingclock

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.net.toUri
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) // super.onCreate()를 최상단으로 옮깁니다.
        setContentView(R.layout.activity_main)

        // 1. 알림 권한 요청 (Android 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        // 2. 다른 앱 위에 그리기 권한 체크
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:$packageName".toUri())
            startActivity(intent)
        }

        val prefs = getSharedPreferences("ClockPrefs", MODE_PRIVATE)
        val editor = prefs.edit()

        val toggleClockSwitch = findViewById<SwitchCompat>(R.id.toggleClockSwitch)
        val autoStartSwitch = findViewById<SwitchCompat>(R.id.autoStartSwitch)
        val radiusSeekBar = findViewById<SeekBar>(R.id.radiusSeekBar)
        val sizeSeekBar = findViewById<SeekBar>(R.id.sizeSeekBar)
        val btnChangeColor = findViewById<Button>(R.id.btnChangeColor)


        // 초기값 설정
        toggleClockSwitch.isChecked = prefs.getBoolean("is_clock_enabled", false)
        autoStartSwitch.isChecked = prefs.getBoolean("auto_start", true)
        radiusSeekBar.progress = prefs.getInt("corner_radius", 30)
        sizeSeekBar.progress = prefs.getInt("clock_size", 32)

        // 시계 켜기/끄기
        toggleClockSwitch.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("is_clock_enabled", isChecked).apply()

            // 권한이 없으면 스위치를 다시 끄고 알림
            if (isChecked && !Settings.canDrawOverlays(this)) {
                toggleClockSwitch.isChecked = false
                return@setOnCheckedChangeListener
            }

            val serviceIntent = Intent(this, FloatingClockService::class.java)
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } else {
                stopService(serviceIntent)
            }
        }

        autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("auto_start", isChecked).apply()
        }

        // SeekBar 리스너 정리
        radiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                editor.putInt("corner_radius", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                editor.putInt("clock_size", progress + 10).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnChangeColor.setOnClickListener {
            val randomColor = Color.argb(200, Random.nextInt(256), Random.nextInt(256), Random.nextInt(256))
            editor.putInt("bg_color", randomColor).apply()
        }
    }
}