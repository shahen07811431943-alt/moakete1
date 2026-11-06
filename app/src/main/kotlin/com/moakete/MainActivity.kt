package com.moakete

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

val ARABIC_LOCALE: Locale = Locale.Builder().setLanguage("ar").build()

class MainActivity : ComponentActivity() {

    private val timezoneReceiver = TimezoneChangeReceiver()

    private var onNotificationResult: (() -> Unit)? = null
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> onNotificationResult?.invoke() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ═══ تسجيل مستقبل تغيّر التوقيت (يعمل حتى لو التطبيق مفتوح) ═══
        val tzFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(timezoneReceiver, tzFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(timezoneReceiver, tzFilter)
        }

        setContent {
            val currentDensity = LocalDensity.current
            val stableDensityValue = if (Build.VERSION.SDK_INT >= 34) {
                android.util.DisplayMetrics.DENSITY_DEVICE_STABLE / 160f
            } else {
                currentDensity.density
            }
            val fixedDensity = Density(density = stableDensityValue, fontScale = 1f)

            CompositionLocalProvider(LocalDensity provides fixedDensity) {
                val context = LocalContext.current
                val sharedPref = remember {
                    context.getSharedPreferences("moakete_prefs", Context.MODE_PRIVATE)
                }

                var currentScreen by remember {
                    mutableStateOf(if (allPermissionsGranted(context)) "main" else "permissions")
                }
                var permissionTick by remember { mutableStateOf(0) }
                var currentTime by remember { mutableStateOf(Calendar.getInstance().time) }
                var selectedPrayerForCalendar by remember { mutableStateOf("") }
                var appBrightness by remember { mutableStateOf(sharedPref.getFloat("brightness", 0.7f)) }

                var registrationInfo by remember { mutableStateOf<RegistrationInfo?>(null) }
                var registrationError by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    val existing = GoogleAuthManager.currentUser()
                    val user = existing
                        ?: GoogleAuthManager.trySilentSignIn(context)
                        ?: GoogleAuthManager.signInWithPicker(context)
                    if (user == null) {
                        registrationError = "تعذّر تسجيل الدخول بحساب جوجل (لم يتم اختيار حساب أو فشل التحقق). راجع Logcat بحثًا عن GoogleAuthManager."
                        Log.e(TAG, "فشل تسجيل الدخول بجوجل — user == null بعد كل المحاولات")
                    } else {
                        try {
                            registrationInfo = UserRegistry.registerOrFetch(user)
                            registrationError = null
                        } catch (e: Exception) {
                            val msg = e.message ?: e.toString()
                            registrationError = when {
                                msg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                                    "تم رفض الصلاحية من Firestore (PERMISSION_DENIED) — راجع قواعد الأمان بلوحة Firebase."
                                msg.contains("UNAVAILABLE", ignoreCase = true) ->
                                    "تعذّر الوصول لخادم Firestore — تحقق من اتصال الإنترنت."
                                else -> "فشل التسجيل بالقاعدة المشتركة: $msg"
                            }
                            Log.e(TAG, "فشل التسجيل بالقاعدة المشتركة", e)
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    scheduleAllPrayers(context)
                    PrayerRescheduleWorker.schedulePeriodic(context)
                }

                // ═══ فحص سريع للموقع عند فتح التطبيق ═══
                LaunchedEffect(Unit) {
                    if (LocationAdjuster.refreshIfMoved(context)) {
                        scheduleAllPrayers(context)
                        Log.i(TAG, "Location auto-updated on app open")
                    }
                }

                LaunchedEffect(Unit) {
                    while (true) {
                        currentTime = Calendar.getInstance().time
                        delay(1000)
                    }
                }

                val tabs = remember { listOf("main", "azkar", "qibla", "month", "settings") }
                val pagerState = rememberPagerState(
                    initialPage = tabs.indexOf(currentScreen).coerceAtLeast(0)
                ) { tabs.size }

                when (currentScreen) {
                    "permissions" -> PermissionScreen(
                        onRequestNotification = {
                            if (Build.VERSION.SDK_INT >= 33) {
                                onNotificationResult = { permissionTick++ }
                                requestPermissionLauncher.launch("android.permission.POST_NOTIFICATIONS")
                            }
                        },
                        onAllDone = {
                            currentScreen = "main"
                            scheduleAllPrayers(context)
                        }
                    )
                    "calendar" -> Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) {
                            PrayerCalendarScreen(selectedPrayerForCalendar) { currentScreen = "main" }
                        }
                        MoakeetNavBar(current = "main", onNavigate = { currentScreen = it })
                    }
                    else -> {
                        LaunchedEffect(currentScreen) {
                            val idx = tabs.indexOf(currentScreen)
                            if (idx >= 0 && idx != pagerState.currentPage) {
                                pagerState.animateScrollToPage(idx)
                            }
                        }
                        LaunchedEffect(pagerState.currentPage) {
                            val target = tabs[pagerState.currentPage]
                            if (currentScreen != target) currentScreen = target
                        }

                        Column(Modifier.fillMaxSize()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.weight(1f).fillMaxWidth()
                            ) { page ->
                                when (tabs[page]) {
                                    "main" -> MainScreen(
                                        currentTime, appBrightness,
                                        onPrayerClick = { selectedPrayerForCalendar = it; currentScreen = "calendar" }
                                    )
                                    "azkar" -> AzkarListScreen { currentScreen = "main" }
                                    "qibla" -> QiblaScreen { currentScreen = "main" }
                                    "month" -> MonthlyScheduleScreen { currentScreen = "main" }
                                    "settings" -> SettingsScreen(
                                        appBrightness, { appBrightness = it },
                                        registrationInfo,
                                        registrationError
                                    ) {
                                        saveSettings(context, appBrightness)
                                        currentScreen = "main"
                                        scheduleAllPrayers(context)
                                    }
                                }
                            }
                            MoakeetNavBar(current = currentScreen, onNavigate = { currentScreen = it })
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(timezoneReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun allPermissionsGranted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS")
            != PackageManager.PERMISSION_GRANTED) return false
        if (Build.VERSION.SDK_INT >= 31) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!am.canScheduleExactAlarms()) return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) return false
        }
        return true
    }

    @SuppressLint("BatteryLife")
    @Suppress("UNUSED")
    private fun requestIgnoreBatteryOptimizations(context: Context) {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!powerManager.isIgnoringBatteryOptimizations(context.packageName)) {
                try {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening battery settings", e)
                }
            }
        }
    }

    private fun saveSettings(context: Context, brightness: Float) {
        context.getSharedPreferences("moakete_prefs", Context.MODE_PRIVATE).edit()
            .putFloat("brightness", brightness)
            .apply()
    }

    private fun scheduleAllPrayers(context: Context) {
        Companion.scheduleAllPrayersForDate(context, Calendar.getInstance())
    }

    companion object {
        private const val TAG = "MainActivity"

        /**
         * تُجدول جميع منبهات يوم معيّن. تُستدعى من:
         *   • MainActivity (فتح التطبيق / حفظ الإعدادات)
         *   • PrayerAlarmReceiver (بعد كل أذان)
         *   • TimezoneChangeReceiver (تغيّر التوقيت)
         *   • PrayerRescheduleWorker (كل ساعتين)
         */
        fun scheduleAllPrayersForDate(context: Context, targetDate: Calendar) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val month = targetDate.get(Calendar.MONTH) + 1
            val day   = targetDate.get(Calendar.DAY_OF_MONTH)

            // ═══ قراءة موحّدة للموقع عبر LocationAdjuster ═══
            val loc   = LocationAdjuster.readCached(context)
            val times = LocationAdjuster.getAdjustedTimes(
                loc.lat, loc.lng, month, day, loc.alt
            )

            val prayerNames = listOf("الفجر", "الظهر", "العصر", "المغرب", "العشاء")
            val prayerTimes = listOf(times[0], times[2], times[3], times[4], times[5])

            cancelAllPrayersStatic(context, alarmManager)

            prayerTimes.forEachIndexed { index, timeStr ->
                try {
                    val alarmCal = buildAlarmCalendar(timeStr, prayerNames[index], targetDate)

                    val now = Calendar.getInstance()
                    if (alarmCal.before(now)) alarmCal.add(Calendar.DATE, 1)

                    val intent = Intent(context, PrayerAlarmReceiver::class.java).apply {
                        action = "com.moakete.ACTION_PRAYER_$index"
                        putExtra("PRAYER_NAME", prayerNames[index])
                        putExtra("PRAYER_TIME", timeStr)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context, index, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    scheduleSingleAlarm(alarmManager, alarmCal, pendingIntent)
                    Log.d(TAG, "Scheduled ${prayerNames[index]} at ${alarmCal.time}")
                } catch (e: Exception) {
                    Log.e(TAG, "Error scheduling ${prayerNames[index]}: ${e.message}", e)
                }
            }

            // الأذان الأول (قبل الفجر) إن كان مفعّلاً
            val sharedPref = context.getSharedPreferences("moakete_prefs", Context.MODE_PRIVATE)
            if (sharedPref.getBoolean("fajr_first_azan_enabled", false)) {
                try {
                    val mins = sharedPref.getFloat("fajr_first_azan_minutes", 30f).toInt()
                    val fajrCal = buildAlarmCalendar(times[0], "الفجر", targetDate)
                    fajrCal.add(Calendar.MINUTE, -mins)
                    val now = Calendar.getInstance()
                    if (fajrCal.before(now)) fajrCal.add(Calendar.DATE, 1)
                    scheduleSingleAlarm(alarmManager, fajrCal, PendingIntent.getBroadcast(
                        context, 10,
                        Intent(context, PrayerAlarmReceiver::class.java).apply {
                            action = "com.moakete.ACTION_PRAYER_FIRST_AZAN"
                            putExtra("PRAYER_NAME", "الأذان الأول")
                            putExtra("PRAYER_TIME", times[0])
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                    Log.d(TAG, "Scheduled الأذان الأول")
                } catch (e: Exception) { Log.e(TAG, "Error scheduling first azan", e) }
            } else {
                try {
                    alarmManager.cancel(PendingIntent.getBroadcast(
                        context, 10,
                        Intent(context, PrayerAlarmReceiver::class.java).apply {
                            action = "com.moakete.ACTION_PRAYER_FIRST_AZAN"
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                } catch (_: Exception) {}
            }
        }

        private fun buildAlarmCalendar(timeStr: String, prayerName: String, targetDate: Calendar): Calendar {
            val parts  = timeStr.split(":")
            var hour   = parts[0].toInt()
            val minute = parts[1].toInt()
            when (prayerName) {
                "العصر", "المغرب", "العشاء", "الأذان الأول" -> {
                    if (prayerName != "الأذان الأول" && hour != 12) hour += 12
                }
            }
            return (targetDate.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE,      minute)
                set(Calendar.SECOND,      0)
                set(Calendar.MILLISECOND, 0)
            }
        }

        fun scheduleSingleAlarm(alarmManager: AlarmManager, calendar: Calendar, pendingIntent: PendingIntent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms())
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(calendar.timeInMillis, pendingIntent), pendingIntent)
                else
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }
        }

        fun cancelAllPrayersStatic(context: Context, alarmManager: AlarmManager) {
            for (i in 0..4) {
                try {
                    alarmManager.cancel(PendingIntent.getBroadcast(
                        context, i,
                        Intent(context, PrayerAlarmReceiver::class.java).apply { action = "com.moakete.ACTION_PRAYER_$i" },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    ))
                } catch (_: Exception) {}
            }
            try {
                alarmManager.cancel(PendingIntent.getBroadcast(
                    context, 10,
                    Intent(context, PrayerAlarmReceiver::class.java).apply { action = "com.moakete.ACTION_PRAYER_FIRST_AZAN" },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ))
            } catch (_: Exception) {}
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  واجهات العرض
// ─────────────────────────────────────────────────────────────

@Composable
fun MainScreen(
    time: Date, brightness: Float,
    onPrayerClick: (String) -> Unit
) {
    val calendar  = Calendar.getInstance().apply { timeInMillis = time.time }
    val context2  = LocalContext.current
    val prefs2    = remember { context2.getSharedPreferences("moakete_prefs", Context.MODE_PRIVATE) }
    val loc2      = remember { LocationAdjuster.readCached(context2) }
    val todayTimes = LocationAdjuster.getAdjustedTimes(
        loc2.lat, loc2.lng,
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
        loc2.alt
    )
    val timeString = SimpleDateFormat("hh:mm:ss a", ARABIC_LOCALE).format(time)
    val dateString = SimpleDateFormat("EEEE، d MMMM yyyy", ARABIC_LOCALE).format(time)

    val prayersForCheck = listOf(
        Triple("الفجر",  todayTimes[0].split(":")[0].toInt(), todayTimes[0].split(":")[1].toInt()),
        Triple("الظهر",  todayTimes[2].split(":")[0].toInt(), todayTimes[2].split(":")[1].toInt()),
        Triple("العصر",  todayTimes[3].split(":")[0].toInt(), todayTimes[3].split(":")[1].toInt()),
        Triple("المغرب", todayTimes[4].split(":")[0].toInt(), todayTimes[4].split(":")[1].toInt()),
        Triple("العشاء", todayTimes[5].split(":")[0].toInt(), todayTimes[5].split(":")[1].toInt())
    )

    val nextPrayerData = remember(time) {
        val now = Calendar.getInstance()
        var targetPrayer = prayersForCheck[0]; var isNextDay = true
        for (p in prayersForCheck) {
            var pHour = p.second
            when (p.first) { "العصر", "المغرب", "العشاء" -> if (pHour < 12) pHour += 12 }
            val pCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, pHour); set(Calendar.MINUTE, p.third); set(Calendar.SECOND, 0)
            }
            if (now.before(pCal)) { targetPrayer = p; isNextDay = false; break }
        }
        val targetCal = Calendar.getInstance().apply {
            var th = targetPrayer.second
            when (targetPrayer.first) { "العصر", "المغرب", "العشاء" -> if (th < 12) th += 12 }
            set(Calendar.HOUR_OF_DAY, th); set(Calendar.MINUTE, targetPrayer.third); set(Calendar.SECOND, 0)
            if (isNextDay) add(Calendar.DATE, 1)
        }
        val diff = targetCal.timeInMillis - now.timeInMillis
        Pair(targetPrayer.first,
            String.format(Locale.ENGLISH, "%02d:%02d:%02d", diff / 3600000, (diff / 60000) % 60, (diff / 1000) % 60))
    }

    Box(Modifier.fillMaxSize().background(AppColors.BgCream)) {
        val dimAlpha = (1f - brightness).coerceIn(0f, 0.45f)
        if (dimAlpha > 0f) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimAlpha)))
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Spacer(Modifier.height(55.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("🌙", fontSize = 34.sp); Text("✨", fontSize = 26.sp)
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dateString, color = AppColors.TextSub, fontSize = 16.sp)
                Text(timeString, color = AppColors.TextDark, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text("الوقت المتبقي لـ ${nextPrayerData.first}:", color = AppColors.TextSub, fontSize = 13.sp)
                Text(nextPrayerData.second, color = AppColors.Gold, fontSize = 60.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(25.dp))
                PrayerItem("الفجر",  todayTimes[0], nextPrayerData.first == "الفجر")  { onPrayerClick("الفجر") }
                PrayerItem("الشروق", todayTimes[1], false)                            { onPrayerClick("الشروق") }
                PrayerItem("الظهر",  todayTimes[2], nextPrayerData.first == "الظهر")  { onPrayerClick("الظهر") }
                PrayerItem("العصر",  todayTimes[3], nextPrayerData.first == "العصر")  { onPrayerClick("العصر") }
                PrayerItem("المغرب", todayTimes[4], nextPrayerData.first == "المغرب") { onPrayerClick("المغرب") }
                PrayerItem("العشاء", todayTimes[5], nextPrayerData.first == "العشاء") { onPrayerClick("العشاء") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun PrayerCalendarScreen(prayerName: String, onBack: () -> Unit) {
    val prayerIndex = when (prayerName) {
        "الفجر" -> 0; "الشروق" -> 1; "الظهر" -> 2
        "العصر" -> 3; "المغرب" -> 4; "العشاء" -> 5; else -> 0
    }
    val screenTitle = if (prayerName == "الشروق") "تقويم الشروق" else "تقويم صلاة $prayerName"

    Column(Modifier.fillMaxSize().background(AppColors.BgCream).padding(top = 40.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AppColors.TextDark)
            }
            Text(screenTitle, color = AppColors.TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
            items(12) { monthIdx ->
                val monthNumber = monthIdx + 1
                val monthName = SimpleDateFormat("MMMM", ARABIC_LOCALE).format(
                    Calendar.getInstance().apply { set(Calendar.MONTH, monthIdx) }.time
                )
                Text(monthName, color = AppColors.Gold, fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(vertical = 12.dp))
                val ctx3   = LocalContext.current
                val loc3   = remember { LocationAdjuster.readCached(ctx3) }
                val daysInMonth = when (monthNumber) {
                    1, 3, 5, 7, 8, 10, 12 -> 31
                    4, 6, 9, 11            -> 30
                    2                      -> 29
                    else                   -> 30
                }
                val monthData = Array(daysInMonth) { dayIdx ->
                    LocationAdjuster.getAdjustedTimes(loc3.lat, loc3.lng, monthNumber, dayIdx + 1, loc3.alt)
                }
                monthData.forEachIndexed { dIdx, dTimes ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("يوم ${dIdx + 1}", color = AppColors.TextSub, fontSize = 16.sp)
                        Text(dTimes[prayerIndex], color = AppColors.TextDark, fontSize = 16.sp)
                    }
                }
                HorizontalDivider(color = AppColors.TextDark.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
fun PrayerItem(name: String, time: String, isHigh: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .background(if (isHigh) AppColors.Gold else AppColors.CardBg.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(horizontal = 15.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(time, color = AppColors.TextDark, fontSize = 20.sp)
        Text(name, color = AppColors.TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MoakeetNavBar(current: String, onNavigate: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.CardBg.copy(alpha = 0.75f))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        NavBarItem("الرئيسية", Icons.Filled.Home, current == "main") { onNavigate("main") }
        NavBarItem("الأذكار", Icons.AutoMirrored.Filled.MenuBook, current == "azkar") { onNavigate("azkar") }
        NavBarItem("القبلة", Icons.Filled.Explore, current == "qibla") { onNavigate("qibla") }
        NavBarItem("الشهر", Icons.Filled.CalendarMonth, current == "month") { onNavigate("month") }
        NavBarItem("الإعدادات", Icons.Filled.Settings, current == "settings") { onNavigate("settings") }
    }
}

@Composable
fun NavBarItem(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (active) AppColors.Gold else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon, null,
                tint = if (active) Color.White else AppColors.TextDark.copy(alpha = 0.65f),
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            color = if (active) AppColors.Gold else AppColors.TextDark.copy(alpha = 0.65f),
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ═════════════════════════════════════════════════════════════
//  شاشة قفل الأذان (داخل نفس الملف — لا توجد ملفات جديدة)
// ═════════════════════════════════════════════════════════════

class AzanLockScreenActivity : ComponentActivity() {

    private var prayerName by mutableStateOf("")
    private var prayerTime by mutableStateOf("")
    private var soundStopped by mutableStateOf(false)

    // مستقبل "تم إيقاف الصوت" من الخدمة (لتحديث الشاشة وإغلاقها)
    private val stoppedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == PrayerService.ACTION_AZAN_STOPPED) {
                soundStopped = true
            }
        }
    }

    // مستقبل الزر الجانبي (الطاقة): يكتم الصوت ويغلق الشاشة
    private val sideButtonReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                Log.d("AzanLockScreen", "Side button pressed — muting Azan")
                try {
                    startService(Intent(this@AzanLockScreenActivity, PrayerService::class.java).apply {
                        action = PrayerService.ACTION_STOP_AZAN
                    })
                } catch (e: Exception) {
                    Log.e("AzanLockScreen", "Error sending stop intent", e)
                }
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ① إظهار فوق شاشة القفل + إضاءة الشاشة
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            try {
                (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                    .requestDismissKeyguard(this, null)
            } catch (_: Exception) {}
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ② قراءة بيانات الصلاة
        prayerName   = intent.getStringExtra("PRAYER_NAME") ?: "الصلاة"
        prayerTime   = intent.getStringExtra("PRAYER_TIME") ?: ""

        // ③ تسجيل مستقبلات البثّ
        val stoppedFilter = IntentFilter(PrayerService.ACTION_AZAN_STOPPED)
        val screenFilter  = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stoppedReceiver, stoppedFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(sideButtonReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(stoppedReceiver, stoppedFilter)
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(sideButtonReceiver, screenFilter)
        }

        setContent {
            AzanLockScreen(
                prayerName   = prayerName,
                prayerTime   = prayerTime,
                soundStopped = soundStopped,
                onStopSound = {
                    startService(Intent(this, PrayerService::class.java).apply {
                        action = PrayerService.ACTION_STOP_AZAN
                    })
                    soundStopped = true
                },
                onClose = { finish() }
            )
        }
    }

    override fun onDestroy() {
        try { unregisterReceiver(stoppedReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(sideButtonReceiver) } catch (_: Exception) {}
        super.onDestroy()
    }
}

@Composable
fun AzanLockScreen(
    prayerName: String,
    prayerTime: String,
    soundStopped: Boolean,
    onStopSound: () -> Unit,
    onClose: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "azanPulse")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.10f,
        targetValue   = 0.28f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    // إغلاق تلقائي بعد إيقاف الصوت (يُشاهد المستخدم رسالة "تم")
    LaunchedEffect(soundStopped) {
        if (soundStopped) {
            delay(2500)
            onClose()
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(AppColors.BgCream, Color(0xFFEFE3C7), AppColors.BgCream)
            )
        )
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    listOf(AppColors.Gold.copy(alpha = pulseAlpha), Color.Transparent)
                )
            )
        )

        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // ── العلوي: اسم الصلاة ووقتها ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 48.dp)
            ) {
                Text("🕌", fontSize = 72.sp)
                Spacer(Modifier.height(10.dp))
                Text("حان الآن موعد صلاة", color = AppColors.TextSub, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(
                    prayerName, color = AppColors.Gold, fontSize = 52.sp,
                    fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
                if (prayerTime.isNotEmpty()) {
                    Text(prayerTime, color = AppColors.TextDark, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                }
            }

            // ── الأوسط: النص الديني ──
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("حيَّ على الصلاة", color = AppColors.TextDark, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text("حيَّ على الفلاح", color = AppColors.TextSub, fontSize = 20.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(22.dp))
                Text(
                    "﴿ وَأَقِمِ الصَّلَاةَ إِنَّ الصَّلَاةَ تَنْهَىٰ عَنِ الْفَحْشَاءِ وَالْمُنكَرِ ﴾",
                    color = AppColors.Gold.copy(alpha = 0.8f), fontSize = 15.sp,
                    textAlign = TextAlign.Center, lineHeight = 26.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // ── السفلي: الأزرار ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)
            ) {
                if (!soundStopped) {
                    Box(
                        Modifier.fillMaxWidth().height(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(AppColors.Gold, AppColors.Gold.copy(alpha = 0.85f))
                                )
                            )
                            .clickable { onStopSound() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("إيقاف صوت الأذان", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "💡 يمكنك إيقافه أيضاً بالزر الجانبي للهاتف",
                        color = AppColors.TextSub, fontSize = 13.sp, textAlign = TextAlign.Center
                    )
                } else {
                    AnimatedVisibility(visible = true, enter = scaleIn()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF4CAF50).copy(alpha = 0.15f))
                                .padding(horizontal = 24.dp, vertical = 16.dp)
                        ) {
                            Icon(Icons.Default.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("تم إيقاف صوت الأذان", color = Color(0xFF4CAF50), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("لا تنسَ الصلاة في وقتها", color = AppColors.TextSub, fontSize = 14.sp, textAlign = TextAlign.Center)
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    "إغلاق", color = AppColors.TextSub, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onClose() }
                        .padding(horizontal = 28.dp, vertical = 10.dp)
                )
            }
        }
    }
}