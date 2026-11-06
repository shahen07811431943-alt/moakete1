package com.moakete

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar

/**
 * تعرض توقيت جميع الصلوات الست لكل أيام الشهر الحالي دفعة واحدة،
 * على شكل جدول مضغوط بدل شاشة تقويم الاثني عشر شهراً الخاصة بصلاة واحدة.
 */
@Composable
fun MonthlyScheduleScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("moakete_prefs", Context.MODE_PRIVATE) }
    val userLat = remember {
        prefs.getString("user_lat_d", null)?.toDoubleOrNull()
            ?: prefs.getFloat("user_lat", LocationAdjuster.REF_LAT.toFloat()).toDouble()
    }
    val userLng = remember {
        prefs.getString("user_lng_d", null)?.toDoubleOrNull()
            ?: prefs.getFloat("user_lng", LocationAdjuster.REF_LNG.toFloat()).toDouble()
    }
    val userAlt = remember {
        prefs.getString("user_alt_d", null)?.toDoubleOrNull() ?: LocationAdjuster.REF_ALT
    }

    val today = remember { Calendar.getInstance() }
    val month = today.get(Calendar.MONTH) + 1
    val year = today.get(Calendar.YEAR)
    val todayOfMonth = today.get(Calendar.DAY_OF_MONTH)
    val monthLabel = remember(month, year) {
        SimpleDateFormat("MMMM yyyy", ARABIC_LOCALE).format(today.time)
    }

    val daysInMonth = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11            -> 30
        2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
        else -> 30
    }

    val monthData = remember(userLat, userLng, userAlt, month, year) {
        Array(daysInMonth) { idx -> LocationAdjuster.getAdjustedTimes(userLat, userLng, month, idx + 1, userAlt) }
    }

    val headers = listOf("اليوم", "فجر", "شروق", "ظهر", "عصر", "مغرب", "عشاء")

    Column(Modifier.fillMaxSize().background(AppColors.BgCream).padding(top = 40.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AppColors.TextDark)
            }
            Column {
                Text("توقيت الشهر", color = AppColors.TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(monthLabel, color = AppColors.Gold, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(10.dp))

        // رأس الجدول
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .background(AppColors.CardBg, RoundedCornerShape(8.dp))
                .padding(vertical = 8.dp)
        ) {
            headers.forEachIndexed { i, h ->
                Text(
                    h, color = AppColors.Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(if (i == 0) 0.8f else 1f)
                )
            }
        }

        HorizontalDivider(color = AppColors.TextDark.copy(alpha = 0.15f), modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))

        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            items(daysInMonth) { dayIdx ->
                val dayNum = dayIdx + 1
                val isToday = dayNum == todayOfMonth
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (isToday) AppColors.Gold.copy(alpha = 0.30f) else Color.Transparent,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$dayNum", color = if (isToday) AppColors.Gold else AppColors.TextDark,
                        fontSize = 12.sp, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(0.8f)
                    )
                    monthData[dayIdx].forEach { t ->
                        Text(
                            t, color = AppColors.TextDark.copy(alpha = 0.80f), fontSize = 11.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                HorizontalDivider(color = AppColors.TextDark.copy(alpha = 0.08f))
            }
        }
    }
}
