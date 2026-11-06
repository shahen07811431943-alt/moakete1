package com.moakete

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

class PrayerService : Service() {

    /**
     * يوقف الخدمة الأمامية ويحذف الإشعار، باستخدام الدالة الحديثة
     * stopForeground(Int) بدل stopForeground(Boolean) المهملة.
     * على أندرويد أقدم من 24 لا يوجد بديل، فنستخدم القديمة فقط هناك.
     */
    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var originalRingerMode = AudioManager.RINGER_MODE_NORMAL
    private var weChangedRingerMode = false   // ← نعرف نحن من غيّره فقط
    private var isSilentPending = false       // ← هل الوضع الصامت لا يزال منتظراً؟

    // ─── WakeLock يمنع الـ CPU من النوم ────────────────────────────────────
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val TAG = "PrayerService"
        const val NOTIFICATION_ID = 1001

        // Actions خاصة بالوضع الصامت تُرسَل عبر AlarmManager
        const val ACTION_SILENT_START = "com.moakete.ACTION_SILENT_START"
        const val ACTION_SILENT_END   = "com.moakete.ACTION_SILENT_END"

        // زر إيقاف الأذان من الإشعار
        const val ACTION_STOP_AZAN = "com.moakete.ACTION_STOP_AZAN"

        // ⬇️ جديد: يُبثّ عند إتمام الصوت أو كتمه (زر / زر جانبي / إشعار)
        //    تسمعه شاشة القفل AzanLockScreenActivity لتحديث حالتها وإغلاقها
        const val ACTION_AZAN_STOPPED = "com.moakete.ACTION_AZAN_STOPPED"

