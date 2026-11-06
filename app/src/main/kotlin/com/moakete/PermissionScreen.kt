package com.moakete

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────
//  ألوان الشاشة
// ─────────────────────────────────────────────────────────────
private val BgGradient  = listOf(AppColors.BgCream, Color(0xFFEFE3C7), AppColors.BgCream)
private val Gold        = AppColors.Gold
private val GoldLight   = AppColors.Gold
private val GreenOk     = Color(0xFF4CAF50)
private val RedMissing  = Color(0xFFB11E3A)
private val CardBg      = Color(0xB3FFFFFF)
private val CardBorder  = AppColors.Gold.copy(alpha = 0.35f)
private val TextWhite   = AppColors.TextDark
private val TextSubPerm = AppColors.TextSub

// ─────────────────────────────────────────────────────────────
//  نموذج الإذن
// ─────────────────────────────────────────────────────────────
data class PermItem(
    val title       : String,
    val description : String,
    val emoji       : String,
    val important   : Boolean = true,
    val check       : (Context) -> Boolean,
    val request     : (Context) -> Unit
)

// ─────────────────────────────────────────────────────────────
//  كشف نوع الجهاز وفتح إعدادات البطارية
// ─────────────────────────────────────────────────────────────
fun openManufacturerBatterySettings(ctx: Context): Boolean {
    val man = Build.MANUFACTURER.lowercase()
    val intents = when {
        man.contains("xiaomi") || man.contains("redmi") -> listOf(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
        )
        man.contains("huawei") || man.contains("honor") -> listOf(
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
            ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")
        )
        man.contains("oppo") || man.contains("realme") -> listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.phonemanager", "com.coloros.phonemanager.permission.ui.StartupAppListActivity")
        )
        man.contains("vivo") -> listOf(
            ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")
        )
        man.contains("samsung") -> listOf(
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            ComponentName("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity")
        )
        man.contains("oneplus") -> listOf(
            ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity")
        )
        man.contains("asus") -> listOf(
            ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity")
        )
        man.contains("meizu") -> listOf(
            ComponentName("com.meizu.safe", "com.meizu.safe.permission.SmartPermissionActivity")
        )
        else -> emptyList()
    }
    for (comp in intents) {
        try {
            ctx.startActivity(Intent().apply { component = comp; addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            return true
        } catch (_: Exception) {}
    }
    return try {
        ctx.startActivity(Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${ctx.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        false
    } catch (_: Exception) { false }
}

fun getManufacturerName(): String = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi" -> "شاومي"; "huawei", "honor" -> "هواوي"
    "oppo"            -> "أوبو";  "realme"           -> "ريلمي"
    "vivo", "iqoo"    -> "فيفو";  "samsung"          -> "سامسونج"
    "oneplus"         -> "ون بلس"; "asus"            -> "أسوس"
    "meizu"           -> "ميزو";  "google"           -> "Google"
    else              -> Build.MANUFACTURER
}

fun needsManufacturerBatteryFix(): Boolean {
    val man = Build.MANUFACTURER.lowercase()
    return listOf("xiaomi","redmi","huawei","honor","oppo","realme",
        "vivo","iqoo","samsung","oneplus","asus","meizu").any { man.contains(it) }
}

// ─────────────────────────────────────────────────────────────
//  قائمة الإذونات
// ─────────────────────────────────────────────────────────────
fun buildPermissions(): List<PermItem> = listOf(
    PermItem(
        title = "إرسال الإشعارات", description = "لإظهار تنبيه وقت الصلاة على الشاشة",
        emoji = "🔔", important = true,
        check = { ctx ->
            if (Build.VERSION.SDK_INT >= 33)
                androidx.core.content.ContextCompat.checkSelfPermission(ctx, "android.permission.POST_NOTIFICATIONS") ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            else true
        },
        request = { }
    ),
    PermItem(
        title = "المنبهات الدقيقة", description = "يضمن دقة وقت الأذان بدون تأخير",
        emoji = "⏰", important = true,
        check = { ctx ->
            if (Build.VERSION.SDK_INT >= 31) (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
            else true
        },
        request = { ctx ->
            try {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                })
            } catch (_: Exception) {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
            }
        }
    ),
    PermItem(
        title = "الأذان مع إغلاق الشاشة", description = "يضمن سماع الأذان حتى عند إغلاق الشاشة",
        emoji = "🔋", important = true,
        check = { ctx ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)
            else true
        },
        request = { ctx ->
            try {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                })
            } catch (_: Exception) {}
        }
    ),
    PermItem(
        title = "عرض شاشة الأذان", description = "يظهر الأذان فوق أي تطبيق مفتوح",
        emoji = "📱", important = false,
        check = { ctx -> if (Build.VERSION.SDK_INT >= 23) Settings.canDrawOverlays(ctx) else true },
        request = { ctx ->
            try {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            } catch (_: Exception) {}
        }
    ),
    PermItem(
        title = "وضع الصامت التلقائي", description = "يُفعّل الصمت تلقائياً بعد الأذان",
        emoji = "🔇", important = true,
        check = { ctx ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted
            else true
        },
        request = { ctx ->
            try { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) } catch (_: Exception) {}
        }
    ),
    PermItem(
        title = "تشغيل تلقائي — ${getManufacturerName()}",
        description = "أجهزة ${getManufacturerName()} تحتاج إذناً إضافياً لضمان عمل الأذان",
        emoji = "🏭", important = true,
        check = { _ -> !needsManufacturerBatteryFix() },
        request = { ctx -> openManufacturerBatterySettings(ctx) }
    ),
    PermItem(
        title = "بطارية بدون قيود (Unrestricted)",
        description = "افتح صفحة البطارية واختر \"غير مقيّدة\" يدوياً — أقوى من إعفاء تحسين البطارية العادي، ولا يوجد رابط مباشر له من أندرويد",
        emoji = "⚡", important = false,
        // ما فيه API عام يكشف تحديداً حالة "Unrestricted" (بس Doze exemption العادي)
        // فنخليها دايماً "مو محسومة" بدون ما نزعج المستخدم بعلامة حمراء دائمة
        check = { _ -> true },
        request = { ctx ->
            try {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${ctx.packageName}")))
            } catch (_: Exception) {}
        }
    )
)

