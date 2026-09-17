#!/usr/bin/env bash
# Stream logcat for the debug app, filtered to warnings/errors/crashes only.
# Useful when the app is already running and you just want to watch for issues.
set -euo pipefail

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$ANDROID_HOME/platform-tools/adb"
APP_ID="com.codesculptor.smartertube.debug"

PID=$("$ADB" shell pidof "$APP_ID" | tr -d '\r')
if [ -z "$PID" ]; then
    echo "$APP_ID is not running. Launch it on the device first." >&2
    exit 1
fi

echo "== Streaming logcat for pid=$PID (Ctrl+C to stop) =="
"$ADB" logcat -v time --pid="$PID" "*:W" AndroidRuntime:E "$APP_ID":V
