package com.moakete

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * شبكة أمان مستقلة تماماً عن فتح التطبيق.
 *
 *  ✅ يشمل الآن: فحص الموقع الجغرافي كل ساعتين.
 *     إن انتقل المستخدم > 10 كم → يحدّث الإحداثيات تلقائياً
 *     ويُعيد جدولة الصلوات بالتوقيت الجديد.
 */
class PrayerRescheduleWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "PrayerRescheduleWorker"
        private const val UNIQUE_WORK_NAME = "prayer_reschedule_safety_net"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder().build()
            val request = PeriodicWorkRequestBuilder<PrayerRescheduleWorker>(
                2, TimeUnit.HOURS   // كان 6 — الآن ساعتان لفحص أسرع للموقع
            ).setConstraints(constraints).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Periodic safety-net worker scheduled (every 2h)")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            // ① فحص الموقع أولاً
            val moved = LocationAdjuster.refreshIfMoved(applicationContext)

            // ② إعادة جدولة الصلوات (سواء انتقل أو لا)
            //    scheduleAllPrayersForDate تتحقق داخلياً من كل صلاة —
            //    إذا وقتها فات اليوم تلقائياً تجدولها بكرة.
            MainActivity.scheduleAllPrayersForDate(applicationContext, Calendar.getInstance())

            Log.d(TAG, "Safety-net reschedule done — moved=$moved")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Safety-net reschedule failed", e)
            Result.retry()
        }
    }
}