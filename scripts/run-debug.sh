#!/usr/bin/env bash
# Build, install and launch the stmobile debug build on the connected device,
# then stream its logcat (crashes highlighted) until Ctrl+C.
set -euo pipefail

cd "$(dirname "$0")/.."

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17)}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
APP_ID="com.codesculptor.smartertube.debug"
LAUNCHER_ACTIVITY="com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity"

echo "== Building stmobile debug =="
./gradlew :smarttubetv:assembleStmobileDebug

DEVICE_COUNT=$("$ADB" devices | grep -c "	device$" || true)
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "No device connected (adb devices shows none). Plug in the phone and enable USB debugging." >&2
    exit 1
fi

ABI=$("$ADB" shell getprop ro.product.cpu.abi | tr -d '\r')
APK="smarttubetv/build/outputs/apk/stmobile/debug/$(basename "$(ls smarttubetv/build/outputs/apk/stmobile/debug/*-"$ABI".apk)")"

echo "== Installing $APK (abi=$ABI) =="
"$ADB" install -r "$APK"

echo "== Launching =="
"$ADB" shell am start -n "$APP_ID/$LAUNCHER_ACTIVITY"

echo "== Waiting for process to start =="
PID=""
for _ in $(seq 1 20); do
    PID=$("$ADB" shell pidof "$APP_ID" | tr -d '\r')
    [ -n "$PID" ] && break
    sleep 0.5
done
if [ -z "$PID" ]; then
    echo "App did not start within 10s." >&2
    exit 1
fi

echo "== Streaming logcat for pid=$PID (Ctrl+C to stop) =="
"$ADB" logcat -v time --pid="$PID" "*:W" AndroidRuntime:E "$APP_ID":V
