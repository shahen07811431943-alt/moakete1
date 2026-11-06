package com.moakete

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

class PrayerAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PrayerAlarmReceiver"
        private const val LAST_PRAYER_INDEX = 4
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast: ${intent.action}")

        when (intent.action) {
            // ⬅️ تم حذف حالة ACTION_SCREEN_OFF / ACTION_SCREEN_ON من هنا نهائياً
            //     لأن شاشة القفل الجديدة (AzanLockScreenActivity) تتعامل معها
            //     وتكتم الصوت فقط دون قتل الخدمة (كان السبب الجذري لقطع الأذان).

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_REBOOT,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d(TAG, "Device rebooted - rescheduling prayers for today")
                // فحص الموقع بعد الإقلاع أيضاً
                LocationAdjuster.refreshIfMoved(context)
                MainActivity.scheduleAllPrayersForDate(context, Calendar.getInstance())
                PrayerRescheduleWorker.schedulePeriodic(context)
                return
            }
        }

        val prayerName = intent.getStringExtra("PRAYER_NAME") ?: "الصلاة"
        val prayerTime = intent.getStringExtra("PRAYER_TIME") ?: ""
        Log.d(TAG, "Prayer alarm triggered: $prayerName at $prayerTime")

        val serviceIntent = Intent(context, PrayerService::class.java).apply {
            putExtra("PRAYER_NAME", prayerName)
            putExtra("PRAYER_TIME", prayerTime)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(serviceIntent)
            else
                context.startService(serviceIntent)
            Log.d(TAG, "Prayer service started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting prayer service", e)
        }

        rescheduleAfterFiring(context, intent.action)
    }

    private fun rescheduleAfterFiring(context: Context, action: String?) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val tomorrow = Calendar.getInstance().apply { add(Calendar.DATE, 1) }

        PrayerRescheduleWorker.schedulePeriodic(context)

        when (action) {
            "com.moakete.ACTION_PRAYER_$LAST_PRAYER_INDEX" -> {
                Log.d(TAG, "العشاء fired → scheduling ALL prayers for tomorrow")
                MainActivity.scheduleAllPrayersForDate(context, tomorrow)
            }

            "com.moakete.ACTION_PRAYER_FIRST_AZAN" -> {
                val sharedPref = context.getSharedPreferences("moakete_prefs", Context.MODE_PRIVATE)
                if (!sharedPref.getBoolean("fajr_first_azan_enabled", false)) return
                try {
                    val mins  = sharedPref.getFloat("fajr_first_azan_minutes", 30f).toInt()
                    val tTimes = PrayerData.getTimes(tomorrow.get(Calendar.MONTH) + 1, tomorrow.get(Calendar.DAY_OF_MONTH))
                    val fajrParts = tTimes[0].split(":")
                    val fajrCal = (tomorrow.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, fajrParts[0].toInt())
                        set(Calendar.MINUTE,      fajrParts[1].toInt())
                        set(Calendar.SECOND,      0); set(Calendar.MILLISECOND, 0)
                        add(Calendar.MINUTE, -mins)
                    }
                    val pi = PendingIntent.getBroadcast(
                        context, 10,
                        Intent(context, PrayerAlarmReceiver::class.java).apply {
                            this.action = "com.moakete.ACTION_PRAYER_FIRST_AZAN"
                            putExtra("PRAYER_NAME", "الأذان الأول")
                            putExtra("PRAYER_TIME", tTimes[0])
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    MainActivity.scheduleSingleAlarm(alarmManager, fajrCal, pi)
                    Log.d(TAG, "Rescheduled الأذان الأول for tomorrow")
                } catch (e: Exception) { Log.e(TAG, "Error rescheduling first azan", e) }
            }

            else -> {
                val index = action?.removePrefix("com.moakete.ACTION_PRAYER_")?.toIntOrNull() ?: return
                try {
                    val tTimes = PrayerData.getTimes(tomorrow.get(Calendar.MONTH) + 1, tomorrow.get(Calendar.DAY_OF_MONTH))
                    val prayerNames = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")
                    val timeStrList = listOf(tTimes[0], tTimes[2], tTimes[3], tTimes[4], tTimes[5])
                    if (index > timeStrList.lastIndex) return

                    val timeStr = timeStrList[index]
                    val name    = prayerNames[index]

                    val parts = timeStr.split(":")
                    var hour  = parts[0].toInt()
                    if (name == "العصر" || name == "المغرب" || name == "العشاء") {
                        if (hour != 12) hour += 12
                    }
                    val alarmCal = (tomorrow.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, hour)
                        set(Calendar.MINUTE,      parts[1].toInt())
                        set(Calendar.SECOND,      0); set(Calendar.MILLISECOND, 0)
                    }
                    val pi = PendingIntent.getBroadcast(
                        context, index,
                        Intent(context, PrayerAlarmReceiver::class.java).apply {
                            this.action = "com.moakete.ACTION_PRAYER_$index"
                            putExtra("PRAYER_NAME", name)
                            putExtra("PRAYER_TIME", timeStr)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    MainActivity.scheduleSingleAlarm(alarmManager, alarmCal, pi)
                    Log.d(TAG, "Rescheduled $name for tomorrow")
                } catch (e: Exception) { Log.e(TAG, "Error rescheduling prayer index $index", e) }
            }
        }
    }
}

/**
 * يستمع لتغيّر التوقيت/التاريخ — يُطلق تلقائياً عند:
 *   • السفر بين دول (أندرويد يكتشف من برج الشبكة)
 *   • تفعيل/تعطيل التوقيت الصيفي
 *   • تغيير يدوي للتاريخ أو الساعة
 */
class TimezoneChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_DATE_CHANGED -> {
                Log.i("TZReceiver", "Time changed: ${intent.action}")
                LocationAdjuster.refreshIfMoved(context)
                MainActivity.scheduleAllPrayersForDate(context, Calendar.getInstance())
            }
        }
    }
}