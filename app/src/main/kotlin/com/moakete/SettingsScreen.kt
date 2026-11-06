package com.moakete

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

// ─────────────────────────────────────────────────────────────
//  صلاحية "الوصول لسياسة الإشعارات" (Do Not Disturb)
//  مطلوبة لتشغيل/إيقاف الوضع الصامت التلقائي برمجياً
// ─────────────────────────────────────────────────────────────
private fun isDndPermissionGranted(ctx: Context): Boolean {
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return nm.isNotificationPolicyAccessGranted
}

private fun openDndSettings(ctx: Context) {
    try {
        ctx.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: Exception) {}
}

// ─────────────────────────────────────────────────────────────
//  ثوابت
// ─────────────────────────────────────────────────────────────

private const val PREFS = "moakete_prefs"

private val PRAYERS = listOf(
    Triple("الفجر",  "🌄", "fajr"),
    Triple("الظهر",  "🌞", "dhuhr"),
    Triple("العصر",  "🌇", "asr"),
    Triple("المغرب", "🌆", "maghrib"),
    Triple("العشاء", "🌙", "isha")
)

private val MOAZINS = listOf(
    "حمادي عبد الله",
    "جاسم حمادي",
    "وسام حمادي",
    "عبد الملك جاسم",
    "ريان جاسم",
    "اختر من الهاتف 📁"
)

// ─────────────────────────────────────────────────────────────
//  ألوان الواجهة
// ─────────────────────────────────────────────────────────────

private val BgColor   = AppColors.BgCream
private val CardColor = AppColors.CardBg
private val GoldColor = AppColors.Gold
private val RedColor  = AppColors.RedMissing
private val TextDark  = AppColors.TextDark

