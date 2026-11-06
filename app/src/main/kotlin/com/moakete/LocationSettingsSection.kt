package com.moakete

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

// ── ألوان محلية ──────────────────────────────────────────────────
private val LocGold = AppColors.Gold
private val LocCard = AppColors.CardBg
private val LocBg   = AppColors.BgCream
private val LocText = AppColors.TextDark

// مفاتيح SharedPrefs
private const val KEY_MODE    = "location_mode"
private const val MODE_AUTO   = "auto"
private const val MODE_MANUAL = "manual"

// ─────────────────────────────────────────────────────────────────
//  دوال مساعدة: حفظ/قراءة Double بدقة كاملة
//  (SharedPrefs لا تدعم Double مباشرة لذا نحفظه كـ String)
// ─────────────────────────────────────────────────────────────────
private fun android.content.SharedPreferences.Editor.putDouble(key: String, value: Double) =
    putString(key, value.toString())

private fun android.content.SharedPreferences.getDouble(key: String, default: Double): Double =
    getString(key, null)?.toDoubleOrNull() ?: default

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationSettingsSection(
    onLocationSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("moakete_prefs", Context.MODE_PRIVATE) }

    var mode by remember {
        mutableStateOf(prefs.getString(KEY_MODE, MODE_MANUAL) ?: MODE_MANUAL)
    }

    // ── قراءة الإحداثيات المحفوظة (Double دقيق) ──
    var latInput  by remember { mutableStateOf(prefs.getDouble("user_lat_d", LocationAdjuster.REF_LAT).toString()) }
    var lngInput  by remember { mutableStateOf(prefs.getDouble("user_lng_d", LocationAdjuster.REF_LNG).toString()) }
    var altInput  by remember { mutableStateOf(prefs.getDouble("user_alt_d", LocationAdjuster.REF_ALT).toInt().toString()) }

    var gpsStatus    by remember { mutableStateOf("") }
    var isLocating   by remember { mutableStateOf(false) }
    var savedMessage by remember { mutableStateOf("") }

    // ── دالة الحفظ المشتركة ────────────────────────────────────────
    fun saveLocation(lat: Double, lng: Double, alt: Double, modeStr: String) {
        prefs.edit()
            .putDouble("user_lat_d", lat)
            .putDouble("user_lng_d", lng)
            .putDouble("user_alt_d", alt)
            // احتفاظ بالـ Float القديم للتوافقية مع الكود الذي لم يُحدَّث بعد
            .putFloat("user_lat",   lat.toFloat())
            .putFloat("user_lng",   lng.toFloat())
            .putString(KEY_MODE,    modeStr)
            .apply()
    }

    // ── طلب إذن الموقع ────────────────────────────────────────────
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                      grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchGpsLocation(context,
                onLocating = { isLocating = true; gpsStatus = "⏳ جاري تحديد الموقع..." },
                onSuccess  = { lat, lng, alt ->
                    isLocating = false
                    latInput   = "%.6f".format(lat)
                    lngInput   = "%.6f".format(lng)
                    altInput   = alt.toInt().toString()
                    saveLocation(lat, lng, alt, MODE_AUTO)
                    gpsStatus    = "✅ تم تحديد الموقع بنجاح (ارتفاع: ${alt.toInt()} م)"
                    savedMessage = ""
                    onLocationSaved()
                },
                onFailure  = { msg -> isLocating = false; gpsStatus = "⚠️ $msg" }
            )
        } else {
            gpsStatus = "⚠️ لم يُمنح إذن الموقع"
        }
    }

    fun startAutoLocation() {
        val hasFine   = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            fetchGpsLocation(context,
                onLocating = { isLocating = true; gpsStatus = "⏳ جاري تحديد الموقع..." },
                onSuccess  = { lat, lng, alt ->
                    isLocating = false
                    latInput   = "%.6f".format(lat)
                    lngInput   = "%.6f".format(lng)
                    altInput   = alt.toInt().toString()
                    saveLocation(lat, lng, alt, MODE_AUTO)
                    gpsStatus    = "✅ تم تحديد الموقع بنجاح (ارتفاع: ${alt.toInt()} م)"
                    savedMessage = ""
                    onLocationSaved()
                },
                onFailure  = { msg -> isLocating = false; gpsStatus = "⚠️ $msg" }
            )
        } else {
            permLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

    // ─────────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LocCard)
            .padding(16.dp)
    ) {

        // ── العنوان ──
        Text("📍 الموقع الجغرافي", color = LocGold, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            "اختر طريقة تحديد موقعك لضبط أوقات الصلاة بدقة",
            color = LocText.copy(0.55f), fontSize = 12.sp
        )

        Spacer(Modifier.height(14.dp))

        // ══ مفتاح الوضع ══════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(LocBg.copy(0.5f))
                .padding(4.dp)
        ) {
            ModeTab(
                label    = "✏️ يدوي",
                selected = mode == MODE_MANUAL,
                modifier = Modifier.weight(1f),
                onClick  = {
                    mode = MODE_MANUAL
                    gpsStatus = ""
                    prefs.edit().putString(KEY_MODE, MODE_MANUAL).apply()
                }
            )
            ModeTab(
                label    = "📡 تلقائي (GPS)",
                selected = mode == MODE_AUTO,
                modifier = Modifier.weight(1f),
                onClick  = {
                    mode = MODE_AUTO
                    savedMessage = ""
                    startAutoLocation()
                }
            )
        }

        Spacer(Modifier.height(14.dp))

        // ══ وضع تلقائي GPS ════════════════════════════════════════
        AnimatedVisibility(visible = mode == MODE_AUTO, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                if (gpsStatus.isNotEmpty()) {
                    Text(
                        gpsStatus,
                        color = when {
                            gpsStatus.startsWith("✅") -> Color(0xFF4CAF50)
                            gpsStatus.startsWith("⚠️") -> Color(0xFFEF5350)
                            else                        -> LocText.copy(0.7f)
                        },
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                }

                if (isLocating) {
                    LinearProgressIndicator(
                        modifier   = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        color      = LocGold,
                        trackColor = LocBg
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // عرض الإحداثيات المحفوظة إذا لم تكن الافتراضية
                val lat = latInput.toDoubleOrNull()
                val lng = lngInput.toDoubleOrNull()
                val isRef = lat != null && lng != null &&
                    kotlin.math.abs(lat - LocationAdjuster.REF_LAT) < 0.001 &&
                    kotlin.math.abs(lng - LocationAdjuster.REF_LNG) < 0.001
                if (lat != null && lng != null && !isRef) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(LocBg.copy(0.4f))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("خط العرض", color = LocText.copy(0.55f), fontSize = 11.sp)
                            Text(latInput, color = LocText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("الارتفاع (م)", color = LocText.copy(0.55f), fontSize = 11.sp)
                            Text("${altInput} م", color = LocGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("خط الطول", color = LocText.copy(0.55f), fontSize = 11.sp)
                            Text(lngInput, color = LocText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Button(
                    onClick  = { startAutoLocation() },
                    enabled  = !isLocating,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = LocGold, contentColor = LocText),
                    shape    = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        if (isLocating) "⏳ جاري التحديد..." else "🔄 تحديث الموقع الآن",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    "ℹ️ الموقع يُحدَّد مرة واحدة ويُحفظ تلقائياً — يشمل الارتفاع عن سطح البحر",
                    color = LocText.copy(0.45f), fontSize = 11.sp, lineHeight = 15.sp
                )
                Spacer(Modifier.height(4.dp))
            }
        }

        // ══ وضع يدوي ══════════════════════════════════════════════
        AnimatedVisibility(visible = mode == MODE_MANUAL, enter = expandVertically(), exit = shrinkVertically()) {
            Column {
                Text("خط العرض (Latitude)", color = LocText.copy(0.75f), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = latInput, onValueChange = { latInput = it; savedMessage = "" },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("مثال: 35.520968", color = LocText.copy(0.3f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LocGold, unfocusedBorderColor = LocText.copy(0.3f),
                        focusedTextColor = LocText, unfocusedTextColor = LocText, cursorColor = LocGold
                    )
                )

                Spacer(Modifier.height(10.dp))

                Text("خط الطول (Longitude)", color = LocText.copy(0.75f), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = lngInput, onValueChange = { lngInput = it; savedMessage = "" },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    placeholder = { Text("مثال: 43.472522", color = LocText.copy(0.3f)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LocGold, unfocusedBorderColor = LocText.copy(0.3f),
                        focusedTextColor = LocText, unfocusedTextColor = LocText, cursorColor = LocGold
                    )
                )

                Spacer(Modifier.height(10.dp))

                // ── [جديد] حقل الارتفاع عن سطح البحر ───────────────
                Text("الارتفاع عن سطح البحر (م)", color = LocText.copy(0.75f), fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = altInput, onValueChange = { altInput = it; savedMessage = "" },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    placeholder = { Text("مثال: 220 (سهل نينوى)", color = LocText.copy(0.3f)) },
                    supportingText = {
                        Text(
                            "يؤثر على دقة الشروق والمغرب • يمكن معرفته من Google Maps أو GPS",
                            color = LocText.copy(0.4f), fontSize = 10.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LocGold, unfocusedBorderColor = LocText.copy(0.3f),
                        focusedTextColor = LocText, unfocusedTextColor = LocText, cursorColor = LocGold
                    )
                )

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ℹ️", fontSize = 12.sp); Spacer(Modifier.width(5.dp))
                    Text(
                        "من Google Maps: اضغط مطولاً على موقعك ← ستظهر الأرقام",
                        color = LocText.copy(0.45f), fontSize = 11.sp, lineHeight = 15.sp
                    )
                }

                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            val lat = latInput.toDoubleOrNull()
                            val lng = lngInput.toDoubleOrNull()
                            val alt = altInput.toDoubleOrNull() ?: 0.0
                            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0 && alt >= 0.0) {
                                saveLocation(lat, lng, alt, MODE_MANUAL)
                                savedMessage = "✅ تم حفظ الموقع"
                                onLocationSaved()
                            } else {
                                savedMessage = "⚠️ إحداثيات غير صحيحة"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.buttonColors(containerColor = LocGold, contentColor = LocText),
                        shape    = RoundedCornerShape(10.dp)
                    ) { Text("💾 حفظ", fontWeight = FontWeight.Bold, fontSize = 14.sp) }

                    OutlinedButton(
                        onClick = {
                            latInput = LocationAdjuster.REF_LAT.toString()
                            lngInput = LocationAdjuster.REF_LNG.toString()
                            altInput = LocationAdjuster.REF_ALT.toInt().toString()
                            saveLocation(LocationAdjuster.REF_LAT, LocationAdjuster.REF_LNG, LocationAdjuster.REF_ALT, MODE_MANUAL)
                            savedMessage = "↩️ تمت إعادة الضبط"
                            onLocationSaved()
                        },
                        modifier = Modifier.weight(1f),
                        colors   = ButtonDefaults.outlinedButtonColors(contentColor = LocText.copy(0.7f)),
                        border   = BorderStroke(1.dp, LocText.copy(0.25f)),
                        shape    = RoundedCornerShape(10.dp)
                    ) { Text("↩️ الافتراضي", fontSize = 13.sp) }
                }

                if (savedMessage.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        savedMessage,
                        color = if (savedMessage.startsWith("✅") || savedMessage.startsWith("↩️"))
                            Color(0xFF4CAF50) else Color(0xFFEF5350),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        // ══ الفرق المحسوب (مشترك) ═════════════════════════════════
        val lat = latInput.toDoubleOrNull()
        val lng = lngInput.toDoubleOrNull()
        val alt = altInput.toDoubleOrNull() ?: 0.0
        if (lat != null && lng != null) {
            val lngDiff = ((LocationAdjuster.REF_LNG - lng) * 4.0).toInt()
            val latDiff = lat - LocationAdjuster.REF_LAT
            val altDiff = alt - LocationAdjuster.REF_ALT
            if (lngDiff != 0 || kotlin.math.abs(latDiff) > 0.1 || kotlin.math.abs(altDiff) > 50) {
                HorizontalDivider(color = LocText.copy(0.1f))
                Spacer(Modifier.height(8.dp))
                Text("📊 الفروقات المحسوبة:", color = LocGold.copy(0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        if (lngDiff != 0) {
                            val dir = if (lngDiff > 0) "تأخير" else "تبكير"
                            append("• فرق الطول: $dir ${kotlin.math.abs(lngDiff)} دقيقة لجميع الأوقات\n")
                        }
                        if (kotlin.math.abs(latDiff) > 0.1) {
                            val dir = if (latDiff > 0) "شمال" else "جنوب"
                            append("• فرق العرض: ${"%.2f".format(kotlin.math.abs(latDiff))}° $dir\n")
                            append("  (يؤثر على الفجر والعشاء حسب الفصل)\n")
                        }
                        if (kotlin.math.abs(altDiff) > 50) {
                            val altEffect = (0.0347 * kotlin.math.sqrt(alt) - 0.0347 * kotlin.math.sqrt(LocationAdjuster.REF_ALT)) * 60
                            val dir = if (altDiff > 0) "أعلى" else "أخفض"
                            append("• الارتفاع: ${alt.toInt()} م ($dir من المرجع)\n")
                            append("  → الشروق يتبكر والمغرب يتأخر بـ ~${"%.1f".format(kotlin.math.abs(altEffect))} دقيقة")
                        }
                    },
                    color = LocText.copy(0.65f), fontSize = 11.sp, lineHeight = 17.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  تبويب الوضع (يدوي / تلقائي)
// ─────────────────────────────────────────────────────────────────
@Composable
private fun ModeTab(
    label    : String,
    selected : Boolean,
    modifier : Modifier = Modifier,
    onClick  : () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) LocCard else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color      = if (selected) LocGold else LocText.copy(0.5f),
            fontSize   = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

// ─────────────────────────────────────────────────────────────────
//  دالة جلب موقع GPS (خارج Composable)
//  [محدَّثة]: تُعيد الارتفاع كمعامل ثالث
// ─────────────────────────────────────────────────────────────────
private fun fetchGpsLocation(
    context    : Context,
    onLocating : () -> Unit,
    onSuccess  : (Double, Double, Double) -> Unit,  // lat, lng, alt
    onFailure  : (String) -> Unit
) {
    val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    val gpsEnabled     = lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    val networkEnabled = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

    if (!gpsEnabled && !networkEnabled) {
        onFailure("يرجى تفعيل GPS من إعدادات الهاتف")
        return
    }

    onLocating()

    val provider = if (gpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER

    // أولاً: استخدم آخر موقع معروف إذا كان حديثاً (أسرع)
    try {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine) {
            val last = lm.getLastKnownLocation(provider)
            if (last != null && (System.currentTimeMillis() - last.time) < 10 * 60 * 1000L) {
                // نُعيد الارتفاع إذا كان متاحاً، وإلا نُعيد 0 ليستخدم المستخدم القيمة اليدوية
                val alt = if (last.hasAltitude()) last.altitude else 0.0
                onSuccess(last.latitude, last.longitude, alt)
                return
            }
        }
    } catch (_: Exception) {}

    // ثانياً: اطلب تحديداً جديداً مع مهلة 20 ثانية
    val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            try { lm.removeUpdates(this) } catch (_: Exception) {}
            val alt = if (loc.hasAltitude()) loc.altitude else 0.0
            onSuccess(loc.latitude, loc.longitude, alt)
        }
        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(p: String)  {}
        override fun onProviderDisabled(p: String) {
            try { lm.removeUpdates(this) } catch (_: Exception) {}
            onFailure("تم تعطيل مزود الموقع")
        }
    }

    try {
        lm.requestLocationUpdates(provider, 0L, 0f, listener)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
            onFailure("انتهت المهلة — تأكد من تفعيل GPS وكن في مكان مكشوف")
        }, 20_000L)
    } catch (e: SecurityException) {
        onFailure("لا يوجد إذن للوصول إلى الموقع")
    } catch (e: Exception) {
        onFailure("خطأ في جلب الموقع")
    }
}
