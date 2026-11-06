package com.moakete

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// إحداثيات الكعبة المشرفة
private const val KAABA_LAT = 21.4225
private const val KAABA_LNG = 39.8262

/**
 * تحسب زاوية اتجاه القبلة (bearing) بالدرجات من الشمال الحقيقي،
 * انطلاقاً من موقع المستخدم نحو الكعبة المشرفة.
 */
private fun calculateQiblaBearing(userLat: Double, userLng: Double): Double {
    val lat1 = Math.toRadians(userLat)
    val lat2 = Math.toRadians(KAABA_LAT)
    val deltaLng = Math.toRadians(KAABA_LNG - userLng)

    val y = sin(deltaLng) * cos(lat2)
    val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLng)
    val bearing = Math.toDegrees(atan2(y, x))
    return (bearing + 360.0) % 360.0
}

@Composable
fun QiblaScreen(onBack: () -> Unit) {
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
    val qiblaBearing = remember(userLat, userLng) { calculateQiblaBearing(userLat, userLng) }

    var azimuth by remember { mutableFloatStateOf(0f) }
    var sensorAvailable by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        if (rotationSensor == null) {
            sensorAvailable = false
            onDispose { }
        } else {
            val rotationMatrix = FloatArray(9)
            val orientationValues = FloatArray(3)

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientationValues)
                    val degrees = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
                    azimuth = (degrees + 360f) % 360f
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    // الزاوية المطلوب تدويرها للسهم حتى يشير للقبلة بالنسبة لاتجاه الجهاز الحالي
    val needleRotation = (qiblaBearing - azimuth + 360.0).toFloat() % 360f
    val diffFromCenter = minOf(needleRotation, 360f - needleRotation)
    val isAligned = diffFromCenter <= 5f

    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.BgCream)
            .padding(top = 40.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = AppColors.TextDark)
            }
            Text("اتجاه القبلة", color = AppColors.TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        if (!sensorAvailable) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("جهازك لا يحتوي على حساس البوصلة اللازم", color = AppColors.TextDark, fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "زاوية القبلة من موقعك: ${qiblaBearing.roundToInt()}° من الشمال",
                    color = AppColors.Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (isAligned) "أنت متجه الآن نحو الكعبة 🕋" else "وجّه الجهاز حتى يستقر السهم في الأعلى",
                    color = if (isAligned) Color(0xFF4CAF50) else AppColors.TextDark.copy(alpha = 0.8f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(30.dp))

                Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val radius = size.minDimension / 2f
                        // دائرة البوصلة الثابتة (تدور مع الجهاز)
                        rotate(-azimuth) {
                            drawCircle(
                                color = AppColors.Gold.copy(alpha = 0.4f),
                                radius = radius,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                            )
                            // علامة الشمال
                            drawContext.canvas.nativeCanvas.apply {
                                drawText(
                                    "N",
                                    center.x,
                                    center.y - radius + 40f,
                                    android.graphics.Paint().apply {
                                        color = android.graphics.Color.parseColor("#3B2D1A")
                                        textSize = 32f
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        isFakeBoldText = true
                                    }
                                )
                            }
                        }
                        // سهم القبلة (يدور حسب bearing القبلة الفعلي مطروحاً منه اتجاه الجهاز)
                        rotate(needleRotation) {
                            val arrowColor = if (isAligned) Color(0xFF4CAF50) else AppColors.Gold
                            drawLine(
                                color = arrowColor,
                                start = Offset(center.x, center.y),
                                end = Offset(center.x, center.y - radius + 20f),
                                strokeWidth = 10f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            drawCircle(color = arrowColor, radius = 14f, center = Offset(center.x, center.y - radius + 20f))
                        }
                        drawCircle(color = AppColors.TextDark, radius = 8f, center = center)
                    }
                }

                Spacer(Modifier.height(30.dp))
                Text(
                    "زاوية القبلة: ${qiblaBearing.roundToInt()}° من الشمال",
                    color = AppColors.TextSub, fontSize = 14.sp
                )
                Text(
                    "الموقع المستخدم في الحساب مأخوذ من إعدادات الموقع في التطبيق",
                    color = AppColors.TextDark.copy(alpha = 0.5f), fontSize = 12.sp
                )
            }
        }
    }
}