// ─────────────────────────────────────────────────────────────
//  الشاشة الرئيسية للإعدادات
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    registrationInfo: RegistrationInfo?,
    registrationError: String? = null,
    onBack: () -> Unit
) {
    val context     = LocalContext.current
    val prefs       = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val mediaPlayer = remember { MediaPlayer() }

    DisposableEffect(Unit) {
        onDispose {
            try { if (mediaPlayer.isPlaying) mediaPlayer.stop(); mediaPlayer.release() } catch (_: Exception) {}
        }
    }

    var customAudioUri by remember {
        mutableStateOf(prefs.getString("custom_uri", null)?.let { Uri.parse(it) })
    }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                customAudioUri = uri
                prefs.edit().putString("custom_uri", uri.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    Column(Modifier.fillMaxSize().background(BgColor)) {
        Spacer(Modifier.height(35.dp))

        // ── شريط العنوان ──
        TopAppBar(
            title = { Text("إعدادات مواقيتي", color = TextDark, fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = { if (mediaPlayer.isPlaying) mediaPlayer.stop(); onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextDark)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = CardColor)
        )

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {

            // ── بطاقة خطأ: تظهر فقط إذا فشل تسجيل الدخول أو الاتصال بالقاعدة المشتركة ──
            if (registrationInfo == null && registrationError != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(RedColor.copy(alpha = 0.12f))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, null, tint = RedColor, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "تعذّر الاتصال بحساب جوجل أو القاعدة المشتركة",
                                color = RedColor, fontSize = 15.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(registrationError, color = TextDark.copy(alpha = 0.8f), fontSize = 13.sp)
                    }
                }
            }

            // ── بطاقة الحساب: رقم التسجيل بالقاعدة المشتركة وحالة المصدر الأساسي ──
            if (registrationInfo != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardColor.copy(alpha = 0.45f))
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountCircle, null, tint = GoldColor, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (registrationInfo.isPrimarySource) "أنت المصدر الأساسي" else "معلومات التسجيل",
                                color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "رقمك التسلسلي: #${registrationInfo.sequenceNumber}",
                            color = TextDark.copy(alpha = 0.75f), fontSize = 14.sp
                        )
                        Text(
                            "إجمالي المستخدمين المسجلين: ${registrationInfo.totalUsers}",
                            color = TextDark.copy(alpha = 0.75f), fontSize = 14.sp
                        )
                    }
                }
            }

            // ── زر إيقاف الأذان الفوري ──
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(12.dp)).background(RedColor)
                    .clickable {
                        context.stopService(Intent(context, PrayerService::class.java))
                        if (mediaPlayer.isPlaying) mediaPlayer.stop()
                    }.padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("إيقاف صوت الأذان الآن", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // ══ إعدادات الصلوات ══
            SectionHeader("إعدادات الصلوات")
            Spacer(Modifier.height(8.dp))

            PRAYERS.forEach { (name, emoji, key) ->
                PrayerSettingsCard(
                    prayerName     = name,
                    prayerEmoji    = emoji,
                    prayerKey      = key,
                    prefs          = prefs,
                    mediaPlayer    = mediaPlayer,
                    customAudioUri = customAudioUri,
                    onPickFile     = { fileLauncher.launch("audio/*") },
                    context        = context
                )
                Spacer(Modifier.height(10.dp))
            }

            HorizontalDivider(color = TextDark.copy(0.10f), modifier = Modifier.padding(vertical = 16.dp))

            // ══ الإعدادات العامة ══
            SectionHeader("الإعدادات العامة")
            Spacer(Modifier.height(12.dp))

            // ── الموقع الجغرافي ──
            LocationSettingsSection(
                onLocationSaved = {
                    // إعادة جدولة الصلوات بالأوقات المعدَّلة للموقع الجديد
                    MainActivity.scheduleAllPrayersForDate(context, java.util.Calendar.getInstance())
                }
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = TextDark.copy(0.10f), modifier = Modifier.padding(vertical = 4.dp))

            // ── سطوع الشاشة ──
            Text("سطوع الشاشة", color = GoldColor, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("🌙", fontSize = 22.sp)
                Slider(
                    value = brightness, onValueChange = onBrightnessChange,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    colors = SliderDefaults.colors(thumbColor = GoldColor, activeTrackColor = GoldColor)
                )
                Text("☀️", fontSize = 22.sp)
            }

            Spacer(Modifier.height(24.dp))

            // ── زر الحفظ ──
            Button(
                onClick = { if (mediaPlayer.isPlaying) mediaPlayer.stop(); onBack() },
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = RoundedCornerShape(15.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GoldColor)
            ) {
                Icon(Icons.Default.Check, null, tint = TextDark)
                Spacer(Modifier.width(6.dp))
                Text("حفظ الإعدادات", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            Spacer(Modifier.height(60.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  بطاقة إعدادات صلاة واحدة
// ─────────────────────────────────────────────────────────────

@Composable
fun PrayerSettingsCard(
    prayerName: String,
    prayerEmoji: String,
    prayerKey: String,
    prefs: android.content.SharedPreferences,
    mediaPlayer: MediaPlayer,
    customAudioUri: Uri?,
    onPickFile: () -> Unit,
    context: Context
) {
    var isExpanded       by remember { mutableStateOf(false) }
    var moazin           by remember { mutableStateOf(prefs.getString("${prayerKey}_moazin", "حمادي عبد الله") ?: "حمادي عبد الله") }
    var soundEnabled     by remember { mutableStateOf(prefs.getBoolean("${prayerKey}_sound_enabled", true)) }
    var silentEnabled    by remember { mutableStateOf(prefs.getBoolean("${prayerKey}_silent_enabled", false)) }
    var vibrationEnabled by remember { mutableStateOf(prefs.getBoolean("${prayerKey}_vibration_enabled", false)) }
    var vibrationOnly    by remember { mutableStateOf(prefs.getBoolean("${prayerKey}_vibration_only", false)) }
    var silentStart      by remember { mutableStateOf(prefs.getFloat("${prayerKey}_silent_s", 5f)) }
    var silentEnd        by remember { mutableStateOf(prefs.getFloat("${prayerKey}_silent_e", 20f)) }
    var firstAzanEnabled by remember { mutableStateOf(prefs.getBoolean("fajr_first_azan_enabled", false)) }
    var firstAzanMinutes by remember { mutableStateOf(prefs.getFloat("fajr_first_azan_minutes", 30f)) }

    // ── صلاحية DND: نعيد فحصها كل مرة يرجع فيها المستخدم للتطبيق ──
    // (لأنه لو راح لإعدادات النظام ومنحها، ما راح ينعاد تشغيل الشاشة)
    var dndGranted by remember { mutableStateOf(isDndPermissionGranted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dndGranted = isDndPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun save() {
        prefs.edit()
            .putString("${prayerKey}_moazin",             moazin)
            .putBoolean("${prayerKey}_sound_enabled",     soundEnabled)
            .putBoolean("${prayerKey}_silent_enabled",    silentEnabled)
            .putBoolean("${prayerKey}_vibration_enabled", vibrationEnabled)
            .putBoolean("${prayerKey}_vibration_only",    vibrationOnly)
            .putFloat("${prayerKey}_silent_s",            silentStart)
            .putFloat("${prayerKey}_silent_e",            silentEnd)
            .also { editor ->
                if (prayerKey == "fajr") {
                    editor.putBoolean("fajr_first_azan_enabled", firstAzanEnabled)
                    editor.putFloat("fajr_first_azan_minutes",   firstAzanMinutes)
                }
            }.apply()
    }

    val summary = buildString {
        when { vibrationOnly -> append("هزاز فقط"); !soundEnabled -> append("صامت"); else -> append("🔊 $moazin") }
        if (silentEnabled) append(" · صامت تلقائي")
        if (vibrationEnabled && !vibrationOnly) append(" · هزاز")
        if (prayerKey == "fajr" && firstAzanEnabled) append(" · أذان أول ${firstAzanMinutes.toInt()}د")
    }

    // ── رأس البطاقة ──
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardColor.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(prayerEmoji, fontSize = 26.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(prayerName, color = TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(summary, color = TextDark.copy(0.60f), fontSize = 13.sp)
                }
            }
            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null, tint = GoldColor
            )
        }

        // ── محتوى الإعدادات ──
        if (isExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .background(BgColor.copy(alpha = 0.35f))
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 4.dp)
            ) {
                HorizontalDivider(color = GoldColor.copy(0.25f), modifier = Modifier.padding(bottom = 14.dp))

                // ════ 1. اختيار المؤذن ════
                OptionHeader("🎙️ اختيار المؤذن")
                Spacer(Modifier.height(6.dp))

                MOAZINS.forEach { m ->
                    val isSelected = (m == moazin || (m == "اختر من الهاتف 📁" && moazin == "ملف مخصص من الهاتف 📁"))
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .selectable(selected = isSelected, onClick = {
                                if (m == "اختر من الهاتف 📁") onPickFile()
                                else { moazin = m; save() }
                            }).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                if (m == "اختر من الهاتف 📁") onPickFile()
                                else { moazin = m; save() }
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = GoldColor)
                        )
                        Text(m, color = TextDark, fontSize = 15.sp, modifier = Modifier.padding(start = 6.dp))
                    }
                }

                // أزرار المعاينة
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                mediaPlayer.reset()
                                if (moazin == "ملف مخصص من الهاتف 📁" && customAudioUri != null) {
                                    mediaPlayer.setDataSource(context, customAudioUri)
                                } else {
                                    val idx = MOAZINS.indexOf(moazin) + 1
                                    val resId = context.resources.getIdentifier("sound$idx", "raw", context.packageName)
                                    if (resId != 0) {
                                        val afd = context.resources.openRawResourceFd(resId)
                                        mediaPlayer.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                        afd.close()
                                    }
                                }
                                mediaPlayer.prepare(); mediaPlayer.start()
                            } catch (e: Exception) { e.printStackTrace() }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.White)
                        Text(" معاينة", color = Color.White, fontSize = 14.sp)
                    }
                    Button(
                        onClick = {
                            if (mediaPlayer.isPlaying) mediaPlayer.stop()
                            context.stopService(Intent(context, PrayerService::class.java))
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                        shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, null, tint = Color.White)
                        Text(" إيقاف", color = Color.White, fontSize = 14.sp)
                    }
                }

                HorizontalDivider(color = TextDark.copy(0.10f), modifier = Modifier.padding(bottom = 14.dp))

                // ════ 2. خيارات الصوت والتنبيه ════
                OptionHeader("🔔 خيارات التنبيه")
                Spacer(Modifier.height(8.dp))

                ToggleRow(
                    label = "تشغيل الأذان بالصوت",
                    subLabel = if (soundEnabled) "سيتم تشغيل صوت الأذان" else "بدون صوت — إشعار فقط",
                    checked = soundEnabled && !vibrationOnly, enabled = !vibrationOnly,
                    onCheckedChange = { soundEnabled = it; save() }
                )
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    label = "الاهتزاز",
                    subLabel = if (vibrationEnabled) "الهاتف سيهتز عند الأذان" else "بدون اهتزاز",
                    checked = vibrationEnabled, enabled = true,
                    onCheckedChange = { vibrationEnabled = it; if (!it) vibrationOnly = false; save() }
                )
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    label = "تنبيه بالهزاز فقط",
                    subLabel = if (vibrationOnly) "بدون صوت — هزاز فقط" else "غير مفعّل",
                    checked = vibrationOnly, enabled = true,
                    onCheckedChange = { vibrationOnly = it; if (it) { vibrationEnabled = true; soundEnabled = false }; save() }
                )

                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = TextDark.copy(0.10f), modifier = Modifier.padding(bottom = 14.dp))

                // ════ 3. الوضع الصامت التلقائي ════
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    OptionHeader("🔇 الوضع الصامت التلقائي")
                    Switch(
                        checked = silentEnabled,
                        onCheckedChange = { checked ->
                            silentEnabled = checked
                            save()
                            // إذا فعّل المستخدم الخاصية والصلاحية ناقصة، وديه فوراً لشاشة المنح
                            if (checked && !dndGranted) {
                                openDndSettings(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = GoldColor,
                            checkedTrackColor = GoldColor.copy(0.5f)
                        )
                    )
                }

                // ── تحذير: الخاصية مفعّلة بس الصلاحية ناقصة = ما راح تشتغل أبداً ──
                if (silentEnabled && !dndGranted) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFB94A48).copy(alpha = 0.25f))
                            .clickable { openDndSettings(context) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "⚠️ الوضع الصامت لن يعمل",
                                color = Color(0xFFFF8A80),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "يحتاج صلاحية \"الوصول لسياسة الإشعارات (DND)\" — اضغط هنا لمنحها",
                                color = TextDark.copy(0.75f),
                                fontSize = 11.sp
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = TextDark.copy(0.6f))
                    }
                }

                if (silentEnabled) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(CardColor.copy(0.40f))
                            .padding(12.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("يبدأ الصمت بعد الأذان بـ:", color = TextDark, fontSize = 13.sp)
                            Text("${silentStart.toInt()} د", color = GoldColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = silentStart,
                            onValueChange = { v -> silentStart = v; if (silentEnd < v + 5f) silentEnd = v + 5f; save() },
                            valueRange = 0f..55f, steps = 54,
                            colors = SliderDefaults.colors(thumbColor = GoldColor, activeTrackColor = GoldColor)
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ينتهي الصمت بعد الأذان بـ:", color = TextDark, fontSize = 13.sp)
                            Text("${silentEnd.toInt()} د", color = GoldColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = silentEnd,
                            onValueChange = { v -> if (v >= silentStart + 5f) { silentEnd = v; save() } },
                            valueRange = 5f..120f, steps = 114,
                            colors = SliderDefaults.colors(thumbColor = GoldColor, activeTrackColor = GoldColor)
                        )
                        Spacer(Modifier.height(4.dp))
                        val silentDuration = (silentEnd - silentStart).toInt()
                        val durationText = if (silentDuration >= 60)
                            "${silentDuration / 60} ساعة و${silentDuration % 60} د"
                        else "$silentDuration دقيقة"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("⏰ المدة: $durationText", color = GoldColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("🔇 حتى ${silentEnd.toInt()}د بعد الأذان", color = TextDark.copy(0.65f), fontSize = 12.sp)
                        }
                    }
                }

                // ════ الأذان الأول — فجر فقط ════
                if (prayerKey == "fajr") {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = TextDark.copy(0.10f), modifier = Modifier.padding(bottom = 14.dp))

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            OptionHeader("🌙 الأذان الأول")
                            Text("يؤذن قبل وقت الفجر بالمدة المحددة", color = TextDark.copy(0.55f), fontSize = 12.sp)
                        }
                        Switch(
                            checked = firstAzanEnabled,
                            onCheckedChange = { firstAzanEnabled = it; save() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = GoldColor,
                                checkedTrackColor = GoldColor.copy(0.5f)
                            )
                        )
                    }

                    if (firstAzanEnabled) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CardColor.copy(0.40f))
                                .padding(14.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("⏱️ يؤذن قبل الفجر بـ:", color = TextDark, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                val mins = firstAzanMinutes.toInt()
                                Text(if (mins == 60) "ساعة كاملة" else "$mins دقيقة",
                                    color = GoldColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(6.dp))
                            Slider(
                                value = firstAzanMinutes,
                                onValueChange = { firstAzanMinutes = it; save() },
                                valueRange = 10f..60f, steps = 49,
                                colors = SliderDefaults.colors(thumbColor = GoldColor, activeTrackColor = GoldColor)
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("١٠ دقائق", color = TextDark.copy(0.55f), fontSize = 11.sp)
                                Text("ساعة", color = TextDark.copy(0.55f), fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = GoldColor.copy(0.20f))
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ℹ️", fontSize = 14.sp)
                                Spacer(Modifier.width(6.dp))
                                Text("الأذان الأول يُنبّه للسحور وقيام الليل قبل دخول وقت الفجر",
                                    color = TextDark.copy(0.65f), fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  مكوّنات مساعدة
// ─────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(title, color = GoldColor, fontSize = 20.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
private fun OptionHeader(title: String) {
    Text(title, color = GoldColor.copy(0.90f), fontSize = 15.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun ToggleRow(
    label: String, subLabel: String,
    checked: Boolean, enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label,    color = if (enabled) TextDark else TextDark.copy(0.45f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subLabel, color = TextDark.copy(0.55f), fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onCheckedChange, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GoldColor,
                checkedTrackColor = GoldColor.copy(0.50f)
            )
        )
    }
}
