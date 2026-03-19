package com.example.floatingclock

import android.annotation.SuppressLint
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
//import kotlin.random.Random
import com.github.dhaval2404.colorpicker.ColorPickerDialog
import com.github.dhaval2404.colorpicker.model.ColorShape
import androidx.appcompat.app.AlertDialog

class MainActivity : AppCompatActivity() {
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                // 파일 복사
                val inputStream = contentResolver.openInputStream(uri)
                val outputFile = java.io.File(filesDir, "custom_font.ttf")
                inputStream?.use { input ->
                    outputFile.outputStream().use { output -> input.copyTo(output) }
                }
                // 경로 저장
                getSharedPreferences("ClockPrefs", MODE_PRIVATE).edit()
                    .putString("font_path", outputFile.absolutePath)
                    .apply()
            }
        }
    }

    @SuppressLint("ResourceType")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 알림 권한 요청 (Android 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        // 다른 앱 위에 표시 권한
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
        val alphaSeekBar = findViewById<SeekBar>(R.id.alphaSeekBar) // 추가된 투명도 SeekBar
        val btnChangeColor = findViewById<Button>(R.id.btnChangeColor)
        val btnReset = findViewById<Button>(R.id.btnReset) // 추가된 초기화 버튼

        // 초기값 설정
        toggleClockSwitch.isChecked = prefs.getBoolean("is_clock_enabled", false)
        autoStartSwitch.isChecked = prefs.getBoolean("auto_start", true)
        radiusSeekBar.progress = prefs.getInt("corner_radius", 30)
        sizeSeekBar.progress = prefs.getInt("clock_size", 32)
        alphaSeekBar.progress = prefs.getInt("alpha", 120)

        // 시계 켜기/끄기
        toggleClockSwitch.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("is_clock_enabled", isChecked).apply()

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

        // 자동 실행 설정
        autoStartSwitch.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("auto_start", isChecked).apply()
        }

        // 모서리 곡률 조절
        radiusSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                editor.putInt("corner_radius", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 시계 크기 조절
        sizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                editor.putInt("clock_size", progress + 10).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 투명도 조절
        alphaSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                editor.putInt("alpha", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 배경색 랜덤 변경
        btnChangeColor.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("배경색 선택")
                .setColorShape(ColorShape.SQAURE)
                .setDefaultColor(Color.BLACK)
                .setColorListener { color: Int, _: String ->
                    editor.putInt("bg_color", color).apply()
                }.show()
        }

        val btnPickFont = findViewById<Button>(R.id.btnPickFont)
        btnPickFont.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype"))
            }
            startActivityForResult(intent, 200) // 200은 폰트 선택 요청 코드
        }

        // 설정 초기화
        btnReset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("초기화 확인")
                .setMessage("정말로 모든 설정을 초기화할까요?\n이건 되돌릴 수 없어요.")
                .setPositiveButton("초기화") { _, _ ->
                    editor.clear().apply()

                    stopService(Intent(this, FloatingClockService::class.java))

                    //toggleClockSwitch.isChecked = false
                    radiusSeekBar.progress = 30
                    sizeSeekBar.progress = 22 // 32-10
                    alphaSeekBar.progress = 120

                    recreate()
                }
                .setNegativeButton("취소", null)
                .show()
        }

        val btnTextColor = findViewById<Button>(R.id.btnTextColor)
        btnTextColor.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("텍스트색 선택")
                .setColorListener { color: Int, _: String ->
                    editor.putInt("text_color", color).apply()
                }.show()
        }


    }
}