        // مفتاح لحفظ originalRingerMode في SharedPrefs
        const val PREF_ORIGINAL_RINGER = "original_ringer_mode"
    }

    // ─── onStartCommand ──────────────────────────────────────────────────────
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // ── معالجة أوامر الوضع الصامت القادمة من AlarmManager ──
        when (intent?.action) {
            ACTION_SILENT_START -> {
                handleSilentStart()
                return START_NOT_STICKY
            }
            ACTION_SILENT_END -> {
                handleSilentEnd()
                return START_NOT_STICKY
            }
            ACTION_STOP_AZAN -> {
                // أوقف الصوت فقط — لا تمسّ إعدادات الهاتف
                stopAzanSoundOnly()
                return START_NOT_STICKY
            }
        }

        // ── بدء WakeLock فوراً حتى لا ينام الـ CPU ──────────────────────────
        acquireWakeLock()

        val prayerName = intent?.getStringExtra("PRAYER_NAME") ?: "الصلاة"
        val prayerTime = intent?.getStringExtra("PRAYER_TIME") ?: ""
        val sharedPref = getSharedPreferences("moakete_prefs", Context.MODE_PRIVATE)

        val isFirstAzan = (prayerName == "الأذان الأول")
        val prayerKey = when (prayerName) {
            "الفجر"        -> "fajr"
            "الظهر"        -> "dhuhr"
            "العصر"        -> "asr"
            "المغرب"       -> "maghrib"
            "العشاء"       -> "isha"
            "الأذان الأول" -> "fajr"
            else           -> "fajr"
        }

        val selectedMoazin      = sharedPref.getString("${prayerKey}_moazin", "حمادي عبد الله")
        val customUriString     = sharedPref.getString("custom_uri", null)
        val isAlarmSoundEnabled = sharedPref.getBoolean("${prayerKey}_sound_enabled", true)
        val isSilentEnabled     = sharedPref.getBoolean("${prayerKey}_silent_enabled", false)
        val isVibrationEnabled  = sharedPref.getBoolean("${prayerKey}_vibration_enabled", false)
        val isVibrationOnly     = sharedPref.getBoolean("${prayerKey}_vibration_only", false)
        val silentStartMin      = sharedPref.getFloat("${prayerKey}_silent_s", 5f)
        val silentEndMin        = sharedPref.getFloat("${prayerKey}_silent_e", 20f)

        Log.d(TAG, "Starting prayer service for: $prayerName | silent=$isSilentEnabled")

        // ── إنشاء قناة الإشعار والإشعار الدائم ─────────────────────────────
        createNotificationChannel()

        // ── شاشة القفل هي وجهة الإشعار والشاشة الكاملة ──
        val lockScreenIntent = Intent(this, AzanLockScreenActivity::class.java).apply {
            putExtra("PRAYER_NAME", prayerName)
            putExtra("PRAYER_TIME", prayerTime)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 500, lockScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent لزر إيقاف الصوت فقط
        val stopAzanIntent = Intent(this, PrayerService::class.java).apply {
            action = ACTION_STOP_AZAN
        }
        val stopAzanPendingIntent = PendingIntent.getService(
            this, 9999, stopAzanIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "prayer_alerts_channel")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(
                if (isFirstAzan) "الأذان الأول — استعداداً لصلاة الفجر"
                else "حان الآن موعد صلاة $prayerName"
            )
            .setContentText("حي على الصلاة.. حي على الفلاح")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(null)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "إيقاف الصوت", stopAzanPendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        // إطلاق شاشة القفل فوراً (بالإضافة إلى fullScreenIntent في الإشعار)
        try {
            startActivity(lockScreenIntent)
            Log.d(TAG, "Azan lock screen launched")
        } catch (e: Exception) {
            Log.w(TAG, "startActivity blocked — relying on fullScreenIntent: ${e.message}")
        }

        // ── الاهتزاز ─────────────────────────────────────────────────────────
        if (isVibrationEnabled || isVibrationOnly) {
            try {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                            as android.os.VibratorManager).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                }
                val pattern = longArrayOf(0, 800, 600, 800, 600, 800)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern, -1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering vibration", e)
            }
        }

        // ── الأذان ───────────────────────────────────────────────────────────
        if (isAlarmSoundEnabled && !isVibrationOnly) {
            val audioUri: Uri? = when {
                selectedMoazin == "ملف مخصص من الهاتف 📁" && customUriString != null ->
                    Uri.parse(customUriString)
                else -> {
                    // ── مرجع مباشر بدل getIdentifier() ─────────────────────────────
                    val soundResId = when (selectedMoazin) {
                        "حمادي عبد الله" -> R.raw.sound1
                        "جاسم حمادي"    -> R.raw.sound2
                        "وسام حمادي"    -> R.raw.sound3
                        "عبد الملك جاسم" -> R.raw.sound4
                        "ريان جاسم"     -> R.raw.sound5
                        else -> 0
                    }
                    if (soundResId != 0) Uri.parse("android.resource://$packageName/$soundResId")
                    else null
                }
            }
            audioUri?.let { playAzan(it, isSilentEnabled) }
        } else {
            Log.d(TAG, "Sound disabled — notification only")
            // إذا لا صوت ولا وضع صامت: أوقف الخدمة بعد ثانية
            if (!isSilentEnabled) {
                stopForegroundCompat()
                stopSelf()
            }
        }

        // ── الوضع الصامت التلقائي عبر AlarmManager ──────────────────────────
        if (isSilentEnabled) {
            val silentDuration = (silentEndMin - silentStartMin).toInt()
            if (silentDuration > 0 && silentEndMin > silentStartMin) {
                isSilentPending = true
                scheduleSilentAlarms(silentStartMin, silentEndMin)
            } else {
                Log.w(TAG, "Silent skipped: invalid range start=$silentStartMin end=$silentEndMin")
            }
        }

        return START_NOT_STICKY
    }

    // ─── جدولة بدء/إنهاء الوضع الصامت عبر AlarmManager ────────────────────
    private fun scheduleSilentAlarms(startMin: Float, endMin: Float) {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val now = System.currentTimeMillis()

        val startMs = now + (startMin * 60 * 1000).toLong()
        val endMs   = now + (endMin   * 60 * 1000).toLong()

        // حفظ الوضع الحالي للاستعادة لاحقاً
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        originalRingerMode = audioManager.ringerMode
        getSharedPreferences("moakete_prefs", Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_ORIGINAL_RINGER, originalRingerMode)
            .apply()

        // PendingIntent لبدء الوضع الصامت
        val startPI = PendingIntent.getService(
            this, 9001,
            Intent(this, PrayerService::class.java).setAction(ACTION_SILENT_START),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // PendingIntent لإنهاء الوضع الصامت
        val endPI = PendingIntent.getService(
            this, 9002,
            Intent(this, PrayerService::class.java).setAction(ACTION_SILENT_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startMs, startPI)
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endMs,   endPI)
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, startMs, startPI)
            am.setExact(AlarmManager.RTC_WAKEUP, endMs,   endPI)
        }

        Log.d(TAG, "Silent alarms scheduled: start in ${startMin.toInt()}m, end in ${endMin.toInt()}m")
    }

    private fun cancelSilentAlarms() {
        val am = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val startPI = PendingIntent.getService(
            this, 9001,
            Intent(this, PrayerService::class.java).setAction(ACTION_SILENT_START),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val endPI = PendingIntent.getService(
            this, 9002,
            Intent(this, PrayerService::class.java).setAction(ACTION_SILENT_END),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        am.cancel(startPI)
        am.cancel(endPI)
        isSilentPending = false
        Log.d(TAG, "Silent alarms cancelled")
    }

    // ─── تفعيل الوضع الصامت (يُستدعى من AlarmManager) ──────────────────────
    private fun handleSilentStart() {
        acquireWakeLock()   // أعد الـ WakeLock إن كان انتهى

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !nm.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "DND permission not granted — skipping silent start")
            notifyMissingDndPermission()
            return
        }

        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            // اقرأ الوضع المحفوظ (قد يكون تغيّر منذ جدولة الأذان)
            originalRingerMode = getSharedPreferences("moakete_prefs", Context.MODE_PRIVATE)
                .getInt(PREF_ORIGINAL_RINGER, AudioManager.RINGER_MODE_NORMAL)

            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            weChangedRingerMode = true
            Log.d(TAG, "Silent mode STARTED — original was $originalRingerMode")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting silent mode", e)
        }
    }

    // ─── إنهاء الوضع الصامت (يُستدعى من AlarmManager) ──────────────────────
    private fun handleSilentEnd() {
        restoreRingerIfNeeded()
        isSilentPending = false
        Log.d(TAG, "Silent mode ENDED")

        // إذا انتهى الأذان سابقاً وكنّا ننتظر الوضع الصامت فقط، أوقف الخدمة الآن
        if (mediaPlayer == null || mediaPlayer?.isPlaying == false) {
            stopForegroundCompat()
            stopSelf()
        }
    }

    // ─── استعادة الوضع الصوتي بأمان ─────────────────────────────────────────
    private fun restoreRingerIfNeeded() {
        if (!weChangedRingerMode) return   // ← لم نغيّره نحن
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                !nm.isNotificationPolicyAccessGranted) return

            audioManager.ringerMode = originalRingerMode
            weChangedRingerMode = false
            Log.d(TAG, "Ringer restored to: $originalRingerMode")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring ringer", e)
        }
    }

    // ─── إيقاف الصوت فقط (من زر الإشعار / الزر الجانبي / شاشة القفل) ───────
    //  يوقف MediaPlayer فقط — لا يمسّ إعدادات الهاتف أو الوضع الصامت
    private fun stopAzanSoundOnly() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            Log.d(TAG, "Azan sound stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping azan sound", e)
        }

        // إبلاغ شاشة القفل بتحديث حالتها (تظهر ✓ ثم تُغلق)
        try {
            sendBroadcast(Intent(ACTION_AZAN_STOPPED).setPackage(packageName))
        } catch (_: Exception) {}

        if (!isSilentPending) {
            stopForegroundCompat()
            stopSelf()
        } else {
            updateNotificationStopped()
        }
    }

    // ─── تنبيه المستخدم أن الوضع الصامت لن يعمل بسبب نقص الصلاحية ──────────
    private fun notifyMissingDndPermission() {
        try {
            val settingsIntent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            val pendingIntent = PendingIntent.getActivity(
                this, 9003, settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val warningNotification = NotificationCompat.Builder(this, "prayer_alerts_channel")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("⚠️ الوضع الصامت التلقائي لم يعمل")
                .setContentText("امنح صلاحية \"الوصول لسياسة الإشعارات\" — اضغط هنا")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(NOTIFICATION_ID + 1, warningNotification)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing DND warning notification", e)
        }
    }

    // ─── تحديث الإشعار بعد إيقاف الصوت ────────────────────────────────────
    private fun updateNotificationStopped() {
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notificationIntent  = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val updatedNotification = NotificationCompat.Builder(this, "prayer_alerts_channel")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("تم إيقاف صوت الأذان")
                .setContentText("الوضع الصامت لا يزال نشطاً")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
            notificationManager.notify(NOTIFICATION_ID, updatedNotification)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }

    // ─── تشغيل الأذان ────────────────────────────────────────────────────────
    private fun playAzan(uri: Uri, hasSilentMode: Boolean) {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@PrayerService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )

                setOnPreparedListener {
                    Log.d(TAG, "MediaPlayer prepared - starting playback")
                    start()
                }

                setOnCompletionListener {
                    Log.d(TAG, "Azan completed")
                    // إبلاغ شاشة القفل لتنهي عرضها
                    try {
                        sendBroadcast(Intent(ACTION_AZAN_STOPPED).setPackage(packageName))
                    } catch (_: Exception) {}
                    mediaPlayer?.release()
                    mediaPlayer = null

                    // ── إصلاح: لا نوقف الخدمة إذا كان الوضع الصامت لا يزال منتظراً ──
                    if (!hasSilentMode || !isSilentPending) {
                        stopForegroundCompat()
                        stopSelf()
                    } else {
                        Log.d(TAG, "Azan done — keeping service alive for silent mode")
                        // حرّر الـ WakeLock الآن — AlarmManager سيعيد تشغيل الخدمة عند الحاجة
                        releaseWakeLock()
                    }
                }

                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    mediaPlayer?.release()
                    mediaPlayer = null
                    // إبلاغ شاشة القفل بالخطأ أيضاً
                    try {
                        sendBroadcast(Intent(ACTION_AZAN_STOPPED).setPackage(packageName))
                    } catch (_: Exception) {}
                    if (!hasSilentMode || !isSilentPending) {
                        stopForegroundCompat()
                        stopSelf()
                    } else {
                        releaseWakeLock()
                    }
                    true
                }

                prepareAsync()
            }

            Log.d(TAG, "Started playing azan from: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "Error playing azan", e)
            stopForegroundCompat()
            stopSelf()
        }
    }

    // ─── WakeLock ────────────────────────────────────────────────────────────
    private fun acquireWakeLock() {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        } catch (e: Exception) {
            Log.e(TAG, "Error raising thread priority", e)
        }
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "moakete:PrayerWakeLock"
            ).also {
                // 90 دقيقة كحد أقصى لمنع تسرب الطاقة
                it.acquire(90 * 60 * 1000L)
                Log.d(TAG, "WakeLock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error acquiring WakeLock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d(TAG, "WakeLock released")
            }
            wakeLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock", e)
        }
    }

    // ─── قناة الإشعار ────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                "prayer_alerts_channel",
                "تنبيهات الأذان",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات مواقيتي للصلاة"
                setSound(null, null)
                enableVibration(true)
                setBypassDnd(true)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    // ─── onDestroy ───────────────────────────────────────────────────────────
    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")

        // لا نلغي alarms الوضع الصامت هنا — ستستمر حتى لو أُغلقت الخدمة
        // (AlarmManager مستقل عن الخدمة تماماً)

        restoreRingerIfNeeded()

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing MediaPlayer", e)
        }

        releaseWakeLock()

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}