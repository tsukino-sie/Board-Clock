package com.example.floatingclock

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.github.dhaval2404.colorpicker.ColorPickerDialog
import com.github.dhaval2404.colorpicker.model.ColorShape
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class MainActivity : AppCompatActivity() {

    private var downloadId: Long = -1L

    // 다운로드가 완료되었을 때 설치 화면을 띄워주는 리시버
    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (id == downloadId) {
                val apkFile = File(getExternalFilesDir(null), "update.apk")
                if (apkFile.exists()) {
                    val uri = FileProvider.getUriForFile(context, "$packageName.provider", apkFile)
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(installIntent)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val outputFile = File(filesDir, "custom_font.ttf")
                    inputStream?.use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    // 폰트 파일 경로 저장
                    getSharedPreferences("ClockPrefs", MODE_PRIVATE).edit()
                        .putString("font_path", outputFile.absolutePath)
                        .apply()

                    Toast.makeText(this, "폰트가 성공적으로 적용되었습니다.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "폰트 적용에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("ResourceType")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //[에러 해결됨] 안드로이드 공식 라이브러리인 ContextCompat을 사용하여 안전하게 리시버 등록
        ContextCompat.registerReceiver(
            this,
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

        checkAndInstallUpdate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

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
        val alphaSeekBar = findViewById<SeekBar>(R.id.alphaSeekBar)
        val btnChangeColor = findViewById<Button>(R.id.btnChangeColor)
        val btnTextColor = findViewById<Button>(R.id.btnTextColor)
        val btnPickFont = findViewById<Button>(R.id.btnPickFont)
        val btnReset = findViewById<Button>(R.id.btnReset)

        toggleClockSwitch.isChecked = prefs.getBoolean("is_clock_enabled", false)
        autoStartSwitch.isChecked = prefs.getBoolean("auto_start", true)
        radiusSeekBar.progress = prefs.getInt("corner_radius", 30)
        sizeSeekBar.progress = prefs.getInt("clock_size", 32) - 10
        alphaSeekBar.progress = prefs.getInt("alpha", 120)

        toggleClockSwitch.setOnCheckedChangeListener { _, isChecked ->
            editor.putBoolean("is_clock_enabled", isChecked).apply()

            if (isChecked && !Settings.canDrawOverlays(this)) {
                toggleClockSwitch.isChecked = false
                Toast.makeText(this, "다른 앱 위에 표시 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
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

        alphaSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                editor.putInt("alpha", progress).apply()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnChangeColor.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("배경색 선택")
                .setColorShape(ColorShape.SQAURE)
                .setDefaultColor(Color.BLACK)
                .setColorListener { color: Int, _: String ->
                    editor.putInt("bg_color", color).apply()
                }.show()
        }

        btnTextColor.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("텍스트 색상 선택")
                .setDefaultColor(Color.WHITE)
                .setColorListener { color: Int, _: String ->
                    editor.putInt("text_color", color).apply()
                }.show()
        }

        btnPickFont.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype"))
            }
            startActivityForResult(intent, 200)
        }

        btnReset.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("초기화 확인")
                .setMessage("정말로 모든 설정을 기본값으로 초기화할까요?\n이 작업은 되돌릴 수 없습니다.")
                .setPositiveButton("초기화") { _, _ ->
                    editor.clear().apply()
                    stopService(Intent(this, FloatingClockService::class.java))
                    recreate()
                }
                .setNegativeButton("취소", null)
                .show()
        }
    }

    private fun checkAndInstallUpdate() {
        try {
            val retrofit = Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val api = retrofit.create(GithubApi::class.java)
            api.getLatestRelease().enqueue(object : Callback<Release> {
                override fun onResponse(call: Call<Release>, response: Response<Release>) {
                    if (response.isSuccessful) {
                        val latestVersionStr = response.body()?.tag_name?.replace("v", "") ?: return
                        val currentVersionStr = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"

                        if (latestVersionStr > currentVersionStr) {
                            val downloadUrl = response.body()?.assets?.firstOrNull()?.browser_download_url ?: return

                            val request = DownloadManager.Request(downloadUrl.toUri())
                                .setTitle("시계 업데이트")
                                .setDescription("최신 버전을 다운로드 중입니다.")
                                .setDestinationInExternalFilesDir(this@MainActivity, null, "update.apk")
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                            downloadId = manager.enqueue(request)

                            Toast.makeText(this@MainActivity, "최신 업데이트를 다운로드하고 있습니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onFailure(call: Call<Release>, t: Throwable) {}
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}