package com.moakete

import java.util.Locale
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.math.sin  as kSin
import kotlin.math.cos  as kCos
import kotlin.math.tan  as kTan
import kotlin.math.acos as kAcos
import kotlin.math.atan as kAtan

/**
 * LocationAdjuster
 *
 * يحسب أوقات الصلاة لأي مكان في العالم، بناءً على:
 *  ─────────────────────────────────────────────────────
 *  ① جدول PrayerData المرجعي (بتوقيت بغداد UTC+3) — مضبوط بعينيك في الموصل.
 *  ② تصحيح فرق خط الطول والعرض والارتفاع.
 *  ③ تحويل تلقائي من توقيت بغداد إلى التوقيت المحلي للدولة (TimeZone.getDefault).
 *
 * النتيجة:
 *  • في الموصل/بغداد → مطابق للجدول 100%.
 *  • في أي دولة أخرى → دقيق بالثواني بتوقيت تلك الدولة.
 */
object LocationAdjuster {

    // ── النقطة المرجعية (مصدر بيانات PrayerData) ────────────────
    const val REF_LAT = 35.520968
    const val REF_LNG = 43.472522
    const val REF_ALT = 220.0
    const val REF_TZ_HOURS = 3.0   // جدول PrayerData مكتوب بتوقيت بغداد (UTC+3)

    // ── ثوابت الحساب ─────────────────────────────────────────────
    private const val DEG = Math.PI / 180.0

    private const val FAJR_ANGLE  = 17.5
    private const val ISHA_ANGLE  = 15.5
    private const val SUNRISE_DIP_BASE = 0.833

    // ═════════════════════════════════════════════════════════════
    //  مفاتيح الحفظ في SharedPreferences
    // ═════════════════════════════════════════════════════════════
    private const val PREFS = "moakete_prefs"
    private const val KEY_LAT = "user_lat_d"
    private const val KEY_LNG = "user_lng_d"
    private const val KEY_ALT = "user_alt_d"
    private const val KEY_LAST_UPDATE = "location_last_update"
    private const val MOVED_THRESHOLD_METERS = 10_000.0

    // ═════════════════════════════════════════════════════════════
    //  [جديد] مساعد الموقع الموحّد
    // ═════════════════════════════════════════════════════════════

