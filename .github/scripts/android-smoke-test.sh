#!/usr/bin/env bash
# Installs an APK on the running emulator, launches MainActivity and fails if the process dies or logs a crash.
# Even without UI tests this catches R8 stripping a serializer or a Ktor engine from the release build.
set -euo pipefail

apk="$1"
package="dev.dache"

adb wait-for-device
adb shell input keyevent 82 || true
adb install -r "$apk"
adb logcat -c
adb shell am start -W -n "$package/.MainActivity"
sleep 10

if ! adb shell pidof "$package" > /dev/null; then
    echo "::error::$package is not running ten seconds after launch"
    adb logcat -d -s AndroidRuntime:E | tail -n 80
    exit 1
fi
if adb logcat -d | grep -E "FATAL EXCEPTION|Process: $package.*died" | grep -q "$package"; then
    echo "::error::$package crashed after launch"
    adb logcat -d -s AndroidRuntime:E | tail -n 80
    exit 1
fi
echo "$package launched and stayed up"
