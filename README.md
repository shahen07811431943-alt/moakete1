# موقيت (moakete1)

تطبيق أندرويد لمواقيت الصلاة والأذكار.

## المميزات
- مواقيت الصلاة حسب الموقع مع تنبيه الأذان
- اتجاه القبلة
- جدول شهري للمواقيت
- قائمة الأذكار والأدعية
- تسجيل الدخول بحساب جوجل

## التقنيات
- Kotlin و Jetpack Compose
- Firebase (Auth و Firestore)
- WorkManager لإعادة جدولة الأذان

## تحميل التطبيق
من تبويب Actions ← آخر تشغيل ناجح ← Artifacts ← `moakete-debug-apk`

## البناء
تطبيق Android بإصدار JDK 17:

    ./gradlew assembleDebug

يُبنى تلقائيًا على GitHub Actions عند كل push.