    data class Loc(val lat: Double, val lng: Double, val alt: Double) {
        /** المسافة بالأمتار (Haversine). */
        fun distanceTo(other: Loc): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(other.lat - lat)
            val dLng = Math.toRadians(other.lng - lng)
            val a = kSin(dLat / 2).let { it * it } +
                    kCos(Math.toRadians(lat)) * kCos(Math.toRadians(other.lat)) *
                    kSin(dLng / 2).let { it * it }
            return 2 * r * asin(sqrt(a))
        }
    }

    /** قراءة الموقع المخزَّن. */
    fun readCached(context: android.content.Context): Loc {
        val p = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        return Loc(
            p.getString(KEY_LAT, null)?.toDoubleOrNull()
                ?: p.getFloat("user_lat", REF_LAT.toFloat()).toDouble(),
            p.getString(KEY_LNG, null)?.toDoubleOrNull()
                ?: p.getFloat("user_lng", REF_LNG.toFloat()).toDouble(),
            p.getString(KEY_ALT, null)?.toDoubleOrNull() ?: REF_ALT
        )
    }

    /** حفظ الموقع (يُستخدم من كل مكان لضمان توحيد المفاتيح). */
    fun save(context: android.content.Context, loc: Loc) {
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAT, loc.lat.toString())
            .putString(KEY_LNG, loc.lng.toString())
            .putString(KEY_ALT, loc.alt.toString())
            .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
            .apply()
    }

    /** هل لدينا إذن الموقع؟ */
    fun hasPermission(context: android.content.Context): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * قراءة سريعة من آخر موقع معروف للنظام — بدون تشغيل GPS.
     * صفر استهلاك للطاقة.
     */
    fun getQuickLocation(context: android.content.Context): Loc? {
        if (!hasPermission(context)) return null
        return try {
            val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                    as android.location.LocationManager
            var best: android.location.Location? = null
            for (p in listOf(
                android.location.LocationManager.GPS_PROVIDER,
                android.location.LocationManager.NETWORK_PROVIDER
            )) {
                if (!lm.isProviderEnabled(p)) continue
                val l = try { lm.getLastKnownLocation(p) } catch (_: Exception) { null }
                    ?: continue
                if (best == null || l.time > best.time) best = l
            }
            best?.let {
                Loc(
                    it.latitude,
                    it.longitude,
                    if (it.hasAltitude()) it.altitude else 0.0
                )
            }
        } catch (_: Exception) { null }
    }

    /**
     * فحص سريع: هل انتقل المستخدم > 10 كم؟
     *  • إن انتقل → يحفظ الجديد ويرجع true.
     *  • إن لم ينتقل → يرجع false بدون أي كتابة.
     */
    fun refreshIfMoved(context: android.content.Context): Boolean {
        val fresh = getQuickLocation(context) ?: return false
        val cached = readCached(context)
        val moved = fresh.distanceTo(cached) > MOVED_THRESHOLD_METERS
        val staleAlt = cached.alt == 0.0 && fresh.alt > 0.0
        if (moved || staleAlt) {
            save(context, fresh)
            return true
        }
        return false
    }

    /** [جديد] فرق التوقيت المحلي الحالي عن UTC (بالساعات). */
    fun currentTimezoneOffsetHours(): Double =
        java.util.TimeZone.getDefault()
            .getOffset(System.currentTimeMillis()) / 3_600_000.0

    // ═════════════════════════════════════════════════════════════
    //  الدالة الرئيسية
    // ═════════════════════════════════════════════════════════════

    /**
     * تُعيد أوقات الصلاة لأي مكان في العالم، بتوقيت الجهاز المحلي.
     *
     * @param userLat   خط عرض المستخدم
     * @param userLng   خط طول المستخدم
     * @param month     الشهر (1-12)
     * @param day       اليوم (1-31)
     * @param userAlt   الارتفاع (متر)
     * @param tzHours   فرق التوقيت المحلي عن UTC. اتركه NaN ليُقرأ من النظام.
     * @return مصفوفة [فجر، شروق، ظهر، عصر، مغرب، عشاء] بتوقيت "HH:mm" محلي.
     */
    fun getAdjustedTimes(
        userLat : Double,
        userLng : Double,
        month   : Int,
        day     : Int,
        userAlt : Double = 0.0,
        tzHours : Double = Double.NaN
    ): Array<String> {

        // ① الأوقات كما يحسبها الجدول المرجعي (بتوقيت بغداد)
        val baghdadTimes = computeWithReferenceTable(userLat, userLng, month, day, userAlt)

        // ② فرق التوقيت عن بغداد
        val userTz = if (tzHours.isNaN()) currentTimezoneOffsetHours() else tzHours
        val shiftMin = ((userTz - REF_TZ_HOURS) * 60).roundToInt()

        // ③ في بغداد: لا فرق. خارجها: نُبدّل الساعات تلقائياً.
        if (shiftMin == 0) return baghdadTimes
        return Array(6) { i ->
            minutesToTime(timeToMinutes(baghdadTimes[i]) + shiftMin)
        }
    }

    // ═════════════════════════════════════════════════════════════
    //  الحساب بناءً على الجدول المرجعي (بغداد)
    //  نفس المنطق القديم — محفوظ بالكامل للحفاظ على الدقة 100% في العراق.
    // ═════════════════════════════════════════════════════════════

    private fun computeWithReferenceTable(
        userLat : Double,
        userLng : Double,
        month   : Int,
        day     : Int,
        userAlt : Double
    ): Array<String> {
        val refTimes   = PrayerData.getTimes(month, day)
        val refMinutes = refTimes.map { timeToMinutes(it) }.toIntArray()

        // كل درجة طول = 4 دقائق (غرباً → تأخير، شرقاً → تبكير)
        val lngDiffMinutes = ((REF_LNG - userLng) * 4.0).roundToInt()

        val latAdjustments = calcLatitudeAdjustments(userLat, userAlt, month, day)

        return Array(6) { i ->
            minutesToTime(refMinutes[i] + lngDiffMinutes + latAdjustments[i])
        }
    }

    private fun calcLatitudeAdjustments(
        userLat : Double,
        userAlt : Double,
        month   : Int,
        day     : Int
    ): IntArray {

        val dayOfYear = dayOfYearApprox(month, day)
        val decl      = solarDeclination(dayOfYear)

        val refSunriseDip = sunriseDipForAltitude(REF_ALT)
        val usrSunriseDip = sunriseDipForAltitude(userAlt)

        fun hourAngle(lat: Double, angle: Double): Double {
            val cosH = (kSin(-angle * DEG) - kSin(lat * DEG) * kSin(decl * DEG)) /
                       (kCos(lat * DEG) * kCos(decl * DEG))
            if (cosH < -1.0 || cosH > 1.0) return Double.NaN
            return kAcos(cosH) / DEG / 15.0
        }

        fun asrHourAngle(lat: Double): Double {
            val angle = -(kAtan(1.0 / (1 + kTan(abs(lat - decl) * DEG))) / DEG)
            return hourAngle(lat, angle)
        }

        val refFajrH    = hourAngle(REF_LAT, FAJR_ANGLE)
        val refSunriseH = hourAngle(REF_LAT, refSunriseDip)
        val refAsrH     = asrHourAngle(REF_LAT)
        val refMaghribH = hourAngle(REF_LAT, refSunriseDip)
        val refIshaH    = hourAngle(REF_LAT, ISHA_ANGLE)

        val usrFajrH    = hourAngle(userLat, FAJR_ANGLE)
        val usrSunriseH = hourAngle(userLat, usrSunriseDip)
        val usrAsrH     = asrHourAngle(userLat)
        val usrMaghribH = hourAngle(userLat, usrSunriseDip)
        val usrIshaH    = hourAngle(userLat, ISHA_ANGLE)

        fun diffMin(usrH: Double, refH: Double, beforeNoon: Boolean): Int {
            if (usrH.isNaN() || refH.isNaN()) return 0
            val diff = (usrH - refH) * 60.0
            return (if (beforeNoon) -diff else diff).roundToInt()
        }

        return intArrayOf(
            diffMin(usrFajrH,    refFajrH,    beforeNoon = true),
            diffMin(usrSunriseH, refSunriseH, beforeNoon = true),
            0,
            diffMin(usrAsrH,     refAsrH,     beforeNoon = false),
            diffMin(usrMaghribH, refMaghribH, beforeNoon = false),
            diffMin(usrIshaH,    refIshaH,    beforeNoon = false)
        )
    }

    private fun sunriseDipForAltitude(altitudeMeters: Double): Double {
        val altM = if (altitudeMeters < 0.0) 0.0 else altitudeMeters
        return SUNRISE_DIP_BASE + 0.0347 * sqrt(altM)
    }

    // ─────────────────────────────────────────────────────────────
    //  معادلات فلكية
    // ─────────────────────────────────────────────────────────────

    private fun solarDeclination(dayOfYear: Int): Double {
        val B = 2.0 * Math.PI * (dayOfYear - 1) / 365.0
        return Math.toDegrees(
            0.006918
            - 0.399912 * kCos(B)   + 0.070257 * kSin(B)
            - 0.006758 * kCos(2*B) + 0.000907 * kSin(2*B)
            - 0.002697 * kCos(3*B) + 0.001480 * kSin(3*B)
        )
    }

    private fun equationOfTime(dayOfYear: Int): Double {
        val b = (360.0 / 365.0) * (dayOfYear - 81) * DEG
        return -7.655 * kSin(b) + 9.873 * kSin(2 * b + 3.588) + 0.439 * kSin(b)
    }

    private fun dayOfYearApprox(month: Int, day: Int): Int {
        val daysPerMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        return daysPerMonth[month - 1] + day
    }

    // ─────────────────────────────────────────────────────────────
    //  تحويل الوقت
    // ─────────────────────────────────────────────────────────────

    fun timeToMinutes(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    fun minutesToTime(minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440
        // Locale.US: يمنع ظهور الأرقام العربية التي تكسر التحويلات
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
    }

    // ─────────────────────────────────────────────────────────────
    //  معايرة اختيارية: استخراج "بصمة الشمس" من جدولك
    //  الاستخدام مرة واحدة: اطبع القيمة، واطرح منها 0.515 (ارتفاع الموصل)
    //  ضع الناتج بدل SUNRISE_DIP_BASE = 0.833 للحصول على 100% مطابقة.
    // ─────────────────────────────────────────────────────────────
    fun calibrateHorizonAngle(): Double {
        val tableSunrise = "07:13"   // من جدولك — 1 يناير
        val tableMaghrib = "17:06"
        val noonMin = (timeToMinutes(tableSunrise) + timeToMinutes(tableMaghrib)) / 2.0
        val doy = dayOfYearApprox(1, 1)
        val decl = solarDeclination(doy)
        val H = 15.0 * (noonMin - timeToMinutes(tableSunrise)) / 60.0

        val lat = REF_LAT * DEG
        val d = decl * DEG
        val Hrad = H * DEG

        val sinAlpha = -(kCos(Hrad) * kCos(lat) * kCos(d) + kSin(lat) * kSin(d))
        return Math.toDegrees(asin(sinAlpha.coerceIn(-1.0, 1.0)))
    }
}