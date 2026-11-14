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
# Launch whatever the APK declares as its launcher activity. The code lives in dev.dache.android, not under the
# application id, so a hard-coded "$package/.MainActivity" names a class that doesn't exist.
activity="$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$package" | tail -n 1 | tr -d '\r')"
if [[ "$activity" != "$package/"* ]]; then
    echo "::error::$package has no launcher activity (resolve-activity said: $activity)"
    exit 1
fi
# am start exits 0 even when it can't start the activity, so check what it printed.
start="$(adb shell am start -W -n "$activity" | tr -d '\r')"
echo "$start"
if grep -q "^Error" <<< "$start"; then
    echo "::error::couldn't start $activity"
    exit 1
fi
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