// ─────────────────────────────────────────────────────────────
//  الشاشة الرئيسية للإذونات
// ─────────────────────────────────────────────────────────────
@Composable
fun PermissionScreen(
    onRequestNotification: () -> Unit,
    onAllDone: () -> Unit
) {
    val ctx = LocalContext.current
    val permissions = remember {
        buildPermissions().filter { perm -> if (perm.emoji == "🏭") needsManufacturerBatteryFix() else true }
    }
    var tick by remember { mutableStateOf(0) }
    val statuses = remember(tick) { permissions.map { it.check(ctx) } }
    val allCriticalGranted = permissions.zip(statuses).filter { (p, _) -> p.important }.all { (_, g) -> g }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(BgGradient))) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(60.dp))
            Text("مواقيتي", color = Gold, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(4.dp))
            Text("لضمان عمل التطبيق بشكل مثالي", color = TextSubPerm, fontSize = 14.sp)
            Text("يحتاج التطبيق إلى بعض الأذونات", color = TextSubPerm, fontSize = 14.sp)
            Spacer(Modifier.height(28.dp))

            permissions.forEachIndexed { i, perm ->
                PermissionCard(
                    perm = perm, granted = statuses[i],
                    onClick = { if (perm.title == "إرسال الإشعارات") onRequestNotification() else perm.request(ctx) },
                    onRefresh = { tick++ }
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.weight(1f))

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!allCriticalGranted) {
                    Text("⚠️ بعض الأذونات المطلوبة لم تُمنح بعد",
                        color = Gold.copy(0.85f), fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                }
                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                        .background(
                            if (allCriticalGranted)
                                Brush.horizontalGradient(listOf(AppColors.Gold, AppColors.Gold))
                            else Brush.horizontalGradient(listOf(Color(0xFFDDD0AC), Color(0xFFCBBB8C)))
                        )
                        .clickable { tick++; if (allCriticalGranted) onAllDone() }
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (allCriticalGranted) "✦  ادخل التطبيق  ✦" else "تحديث الحالة",
                        color = if (allCriticalGranted) Color.White else AppColors.TextDark,
                        fontSize = 18.sp, fontWeight = FontWeight.ExtraBold
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("يمكنك تغيير هذه الإعدادات لاحقاً من داخل التطبيق",
                    color = TextSubPerm, fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  بطاقة إذن واحد
// ─────────────────────────────────────────────────────────────
@Composable
fun PermissionCard(perm: PermItem, granted: Boolean, onClick: () -> Unit, onRefresh: () -> Unit) {
    val borderColor = when {
        granted        -> GreenOk.copy(0.70f)
        perm.important -> RedMissing.copy(0.60f)
        else           -> CardBorder
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(CardBg).border(1.5.dp, borderColor, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(50.dp).clip(CircleShape)
                .background(if (granted) GreenOk.copy(0.20f) else Gold.copy(0.15f)),
            contentAlignment = Alignment.Center
        ) { Text(perm.emoji, fontSize = 22.sp) }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(perm.title, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (perm.important) {
                    Spacer(Modifier.width(6.dp))
                    Text("مطلوب", color = RedMissing, fontSize = 10.sp,
                        modifier = Modifier.background(RedMissing.copy(0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(perm.description, color = TextSubPerm, fontSize = 12.sp)
        }

        Spacer(Modifier.width(10.dp))

        if (granted) {
            Icon(Icons.Default.CheckCircle, null, tint = GreenOk, modifier = Modifier.size(30.dp))
        } else {
            Box(
                modifier = Modifier.clip(RoundedCornerShape(10.dp))
                    .background(Gold.copy(0.20f)).border(1.dp, Gold.copy(0.70f), RoundedCornerShape(10.dp))
                    .clickable { onClick(); onRefresh() }.padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("منح", color = GoldLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
