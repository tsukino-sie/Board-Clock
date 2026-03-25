package com.example.floatingclock

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
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

    // 폰트 파일 선택기 (Deprecated 경고를 해결한 최신 방식)
    private val fontPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
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

        // 리시버 등록 (Android 14+ 정책 에러 해결)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                this,
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED
            )
        } else {
            ContextCompat.registerReceiver(
                this,
                downloadReceiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }

        // 앱 실행 시 깃허브 업데이트 체크
        checkAndInstallUpdate()

        // 알림 권한 요청 (Android 13 이상)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        // 다른 앱 위에 표시 권한 요청
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

        // 초기값 설정
        toggleClockSwitch.isChecked = prefs.getBoolean("is_clock_enabled", false)
        autoStartSwitch.isChecked = prefs.getBoolean("auto_start", true)
        radiusSeekBar.progress = prefs.getInt("corner_radius", 30)
        sizeSeekBar.progress = prefs.getInt("clock_size", 32) - 10
        alphaSeekBar.progress = prefs.getInt("alpha", 120)

        // 시계 켜기/끄기 설정
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

        // (변경됨) 새 고급 컬러 피커 적용 - 배경색
        btnChangeColor.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("배경색 선택")
                .setPreferenceName("bg_color_dialog")
                .setPositiveButton("적용", ColorEnvelopeListener { envelope, _ ->
                    // envelope.color 안에 선택한 색상이 들어있습니다.
                    editor.putInt("bg_color", envelope.color).apply()
                })
                .setNegativeButton("취소") { dialogInterface, _ ->
                    dialogInterface.dismiss()
                }
                .attachAlphaSlideBar(true)       // 투명도 슬라이더 활성화
                .attachBrightnessSlideBar(true)  // 밝기(검정, 흰색 선택) 슬라이더 활성화
                .setBottomSpace(12)
                .show()
        }

        // (변경됨) 새 고급 컬러 피커 적용 - 텍스트 색상
        btnTextColor.setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("텍스트 색상 선택")
                .setPreferenceName("text_color_dialog")
                .setPositiveButton("적용", ColorEnvelopeListener { envelope, _ ->
                    editor.putInt("text_color", envelope.color).apply()
                })
                .setNegativeButton("취소") { dialogInterface, _ ->
                    dialogInterface.dismiss()
                }
                .attachAlphaSlideBar(true)
                .attachBrightnessSlideBar(true)
                .setBottomSpace(12)
                .show()
        }

        // 폰트 선택 버튼
        btnPickFont.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/x-font-opentype"))
            }
            fontPickerLauncher.launch(intent)
        }

        // 설정 초기화
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

    // 자동 업데이트 로직 (Null 값 타입 매치 오류 해결 완료)
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

                        // 버전 이름이 Null일 경우를 대비해 Elvis 연산자(?:)로 기본값 지정
                        val currentVersionStr = packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0.0"

                        if (latestVersionStr > currentVersionStr) {
                            val downloadUrl = response.body()?.assets?.firstOrNull()?.browser_download_url ?: return

                            val request = DownloadManager.Request(downloadUrl.toUri())
                                .setTitle("전자칠판 시계 업데이트")
                                .setDescription("최신 버전을 다운로드 중입니다.")
                                .setDestinationInExternalFilesDir(this@MainActivity, null, "update.apk")
                                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                            downloadId = manager.enqueue(request)

                            Toast.makeText(this@MainActivity, "최신 업데이트를 다운로드합니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                override fun onFailure(call: Call<Release>, t: Throwable) {
                    // 인터넷 연결 실패 시 무시
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // 메모리 누수 방지
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}