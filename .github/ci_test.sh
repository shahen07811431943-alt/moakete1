#!/bin/bash
mkdir -p report
APK=$(find . -name "*debug*.apk" -path "*outputs*" | head -1)
BT=$(ls -d $ANDROID_HOME/build-tools/* | tail -1)
PKG=$($BT/aapt dump badging "$APK" | sed -n "s/package: name='\([^']*\)'.*/\1/p")
echo "PKG=$PKG APK=$APK" > report/info.txt
$BT/aapt dump permissions "$APK" >> report/info.txt

adb install -r -g "$APK" > report/install.txt 2>&1
adb logcat -c
adb logcat -v time > report/logcat_full.txt &
LP=$!

# تشغيل التطبيق ولقطة أولى
adb shell monkey -p $PKG -c android.intent.category.LAUNCHER 1
sleep 8
adb exec-out screencap -p > report/01_start.png
adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml report/ui_home.xml
adb shell pidof $PKG > report/alive_after_start.txt || echo "CRASHED AT START" >> report/alive_after_start.txt

# ضغط عشوائي مكثف
adb shell monkey -p $PKG --throttle 300 --pct-syskeys 0 -s 42 -v 3000 > report/monkey.txt 2>&1
adb exec-out screencap -p > report/02_after_monkey.png
sleep 3
kill $LP

# تلخيص
{
  echo "# تقرير الفحص"
  echo "## الانهيارات (FATAL)"
  grep -A12 "FATAL EXCEPTION" report/logcat_full.txt | head -80
  echo "## تجمّد (ANR)"
  grep "ANR in" report/logcat_full.txt | head
  echo "## أخطاء التطبيق"
  grep -E "E/.*($PKG|AndroidRuntime)" report/logcat_full.txt | sort | uniq -c | sort -rn | head -40
  echo "## نتيجة monkey"
  grep -E "crashed|ANR|Events injected" report/monkey.txt
  echo "## هل بقي التطبيق حياً؟"
  adb shell pidof $PKG || echo "لا، انهار"
} > report/SUMMARY.md
