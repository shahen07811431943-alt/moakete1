package com.moakete

import androidx.compose.ui.graphics.Color

/**
 * لوحة الألوان الموحدة للتطبيق بالكامل — مبنية على ألوان شاشة الأذكار
 * (خلفية كريمية فاتحة + نصوص بنية غامقة + ذهبي واحد فقط للتمييز).
 *
 * ملاحظة مهمة: يوجد لون ذهبي واحد فقط (Gold) يُستخدم في كل الشاشات —
 * للعداد التنازلي، الأيقونات، الحدود، والعناصر المُفعّلة. لا تُنشئ درجة
 * ذهبي إضافية في أي شاشة؛ استورد هذا اللون دائماً حتى يبقى التطبيق متناسقاً.
 */
object AppColors {
    val BgCream    = Color(0xFFF5EDD8)   // الخلفية الرئيسية الثابتة لكل الشاشات (لا يوجد بدائل)
    val CardBg     = Color(0xFFFFFFFF)   // خلفية الكروت
    val CardBgSoft = Color(0xFFF5EDD8)   // خلفية كروت ثانوية / أقسام
    val TextDark   = Color(0xFF3B2D1A)   // النص الأساسي
    val TextSub    = Color(0xFF3B2D1A).copy(alpha = 0.60f) // نص ثانوي
    val TextFaint  = Color(0xFF3B2D1A).copy(alpha = 0.35f) // نص خافت جدًا
    val Gold       = Color(0xFF8A6A2F)   // الذهبي الوحيد في التطبيق (أيقونات، حدود، عداد، تمييز)
    val GreenOk    = Color(0xFF4CAF50)
    val RedMissing = Color(0xFFB11E3A)
    val RedSoft    = Color(0xFFB94A48)
}
