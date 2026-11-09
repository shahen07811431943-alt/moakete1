#!/usr/bin/env bash
set -u
mkdir -p report
APK=$(ls app/build/outputs/apk/debug/*.apk 2>/dev/null | head -1)
D=report/details.txt
: > "$D"

if [ -z "$APK" ]; then
  echo "NO APK (build failed)" >> "$D"
  echo FAIL > report/install.status
  exit 0
fi

AAPT=$(ls $ANDROID_HOME/build-tools/*/aapt2 | tail -1)
PKG=$($AAPT dump packagename "$APK")
echo "Package: $PKG" >> "$D"

if adb install -r -g "$APK" > report/install.log 2>&1; then
  echo PASS > report/install.status
else
  echo FAIL > report/install.status
  cat report/install.log >> "$D"
  exit 0
fi

adb logcat -c
adb shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 10
adb exec-out screencap -p > report/screen_launch.png
if [ -n "$(adb shell pidof "$PKG")" ]; then echo PASS > report/launch.status; else echo FAIL > report/launch.status; fi

adb shell dumpsys alarm > report/alarms_before.txt
echo "Alarm entries after launch: $(grep -c "$PKG" report/alarms_before.txt)" >> "$D"

adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p "$PKG" >/dev/null 2>&1
sleep 5
adb shell dumpsys alarm > report/alarms_after.txt
AFTER=$(grep -c "$PKG" report/alarms_after.txt)
echo "Alarm entries after BOOT_COMPLETED: $AFTER" >> "$D"
if [ "$AFTER" -gt 0 ]; then echo PASS > report/alarms.status; else echo "FAIL (no alarms)" > report/alarms.status; fi

if adb shell monkey -p "$PKG" --throttle 300 -s 42 --pct-syskeys 0 -v 600 > report/monkey.log 2>&1; then echo PASS > report/monkey.status; else echo FAIL > report/monkey.status; fi
adb exec-out screencap -p > report/screen_after_monkey.png

adb logcat -d > report/logcat.txt
if grep -q "FATAL EXCEPTION" report/logcat.txt; then
  echo FAIL > report/crash.status
  grep -A12 "FATAL EXCEPTION" report/logcat.txt | head -n 60 >> "$D"
else
  echo PASS > report/crash.status
fi
if grep -q "ANR in $PKG" report/logcat.txt; then echo FAIL > report/anr.status; else echo PASS > report/anr.status; fi
