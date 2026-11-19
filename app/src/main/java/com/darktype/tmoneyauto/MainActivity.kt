package com.darktype.tmoneyauto

import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.darktype.tmoneyauto.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSettings()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("tmoney_auto_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("alarm_hour")) {
            prefs.edit().putInt("alarm_hour", 7).putInt("alarm_minute", 0).apply()
            AttendanceAlarmReceiver.scheduleDailyAlarm(this, 7, 0)
        }
        val hour = prefs.getInt("alarm_hour", 7)
        val minute = prefs.getInt("alarm_minute", 0)
        val nasUrl = prefs.getString("nas_url", "https://darktype.synology.me:7878") ?: ""
        val userId = prefs.getString("nas_user_id", "") ?: ""
        val password = prefs.getString("nas_password", "") ?: ""
        val isAuth = prefs.getBoolean("is_authenticated", false)

        val timeStr = String.format("%02d:%02d", hour, minute)
        binding.tvAlarmTime.text = "매일 자동 실행: $timeStr"
        binding.etNasUrl.setText(nasUrl)
        binding.etNasUserId.setText(userId)
        binding.etNasPassword.setText(password)
        updateNasAuthStatus(isAuth, userId)
    }

    private fun updateNasAuthStatus(isAuth: Boolean, userId: String) {
        if (isAuth && userId.isNotEmpty()) {
            binding.tvNasAuthStatus.text = "계정 연동 상태: 🟢 인증 완료 (사용자: $userId)"
            binding.tvNasAuthStatus.setTextColor(getColor(R.color.status_green))
        } else {
            binding.tvNasAuthStatus.text = "계정 연동 상태: 🔴 미인증 (아이디/비밀번호 확인 필요)"
            binding.tvNasAuthStatus.setTextColor(getColor(R.color.status_red))
        }
    }

    private fun setupListeners() {
        // 접근성 설정 열기
        binding.btnOpenAcc.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // 배터리 최적화 예외
        binding.btnOpenBattery.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }

        // 알람 시각 변경
        binding.btnChangeTime.setOnClickListener {
            val prefs = getSharedPreferences("tmoney_auto_prefs", Context.MODE_PRIVATE)
            val curH = prefs.getInt("alarm_hour", 7)
            val curM = prefs.getInt("alarm_minute", 0)

            TimePickerDialog(this, { _, hourOfDay, minute ->
                prefs.edit().putInt("alarm_hour", hourOfDay).putInt("alarm_minute", minute).apply()
                AttendanceAlarmReceiver.scheduleDailyAlarm(this, hourOfDay, minute)
                val timeStr = String.format("%02d:%02d", hourOfDay, minute)
                binding.tvAlarmTime.text = "매일 자동 실행: $timeStr"
                Toast.makeText(this, "실행 시각이 $timeStr 로 변경되었습니다.", Toast.LENGTH_SHORT).show()
            }, curH, curM, true).show()
        }

        // 대시보드 로그인 및 연동 확인
        binding.btnLoginNas.setOnClickListener {
            val url = binding.etNasUrl.text.toString().trim()
            val uid = binding.etNasUserId.text.toString().trim()
            val pwd = binding.etNasPassword.text.toString()

            if (uid.isEmpty() || pwd.isEmpty()) {
                Toast.makeText(this, "아이디와 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnLoginNas.isEnabled = false
            binding.btnLoginNas.text = "인증 중..."

            activityScope.launch {
                val (ok, message) = NasReporter.login(url, uid, pwd)
                binding.btnLoginNas.isEnabled = true
                binding.btnLoginNas.text = "🔐 대시보드 로그인 및 연동 확인"

                if (ok) {
                    val prefs = getSharedPreferences("tmoney_auto_prefs", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("nas_url", url)
                        .putString("nas_user_id", uid)
                        .putString("nas_password", pwd)
                        .putBoolean("is_authenticated", true)
                        .apply()
                    updateNasAuthStatus(true, uid)
                    Toast.makeText(this@MainActivity, "로그인 인증 성공! (사용자: $uid)", Toast.LENGTH_SHORT).show()
                } else {
                    val prefs = getSharedPreferences("tmoney_auto_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("is_authenticated", false).apply()
                    updateNasAuthStatus(false, uid)
                    Toast.makeText(this@MainActivity, "인증 실패: $message", Toast.LENGTH_LONG).show()
                }
            }
        }

        // NAS URL 및 설정 저장
        binding.btnSaveUrl.setOnClickListener {
            val url = binding.etNasUrl.text.toString().trim()
            val uid = binding.etNasUserId.text.toString().trim()
            val pwd = binding.etNasPassword.text.toString()
            val prefs = getSharedPreferences("tmoney_auto_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("nas_url", url)
                .putString("nas_user_id", uid)
                .putString("nas_password", pwd)
                .apply()
            Toast.makeText(this, "NAS 설정 정보가 저장되었습니다.", Toast.LENGTH_SHORT).show()
        }

        // 테스트 실행
        binding.btnTestLaunch.setOnClickListener {
            val launchIntent = packageManager.getLaunchIntentForPackage("com.lgt.tmoney")
                ?: Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName("com.lgt.tmoney", "com.tmoney.activity.IntroActivity")
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            try {
                startActivity(launchIntent)
                Toast.makeText(this, "모바일티머니 앱을 실행합니다...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "실행 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateStatus() {
        val isAccEnabled = isAccessibilityServiceEnabled()
        if (isAccEnabled) {
            binding.tvAccStatus.text = "상태: 🟢 접근성 권한 켜짐 (정상 동작 중)"
            binding.tvAccStatus.setTextColor(getColor(R.color.status_green))
        } else {
            binding.tvAccStatus.text = "상태: 🔴 접근성 권한 꺼짐 (설정에서 켜주세요)"
            binding.tvAccStatus.setTextColor(getColor(R.color.status_red))
        }

        binding.tvLog.text = TmoneyAccessibilityService.lastResultStatus
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val serviceName = "$packageName/${TmoneyAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(serviceName, ignoreCase = true)) {
                return true
            }
        }
        return false
    }
}
