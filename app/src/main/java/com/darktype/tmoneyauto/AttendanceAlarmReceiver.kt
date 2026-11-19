package com.darktype.tmoneyauto

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import java.util.Calendar

class AttendanceAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AttendAlarmReceiver"
        const val ACTION_ATTEND = "com.darktype.tmoneyauto.ACTION_ATTEND"

        fun scheduleDailyAlarm(context: Context, hour: Int, minute: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AttendanceAlarmReceiver::class.java).apply {
                action = ACTION_ATTEND
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (before(Calendar.getInstance())) {
                    add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        calendar.timeInMillis,
                        pendingIntent
                    )
                }
                Log.i(TAG, "알람 설정 완료: ${calendar.time}")
            } catch (e: SecurityException) {
                Log.e(TAG, "정확한 알람 권한 필요: ${e.message}")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "알람 수신됨: action=${intent.action}")

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("tmoney_auto_prefs", Context.MODE_PRIVATE)
            val h = prefs.getInt("alarm_hour", 9)
            val m = prefs.getInt("alarm_minute", 0)
            scheduleDailyAlarm(context, h, m)
            return
        }

        // 화면 깨우기
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "tmoneyauto:alarm_wakelock"
        )
        wakeLock.acquire(30000) // 30초 유지

        try {
            // 모바일티머니 앱 실행
            val launchIntent = context.packageManager.getLaunchIntentForPackage("com.lgt.tmoney")
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                context.startActivity(launchIntent)
                Log.i(TAG, "모바일티머니 앱 실행 성공")
            } else {
                Log.e(TAG, "모바일티머니 앱 패키지를 찾을 수 없습니다.")
            }
        } finally {
            // 내일 알람 재설정
            val prefs = context.getSharedPreferences("tmoney_auto_prefs", Context.MODE_PRIVATE)
            val h = prefs.getInt("alarm_hour", 9)
            val m = prefs.getInt("alarm_minute", 0)
            scheduleDailyAlarm(context, h, m)
            wakeLock.release()
        }
    }
}
