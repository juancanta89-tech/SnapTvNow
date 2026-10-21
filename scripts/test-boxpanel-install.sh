#!/usr/bin/env bash
set -euo pipefail
box_a='com.snaptvnow.box.a11111111111141118111111111111111'
box_b='com.snaptvnow.box.a22222222222242228222222222222222'
adb install -r app/build/boxpanel-fixtures/a1.apk
adb install -r app/build/boxpanel-fixtures/b1.apk
adb shell "run-as $box_a sh -c 'mkdir -p files'"
adb shell "run-as $box_b sh -c 'mkdir -p files'"
adb shell "run-as $box_a sh -c 'echo reseller-a > files/boxpanel-marker'"
adb shell "run-as $box_b sh -c 'echo reseller-b > files/boxpanel-marker'"
adb install -r app/build/boxpanel-fixtures/a2.apk
test "$(adb shell run-as "$box_a" cat files/boxpanel-marker | tr -d '\r')" = reseller-a
test "$(adb shell run-as "$box_b" cat files/boxpanel-marker | tr -d '\r')" = reseller-b
adb shell dumpsys package "$box_a" | grep -q 'versionCode=2 '
adb shell dumpsys package "$box_b" | grep -q 'versionCode=1 '
printf 'PASS: separate installed packages retain isolated data through an in-place signed update.\n'
